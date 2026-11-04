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
import com.example.security.*
import com.example.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    val prefs = PreferencesManager(context)
    private val pairing = PairingStore(context)
    val room = RoomMonitoringService.serviceState
    val monitor = MonitorMonitoringService.serviceState
    val role = MutableStateFlow(prefs.role)
    val message = MutableStateFlow<String?>(null)
    val pairingCode = MutableStateFlow<String?>(null)
    val peers = MutableStateFlow<List<String>>(emptyList())
    val pairingSeconds = MutableStateFlow(0)
    private var enrollmentJob: Job? = null
    private var resumed = false
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
    fun roomSettings(name: String, threshold: Int, duration: Int) {
        prefs.roomName = name.trim().take(80).ifBlank { "Chambre" }; prefs.defaultSensitivityDb = threshold; prefs.minNoiseDurationMs = duration
    }
    fun remoteThreshold(id: String, value: Int) = MonitorMonitoringService.sendRemoteThreshold(context, id, value)
    fun eventAction(event: RoomAlertEvent, resolve: Boolean) = MonitorMonitoringService.eventAction(context, event.eventId, resolve)
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
                pairingCode.value = pairing.createCode(SecureTransport(context).certificate(), NetworkUtils.getLocalIpAddress(context))
                for (left in 120 downTo 1) { pairingSeconds.value = left; delay(1000); if (!pairing.enrollmentOpen()) { message.value = "Veilleur appairé. Vérifiez la réception et les autorisations."; break } }
            } catch (_: Exception) { message.value = "Impossible de créer l’appairage sécurisé sur cet appareil" }
            finally { pairingCode.value = null; pairingSeconds.value = 0; pairing.cancelEnrollment(); reloadPeers() }
        }
    }
    fun cancelPairing() { enrollmentJob?.cancel(); pairingCode.value = null; pairing.cancelEnrollment() }
    fun importPairing(code: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            pairing.importCode(code.trim()); MonitorMonitoringService.refresh(context)
            message.value = "Chambre ajoutée. Attendez sa vérification avant de commencer la surveillance."
        } catch (_: Exception) { message.value = "Code invalide. Vérifiez le QR et sa durée de validité." }
    }
    fun reloadPeers() = viewModelScope.launch(Dispatchers.IO) { runCatching { peers.value = pairing.monitors() } }
    fun revoke(id: String) = viewModelScope.launch(Dispatchers.IO) { pairing.revoke(id); reloadPeers() }
    fun forget(id: String) = MonitorMonitoringService.forget(context, id)
}
