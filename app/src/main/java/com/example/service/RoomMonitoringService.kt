package com.example.service

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.ServiceCompat
import com.example.audio.AudioCaptureEngine
import com.example.data.*
import com.example.model.*
import com.example.network.*
import com.example.security.PairingStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject

class RoomMonitoringService : Service() {
    data class RoomServiceState(val isRunning: Boolean = false, val roomId: String = "", val roomName: String = "",
        val currentDecibels: Int = 0, val thresholdDb: Int = 55, val status: RoomStatus = RoomStatus.VERIFYING,
        val isMicOperational: Boolean = false, val errorMessage: String? = null, val isCaregiverTalking: Boolean = false,
        val connectedMonitors: Int = 0, val captureAgeMs: Long = Long.MAX_VALUE, val samples: Long = 0,
        val localAddress: String = "", val connectionDiagnostic: String? = null)
    companion object {
        const val ACTION_START = "room.START"; const val ACTION_STOP = "room.STOP"
        const val ACTION_UPDATE_SETTINGS = "room.SETTINGS"; const val ACTION_TRIGGER_SPIKE = "room.TEST"
        const val EXTRA_ROOM_NAME = "name"; const val EXTRA_THRESHOLD_DB = "threshold"
        private val _state = MutableStateFlow(RoomServiceState())
        val serviceState = _state.asStateFlow()
        fun startService(context: Context, roomName: String? = null, thresholdDb: Int? = null) {
            val i = Intent(context, RoomMonitoringService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }
        fun stopService(context: Context) { context.stopService(Intent(context, RoomMonitoringService::class.java)) }
        fun updateSettings(context: Context, roomName: String? = null, thresholdDb: Int? = null) {
            val p = PreferencesManager(context); roomName?.let { p.roomName = it.take(80) }; thresholdDb?.let { p.defaultSensitivityDb = it }
        }
        fun triggerManualSpike(context: Context) { context.startService(Intent(context, RoomMonitoringService::class.java).setAction(ACTION_TRIGGER_SPIKE)) }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: PreferencesManager
    private lateinit var store: EventStore
    private var engine: AudioCaptureEngine? = null
    private var server: RoomServer? = null
    private var discovery: LanDiscoveryManager? = null
    private var wake: PowerManager.WakeLock? = null
    private var networkLocks: LocalNetworkLocks? = null
    @Volatile private var running = false
    @Volatile private var talking = false
    @Volatile private var fatal: String? = null
    @Volatile private var connectionDiagnostic: String? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate(); prefs = PreferencesManager(this); store = EventStore(this)
        CareLinkNotificationHelper.createNotificationChannels(this)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { prefs.role = DeviceRole.NONE; stopSelf(); return START_NOT_STICKY }
        if (intent?.action == ACTION_TRIGGER_SPIKE) { engine?.triggerManualNoiseSpike(); return START_STICKY }
        if (running) return START_STICKY
        try {
            ServiceCompat.startForeground(this, 1001, CareLinkNotificationHelper.ongoing(this, "CareLink · Chambre", "Vérification de la capture…"),
                if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        } catch (e: Exception) {
            fatal = "Démarrage refusé : ouvrez CareLink et vérifiez l’autorisation microphone"
            _state.value = RoomServiceState(errorMessage = fatal)
            stopSelf(); return START_NOT_STICKY
        }
        running = true
        scope.launch {
            try {
                store.prune()
                val localServer = RoomServer(this@RoomMonitoringService, store)
                server = localServer
                val capture = AudioCaptureEngine(this@RoomMonitoringService, prefs.installationId) { prefs.roomName }
                engine = capture
                capture.onPacket = { localServer.broadcastAudioPacket(it) }
                capture.onAlertTriggered = { event ->
                    if (store.get("room", event.eventId) == null) store.put("room", event)
                    else store.measurements(event.eventId, event.levelReachedDb, event.durationSeconds)
                }
                capture.onCriticalError = { /* The periodic health state reports and persists microphone failures. */ }
                localServer.onCommand = { _, j -> prefs.defaultSensitivityDb = j.getInt("value") }
                localServer.onTalkBackStatusChanged = { active -> talking = active; capture.analysisPaused = active }
                localServer.onError = { fatal = it }
                localServer.onConnectionDiagnostic = { connectionDiagnostic = it }
                localServer.telemetry = { telemetry() }
                localServer.start()
                discovery = LanDiscoveryManager(this@RoomMonitoringService).also { it.startBroadcasting(prefs.installationId) }
                networkLocks = LocalNetworkLocks(this@RoomMonitoringService).also { it.acquire() }
                val power = getSystemService(Context.POWER_SERVICE) as PowerManager
                wake = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CareLink:Room").apply { setReferenceCounted(false); acquire(12 * 60 * 60 * 1000L) }
                var retryAt = 0L; var lastError: String? = null
                val startedAt = SystemClock.elapsedRealtime()
                var lastPruned = startedAt
                var lastWakeRenewed = startedAt
                while (isActive) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastPruned > 3600000) { store.prune(); lastPruned = now }
                    // Renew before expiry, avoiding a suspend gap during a long shift.
                    if (wake?.isHeld == false || now - lastWakeRenewed >= 6 * 60 * 60 * 1000L) {
                        wake?.acquire(12 * 60 * 60 * 1000L); lastWakeRenewed = now
                    }
                    capture.thresholdDb = prefs.defaultSensitivityDb; capture.minNoiseDurationMs = prefs.minNoiseDurationMs
                    if (!capture.isRecording && now >= retryAt) { capture.startCapture(); retryAt = now + 5000 }
                    if (capture.isRecording && capture.captureAgeMs > 4000 && now - startedAt > 5000) capture.stopCapture()
                    val starting = capture.samplesCaptured == 0L && now - startedAt < 5000 && capture.errorMessage == null
                    val error = if (starting) null else fatal ?: capture.diagnostic
                    val status = when {
                        starting -> RoomStatus.VERIFYING
                        fatal != null && capture.isRecording -> RoomStatus.DEGRADED
                        !capture.isOperational -> RoomStatus.MIC_ERROR
                        talking || error != null -> RoomStatus.DEGRADED
                        capture.isAlertActive -> RoomStatus.ALERT
                        capture.currentLevelDb >= prefs.defaultSensitivityDb - 8 -> RoomStatus.ACTIVITY
                        else -> RoomStatus.CALM
                    }
                    _state.value = RoomServiceState(true, prefs.installationId, prefs.roomName, capture.currentLevelDb,
                        prefs.defaultSensitivityDb, status, capture.isOperational, error, talking,
                        localServer.connectedMonitors, capture.captureAgeMs, capture.samplesCaptured,
                        NetworkUtils.getLocalIpAddress(this@RoomMonitoringService), connectionDiagnostic)
                    if (error != null && error != lastError && !talking) {
                        store.put("room", RoomAlertEvent(roomId = prefs.installationId, roomName = prefs.roomName,
                            eventType = AlertEventType.MIC_ERROR, notes = error, remote = true))
                    }
                    lastError = error
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1001,
                        CareLinkNotificationHelper.ongoing(this@RoomMonitoringService, "CareLink · ${prefs.roomName}",
                            error ?: "Capture récente · ${localServer.connectedMonitors} veilleur(s) connecté(s)"))
                    delay(1000)
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    fatal = "Surveillance interrompue : ${e.message}"
                    _state.update { it.copy(status = RoomStatus.DEGRADED, isMicOperational = false, errorMessage = fatal) }
                    server?.stop(); engine?.release()
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1001,
                        CareLinkNotificationHelper.ongoing(this@RoomMonitoringService, "CareLink · interruption", "Ouvrez l’application pour reprendre"))
                    CareLinkNotificationHelper.restartReminder(this@RoomMonitoringService)
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }
    private fun telemetry(): JSONObject {
        val s = _state.value; val battery = TelemetryHelper.getBatteryInfo(this)
        val age = engine?.captureAgeMs ?: Long.MAX_VALUE
        return JSONObject().put("id", prefs.installationId).put("name", prefs.roomName).put("decibels", s.currentDecibels)
            .put("threshold", prefs.defaultSensitivityDb).put("status", s.status.name)
            .put("isMicOperational", s.isMicOperational && age < 2000 && fatal == null).put("captureAgeMs", age)
            .put("samples", engine?.samplesCaptured ?: 0).put("battery", battery.percentage).put("isCharging", battery.isCharging)
            .put("diagnostic", s.errorMessage ?: if (s.status == RoomStatus.DEGRADED) "Surveillance dégradée" else "")
            .apply { battery.temperatureC?.let { put("temperatureC", it) } }
    }
    override fun onDestroy() {
        running = false; scope.cancel(); engine?.release(); server?.stop(); discovery?.release()
        PairingStore(this).cancelEnrollment()
        networkLocks?.close()
        runCatching { if (wake?.isHeld == true) wake?.release() }
        _state.value = RoomServiceState(errorMessage = fatal); ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // Do not close the DB while a cancelled blocking I/O callback may still unwind.
        super.onDestroy()
    }
}
