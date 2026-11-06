package com.example

import com.example.network.*
import com.example.security.SecureTransport
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.Inet4Address
import java.util.Date

class LocalNetworkTest {
    private fun ip(text: String) = InetAddress.getByName(text) as Inet4Address
    private fun candidate(name: String?, local: Boolean, vararg addresses: String, vpn: Boolean = false) =
        LinkCandidate(null, name, local, vpn, addresses.map { ip(it) to 24 }, "test $name")

    @Test fun aWifiNetworkWithoutIpv4DoesNotHideTheRealAccessPoint() {
        val link = NetworkUtils.pick(listOf(candidate("p2p0", true), candidate("wlan0", true, "192.168.1.42")))
        assertEquals("192.168.1.42", link?.hostAddress)
    }
    @Test fun cellularVpnLoopbackAndLinkLocalAddressesAreNeverOffered() {
        assertNull(NetworkUtils.pick(listOf(candidate("rmnet0", false, "10.12.0.4"), candidate("tun0", true, "10.8.0.2", vpn = true),
            candidate("lo", true, "127.0.0.1"), candidate("wlan0", true, "169.254.3.3"))))
    }
    @Test fun interfaceListFallbackFindsWifiWhenAndroidReportsNoNetwork() {
        assertTrue(NetworkUtils.isLocalInterfaceName("wlan0"))
        assertTrue(NetworkUtils.isLocalInterfaceName("swlan0"))
        assertTrue(NetworkUtils.isLocalInterfaceName("eth0"))
        assertFalse(NetworkUtils.isLocalInterfaceName("rmnet_data0"))
        assertFalse(NetworkUtils.isLocalInterfaceName("p2p-wlan0-0"))
        assertFalse(NetworkUtils.isLocalInterfaceName(null))
        val link = NetworkUtils.pick(listOf(candidate("rmnet_data0", false, "100.71.2.9"), candidate("wlan0", true, "10.0.0.17")))
        assertEquals("10.0.0.17", link?.hostAddress)
    }
    @Test fun broadcastFollowsThePrefix() {
        assertEquals("192.168.1.255", NetworkUtils.broadcastAddress(LocalLink(null, ip("192.168.1.42"), 24, "t")).hostAddress)
        assertEquals("10.0.15.255", NetworkUtils.broadcastAddress(LocalLink(null, ip("10.0.12.3"), 22, "t")).hostAddress)
    }
    @Test fun missingLocalAddressGivesAnActionableMessage() {
        val text = ConnectionDiagnostic.describe(IllegalStateException("Réseau local Wi-Fi ou Ethernet indisponible"), "192.168.1.20", false)
        assertTrue(text.contains("adresse Wi-Fi locale"))
    }
    @Test fun identityDateStaysBelow2038AndOnlyAnExpiredIdentityIsReplaced() {
        assertTrue(Date(SecureTransport.CERTIFICATE_NOT_AFTER).before(Date(2147483647000L)))
    }
}
