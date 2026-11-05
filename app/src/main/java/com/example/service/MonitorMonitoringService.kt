package com.example.service

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.ServiceCompat
import com.example.data.*
import com.example.model.*
import com.example.network.*
import com.example.security.PairingStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

class MonitorMonitoringService : Service() {
    data class MonitorServiceState(val isRunning: Boolean = false, val monitoredRooms: List<RoomUnitState> = emptyList(),
        val activeAlerts: List<RoomAlertEvent> = emptyList(), val history: List<RoomAlertEvent> = emptyList(),
        val activeListeningRoomId: String? = null, val isTransmittingVoice: Boolean = false,
        val diagnostic: String? = null, val pendingActions: Set<String> = emptySet(), val notificationWarnings: List<String> = emptyList(),
        val nearbyRooms: List<LanDiscoveryManager.DiscoveredRoomBeacon> = emptyList(), val discoveryDiagnostic: String = "Recherche réseau en cours…")
    companion object {
        const val ACTION_START = "monitor.START"; const val ACTION_REFRESH = "monitor.REFRESH"
        const val ACTION_ACKNOWLEDGE = "monitor.ACK"; const val ACTION_RESOLVE = "monitor.RESOLVE"
        const val ACTION_TOGGLE_LISTEN = "monitor.LISTEN"; const val ACTION_START_TALK = "monitor.TALK"
        const val ACTION_STOP_TALK = "monitor.STOP_TALK"; const val ACTION_SET_THRESHOLD = "monitor.THRESHOLD"
        const val ACTION_FORGET = "monitor.FORGET"
        const val ACTION_RECONNECT = "monitor.RECONNECT"
        private val _state = MutableStateFlow(MonitorServiceState())
        val serviceState = _state.asStateFlow()
        private fun command(c: Context, action: String, id: String? = null, value: Int? = null) {
            c.startService(Intent(c, MonitorMonitoringService::class.java).setAction(action).apply {
                id?.let { putExtra("id", it) }; value?.let { putExtra("value", it) }
            })
        }
        fun startService(c: Context) {
            val i = Intent(c, MonitorMonitoringService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }
        fun stopService(c: Context) { c.stopService(Intent(c, MonitorMonitoringService::class.java)) }
        fun refresh(c: Context) = command(c, ACTION_REFRESH)
        fun eventAction(c: Context, id: String, resolve: Boolean) = command(c, if (resolve) ACTION_RESOLVE else ACTION_ACKNOWLEDGE, id)
        fun eventActions(c: Context, ids: List<String>, resolve: Boolean) {
            // The snapshot excludes events arriving after the user's tap; bounded Binder payloads.
            ids.distinct().chunked(256).forEach { batch ->
                c.startService(Intent(c, MonitorMonitoringService::class.java)
                    .setAction(if (resolve) ACTION_RESOLVE else ACTION_ACKNOWLEDGE)
                    .putStringArrayListExtra("events", ArrayList(batch)))
            }
        }
        fun toggleListenAudio(c: Context, id: String) = command(c, ACTION_TOGGLE_LISTEN, id)
        fun startTalkBack(c: Context, id: String) = command(c, ACTION_START_TALK, id)
        fun stopTalkBack(c: Context) { if (_state.value.isRunning) command(c, ACTION_STOP_TALK) }
        fun sendRemoteThreshold(c: Context, id: String, threshold: Int) = command(c, ACTION_SET_THRESHOLD, id, threshold)
        fun forget(c: Context, id: String) = command(c, ACTION_FORGET, id)
        fun reconnect(c: Context, id: String) = command(c, ACTION_RECONNECT, id)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: PreferencesManager
    private lateinit var store: EventStore
    private var alerts: AlertManager? = null
    private var client: MonitorClient? = null
    private var discovery: LanDiscoveryManager? = null
    private var wake: PowerManager.WakeLock? = null
    private var networkLocks: LocalNetworkLocks? = null
    private val rooms = ConcurrentHashMap<String, RoomUnitState>()
    private val lastTelemetry = ConcurrentHashMap<String, Long>()
    private val nearby = ConcurrentHashMap<String, Pair<LanDiscoveryManager.DiscoveredRoomBeacon, Long>>()
    private var talkDeadline: Job? = null
    @Volatile private var running = false
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate(); prefs = PreferencesManager(this); store = EventStore(this)
        CareLinkNotificationHelper.createNotificationChannels(this)
    }
    private fun foreground(microphone: Boolean = false) {
        var type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        if (microphone && Build.VERSION.SDK_INT >= 30) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        ServiceCompat.startForeground(this, 1002, CareLinkNotificationHelper.ongoing(this, "CareLink · Veilleur", "Surveillance des chambres appairées"), type)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) {
            try { foreground() } catch (_: Exception) {
                _state.value = MonitorServiceState(diagnostic = "Démarrage refusé par Android. Ouvrez l’application pour reprendre.")
                stopSelf(); return START_NOT_STICKY
            }
            running = true; _state.update { it.copy(isRunning = true) }
            initialize()
        }
        val id = intent?.getStringExtra("id")
        scope.launch {
            try {
                when (intent?.action) {
                    ACTION_REFRESH -> refreshRooms()
                    ACTION_RECONNECT -> if (id != null) { client?.forget(id); refreshRooms() }
                    ACTION_ACKNOWLEDGE, ACTION_RESOLVE -> {
                        val snapshot = intent.getStringArrayListExtra("events")?.take(256) ?: listOfNotNull(id)
                        alerts?.transitionMany(snapshot, intent.action == ACTION_RESOLVE)
                        _state.update { it.copy(pendingActions = store.pendingCommandIds()) }
                    }
                    ACTION_TOGGLE_LISTEN -> if (id != null) {
                        if (_state.value.activeListeningRoomId == id) { client?.stopListeningToAudio(); _state.update { it.copy(activeListeningRoomId = null) } }
                        else client?.startListeningToAudio(id)
                    }
                    ACTION_START_TALK -> if (id != null) {
                        withContext(Dispatchers.Main) { foreground(true) }
                        client?.startSpeaking(id)
                        talkDeadline?.cancel()
                        talkDeadline = scope.launch { delay(30000); client?.stopSpeaking() }
                    }
                    ACTION_STOP_TALK -> { talkDeadline?.cancel(); client?.stopSpeaking(); withContext(Dispatchers.Main) { foreground() } }
                    ACTION_SET_THRESHOLD -> if (id != null) {
                        client?.sendThresholdUpdate(id, intent.getIntExtra("value", 55).coerceIn(35, 85))
                        _state.update { it.copy(diagnostic = "Réglage envoyé ; attendre la confirmation de la chambre") }
                    }
                    ACTION_FORGET -> if (id != null) {
                        client?.forget(id); PairingStore(this@MonitorMonitoringService).forget(id); rooms.remove(id); publish()
                    }
                }
            } catch (_: Exception) { _state.update { it.copy(diagnostic = "Action impossible. Vérifiez la connexion et les autorisations.") } }
        }
        return START_STICKY
    }
    private fun initialize() = scope.launch {
        try {
            store.prune()
            val manager = AlertManager(this@MonitorMonitoringService, store); alerts = manager
            launch { manager.history.collect { events -> _state.update { it.copy(history = events, activeAlerts = events.filterNot { e -> e.resolved }) } } }
            val network = MonitorClient(this@MonitorMonitoringService, store); client = network
            network.onRoomAlertReceived = { if (running) manager.triggerAlert(it) }
            network.onRoomTelemetryReceived = { room -> if (running) receive(room) }
            network.onRoomDisconnected = { id, message -> if (running) disconnect(id, message) }
            network.onRoomConnected = { id, address ->
                if (running) {
                    rooms.computeIfPresent(id) { _, room -> room.copy(ipAddress = address, enrollmentPending = false,
                        diagnostic = "Connexion authentifiée. Attente des données de capture…", status = RoomStatus.VERIFYING).also {
                            if (room.enrollmentPending) lastTelemetry[id] = SystemClock.elapsedRealtime()
                        } }
                    publish()
                }
            }
            network.onListeningChanged = { id, error -> _state.update { it.copy(activeListeningRoomId = id, diagnostic = error ?: it.diagnostic) } }
            network.onSettingsApplied = { _, _ -> _state.update { it.copy(diagnostic = "Réglage confirmé par la chambre") } }
            network.talkBackTransmitter.onStatusChanged = { active -> _state.update { it.copy(isTransmittingVoice = active) } }
            network.talkBackTransmitter.onError = { message -> _state.update { it.copy(isTransmittingVoice = false, diagnostic = message) } }
            refreshRooms()
            discovery = LanDiscoveryManager(this@MonitorMonitoringService).also { d ->
                d.addBeaconListener { b ->
                    if (running) {
                        if (nearby.size < 128 || nearby.containsKey(b.id)) nearby[b.id] = b to SystemClock.elapsedRealtime()
                        if (rooms.containsKey(b.id)) network.connectToRoom(b.id, b.ipAddress)
                    }
                }
                d.onDiagnostic = { text -> _state.update { it.copy(discoveryDiagnostic = text) } }
                d.startListening()
            }
            networkLocks = LocalNetworkLocks(this@MonitorMonitoringService).also { it.acquire() }
            wake = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CareLink:Monitor")
                .apply { setReferenceCounted(false); acquire(12 * 60 * 60 * 1000L) }
            var lastPruned = SystemClock.elapsedRealtime()
            var lastWakeRenewed = lastPruned
            while (isActive) {
                if (SystemClock.elapsedRealtime() - lastPruned > 3600000) { store.prune(); manager.publish(); lastPruned = SystemClock.elapsedRealtime() }
                if (wake?.isHeld == false || SystemClock.elapsedRealtime() - lastWakeRenewed >= 6 * 60 * 60 * 1000L) {
                    wake?.acquire(12 * 60 * 60 * 1000L); lastWakeRenewed = SystemClock.elapsedRealtime()
                }
                val now = SystemClock.elapsedRealtime()
                for ((id, room) in rooms) {
                    if (now - (lastTelemetry[id] ?: now) > 7500 && !room.enrollmentPending)
                        disconnect(id, if (room.status == RoomStatus.DISCONNECTED) room.diagnostic else "Aucune télémétrie récente. Surveillance interrompue.")
                }
                nearby.entries.removeAll { now - it.value.second > 15000 }
                _state.update { it.copy(nearbyRooms = nearby.values.map { entry -> entry.first }.sortedBy { entry -> entry.id }) }
                network.checkDeadlines()
                _state.update { it.copy(pendingActions = store.pendingCommandIds(), notificationWarnings = CareLinkNotificationHelper.readiness(this@MonitorMonitoringService)) }
                val healthy = rooms.values.count { it.status in listOf(RoomStatus.CALM, RoomStatus.ACTIVITY, RoomStatus.ALERT) }
                val pending = AlertGroup.active(_state.value.activeAlerts).count { !it.isAcknowledged }
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1002,
                    CareLinkNotificationHelper.ongoing(this@MonitorMonitoringService, "CareLink · Veilleur", "$healthy/${rooms.size} captures récentes · $pending événement(s) à vérifier"))
                delay(1000)
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                _state.update { it.copy(diagnostic = "Surveillance interrompue : journal ou service indisponible", isRunning = false,
                    monitoredRooms = rooms.values.map { r -> r.copy(status = RoomStatus.DISCONNECTED, isMicOperational = false) }) }
                client?.disconnectAll(); discovery?.release()
                CareLinkNotificationHelper.restartReminder(this@MonitorMonitoringService)
                stopSelf()
            }
        }
    }
    private fun refreshRooms() {
        for (room in PairingStore(this).rooms()) {
            val pending = room.enrolling
            val initial = RoomUnitState(room.id, room.name, room.address, enrollmentPending = pending,
                diagnostic = if (pending) "Appairage en attente de confirmation. Gardez le QR ouvert sur la chambre." else "Connexion à la chambre en cours…")
            rooms.compute(room.id) { _, old -> if (old == null || pending) initial else old }
            if (pending) alerts?.recoverIncident(room.id, AlertEventType.DISCONNECTION)
            lastTelemetry.putIfAbsent(room.id, SystemClock.elapsedRealtime())
            client?.connectToRoom(room.id, room.address)
        }; publish()
    }
    @Synchronized private fun receive(room: RoomUnitState) {
        lastTelemetry[room.id] = SystemClock.elapsedRealtime(); rooms[room.id] = room; publish()
        alerts?.recoverIncident(room.id, AlertEventType.DISCONNECTION)
        if (room.isMicOperational) alerts?.recoverIncident(room.id, AlertEventType.MIC_ERROR)
        if (!room.isMicOperational && room.status != RoomStatus.VERIFYING) alerts?.incident(room, AlertEventType.MIC_ERROR, room.diagnostic.ifBlank { "Capture non vérifiée" })
        if (room.batteryPercent in 0..14 && !room.isCharging) alerts?.incident(room, AlertEventType.LOW_BATTERY, "Batterie ${room.batteryPercent}% · brancher la chambre")
        if (room.isCharging || room.batteryPercent >= 20) alerts?.recoverIncident(room.id, AlertEventType.LOW_BATTERY)
        if ((room.temperatureC ?: 0f) >= 45f) alerts?.incident(room, AlertEventType.SERVICE_INTERRUPTED, "Température batterie élevée : ${room.temperatureC} °C")
        if (room.temperatureC != null && room.temperatureC < 42f) alerts?.recoverIncident(room.id, AlertEventType.SERVICE_INTERRUPTED)
    }
    @Synchronized private fun disconnect(id: String, message: String) {
        val room = rooms[id] ?: return
        val pending = PairingStore(this).rooms().firstOrNull { it.id == id }?.enrolling == true
        rooms[id] = room.copy(status = if (pending) RoomStatus.VERIFYING else RoomStatus.DISCONNECTED,
            isMicOperational = false, diagnostic = message, enrollmentPending = pending)
        publish()
        if (!pending && SystemClock.elapsedRealtime() - (lastTelemetry[id] ?: 0) > 7500)
            alerts?.incident(room, AlertEventType.DISCONNECTION, message)
    }
    private fun publish() { _state.update { it.copy(monitoredRooms = rooms.values.sortedBy { r -> r.name }) } }
    override fun onTimeout(startId: Int, fgsType: Int) { CareLinkNotificationHelper.restartReminder(this); stopSelf() }
    override fun onDestroy() {
        running = false; scope.cancel(); discovery?.release(); client?.disconnectAll(); alerts?.release()
        networkLocks?.close()
        runCatching { if (wake?.isHeld == true) wake?.release() }
        _state.value = MonitorServiceState(diagnostic = _state.value.diagnostic, monitoredRooms = rooms.values.map { it.copy(status = RoomStatus.DISCONNECTED, isMicOperational = false) })
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE); super.onDestroy()
    }
}
