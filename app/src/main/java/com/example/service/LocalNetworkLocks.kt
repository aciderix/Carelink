package com.example.service

import android.content.Context
import android.net.wifi.WifiManager

/** Requests active Wi-Fi/multicast delivery; OEM/Doze behaviour still needs device qualification. */
class LocalNetworkLocks(context: Context) : AutoCloseable {
    private val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    @Suppress("DEPRECATION")
    private val wifi = manager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "CareLink:Wifi")?.apply { setReferenceCounted(false) }
    private val multicast = manager?.createMulticastLock("CareLink:Discovery")?.apply { setReferenceCounted(false) }
    fun acquire() { wifi?.acquire(); multicast?.acquire() }
    override fun close() { runCatching { if (wifi?.isHeld == true) wifi.release() }; runCatching { if (multicast?.isHeld == true) multicast.release() } }
}
