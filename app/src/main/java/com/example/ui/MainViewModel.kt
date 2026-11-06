package com.example.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.model.*
import com.example.network.NetworkUtils
import com.example.network.RoomAddresses
import com.example.security.*
import com.example.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    val prefs = PreferencesManager(context)
    private val _codeProtectionEnabled = MutableStateFlow(prefs.isRoleLocked)
    val codeProtectionEnabled = _codeProtectionEnabled.asStateFlow()
    private val pairing = PairingStore(context)
    val room = RoomMonitoringService.serviceState
    val monitor = MonitorMonitoringService.serviceState
    val role = MutableStateFlow(prefs.role)
    val message = MutableStateFlow<String?>(null)
    val pairingCode = MutableStateFlow<String?>(null)
    val peers = MutableStateFlow<List<String>>(emptyList())
    val pairingSeconds = MutableStateFlow(0)
    private val _themeMode = MutableStateFlow(prefs.themeMode)
    val themeMode = _themeMode.asStateFlow()
    /** Room to open, requested by a notification tap; consumed by the UI. */
    val openRoomRequest = MutableStateFlow<String?>(null)
    fun setThemeMode(mode: ThemeMode) { prefs.themeMode = mode; _themeMode.value = mode }
    private var enrollmentJob: Job? = null
    private var resumed = false
    fun setCodeProtection(enabled: Boolean) {
        prefs.isRoleLocked = enabled
        _codeProtectionEnabled.value = enabled
    }
    fun ready() {
        if (resumed) return
        resumed = true
        if (role.value == DeviceRole.ROOM_UNIT && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            message.value = "Microphone non autorisé : la surveillance n’a pas repris. Autorisez-le puis touchez Reprendre."
        } else if (role.value != DeviceRole.NONE) start(role.value)
    }
    fun start(next: DeviceRole) {
        try {
            if (next == DeviceRole.ROOM_UNIT && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                message.value = "Autorisation microphone nécessaire"; return
            }
            if (next != role.value) {
                RoomMonitoringService.stopService(context); MonitorMonitoringService.stopService(context)
            }
            prefs.role = next; role.value = next
            when (next) {
                DeviceRole.ROOM_UNIT -> RoomMonitoringService.startService(context)
                DeviceRole.MONITOR_UNIT -> MonitorMonitoringService.startService(context)
                DeviceRole.NONE -> { RoomMonitoringService.stopService(context); MonitorMonitoringService.stopService(context) }
            }
        } catch (_: Exception) { message.value = "Android refuse le démarrage. Gardez l’application visible et vérifiez les autorisations." }
    }
    fun stop() { cancelPairing(); prefs.role = DeviceRole.NONE; role.value = DeviceRole.NONE; RoomMonitoringService.stopService(context); MonitorMonitoringService.stopService(context) }
    fun setMonitoringPaused(paused: Boolean) {
        try { MonitorMonitoringService.setMonitoringPaused(context, paused) }
        catch (_: Exception) { message.value = "Impossible de modifier la pause. Gardez l’application ouverte et réessayez." }
    }
    fun roomSettings(name: String, threshold: Int, duration: Int) {
        prefs.roomName = name.trim().take(80).ifBlank { "Chambre" }; prefs.defaultSensitivityDb = threshold; prefs.minNoiseDurationMs = duration
    }
    fun remoteThreshold(id: String, value: Int) = MonitorMonitoringService.sendRemoteThreshold(context, id, value)
    fun eventAction(event: RoomAlertEvent, resolve: Boolean) = MonitorMonitoringService.eventAction(context, event.eventId, resolve)
    fun eventActions(events: List<RoomAlertEvent>, resolve: Boolean) = MonitorMonitoringService.eventActions(context, events.map { it.eventId }, resolve)
    fun listen(id: String) = MonitorMonitoringService.toggleListenAudio(context, id)
    fun speak(id: String) = MonitorMonitoringService.startTalkBack(context, id)
    fun stopSpeaking() = MonitorMonitoringService.stopTalkBack(context)
    fun test() { if (room.value.isRunning) RoomMonitoringService.triggerManualSpike(context) }
    fun newPairing() {
        val previous = enrollmentJob
        previous?.cancel()
        enrollmentJob = viewModelScope.launch(Dispatchers.IO) {
            previous?.join()
            try {
                val address = NetworkUtils.getLocalIpAddress(context)
                if (address.isBlank()) { message.value = "Connectez ce téléphone au Wi-Fi local avant d’ouvrir un QR"; return@launch }
                pairingCode.value = pairing.createCode(SecureTransport(context).certificate(), address)
                var closed = false
                for (left in 120 downTo 1) {
                    pairingSeconds.value = left; delay(1000)
                    if (!pairing.enrollmentOpen()) { closed = true; break }
                }
                // The code closes early only when a monitor consumed it (cancellation ends this job instead).
                message.value = if (closed) "Code utilisé par un veilleur. Vérifiez sur le veilleur que la chambre apparaît connectée."
                    else "QR expiré sans appairage. Générez un nouveau QR pour réessayer."
            } catch (_: Exception) { message.value = "Impossible de créer l’appairage sécurisé sur cet appareil" }
            finally { pairingCode.value = null; pairingSeconds.value = 0; pairing.cancelEnrollment(); reloadPeers() }
        }
    }
    fun cancelPairing() { enrollmentJob?.cancel(); pairingCode.value = null; pairing.cancelEnrollment() }
    fun importPairing(code: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val imported = pairing.importCode(code.trim()); MonitorMonitoringService.reconnect(context, imported.id)
            message.value = "Chambre ajoutée. Attendez sa vérification avant de commencer la surveillance."
        } catch (_: Exception) { message.value = "Code invalide. Vérifiez le QR et sa durée de validité." }
    }
    fun reloadPeers() = viewModelScope.launch(Dispatchers.IO) { runCatching { peers.value = pairing.monitors() } }
    fun revoke(id: String) = viewModelScope.launch(Dispatchers.IO) { pairing.revoke(id); reloadPeers() }
    fun forget(id: String) = MonitorMonitoringService.forget(context, id)
    fun reconnect(id: String) = MonitorMonitoringService.reconnect(context, id)
    fun refreshNetwork() = MonitorMonitoringService.refresh(context)
    fun updateAddress(id: String, address: String) = viewModelScope.launch(Dispatchers.IO) {
        if (!RoomAddresses.isLocalAddress(address.trim())) { message.value = "Saisissez l’adresse IP locale affichée sur la chambre"; return@launch }
        pairing.updateAddress(id, address.trim()); MonitorMonitoringService.reconnect(context, id)
    }
}
