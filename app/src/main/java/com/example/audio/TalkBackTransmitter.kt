package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class TalkBackTransmitter(private val context: Context) {

    private val sampleRate = 16000
    private val scope = CoroutineScope(Dispatchers.IO)
    private var transmitJob: Job? = null
    private var socket: Socket? = null
    private var audioRecord: AudioRecord? = null

    @Volatile
    var isTransmitting = false
        private set

    var onStatusChanged: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startTransmitting(targetIp: String, targetPort: Int = 9879) {
        if (isTransmitting) return

        val hasMic = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMic) {
            onError?.invoke("Permission microphone manquante pour l'interphone")
            return
        }

        isTransmitting = true
        onStatusChanged?.invoke(true)

        transmitJob = scope.launch {
            var outputStream: OutputStream? = null
            try {
                val newSocket = Socket()
                newSocket.connect(InetSocketAddress(targetIp, targetPort), 3000)
                socket = newSocket
                outputStream = newSocket.getOutputStream()

                val minBuf = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = (minBuf * 2).coerceAtLeast(2048)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    onError?.invoke("Microphone indisponible")
                    stopTransmitting()
                    return@launch
                }

                audioRecord?.startRecording()
                val buffer = ByteArray(1024)

                while (isActive && isTransmitting && !newSocket.isClosed) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        outputStream.write(buffer, 0, read)
                    } else if (read < 0) {
                        break
                    }
                }
            } catch (e: Exception) {
                if (isTransmitting) {
                    onError?.invoke("Échec de connexion interphone : ${e.message}")
                }
            } finally {
                cleanUp()
            }
        }
    }

    fun stopTransmitting() {
        isTransmitting = false
        transmitJob?.cancel()
        transmitJob = null
        cleanUp()
        onStatusChanged?.invoke(false)
    }

    private fun cleanUp() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
    }
}
