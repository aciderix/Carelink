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

/** One way of opening the microphone. Rates above 16 kHz are averaged down to 16 kHz. */
internal data class CaptureConfig(val source: Int, val rate: Int, val label: String) { val factor get() = rate / AudioCaptureEngine.RATE }

class AudioCaptureEngine(private val context: Context, private val roomId: String, private val roomNameProvider: () -> String) {
    companion object {
        const val RATE = 16000
        /** Exact digital zeros for this long mean a muted, gated or blocked source. */
        const val ZERO_LIMIT_MS = 10_000L
        /** Silent sources are swapped before [ZERO_LIMIT_MS], so a gated source never shows as a fault. */
        const val ROTATE_MS = 6_000L
        /**
         * Some devices (older Samsung tablets in particular) return pure zeros or refuse 16 kHz on the default source.
         * Sources are tried in order and the first one that delivers a real signal is remembered.
         */
        internal val CONFIGS = listOf(
            CaptureConfig(MediaRecorder.AudioSource.MIC, 16000, "Micro standard"),
            CaptureConfig(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, "Micro sans traitement"),
            CaptureConfig(MediaRecorder.AudioSource.CAMCORDER, 16000, "Micro vidéo"),
            CaptureConfig(MediaRecorder.AudioSource.MIC, 48000, "Micro standard 48 kHz"),
            CaptureConfig(MediaRecorder.AudioSource.VOICE_RECOGNITION, 48000, "Micro sans traitement 48 kHz"),
            CaptureConfig(MediaRecorder.AudioSource.DEFAULT, 48000, "Source par défaut 48 kHz"),
        )
        /** Averages groups of [factor] samples (simple low-pass then decimation). */
        internal fun decimate(input: ShortArray, count: Int, factor: Int): ShortArray {
            if (factor <= 1) return input.copyOf(count)
            return ShortArray(count / factor) { i ->
                var sum = 0
                for (j in 0 until factor) sum += input[i * factor + j]
                (sum / factor).toShort()
            }
        }
    }
    private val settings = context.getSharedPreferences("carelink_capture", Context.MODE_PRIVATE)
    @Volatile private var configIndex = settings.getInt("config", 0).coerceIn(0, CONFIGS.lastIndex)
    /** Label of the microphone source in use, for diagnostics. */
    val sourceLabel: String get() = CONFIGS[configIndex].label
    private fun nextConfig() { configIndex = (configIndex + 1) % CONFIGS.size }
    private class SourceSwitch : Exception()
    /** True between closing a silent source and opening the next one; the caller restarts capture at once. */
    @Volatile var switching = false; private set
    /** Consecutive sources that delivered only zeros; once all were tried, the silence is reported. */
    @Volatile private var silentRotations = 0
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
        SystemClock.elapsedRealtime() - lastSampleMs < 2000 && !(zeroSince > 0 && SystemClock.elapsedRealtime() - zeroSince > ZERO_LIMIT_MS)
    @Volatile var analysisPaused = false
    val isAlertActive get() = detector.active
    val captureAgeMs get() = if (lastSampleMs == 0L) Long.MAX_VALUE else SystemClock.elapsedRealtime() - lastSampleMs
    val diagnostic: String? get() = when {
        analysisPaused -> "Interphone actif : analyse acoustique suspendue"
        !isRecording -> errorMessage ?: "Capture interrompue"
        silenced -> "Capture rendue silencieuse par Android"
        captureAgeMs >= 2000 -> "Aucun échantillon récent"
        zeroSince > 0 && SystemClock.elapsedRealtime() - zeroSince > ZERO_LIMIT_MS -> "Silence numérique prolongé ($sourceLabel) : vérifier le microphone"
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
            val config = CONFIGS[configIndex]
            try {
                check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "Permission microphone absente" }
                val minimum = AudioRecord.getMinBufferSize(config.rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (minimum <= 0) { nextConfig(); error("Format ${config.rate / 1000} kHz refusé (${config.label})") }
                local = try {
                    AudioRecord(config.source, config.rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum * 2, 4096 * config.factor))
                } catch (e: Exception) { nextConfig(); throw IllegalStateException("${config.label} indisponible", e) }
                recorder = local
                if (local.state != AudioRecord.STATE_INITIALIZED) { nextConfig(); error("Microphone inaccessible (${config.label})") }
                if (Build.VERSION.SDK_INT >= 29) local.registerAudioRecordingCallback(context.mainExecutor,
                    object : AudioManager.AudioRecordingCallback() {
                        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                            silenced = local.activeRecordingConfiguration?.isClientSilenced == true
                        }
                    })
                local.startRecording()
                if (local.recordingState != AudioRecord.RECORDSTATE_RECORDING) { nextConfig(); error("Capture non démarrée : micro peut-être utilisé par une autre application") }
                detector.reset(); silenced = false; lastSampleMs = 0; zeroSince = 0; isRecording = true; errorMessage = null; switching = false
                var episode: RoomAlertEvent? = null
                var episodeStarted = 0L
                var peakInEpisode = 0
                var signalSeen = false
                val raw = ShortArray(1024 * config.factor)
                while (isActive) {
                    val count = local.read(raw, 0, raw.size)
                    if (count < 0) { nextConfig(); error("Erreur microphone ($count, ${config.label})") }
                    if (count == 0) { delay(20); continue }
                    val buffer = decimate(raw, count, config.factor)
                    val read = buffer.size
                    if (read == 0) continue
                    val now = SystemClock.elapsedRealtime()
                    var sum = 0.0; var peak = 0
                    val bytes = ByteArray(read * 2)
                    for (i in 0 until read) {
                        val n = buffer[i].toInt(); sum += n.toDouble() * n; peak = maxOf(peak, kotlin.math.abs(n))
                        bytes[i * 2] = n.toByte(); bytes[i * 2 + 1] = (n shr 8).toByte()
                    }
                    lastSampleMs = now; samplesCaptured += read
                    if (peak == 0) { if (zeroSince == 0L) zeroSince = now } else zeroSince = 0
                    if (peak > 0 && !signalSeen) {
                        // Remember the first source that delivers a real signal on this device.
                        signalSeen = true; silentRotations = 0; settings.edit().putInt("config", configIndex).apply()
                    }
                    // Pure zeros: a noise gate or a muted source. Try the next source while others remain untested.
                    if (zeroSince > 0 && now - zeroSince > ROTATE_MS && silentRotations < CONFIGS.size - 1) {
                        silentRotations++; nextConfig(); switching = true; throw SourceSwitch()
                    }
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
            catch (_: SourceSwitch) { errorMessage = null }
            catch (e: Exception) { switching = false; errorMessage = e.message ?: "Capture interrompue"; onCriticalError?.invoke(errorMessage!!) }
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
