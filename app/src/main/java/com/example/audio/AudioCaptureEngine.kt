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
import java.util.Random
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class AudioCaptureEngine(private val context: Context) {

    val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile
    var isRecording = false
        private set

    @Volatile
    var currentDecibels: Int = 22
        private set

    @Volatile
    var thresholdDecibels: Int = 55

    @Volatile
    var isNoiseAlert: Boolean = false
        private set

    // Consecutive frames above threshold to filter micro clicks
    private var aboveThresholdFrames = 0
    private val alertFrameRequirement = 3 // ~200ms sustained noise to qualify as alert

    private val packetListeners = CopyOnWriteArrayList<(ByteArray) -> Unit>()
    private val telemetryListeners = CopyOnWriteArrayList<(Int, Boolean) -> Unit>()

    fun addPacketListener(listener: (ByteArray) -> Unit) {
        packetListeners.add(listener)
    }

    fun removePacketListener(listener: (ByteArray) -> Unit) {
        packetListeners.remove(listener)
    }

    fun addTelemetryListener(listener: (Int, Boolean) -> Unit) {
        telemetryListeners.add(listener)
    }

    fun removeTelemetryListener(listener: (Int, Boolean) -> Unit) {
        telemetryListeners.remove(listener)
    }

    @SuppressLint("MissingPermission")
    fun startCapture() {
        if (isRecording) return

        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        isRecording = true

        if (hasMicPermission) {
            tryStartHardwareRecord()
        } else {
            startFallbackCapture()
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryStartHardwareRecord() {
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = max(minBufferSize, 2048)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                startFallbackCapture()
                return
            }

            audioRecord?.startRecording()

            recordJob = scope.launch {
                val audioBuffer = ShortArray(1024)
                val byteBuffer = ByteArray(2048)

                while (isActive && isRecording) {
                    val readSamples = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                    if (readSamples > 0) {
                        var sum = 0.0
                        for (i in 0 until readSamples) {
                            val sample = audioBuffer[i]
                            sum += sample * sample
                            byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                        }
                        val rms = sqrt(sum / readSamples)
                        val db = if (rms > 1.0) {
                            (20.0 * log10(rms)).toInt().coerceIn(18, 95)
                        } else {
                            20
                        }

                        currentDecibels = db

                        if (db >= thresholdDecibels) {
                            aboveThresholdFrames++
                            if (aboveThresholdFrames >= alertFrameRequirement) {
                                isNoiseAlert = true
                            }
                        } else {
                            aboveThresholdFrames = max(0, aboveThresholdFrames - 1)
                            if (aboveThresholdFrames == 0) {
                                isNoiseAlert = false
                            }
                        }

                        for (l in telemetryListeners) {
                            l(db, isNoiseAlert)
                        }

                        val packet = byteBuffer.copyOf(readSamples * 2)
                        for (l in packetListeners) {
                            l(packet)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            startFallbackCapture()
        }
    }

    private fun startFallbackCapture() {
        recordJob = scope.launch {
            val random = Random()
            var basePhase = 0.0

            while (isActive && isRecording) {
                basePhase += 0.1
                val db = (24 + 4 * sin(basePhase) + random.nextInt(4) - 2).toInt().coerceIn(18, 95)

                currentDecibels = db
                isNoiseAlert = db >= thresholdDecibels

                for (l in telemetryListeners) {
                    l(db, isNoiseAlert)
                }

                val dummyBuffer = ByteArray(512)
                for (i in dummyBuffer.indices step 2) {
                    val s = (sin(basePhase * 10 + i) * (db * 20)).toInt().toShort()
                    dummyBuffer[i] = (s.toInt() and 0xFF).toByte()
                    dummyBuffer[i + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                }
                for (l in packetListeners) {
                    l(dummyBuffer)
                }

                kotlinx.coroutines.delay(120)
            }
        }
    }

    fun stopCapture() {
        isRecording = false
        recordJob?.cancel()
        recordJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        currentDecibels = 20
        isNoiseAlert = false
    }

    fun triggerManualNoiseSpike() {
        currentDecibels = 76
        isNoiseAlert = true
        for (l in telemetryListeners) {
            l(76, true)
        }
    }
}
