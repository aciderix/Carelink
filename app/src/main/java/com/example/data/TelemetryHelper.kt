package com.example.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

object TelemetryHelper {

    data class BatteryInfo(
        val percentage: Int,
        val isCharging: Boolean,
        val temperatureC: Float? = null
    )

    fun getBatteryInfo(context: Context): BatteryInfo {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)
            if (batteryStatus != null) {
                val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val percent = if (level >= 0 && scale > 0) {
                    ((level.toFloat() / scale.toFloat()) * 100).toInt().coerceIn(0, 100)
                } else {
                    -1
                }
                val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                BatteryInfo(percent, isCharging, batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1).takeIf { it >= 0 }?.div(10f))
            } else {
                BatteryInfo(-1, false)
            }
        } catch (_: Exception) {
            BatteryInfo(-1, false)
        }
    }

}
