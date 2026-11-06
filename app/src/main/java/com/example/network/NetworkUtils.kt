package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

/**
 * Local Wi-Fi/Ethernet link of this device.
 * [network] is null when the address was only found through the interface list or the Wi-Fi manager
 * (older or OEM-modified Android builds); sockets then use the default route, which is the LAN for private addresses.
 */
data class LocalLink(val network: Network?, val address: Inet4Address, val prefixLength: Int, val source: String) {
    val hostAddress: String get() = address.hostAddress.orEmpty()
}

/** One possible link, described with plain values so that the selection rules can be unit tested. */
data class LinkCandidate(val network: Network?, val interfaceName: String?, val localTransport: Boolean, val vpn: Boolean,
    val addresses: List<Pair<Inet4Address, Int>>, val source: String)

object NetworkUtils {
    private val mobileInterfaces = listOf("rmnet", "ccmni", "pdp", "ppp", "tun", "ipsec", "clat", "v4-", "dummy", "lo", "p2p", "aware", "nan")

    /** Wi-Fi/Ethernet interface names, including the OEM variants seen on Samsung and MediaTek devices. */
    fun isLocalInterfaceName(name: String?): Boolean {
        val n = name?.lowercase() ?: return false
        if (mobileInterfaces.any { n.startsWith(it) }) return false
        return n.startsWith("wlan") || n.startsWith("eth") || n.startsWith("swlan") || n.startsWith("ap") || n.startsWith("wifi") || n.startsWith("en")
    }

    private fun usable(address: Inet4Address) = !address.isLoopbackAddress && !address.isLinkLocalAddress && !address.isAnyLocalAddress && !address.isMulticastAddress

    /**
     * Keeps the first Wi-Fi/Ethernet candidate that actually carries a usable IPv4 address. A Wi-Fi Direct or
     * half-configured network listed before the real access point must not hide it.
     */
    fun pick(candidates: List<LinkCandidate>): LocalLink? {
        for (c in candidates) {
            if (!c.localTransport || c.vpn) continue
            val (address, prefix) = c.addresses.firstOrNull { usable(it.first) } ?: continue
            return LocalLink(c.network, address, prefix, c.source)
        }
        return null
    }

    private fun candidates(context: Context): List<LinkCandidate> {
        val list = mutableListOf<LinkCandidate>()
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (manager != null) {
            val networks = buildList {
                runCatching { manager.activeNetwork }.getOrNull()?.let { add(it) }
                runCatching { manager.allNetworks.toList() }.getOrDefault(emptyList()).forEach { if (it !in this) add(it) }
            }
            for (network in networks) {
                val caps = runCatching { manager.getNetworkCapabilities(network) }.getOrNull()
                val link = runCatching { manager.getLinkProperties(network) }.getOrNull() ?: continue
                val name = link.interfaceName
                val local = caps?.let { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) }
                    ?: isLocalInterfaceName(name)
                val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                list += LinkCandidate(network, name, local, vpn,
                    link.linkAddresses.mapNotNull { la -> (la.address as? Inet4Address)?.let { it to la.prefixLength } }, "Android ${name ?: "réseau"}")
            }
        }
        runCatching {
            for (iface in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
                val addresses = iface.interfaceAddresses.mapNotNull { ia -> (ia.address as? Inet4Address)?.let { it to ia.networkPrefixLength.toInt() } }
                // Bind to the Android Network of the same interface when there is one.
                val owner = list.firstOrNull { it.network != null && it.interfaceName == iface.name }?.network
                list += LinkCandidate(owner, iface.name, isLocalInterfaceName(iface.name), false, addresses, "interface ${iface.name}")
            }
        }
        runCatching {
            @Suppress("DEPRECATION")
            val ip = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo?.ipAddress ?: 0
            if (ip != 0) {
                val bytes = ByteArray(4) { i -> (ip shr (8 * i)).toByte() } // WifiManager reports little-endian.
                list += LinkCandidate(null, null, true, false, listOf(InetAddress.getByAddress(bytes) as Inet4Address to 24), "Wi-Fi Android")
            }
        }
        return list
    }

    fun localLink(context: Context): LocalLink? = runCatching { pick(candidates(context)) }.getOrNull()

    fun localNetwork(context: Context): Network? = localLink(context)?.network

    fun getLocalIpAddress(context: Context? = null): String {
        // Never put a cellular or VPN address in a QR intended for the local Wi-Fi.
        if (context != null) return localLink(context)?.hostAddress.orEmpty()
        return runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces()).filter { it.isUp && !it.isLoopback }
                .flatMap { Collections.list(it.inetAddresses) }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress.orEmpty()
        }.getOrDefault("")
    }

    fun broadcastAddress(link: LocalLink): InetAddress {
        val prefix = link.prefixLength.coerceIn(1, 30)
        val ip = link.address.address.fold(0L) { value, b -> (value shl 8) or (b.toLong() and 255) }
        val mask = (0xffffffffL shl (32 - prefix)) and 0xffffffffL
        val broadcast = (ip and mask) or (mask xor 0xffffffffL)
        return InetAddress.getByAddress(ByteArray(4) { index -> (broadcast shr (24 - index * 8)).toByte() })
    }

    fun getBroadcastAddress(context: Context? = null): InetAddress =
        context?.let { localLink(it) }?.let { broadcastAddress(it) } ?: InetAddress.getByName("255.255.255.255")

    fun isWifiConnected(context: Context) = localLink(context) != null

    /** Short human-readable description for the diagnostic panels. */
    fun describe(context: Context): String = localLink(context)?.let { "${it.hostAddress}/${it.prefixLength} · ${it.source}" }
        ?: "Aucune adresse Wi-Fi ou Ethernet détectée"
}
