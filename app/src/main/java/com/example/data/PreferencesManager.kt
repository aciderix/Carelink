package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.DeviceRole

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("carelink_prefs", Context.MODE_PRIVATE)

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

    var roomName: String
        get() = prefs.getString("room_name", "Chambre 1") ?: "Chambre 1"
        set(value) = prefs.edit().putString("room_name", value).apply()

    var defaultSensitivityDb: Int
        get() = prefs.getInt("sensitivity_db", 55)
        set(value) = prefs.edit().putInt("sensitivity_db", value).apply()

    var isVibrationAlertEnabled: Boolean
        get() = prefs.getBoolean("vibrate_alert", true)
        set(value) = prefs.edit().putBoolean("vibrate_alert", value).apply()

    var isSoundAlertEnabled: Boolean
        get() = prefs.getBoolean("sound_alert", true)
        set(value) = prefs.edit().putBoolean("sound_alert", value).apply()
}
