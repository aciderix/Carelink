package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.DeviceRole
import java.util.UUID

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("carelink_prefs", Context.MODE_PRIVATE)

    val installationId: String
        get() {
            var id = prefs.getString("device_installation_id", null)
            if (id.isNullOrBlank()) {
                id = UUID.randomUUID().toString()
                prefs.edit().putString("device_installation_id", id).apply()
            }
            return id
        }

    var role: DeviceRole
        get() {
            val name = prefs.getString("device_role", DeviceRole.NONE.name) ?: DeviceRole.NONE.name
            return try {
                DeviceRole.valueOf(name)
            } catch (_: Exception) {
                DeviceRole.NONE
            }
        }
        set(value) {
            prefs.edit().putString("device_role", value.name).apply()
        }

    var isRoleLocked: Boolean
        get() = prefs.getBoolean("is_role_locked", false)
        set(value) = prefs.edit().putBoolean("is_role_locked", value).apply()

    var roomName: String
        get() = prefs.getString("room_name", "Chambre 1") ?: "Chambre 1"
        set(value) = prefs.edit().putString("room_name", value).apply()

    var defaultSensitivityDb: Int
        get() = prefs.getInt("sensitivity_db", 55)
        set(value) = prefs.edit().putInt("sensitivity_db", value).apply()

    var minNoiseDurationMs: Int
        get() = prefs.getInt("min_noise_duration_ms", 300)
        set(value) = prefs.edit().putInt("min_noise_duration_ms", value).apply()

    var isVibrationAlertEnabled: Boolean
        get() = prefs.getBoolean("vibrate_alert", true)
        set(value) = prefs.edit().putBoolean("vibrate_alert", value).apply()

    var isSoundAlertEnabled: Boolean
        get() = prefs.getBoolean("sound_alert", true)
        set(value) = prefs.edit().putBoolean("sound_alert", value).apply()

    var isAlertRepeatEnabled: Boolean
        get() = prefs.getBoolean("alert_repeat_enabled", true)
        set(value) = prefs.edit().putBoolean("alert_repeat_enabled", value).apply()

    var alertRepeatIntervalSeconds: Int
        get() = prefs.getInt("alert_repeat_interval_sec", 30)
        set(value) = prefs.edit().putInt("alert_repeat_interval_sec", value).apply()

    fun getThresholdForRoom(roomId: String): Int {
        return prefs.getInt("threshold_$roomId", defaultSensitivityDb)
    }

    fun setThresholdForRoom(roomId: String, thresholdDb: Int) {
        prefs.edit().putInt("threshold_$roomId", thresholdDb).apply()
    }
}
