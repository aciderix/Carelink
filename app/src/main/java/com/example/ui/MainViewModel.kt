package com.example.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import com.example.audio.AudioCaptureEngine
import com.example.data.PreferencesManager
import com.example.model.DeviceRole
import com.example.model.NoiseEvent
import com.example.model.RoomStatus
import com.example.model.RoomUnitState
import com.example.network.LanDiscoveryManager
import com.example.network.MonitorClient
import com.example.network.NetworkUtils
import com.example.network.RoomServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.util.UUID

data class MainUiState(
    val currentRole: DeviceRole = DeviceRole.NONE,
    val localIpAddress: String = "127.0.0.1",
    val isWifiConnected: Boolean = true,

    // Room Unit Mode (Transmitter)
    val roomUnitName: String = "Chambre 1",
    val roomUnitId: String = "room_local",
    val roomCurrentDb: Int = 22,
    val roomThresholdDb: Int = 55,
    val roomStatus: RoomStatus = RoomStatus.CALM,
    val isCaregiverTalkingToRoom: Boolean = false,

    // Monitor Unit Mode (Caregiver Multi-Room Dashboard)
    val monitoredRooms: List<RoomUnitState> = emptyList(),
    val activeListeningRoomId: String? = null,
    val talkBackRoom: RoomUnitState? = null,
    val selectedDetailRoom: RoomUnitState? = null,
    val activeNoiseAlert: NoiseEvent? = null,
    val noiseHistory: List<NoiseEvent> = emptyList(),

    // Dialogs
    val isSettingsOpen: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val prefs = PreferencesManager(context)

    private val _uiState = MutableStateFlow(
        MainUiState(
            currentRole = prefs.role,
            roomUnitName = prefs.roomName,
            roomThresholdDb = prefs.defaultSensitivityDb
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val audioCaptureEngine = AudioCaptureEngine(context)
    private val discoveryManager = LanDiscoveryManager()
    private val roomServer = RoomServer()
    private val monitorClient = MonitorClient()

    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null

    init {
        updateNetworkInfo()
        setupAudioAlerts()

        discoveryManager.addBeaconListener { beacon ->
            handleDiscoveredRoom(beacon)
        }

        roomServer.onCommandReceived = { cmd ->
            handleRemoteCommandOnRoom(cmd)
        }
        roomServer.onTalkBackStatusChanged = { isTalking ->
            _uiState.update { it.copy(isCaregiverTalkingToRoom = isTalking) }
        }

        monitorClient.onRoomTelemetryReceived = { updatedRoom ->
            handleRoomTelemetry(updatedRoom)
        }

        if (prefs.role == DeviceRole.ROOM_UNIT) {
            startRoomUnitMode()
        } else if (prefs.role == DeviceRole.MONITOR_UNIT) {
            startMonitorUnitMode()
        }
    }

    private fun setupAudioAlerts() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (_: Exception) {}

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun updateNetworkInfo() {
        val ip = NetworkUtils.getLocalIpAddress()
        val isWifi = NetworkUtils.isWifiConnected(context)
        _uiState.update { it.copy(localIpAddress = ip, isWifiConnected = isWifi) }
    }

    fun selectRole(role: DeviceRole) {
        prefs.role = role
        _uiState.update { it.copy(currentRole = role) }
        when (role) {
            DeviceRole.ROOM_UNIT -> startRoomUnitMode()
            DeviceRole.MONITOR_UNIT -> startMonitorUnitMode()
            DeviceRole.NONE -> stopAll()
        }
    }

    // ==============================================================
    // ROOM UNIT MODE (Transmitter in room)
    // ==============================================================
    private fun startRoomUnitMode() {
        stopMonitorUnitMode()
        updateNetworkInfo()

        audioCaptureEngine.thresholdDecibels = _uiState.value.roomThresholdDb
        audioCaptureEngine.addTelemetryListener { db, isAlert ->
            val status = when {
                isAlert -> RoomStatus.ALERT
                db >= _uiState.value.roomThresholdDb - 10 -> RoomStatus.ACTIVITY
                else -> RoomStatus.CALM
            }
            _uiState.update {
                it.copy(
                    roomCurrentDb = db,
                    roomStatus = status
                )
            }

            val state = _uiState.value
            roomServer.broadcastTelemetry(
                unitId = state.roomUnitId,
                unitName = state.roomUnitName,
                decibels = db,
                status = status,
                batteryPercent = 88,
                isCharging = true,
                temperature = 20.6f,
                thresholdDb = state.roomThresholdDb
            )
        }

        audioCaptureEngine.addPacketListener { packet ->
            roomServer.broadcastAudioPacket(packet)
        }

        audioCaptureEngine.startCapture()
        roomServer.start()
        discoveryManager.startBroadcasting(
            unitId = _uiState.value.roomUnitId,
            unitName = _uiState.value.roomUnitName
        )
    }

    private fun stopRoomUnitMode() {
        audioCaptureEngine.stopCapture()
        roomServer.stop()
        discoveryManager.stop()
    }

    fun setRoomUnitName(name: String) {
        prefs.roomName = name
        _uiState.update { it.copy(roomUnitName = name) }
        discoveryManager.startBroadcasting(
            unitId = _uiState.value.roomUnitId,
            unitName = name
        )
    }

    fun setRoomThreshold(db: Int) {
        prefs.defaultSensitivityDb = db
        audioCaptureEngine.thresholdDecibels = db
        _uiState.update { it.copy(roomThresholdDb = db) }
    }

    fun triggerRoomManualNoiseSpike() {
        audioCaptureEngine.triggerManualNoiseSpike()
    }

    private fun handleRemoteCommandOnRoom(cmd: JSONObject) {
        when (cmd.optString("cmd")) {
            "SET_THRESHOLD" -> {
                val db = cmd.optInt("value", 55)
                setRoomThreshold(db)
            }
        }
    }

    // ==============================================================
    // MONITOR UNIT MODE (Caregiver Multi-Room Dashboard)
    // ==============================================================
    private fun startMonitorUnitMode() {
        stopRoomUnitMode()
        updateNetworkInfo()
        discoveryManager.startListening()
    }

    private fun stopMonitorUnitMode() {
        discoveryManager.stop()
        monitorClient.disconnectAll()
    }

    private fun handleDiscoveredRoom(beacon: LanDiscoveryManager.DiscoveredRoomBeacon) {
        val existing = _uiState.value.monitoredRooms.find { it.id == beacon.id }
        if (existing == null) {
            val newRoom = RoomUnitState(
                id = beacon.id,
                name = beacon.name,
                ipAddress = beacon.ipAddress,
                port = beacon.controlPort,
                status = RoomStatus.CALM
            )
            _uiState.update { it.copy(monitoredRooms = it.monitoredRooms + newRoom) }
            monitorClient.connectToRoom(beacon.id, beacon.ipAddress, beacon.controlPort)
        }
    }

    private fun handleRoomTelemetry(updatedRoom: RoomUnitState) {
        _uiState.update { current ->
            val list = current.monitoredRooms.toMutableList()
            val index = list.indexOfFirst { it.id == updatedRoom.id }
            if (index != -1) {
                val previous = list[index]
                list[index] = updatedRoom.copy(
                    isAudioStreaming = monitorClient.isListening(updatedRoom.id),
                    isListeningToAudio = previous.isListeningToAudio
                )
            } else {
                list.add(updatedRoom)
            }

            var newAlert = current.activeNoiseAlert
            val newHistory = current.noiseHistory.toMutableList()
            if (updatedRoom.status == RoomStatus.ALERT) {
                if (newAlert == null || newAlert.roomName != updatedRoom.name) {
                    val event = NoiseEvent(
                        id = UUID.randomUUID().toString(),
                        roomName = updatedRoom.name,
                        timestamp = System.currentTimeMillis(),
                        durationSeconds = 6,
                        maxDecibels = updatedRoom.currentDecibels,
                        notes = "Bruit anormal (> ${updatedRoom.noiseThresholdDb} dB)"
                    )
                    newAlert = event
                    newHistory.add(0, event)
                    triggerAlertFeedback()
                }
            } else if (newAlert != null && newAlert.roomName == updatedRoom.name) {
                newAlert = null
            }

            current.copy(
                monitoredRooms = list,
                activeNoiseAlert = newAlert,
                noiseHistory = newHistory.take(30)
            )
        }
    }

    private fun triggerAlertFeedback() {
        if (prefs.isVibrationAlertEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 300, 150, 300), -1)
                }
            } catch (_: Exception) {}
        }

        if (prefs.isSoundAlertEnabled) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 450)
            } catch (_: Exception) {}
        }
    }

    fun dismissNoiseAlert() {
        _uiState.update { it.copy(activeNoiseAlert = null) }
    }

    fun toggleAudioListening(unit: RoomUnitState) {
        val currentListeningId = _uiState.value.activeListeningRoomId
        if (currentListeningId == unit.id) {
            monitorClient.stopListeningToAudio(unit.id)
            _uiState.update { it.copy(activeListeningRoomId = null) }
        } else {
            if (currentListeningId != null) {
                monitorClient.stopListeningToAudio(currentListeningId)
            }
            monitorClient.startListeningToAudio(unit.id, unit.ipAddress)
            _uiState.update { it.copy(activeListeningRoomId = unit.id) }
        }
    }

    fun openTalkBack(unit: RoomUnitState) {
        _uiState.update { it.copy(talkBackRoom = unit) }
    }

    fun closeTalkBack() {
        _uiState.update { it.copy(talkBackRoom = null) }
    }

    fun startSpeakingToRoom(unit: RoomUnitState) {
        // Stream voice to room
    }

    fun stopSpeakingToRoom(unit: RoomUnitState) {
        // Stop streaming
    }

    fun remoteUpdateThreshold(unit: RoomUnitState, thresholdDb: Int) {
        monitorClient.sendThresholdUpdate(unit.id, thresholdDb)
    }

    fun selectDetailRoom(unit: RoomUnitState?) {
        _uiState.update { it.copy(selectedDetailRoom = unit) }
    }

    fun openSettings() {
        _uiState.update { it.copy(isSettingsOpen = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(isSettingsOpen = false) }
    }

    private fun stopAll() {
        stopRoomUnitMode()
        stopMonitorUnitMode()
    }

    override fun onCleared() {
        super.onCleared()
        stopAll()
        toneGenerator?.release()
    }
}
