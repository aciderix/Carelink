package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PreferencesManager
import com.example.model.DeviceRole
import com.example.model.RoomAlertEvent
import com.example.model.RoomStatus
import com.example.model.RoomUnitState
import com.example.network.NetworkUtils
import com.example.service.MonitorMonitoringService
import com.example.service.RoomMonitoringService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val currentRole: DeviceRole = DeviceRole.NONE,
    val localIpAddress: String = "127.0.0.1",
    val isWifiConnected: Boolean = true,

    // Room Unit Mode (Transmitter)
    val isRoomServiceRunning: Boolean = false,
    val roomUnitName: String = "Chambre 1",
    val roomUnitId: String = "",
    val roomCurrentDb: Int = 0,
    val roomThresholdDb: Int = 55,
    val roomStatus: RoomStatus = RoomStatus.CALM,
    val isCaregiverTalkingToRoom: Boolean = false,
    val isMicOperational: Boolean = true,
    val micErrorMessage: String? = null,

    // Monitor Unit Mode (Caregiver Multi-Room Dashboard)
    val isMonitorServiceRunning: Boolean = false,
    val monitoredRooms: List<RoomUnitState> = emptyList(),
    val activeAlerts: List<RoomAlertEvent> = emptyList(),
    val activeListeningRoomId: String? = null,
    val isTransmittingVoice: Boolean = false,
    val talkBackRoom: RoomUnitState? = null,
    val selectedDetailRoom: RoomUnitState? = null,

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
            roomUnitId = prefs.installationId,
            roomThresholdDb = prefs.defaultSensitivityDb
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        updateNetworkInfo()

        // Observe Room Monitoring Service state
        viewModelScope.launch {
            RoomMonitoringService.serviceState.collect { roomState ->
                _uiState.update { current ->
                    current.copy(
                        isRoomServiceRunning = roomState.isRunning,
                        roomUnitName = roomState.roomName.ifEmpty { prefs.roomName },
                        roomUnitId = roomState.roomId.ifEmpty { prefs.installationId },
                        roomCurrentDb = roomState.currentDecibels,
                        roomThresholdDb = roomState.thresholdDb,
                        roomStatus = roomState.status,
                        isCaregiverTalkingToRoom = roomState.isCaregiverTalking,
                        isMicOperational = roomState.isMicOperational,
                        micErrorMessage = roomState.errorMessage
                    )
                }
            }
        }

        // Observe Monitor Monitoring Service state
        viewModelScope.launch {
            MonitorMonitoringService.serviceState.collect { monitorState ->
                _uiState.update { current ->
                    val updatedSelected = if (current.selectedDetailRoom != null) {
                        monitorState.monitoredRooms.find { it.id == current.selectedDetailRoom.id }
                            ?: current.selectedDetailRoom
                    } else null

                    current.copy(
                        isMonitorServiceRunning = monitorState.isRunning,
                        monitoredRooms = monitorState.monitoredRooms,
                        activeAlerts = monitorState.activeAlerts,
                        activeListeningRoomId = monitorState.activeListeningRoomId,
                        isTransmittingVoice = monitorState.isTransmittingVoice,
                        selectedDetailRoom = updatedSelected
                    )
                }
            }
        }

        // Start corresponding service if role was persisted
        when (prefs.role) {
            DeviceRole.ROOM_UNIT -> {
                RoomMonitoringService.startService(
                    context,
                    roomName = prefs.roomName,
                    thresholdDb = prefs.defaultSensitivityDb
                )
            }
            DeviceRole.MONITOR_UNIT -> {
                MonitorMonitoringService.startService(context)
            }
            DeviceRole.NONE -> {
                // No service started
            }
        }
    }

    fun updateNetworkInfo() {
        val ip = NetworkUtils.getLocalIpAddress()
        val isWifi = NetworkUtils.isWifiConnected(context)
        _uiState.update { it.copy(localIpAddress = ip, isWifiConnected = isWifi) }
    }

    fun selectRole(role: DeviceRole) {
        prefs.role = role
        _uiState.update { it.copy(currentRole = role, selectedDetailRoom = null, talkBackRoom = null) }

        when (role) {
            DeviceRole.ROOM_UNIT -> {
                MonitorMonitoringService.stopService(context)
                RoomMonitoringService.startService(
                    context,
                    roomName = prefs.roomName,
                    thresholdDb = prefs.defaultSensitivityDb
                )
            }
            DeviceRole.MONITOR_UNIT -> {
                RoomMonitoringService.stopService(context)
                MonitorMonitoringService.startService(context)
            }
            DeviceRole.NONE -> {
                RoomMonitoringService.stopService(context)
                MonitorMonitoringService.stopService(context)
            }
        }
    }

    // ==============================================================
    // ROOM UNIT ACTIONS
    // ==============================================================
    fun setRoomUnitName(name: String) {
        prefs.roomName = name
        _uiState.update { it.copy(roomUnitName = name) }
        RoomMonitoringService.updateSettings(context, roomName = name)
    }

    fun setRoomThreshold(db: Int) {
        prefs.defaultSensitivityDb = db
        _uiState.update { it.copy(roomThresholdDb = db) }
        RoomMonitoringService.updateSettings(context, thresholdDb = db)
    }

    fun triggerRoomManualNoiseSpike() {
        RoomMonitoringService.triggerManualSpike(context)
    }

    // ==============================================================
    // MONITOR UNIT ACTIONS
    // ==============================================================
    fun toggleAudioListening(unit: RoomUnitState) {
        MonitorMonitoringService.toggleListenAudio(context, unit.id, unit.ipAddress)
    }

    fun openTalkBack(unit: RoomUnitState) {
        _uiState.update { it.copy(talkBackRoom = unit) }
    }

    fun closeTalkBack() {
        MonitorMonitoringService.stopTalkBack(context)
        _uiState.update { it.copy(talkBackRoom = null) }
    }

    fun startSpeakingToRoom(unit: RoomUnitState) {
        MonitorMonitoringService.startTalkBack(context, unit.id, unit.ipAddress)
    }

    fun stopSpeakingToRoom(unit: RoomUnitState) {
        MonitorMonitoringService.stopTalkBack(context)
    }

    fun remoteUpdateThreshold(unit: RoomUnitState, thresholdDb: Int) {
        MonitorMonitoringService.sendRemoteThreshold(context, unit.id, thresholdDb)
    }

    fun acknowledgeAlert(roomId: String) {
        MonitorMonitoringService.acknowledgeAlert(context, roomId)
    }

    fun acknowledgeAllAlerts() {
        MonitorMonitoringService.acknowledgeAll(context)
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

    override fun onCleared() {
        super.onCleared()
        // Note: Services intentionally keep running in foreground when UI is closed or destroyed.
    }
}
