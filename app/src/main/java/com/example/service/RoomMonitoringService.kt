package com.example.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.example.audio.AudioCaptureEngine
import com.example.data.PreferencesManager
import com.example.data.TelemetryHelper
import com.example.model.AlertEventType
import com.example.model.RoomAlertEvent
import com.example.model.RoomStatus
import com.example.network.LanDiscoveryManager
import com.example.network.RoomServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

class RoomMonitoringService : Service() {

    data class RoomServiceState(
        val isRunning: Boolean = false,
        val roomId: String = "",
        val roomName: String = "",
        val currentDecibels: Int = 0,
        val thresholdDb: Int = 55,
        val status: RoomStatus = RoomStatus.CALM,
        val isMicOperational: Boolean = true,
        val errorMessage: String? = null,
        val isCaregiverTalking: Boolean = false
    )

    companion object {
        const val ACTION_START = "com.example.service.action.START_ROOM_MONITORING"
        const val ACTION_STOP = "com.example.service.action.STOP_ROOM_MONITORING"
        const val ACTION_UPDATE_SETTINGS = "com.example.service.action.UPDATE_ROOM_SETTINGS"
        const val ACTION_TRIGGER_SPIKE = "com.example.service.action.TRIGGER_ROOM_SPIKE"

        const val EXTRA_ROOM_NAME = "extra_room_name"
        const val EXTRA_THRESHOLD_DB = "extra_threshold_db"

        private val _serviceState = MutableStateFlow(RoomServiceState())
        val serviceState: StateFlow<RoomServiceState> = _serviceState.asStateFlow()

        fun startService(context: Context, roomName: String? = null, thresholdDb: Int? = null) {
            val intent = Intent(context, RoomMonitoringService::class.java).apply {
                action = ACTION_START
                roomName?.let { putExtra(EXTRA_ROOM_NAME, it) }
                thresholdDb?.let { putExtra(EXTRA_THRESHOLD_DB, it) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, RoomMonitoringService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun updateSettings(context: Context, roomName: String? = null, thresholdDb: Int? = null) {
            val intent = Intent(context, RoomMonitoringService::class.java).apply {
                action = ACTION_UPDATE_SETTINGS
                roomName?.let { putExtra(EXTRA_ROOM_NAME, it) }
                thresholdDb?.let { putExtra(EXTRA_THRESHOLD_DB, it) }
            }
            context.startService(intent)
        }

        fun triggerManualSpike(context: Context) {
            val intent = Intent(context, RoomMonitoringService::class.java).apply {
                action = ACTION_TRIGGER_SPIKE
            }
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var prefs: PreferencesManager
    private var audioCaptureEngine: AudioCaptureEngine? = null
    private var roomServer: RoomServer? = null
    private var discoveryManager: LanDiscoveryManager? = null
    private var telemetryJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
        CareLinkNotificationHelper.createNotificationChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                stopMonitoring()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_UPDATE_SETTINGS -> {
                val newName = intent?.getStringExtra(EXTRA_ROOM_NAME)
                val newThreshold = intent?.getIntExtra(EXTRA_THRESHOLD_DB, -1) ?: -1
                applySettingsUpdate(newName, if (newThreshold > 0) newThreshold else null)
                return START_STICKY
            }

            ACTION_TRIGGER_SPIKE -> {
                audioCaptureEngine?.triggerManualNoiseSpike()
                return START_STICKY
            }

            ACTION_START -> {
                val roomName = intent?.getStringExtra(EXTRA_ROOM_NAME) ?: prefs.roomName
                val threshold = intent?.getIntExtra(EXTRA_THRESHOLD_DB, prefs.defaultSensitivityDb)
                    ?: prefs.defaultSensitivityDb
                startMonitoring(roomName, threshold)
                return START_STICKY
            }
        }

        return START_STICKY
    }

    private fun startMonitoring(roomName: String, threshold: Int) {
        val roomId = prefs.installationId

        // Build and display foreground notification
        val notification = CareLinkNotificationHelper.buildRoomOngoingNotification(this, roomName)
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(
                this,
                CareLinkNotificationHelper.NOTIFICATION_ID_ROOM_ONGOING,
                notification,
                foregroundType
            )
        } catch (e: Exception) {
            Log.e("RoomMonitoringService", "Failed to start foreground service: ${e.message}")
            stopSelf()
            return
        }

        _serviceState.update {
            it.copy(
                isRunning = true,
                roomId = roomId,
                roomName = roomName,
                thresholdDb = threshold,
                status = RoomStatus.CALM,
                isMicOperational = true,
                errorMessage = null
            )
        }

        // Initialize audio engine
        audioCaptureEngine = AudioCaptureEngine(
            context = this,
            roomId = roomId,
            roomNameProvider = { _serviceState.value.roomName }
        ).apply {
            thresholdDb = threshold
            minNoiseDurationMs = prefs.minNoiseDurationMs

            addTelemetryListener { db, isAlert, isOperational ->
                val status = when {
                    !isOperational -> RoomStatus.MIC_ERROR
                    isAlert -> RoomStatus.ALERT
                    db >= threshold - 8 -> RoomStatus.ACTIVITY
                    else -> RoomStatus.CALM
                }

                _serviceState.update {
                    it.copy(
                        currentDecibels = db,
                        status = status,
                        isMicOperational = isOperational,
                        errorMessage = if (!isOperational) this.errorMessage else null
                    )
                }

                // Send live telemetry to all connected monitors
                val battery = TelemetryHelper.getBatteryInfo(this@RoomMonitoringService)
                val wifiRssi = TelemetryHelper.getWifiSignalDbm(this@RoomMonitoringService)
                roomServer?.broadcastTelemetry(
                    unitId = roomId,
                    unitName = _serviceState.value.roomName,
                    decibels = db,
                    status = status,
                    batteryPercent = battery.percentage,
                    isCharging = battery.isCharging,
                    wifiSignalDbm = wifiRssi,
                    isMicOperational = isOperational,
                    thresholdDb = threshold
                )
            }

            addPacketListener { packet ->
                roomServer?.broadcastAudioPacket(packet)
            }

            onAlertTriggered = { event ->
                roomServer?.broadcastAlertEvent(event)
            }

            onCriticalError = { errorMsg ->
                _serviceState.update {
                    it.copy(
                        isMicOperational = false,
                        errorMessage = errorMsg,
                        status = RoomStatus.MIC_ERROR
                    )
                }
                val errorAlert = RoomAlertEvent(
                    roomId = roomId,
                    roomName = _serviceState.value.roomName,
                    eventType = AlertEventType.MIC_ERROR,
                    notes = errorMsg
                )
                roomServer?.broadcastAlertEvent(errorAlert)
            }
        }

        // Initialize server
        roomServer = RoomServer().apply {
            onCommandReceived = { cmd ->
                handleRemoteCommand(cmd)
            }
            onTalkBackStatusChanged = { isTalking ->
                _serviceState.update { it.copy(isCaregiverTalking = isTalking) }
            }
        }

        // Initialize discovery
        discoveryManager = LanDiscoveryManager()

        // Start subsystems
        audioCaptureEngine?.startCapture()
        roomServer?.start()
        discoveryManager?.startBroadcasting(roomId)

        // Periodic telemetry broadcaster (every 2.5 seconds even during calm silence)
        startTelemetryLoop()
    }

    private fun startTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(2500)
                val state = _serviceState.value
                if (!state.isRunning) break

                val battery = TelemetryHelper.getBatteryInfo(this@RoomMonitoringService)
                val wifiRssi = TelemetryHelper.getWifiSignalDbm(this@RoomMonitoringService)

                // Drain and send any offline queued events
                audioCaptureEngine?.let { engine ->
                    val pendingEvents = engine.drainOfflineEvents()
                    for (evt in pendingEvents) {
                        roomServer?.broadcastAlertEvent(evt)
                    }
                }

                roomServer?.broadcastTelemetry(
                    unitId = state.roomId,
                    unitName = state.roomName,
                    decibels = state.currentDecibels,
                    status = state.status,
                    batteryPercent = battery.percentage,
                    isCharging = battery.isCharging,
                    wifiSignalDbm = wifiRssi,
                    isMicOperational = state.isMicOperational,
                    thresholdDb = state.thresholdDb
                )
            }
        }
    }

    private fun handleRemoteCommand(cmd: JSONObject) {
        when (cmd.optString("cmd")) {
            "SET_THRESHOLD" -> {
                val db = cmd.optInt("value", 55)
                applySettingsUpdate(null, db)
            }
        }
    }

    private fun applySettingsUpdate(newName: String?, newThreshold: Int?) {
        newName?.let { name ->
            prefs.roomName = name
            _serviceState.update { it.copy(roomName = name) }
            discoveryManager?.startBroadcasting(_serviceState.value.roomId)

            // Update ongoing notification text
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(
                CareLinkNotificationHelper.NOTIFICATION_ID_ROOM_ONGOING,
                CareLinkNotificationHelper.buildRoomOngoingNotification(this, name)
            )
        }

        newThreshold?.let { db ->
            prefs.defaultSensitivityDb = db
            audioCaptureEngine?.thresholdDb = db
            _serviceState.update { it.copy(thresholdDb = db) }
        }
    }

    private fun stopMonitoring() {
        telemetryJob?.cancel()
        telemetryJob = null

        audioCaptureEngine?.stopCapture()
        audioCaptureEngine = null

        roomServer?.stop()
        roomServer = null

        discoveryManager?.stop()
        discoveryManager = null

        _serviceState.update { RoomServiceState(isRunning = false) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }
}
