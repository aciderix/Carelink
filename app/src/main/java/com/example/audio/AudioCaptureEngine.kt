package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.example.model.AlertEventType
import com.example.model.RoomAlertEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

class AudioCaptureEngine(
    private val context: Context,
    private val roomId: String,
    private val roomNameProvider: () -> String
) {

    val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    var isRecording = false
        private set

    @Volatile
    var isOperational = true
        private set

    @Volatile
    var errorMessage: String? = null
        private set

    @Volatile
    var currentLevelDb: Int = 0
        private set

    @Volatile
    var thresholdDb: Int = 55

    @Volatile
    var minNoiseDurationMs: Int = 300

    @Volatile
    var isAlertActive: Boolean = false
        private set

    // Hysteresis & timing
    private val hysteresisDb = 6
    private val calmDelayMs = 1500L
    private var noiseStartTime = 0L
    private var belowThresholdStartTime = 0L
    private var currentAlertStartTime = 0L

    // Offline event buffer to store events during network outages
    val offlineEventQueue = ConcurrentLinkedQueue<RoomAlertEvent>()

    private val packetListeners = CopyOnWriteArrayList<(ByteArray) -> Unit>()
    private val telemetryListeners = CopyOnWriteArrayList<(Int, Boolean, Boolean) -> Unit>() // levelDb, isAlert, isOperational
    var onAlertTriggered: ((RoomAlertEvent) -> Unit)? = null
    var onCriticalError: ((String) -> Unit)? = null

    fun addPacketListener(listener: (ByteArray) -> Unit) {
        packetListeners.add(listener)
    }

    fun removePacketListener(listener: (ByteArray) -> Unit) {
        packetListeners.remove(listener)
    }

    fun addTelemetryListener(listener: (Int, Boolean, Boolean) -> Unit) {
        telemetryListeners.add(listener)
    }

    fun removeTelemetryListener(listener: (Int, Boolean, Boolean) -> Unit) {
        telemetryListeners.remove(listener)
    }

    @SuppressLint("MissingPermission")
    fun startCapture() {
        if (isRecording) return

        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMicPermission) {
            isOperational = false
            errorMessage = "Permission microphone non accordée"
            onCriticalError?.invoke("Permission microphone manquante")
            notifyTelemetry(0, false, false)
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize <= 0) {
            isOperational = false
            errorMessage = "Configuration audio non supportée sur cet appareil"
            onCriticalError?.invoke("Matériel audio incompatible")
            notifyTelemetry(0, false, false)
            return
        }

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
                isOperational = false
                errorMessage = "Échec d'initialisation du microphone"
                onCriticalError?.invoke("Microphone inaccessible ou occupé")
                notifyTelemetry(0, false, false)
                return
            }

            audioRecord?.startRecording()
            isRecording = true
            isOperational = true
            errorMessage = null

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
                        // Relative acoustic decibel scale: 18 dB (silence) to 95 dB (loud scream/call)
                        val db = if (rms > 1.0) {
                            (20.0 * log10(rms)).toInt().coerceIn(18, 95)
                        } else {
                            18
                        }

                        currentLevelDb = db
                        processAcousticAnalysis(db)

                        // Dispatch audio packet sequentially to registered listeners
                        val packet = byteBuffer.copyOf(readSamples * 2)
                        for (l in packetListeners) {
                            try { l(packet) } catch (_: Exception) {}
                        }
                    } else if (readSamples < 0) {
                        // Hardware error while recording
                        isOperational = false
                        errorMessage = "Erreur de lecture du flux microphone"
                        onCriticalError?.invoke("Interruption du flux microphone")
                        notifyTelemetry(0, false, false)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            isOperational = false
            errorMessage = "Erreur microphone : ${e.message}"
            onCriticalError?.invoke("Erreur microphone : ${e.message}")
            notifyTelemetry(0, false, false)
        }
    }

    private fun processAcousticAnalysis(db: Int) {
        val now = System.currentTimeMillis()

        if (db >= thresholdDb) {
            belowThresholdStartTime = 0L
            if (noiseStartTime == 0L) {
                noiseStartTime = now
            } else if (now - noiseStartTime >= minNoiseDurationMs) {
                if (!isAlertActive) {
                    isAlertActive = true
                    currentAlertStartTime = now
                    val alertEvent = RoomAlertEvent(
                        eventId = UUID.randomUUID().toString(),
                        roomId = roomId,
                        roomName = roomNameProvider(),
                        timestamp = now,
                        levelReachedDb = db,
                        thresholdDb = thresholdDb,
                        eventType = AlertEventType.SOUND_ALERT,
                        durationSeconds = 1,
                        notes = "Bruit anormal (> $thresholdDb dB)"
                    )
                    offlineEventQueue.offer(alertEvent)
                    onAlertTriggered?.invoke(alertEvent)
                }
            }
        } else {
            noiseStartTime = 0L
            // Hysteresis: to clear alert, level must stay below (threshold - hysteresisDb)
            if (db < (thresholdDb - hysteresisDb)) {
                if (belowThresholdStartTime == 0L) {
                    belowThresholdStartTime = now
                } else if (now - belowThresholdStartTime >= calmDelayMs) {
                    isAlertActive = false
                    currentAlertStartTime = 0L
                }
            }
        }

        notifyTelemetry(db, isAlertActive, isOperational)
    }

    private fun notifyTelemetry(db: Int, alert: Boolean, operational: Boolean) {
        for (l in telemetryListeners) {
            l(db, alert, operational)
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
        currentLevelDb = 0
        isAlertActive = false
    }

    fun drainOfflineEvents(): List<RoomAlertEvent> {
        val list = mutableListOf<RoomAlertEvent>()
        while (!offlineEventQueue.isEmpty()) {
            offlineEventQueue.poll()?.let { list.add(it) }
        }
        return list
    }

    fun triggerManualNoiseSpike() {
        val now = System.currentTimeMillis()
        currentLevelDb = 78
        isAlertActive = true
        val alertEvent = RoomAlertEvent(
            eventId = UUID.randomUUID().toString(),
            roomId = roomId,
            roomName = roomNameProvider(),
            timestamp = now,
            levelReachedDb = 78,
            thresholdDb = thresholdDb,
            eventType = AlertEventType.SOUND_ALERT,
            durationSeconds = 3,
            notes = "Test alerte acoustique"
        )
        offlineEventQueue.offer(alertEvent)
        onAlertTriggered?.invoke(alertEvent)
        notifyTelemetry(78, true, true)
    }
}
