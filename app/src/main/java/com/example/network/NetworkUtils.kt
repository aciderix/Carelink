package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {
    fun localNetwork(context: Context): Network? {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return manager.allNetworks.firstOrNull { n -> manager.getNetworkCapabilities(n)?.let {
            (it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) &&
                !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } == true }
    }
    fun getLocalIpAddress(context: Context? = null): String {
        if (context != null) {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            localNetwork(context)?.let { n ->
                manager.getLinkProperties(n)?.linkAddresses?.firstOrNull { it.address is Inet4Address }?.let { return it.address.hostAddress ?: "" }
            }
            // Never put a cellular or VPN address in a QR intended for the local Wi-Fi.
            return ""
        }
        return runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces()).filter { it.isUp && !it.isLoopback }
                .flatMap { Collections.list(it.inetAddresses) }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress.orEmpty()
        }.getOrDefault("")
    }
    fun getBroadcastAddress(context: Context? = null): InetAddress {
        if (context != null) {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val address = localNetwork(context)?.let { manager.getLinkProperties(it) }?.linkAddresses?.firstOrNull {
                it.address is Inet4Address && it.prefixLength in 1..30
            }
            if (address != null) {
                val bytes = address.address.address
                val ip = bytes.fold(0L) { value, b -> (value shl 8) or (b.toLong() and 255) }
                val mask = (0xffffffffL shl (32 - address.prefixLength)) and 0xffffffffL
                val broadcast = (ip and mask) or (mask xor 0xffffffffL)
                return InetAddress.getByAddress(ByteArray(4) { index -> (broadcast shr (24 - index * 8)).toByte() })
            }
        }
        return InetAddress.getByName("255.255.255.255")
    }
    fun isWifiConnected(context: Context) = localNetwork(context) != null
}
