package com.example.model

enum class DeviceRole {
    NONE,
    ROOM_UNIT,      // Appareil installé dans la chambre (capteur sonore / micro)
    MONITOR_UNIT    // Poste de surveillance du soignant / aidant (récepteur multi-chambres)
}

enum class RoomStatus {
    CALM,           // Chambre calme / silence
    ACTIVITY,       // Faible activité sonore / murmure
    ALERT,          // Bruit anormal / appel / dépassement du seuil
    DISCONNECTED    // Appareil injoignable
}

data class NoiseEvent(
    val id: String,
    val roomName: String,
    val timestamp: Long,
    val durationSeconds: Int,
    val maxDecibels: Int,
    val notes: String = "Bruit anormal détecté"
)

data class RoomUnitState(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int = 9877,
    val status: RoomStatus = RoomStatus.CALM,
    val currentDecibels: Int = 24,
    val noiseThresholdDb: Int = 55,
    val batteryPercent: Int = 85,
    val isCharging: Boolean = true,
    val temperatureCelsius: Float = 20.5f,
    val lastPingTimestamp: Long = System.currentTimeMillis(),
    val isAudioStreaming: Boolean = false,
    val isListeningToAudio: Boolean = false,
    val noiseEventsCount: Int = 0
)
