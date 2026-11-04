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
import com.example.data.PreferencesManager
import com.example.model.AlertEventType
import com.example.model.RoomAlertEvent
import com.example.model.RoomStatus
import com.example.model.RoomUnitState
import com.example.network.LanDiscoveryManager
import com.example.network.MonitorClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class MonitorMonitoringService : Service() {

    data class MonitorServiceState(
        val isRunning: Boolean = false,
        val monitoredRooms: List<RoomUnitState> = emptyList(),
        val activeAlerts: List<RoomAlertEvent> = emptyList(),
        val activeListeningRoomId: String? = null,
        val isTransmittingVoice: Boolean = false
    )

    companion object {
        const val ACTION_START = "com.example.service.action.START_MONITOR_MONITORING"
        const val ACTION_STOP = "com.example.service.action.STOP_MONITOR_MONITORING"
        const val ACTION_ACKNOWLEDGE = "com.example.service.action.ACKNOWLEDGE_ALERT"
        const val ACTION_ACKNOWLEDGE_ALL = "com.example.service.action.ACKNOWLEDGE_ALL_ALERTS"
        const val ACTION_TOGGLE_LISTEN = "com.example.service.action.TOGGLE_LISTEN_AUDIO"
        const val ACTION_START_TALK = "com.example.service.action.START_TALKBACK"
        const val ACTION_STOP_TALK = "com.example.service.action.STOP_TALKBACK"
        const val ACTION_SET_THRESHOLD = "com.example.service.action.REMOTE_SET_THRESHOLD"

        const val EXTRA_ROOM_ID = "extra_room_id"
        const val EXTRA_ROOM_IP = "extra_room_ip"
        const val EXTRA_THRESHOLD_DB = "extra_threshold_db"

        private val _serviceState = MutableStateFlow(MonitorServiceState())
        val serviceState: StateFlow<MonitorServiceState> = _serviceState.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, MonitorMonitoringService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MonitorMonitoringService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun acknowledgeAlert(context: Context, roomId: String) {
            val intent = Intent(context, MonitorMonitoringService::class.java).apply {
                action = ACTION_ACKNOWLEDGE
                putExtra(EXTRA_ROOM_ID, roomId)
            }
            context.startService(intent)
        }

        fun acknowledgeAll(context: Context) {
            val intent = Intent(context, MonitorMonitoringService::class.java).apply {
                action = ACTION_ACKNOWLEDGE_ALL
            }
            context.startService(intent)
        }

        fun toggleListenAudio(context: Context, roomId: String, ipAddress: String) {
            val intent = Intent(context, MonitorMonitoringService::class.java).apply {
                action = ACTION_TOGGLE_LISTEN
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_ROOM_IP, ipAddress)
            }
            context.startService(intent)
        }

        fun startTalkBack(context: Context, roomId: String, targetIp: String) {
            val intent = Intent(context, MonitorMonitoringService::class.java).apply {
                action = ACTION_START_TALK
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_ROOM_IP, targetIp)
            }
            context.startService(intent)
        }

        fun stopTalkBack(context: Context) {
            val intent = Intent(context, MonitorMonitoringService::class.java).apply {
                action = ACTION_STOP_TALK
            }
            context.startService(intent)
        }

        fun sendRemoteThreshold(context: Context, roomId: String, thresholdDb: Int) {
            val intent = Intent(context, MonitorMonitoringService::class.java).apply {
                action = ACTION_SET_THRESHOLD
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_THRESHOLD_DB, thresholdDb)
            }
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var prefs: PreferencesManager
    private lateinit var alertManager: AlertManager
    private var discoveryManager: LanDiscoveryManager? = null
    private var monitorClient: MonitorClient? = null

    private val roomsMap = ConcurrentHashMap<String, RoomUnitState>()
    private var alertObserverJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
        alertManager = AlertManager(this)
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

            ACTION_ACKNOWLEDGE -> {
                val roomId = intent?.getStringExtra(EXTRA_ROOM_ID)
                if (roomId != null) {
                    alertManager.acknowledgeAlert(roomId)
                    updateOngoingNotification()
                }
                return START_STICKY
            }

            ACTION_ACKNOWLEDGE_ALL -> {
                alertManager.acknowledgeAll()
                updateOngoingNotification()
                return START_STICKY
            }

            ACTION_TOGGLE_LISTEN -> {
                val roomId = intent?.getStringExtra(EXTRA_ROOM_ID)
                val ip = intent?.getStringExtra(EXTRA_ROOM_IP) ?: ""
                if (roomId != null) {
                    val current = _serviceState.value.activeListeningRoomId
                    if (current == roomId) {
                        monitorClient?.stopListeningToAudio(roomId)
                        _serviceState.update { it.copy(activeListeningRoomId = null) }
                    } else {
                        if (current != null) {
                            monitorClient?.stopListeningToAudio(current)
                        }
                        monitorClient?.startListeningToAudio(roomId, ip)
                        _serviceState.update { it.copy(activeListeningRoomId = roomId) }
                    }
                }
                return START_STICKY
            }

            ACTION_START_TALK -> {
                val targetIp = intent?.getStringExtra(EXTRA_ROOM_IP) ?: ""
                val roomId = intent?.getStringExtra(EXTRA_ROOM_ID) ?: ""
                monitorClient?.startSpeaking(roomId, targetIp)
                _serviceState.update { it.copy(isTransmittingVoice = true) }
                return START_STICKY
            }

            ACTION_STOP_TALK -> {
                monitorClient?.stopSpeaking()
                _serviceState.update { it.copy(isTransmittingVoice = false) }
                return START_STICKY
            }

            ACTION_SET_THRESHOLD -> {
                val roomId = intent?.getStringExtra(EXTRA_ROOM_ID)
                val threshold = intent?.getIntExtra(EXTRA_THRESHOLD_DB, 55) ?: 55
                if (roomId != null) {
                    prefs.setThresholdForRoom(roomId, threshold)
                    monitorClient?.sendThresholdUpdate(roomId, threshold)
                }
                return START_STICKY
            }

            ACTION_START -> {
                startMonitoring()
                return START_STICKY
            }
        }

        return START_STICKY
    }

    private fun startMonitoring() {
        val notification = CareLinkNotificationHelper.buildMonitorOngoingNotification(
            this,
            roomsMap.size,
            alertManager.activeAlertsFlow.value.size
        )

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            type
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(
                this,
                CareLinkNotificationHelper.NOTIFICATION_ID_MONITOR_ONGOING,
                notification,
                foregroundType
            )
        } catch (e: Exception) {
            Log.e("MonitorMonitoringService", "Failed to start foreground service: ${e.message}")
            stopSelf()
            return
        }

        _serviceState.update {
            it.copy(isRunning = true)
        }

        // Observe active alerts
        alertObserverJob?.cancel()
        alertObserverJob = scope.launch {
            alertManager.activeAlertsFlow.collect { alerts ->
                _serviceState.update { it.copy(activeAlerts = alerts) }
                updateOngoingNotification()
            }
        }

        // Initialize MonitorClient
        monitorClient = MonitorClient(this).apply {
            onRoomTelemetryReceived = { updatedRoom ->
                handleRoomTelemetry(updatedRoom)
            }
            onRoomAlertReceived = { alertEvent ->
                alertManager.triggerAlert(alertEvent)
            }
            onRoomDisconnected = { roomId ->
                handleRoomDisconnection(roomId)
            }
        }

        // Initialize Discovery
        discoveryManager = LanDiscoveryManager().apply {
            addBeaconListener { beacon ->
                handleDiscoveredBeacon(beacon)
            }
            startListening()
        }
    }

    private fun handleDiscoveredBeacon(beacon: LanDiscoveryManager.DiscoveredRoomBeacon) {
        val existing = roomsMap[beacon.id]
        if (existing == null) {
            val newRoom = RoomUnitState(
                id = beacon.id,
                name = "Chambre ${beacon.id.takeLast(4)}",
                ipAddress = beacon.ipAddress,
                port = beacon.controlPort,
                status = RoomStatus.CALM,
                noiseThresholdDb = prefs.getThresholdForRoom(beacon.id)
            )
            roomsMap[beacon.id] = newRoom
            updateRoomsState()
            monitorClient?.connectToRoom(beacon.id, beacon.ipAddress, beacon.controlPort)
        } else if (existing.ipAddress != beacon.ipAddress) {
            val updated = existing.copy(ipAddress = beacon.ipAddress)
            roomsMap[beacon.id] = updated
            updateRoomsState()
            monitorClient?.connectToRoom(beacon.id, beacon.ipAddress, beacon.controlPort)
        }
    }

    private fun handleRoomTelemetry(telemetry: RoomUnitState) {
        val isListeningAudio = monitorClient?.isListening(telemetry.id) == true

        val merged = telemetry.copy(
            isAudioStreaming = isListeningAudio
        )
        roomsMap[telemetry.id] = merged
        updateRoomsState()

        // Acoustic alert check
        if (merged.status == RoomStatus.ALERT) {
            val alert = RoomAlertEvent(
                roomId = merged.id,
                roomName = merged.name,
                levelReachedDb = merged.currentDecibels,
                thresholdDb = merged.noiseThresholdDb,
                eventType = AlertEventType.SOUND_ALERT,
                notes = "Dépassement de seuil acoustique (> ${merged.noiseThresholdDb} dB)"
            )
            alertManager.triggerAlert(alert)
        }

        // Mic error check
        if (!merged.isMicOperational) {
            val alert = RoomAlertEvent(
                roomId = merged.id,
                roomName = merged.name,
                eventType = AlertEventType.MIC_ERROR,
                notes = "Microphone en panne ou inaccessible"
            )
            alertManager.triggerAlert(alert)
        }

        // Low battery check (< 15% and not charging)
        if (merged.batteryPercent in 1..14 && !merged.isCharging) {
            val alert = RoomAlertEvent(
                roomId = merged.id,
                roomName = merged.name,
                eventType = AlertEventType.LOW_BATTERY,
                notes = "Batterie critique (${merged.batteryPercent}%)"
            )
            alertManager.triggerAlert(alert)
        }
    }

    private fun handleRoomDisconnection(roomId: String) {
        val room = roomsMap[roomId]
        if (room != null && room.status != RoomStatus.DISCONNECTED) {
            val disconnected = room.copy(
                status = RoomStatus.DISCONNECTED,
                isAudioStreaming = false
            )
            roomsMap[roomId] = disconnected
            updateRoomsState()

            val alert = RoomAlertEvent(
                roomId = roomId,
                roomName = room.name,
                eventType = AlertEventType.DISCONNECTION,
                notes = "Chambre non joignable sur le réseau"
            )
            alertManager.triggerAlert(alert)
        }
    }

    private fun updateRoomsState() {
        _serviceState.update {
            it.copy(monitoredRooms = roomsMap.values.toList())
        }
        updateOngoingNotification()
    }

    private fun updateOngoingNotification() {
        val activeRooms = roomsMap.values.count { it.status != RoomStatus.DISCONNECTED }
        val alertCount = alertManager.activeAlertsFlow.value.size
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            CareLinkNotificationHelper.NOTIFICATION_ID_MONITOR_ONGOING,
            CareLinkNotificationHelper.buildMonitorOngoingNotification(this, activeRooms, alertCount)
        )
    }

    private fun stopMonitoring() {
        alertObserverJob?.cancel()
        alertObserverJob = null

        discoveryManager?.stop()
        discoveryManager = null

        monitorClient?.disconnectAll()
        monitorClient = null

        alertManager.release()
        roomsMap.clear()

        _serviceState.update { MonitorServiceState(isRunning = false) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }
}
