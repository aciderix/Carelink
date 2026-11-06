package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.log10
import kotlin.math.sqrt

class AudioCaptureEngine(private val context: Context, private val roomId: String, private val roomNameProvider: () -> String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val lifecycle = Mutex()
    private var recorder: AudioRecord? = null
    private val detector = AcousticDetector()
    @Volatile var isRecording = false; private set
    @Volatile var currentLevelDb = 0; private set
    @Volatile var thresholdDb = 55
    @Volatile var minNoiseDurationMs = 300
    @Volatile var samplesCaptured = 0L; private set
    @Volatile var lastSampleMs = 0L; private set
    @Volatile var errorMessage: String? = "Capture non démarrée"; private set
    @Volatile private var silenced = false
    @Volatile private var zeroSince = 0L
    @Volatile private var clipped = false
    val isOperational get() = isRecording && !silenced && lastSampleMs > 0 &&
        SystemClock.elapsedRealtime() - lastSampleMs < 2000 && !(zeroSince > 0 && SystemClock.elapsedRealtime() - zeroSince > 3000)
    @Volatile var analysisPaused = false
    val isAlertActive get() = detector.active
    val captureAgeMs get() = if (lastSampleMs == 0L) Long.MAX_VALUE else SystemClock.elapsedRealtime() - lastSampleMs
    val diagnostic: String? get() = when {
        analysisPaused -> "Interphone actif : analyse acoustique suspendue"
        !isRecording -> errorMessage ?: "Capture interrompue"
        silenced -> "Capture rendue silencieuse par Android"
        captureAgeMs >= 2000 -> "Aucun échantillon récent"
        zeroSince > 0 && SystemClock.elapsedRealtime() - zeroSince > 3000 -> "Silence numérique prolongé : vérifier le microphone"
        clipped -> "Saturation du microphone : vérifier le placement"
        else -> null
    }
    var onPacket: ((ByteArray) -> Unit)? = null
    var onAlertTriggered: ((RoomAlertEvent) -> Unit)? = null
    var onCriticalError: ((String) -> Unit)? = null

    @SuppressLint("MissingPermission")
    @Synchronized fun startCapture() {
        if (job?.isActive == true) return
        job = scope.launch { lifecycle.withLock {
            var local: AudioRecord? = null
            try {
                check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "Permission microphone absente" }
                val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(minimum > 0) { "Format microphone indisponible" }
                local = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum * 2, 4096))
                recorder = local
                check(local.state == AudioRecord.STATE_INITIALIZED) { "Microphone inaccessible" }
                if (Build.VERSION.SDK_INT >= 29) local.registerAudioRecordingCallback(context.mainExecutor,
                    object : AudioManager.AudioRecordingCallback() {
                        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                            silenced = local.activeRecordingConfiguration?.isClientSilenced == true
                        }
                    })
                local.startRecording()
                check(local.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Capture non démarrée" }
                detector.reset(); silenced = false; lastSampleMs = 0; zeroSince = 0; isRecording = true; errorMessage = null
                var episode: RoomAlertEvent? = null
                var episodeStarted = 0L
                var peakInEpisode = 0
                val buffer = ShortArray(1024)
                while (isActive) {
                    val read = local.read(buffer, 0, buffer.size)
                    check(read >= 0) { "Erreur microphone ($read)" }
                    if (read == 0) { delay(20); continue }
                    val now = SystemClock.elapsedRealtime()
                    var sum = 0.0; var peak = 0
                    val bytes = ByteArray(read * 2)
                    for (i in 0 until read) {
                        val n = buffer[i].toInt(); sum += n.toDouble() * n; peak = maxOf(peak, kotlin.math.abs(n))
                        bytes[i * 2] = n.toByte(); bytes[i * 2 + 1] = (n shr 8).toByte()
                    }
                    lastSampleMs = now; samplesCaptured += read
                    if (peak == 0) { if (zeroSince == 0L) zeroSince = now } else zeroSince = 0
                    clipped = peak >= 32760
                    val rms = sqrt(sum / read)
                    currentLevelDb = if (rms > 1) (20 * log10(rms)).toInt().coerceIn(0, 91) else 0
                    if (Build.VERSION.SDK_INT >= 29) silenced = local.activeRecordingConfiguration?.isClientSilenced == true
                    val wasActive = detector.active
                    if (!analysisPaused && isOperational && detector.process(currentLevelDb, thresholdDb, minNoiseDurationMs, now)) {
                        episodeStarted = now - minNoiseDurationMs
                        peakInEpisode = currentLevelDb
                        episode = RoomAlertEvent(roomId = roomId, roomName = roomNameProvider(),
                            timestamp = System.currentTimeMillis() - minNoiseDurationMs,
                            levelReachedDb = currentLevelDb, thresholdDb = thresholdDb, eventType = AlertEventType.SOUND_ALERT,
                            notes = "Seuil relatif dépassé", remote = true)
                        onAlertTriggered?.invoke(episode)
                    }
                    if (detector.active) peakInEpisode = maxOf(peakInEpisode, currentLevelDb)
                    if (wasActive && (!detector.active || analysisPaused || !isOperational)) {
                        episode?.let { onAlertTriggered?.invoke(it.copy(levelReachedDb = peakInEpisode,
                            durationSeconds = ((now - episodeStarted) / 1000).toInt())) }
                        episode = null
                        if (analysisPaused || !isOperational) detector.reset()
                    }
                    if (!silenced) onPacket?.invoke(bytes)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { errorMessage = e.message ?: "Capture interrompue"; onCriticalError?.invoke(errorMessage!!) }
            finally {
                isRecording = false; detector.reset()
                runCatching { local?.stop() }; runCatching { local?.release() }
                recorder = null
            }
        } }
    }
    @Synchronized fun stopCapture() {
        job?.cancel(); runCatching { recorder?.stop() }; isRecording = false
    }
    fun release() { stopCapture(); scope.cancel() }
    fun triggerManualNoiseSpike() {
        onAlertTriggered?.invoke(RoomAlertEvent(roomId = roomId, roomName = roomNameProvider(),
            thresholdDb = thresholdDb, eventType = AlertEventType.TEST, notes = "Test de transmission, sans validation du microphone", remote = true))
    }
}
