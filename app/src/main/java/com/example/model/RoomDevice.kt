package com.example.model

import org.json.JSONObject
import java.util.UUID

enum class DeviceRole { NONE, ROOM_UNIT, MONITOR_UNIT }
enum class RoomStatus { VERIFYING, CALM, ACTIVITY, ALERT, DISCONNECTED, MIC_ERROR, DEGRADED }
enum class AlertEventType { SOUND_ALERT, DISCONNECTION, MIC_ERROR, LOW_BATTERY, SERVICE_INTERRUPTED, TEST }

data class RoomAlertEvent(
    val eventId: String = UUID.randomUUID().toString(), val roomId: String, val roomName: String,
    val timestamp: Long = System.currentTimeMillis(), val levelReachedDb: Int = 0, val thresholdDb: Int = 55,
    val eventType: AlertEventType, val isAcknowledged: Boolean = false, val durationSeconds: Int = 0,
    val notes: String = "", val resolved: Boolean = false, val acknowledgedBy: String = "", val remote: Boolean = false, val revision: Int = 0, val resolvedAt: Long = 0L
) {
    fun toJson() = JSONObject().apply {
        put("eventId", eventId); put("roomId", roomId); put("roomName", roomName); put("timestamp", timestamp)
        put("levelReachedDb", levelReachedDb); put("thresholdDb", thresholdDb); put("eventType", eventType.name)
        put("isAcknowledged", isAcknowledged); put("durationSeconds", durationSeconds); put("notes", notes)
        put("resolvedAt", resolvedAt); put("revision", revision); put("resolved", resolved); put("acknowledgedBy", acknowledgedBy); put("remote", remote)
    }
    companion object {
        fun fromJson(j: JSONObject): RoomAlertEvent {
            val id = j.getString("eventId"); require(id.length in 1..128)
            return RoomAlertEvent(id, j.getString("roomId"), j.getString("roomName").take(80), j.getLong("timestamp"),
                j.optInt("levelReachedDb"), j.optInt("thresholdDb", 55), AlertEventType.valueOf(j.getString("eventType")),
                j.optBoolean("isAcknowledged"), j.optInt("durationSeconds"), j.optString("notes").take(300),
                j.optBoolean("resolved"), j.optString("acknowledgedBy").take(80), j.optBoolean("remote"), j.optInt("revision"), j.optLong("resolvedAt"))
        }
    }
}

data class RoomUnitState(
    val id: String, val name: String, val ipAddress: String,
    val port: Int = 9877, val audioPort: Int = 9878, val talkbackPort: Int = 9879,
    val status: RoomStatus = RoomStatus.VERIFYING, val currentDecibels: Int = 0, val noiseThresholdDb: Int = 55,
    val batteryPercent: Int = -1, val isCharging: Boolean = false, val wifiSignalDbm: Int = 0,
    val isMicOperational: Boolean = false, val lastSeenTimestamp: Long = 0L, val isAudioStreaming: Boolean = false,
    val activeAlert: RoomAlertEvent? = null, val captureAgeMs: Long = Long.MAX_VALUE, val samplesCaptured: Long = 0,
    val temperatureC: Float? = null, val diagnostic: String = "Vérification en attente"
)
