package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import com.example.security.*
import kotlinx.coroutines.*

class TalkBackTransmitter(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var session: SecureSession? = null
    @Volatile private var record: AudioRecord? = null
    var onStatusChanged: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    @SuppressLint("MissingPermission") @Synchronized fun startTransmitting(room: PairedRoom) {
        if (job?.isActive == true) return
        val previous = job
        job = scope.launch {
            previous?.join()
            var local: AudioRecord? = null
            var connection: SecureSession? = null
            try {
                check(!room.enrolling) { "Terminez l'appairage avant de parler" }
                connection = SecureTransport(context).connect(room, "talk", 9879); session = connection
                val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(min > 0)
                local = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, 4096)); record = local
                check(local.state == AudioRecord.STATE_INITIALIZED) { "Microphone indisponible" }
                local.startRecording(); check(local.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                onStatusChanged?.invoke(true)
                val buffer = ByteArray(2048)
                while (isActive) {
                    val read = local.read(buffer, 0, buffer.size); check(read > 0) { "Interphone interrompu" }
                    connection.send(buffer.copyOf(read))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { onError?.invoke(e.message ?: "Interphone indisponible") }
            finally {
                connection?.close(); runCatching { local?.stop() }; runCatching { local?.release() }
                record = null; session = null; onStatusChanged?.invoke(false)
            }
        }
    }
    fun checkDeadline() = session?.checkWriteDeadline()
    @Synchronized fun stopTransmitting() { job?.cancel(); session?.close(); runCatching { record?.stop() } }
    fun release() { stopTransmitting(); scope.cancel() }
}
