package com.example.model

import java.util.UUID

enum class DeviceRole {
    NONE,
    ROOM_UNIT,      // Appareil installé en chambre (Capteur microphone)
    MONITOR_UNIT    // Poste de surveillance du soignant / veilleur
}

enum class RoomStatus {
    CALM,           // Chambre calme (bruit en dessous du seuil)
    ACTIVITY,       // Murmure / légère activité sonore
    ALERT,          // Bruit anormal / dépassement de seuil
    DISCONNECTED,   // Connexion perdue ou heartbeat expiré
    MIC_ERROR       // Erreur matérielle ou permission microphone manquante
}

enum class AlertEventType {
    SOUND_ALERT,            // Dépassement de seuil acoustique
    DISCONNECTION,          // Perte de contact réseau avec la chambre
    MIC_ERROR,              // Panne ou blocage du microphone en chambre
    LOW_BATTERY,            // Batterie faible (< 15%) sur l'appareil chambre
    SERVICE_INTERRUPTED     // Arrêt inattendu du service de surveillance
}

data class RoomAlertEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val roomId: String,
    val roomName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val levelReachedDb: Int = 0,
    val thresholdDb: Int = 55,
    val eventType: AlertEventType,
    val isAcknowledged: Boolean = false,
    val durationSeconds: Int = 0,
    val notes: String = ""
)

data class RoomUnitState(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int = 9877,
    val audioPort: Int = 9878,
    val talkbackPort: Int = 9879,
    val status: RoomStatus = RoomStatus.CALM,
    val currentDecibels: Int = 0,
    val noiseThresholdDb: Int = 55,
    val batteryPercent: Int = -1,
    val isCharging: Boolean = false,
    val wifiSignalDbm: Int = 0,
    val isMicOperational: Boolean = true,
    val lastSeenTimestamp: Long = 0L,
    val isAudioStreaming: Boolean = false,
    val activeAlert: RoomAlertEvent? = null
)
