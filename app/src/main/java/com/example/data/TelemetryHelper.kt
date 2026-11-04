package com.example.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager

object TelemetryHelper {

    data class BatteryInfo(
        val percentage: Int,
        val isCharging: Boolean
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
                BatteryInfo(percent, isCharging)
            } else {
                BatteryInfo(-1, false)
            }
        } catch (_: Exception) {
            BatteryInfo(-1, false)
        }
    }

    fun getWifiSignalDbm(context: Context): Int {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val info = wifiManager?.connectionInfo
            info?.rssi ?: 0
        } catch (_: Exception) {
            0
        }
    }
}
