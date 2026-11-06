package com.example

import com.example.network.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ConnectException
import javax.net.ssl.SSLHandshakeException

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class DiscoveryTest {
    @Test fun discoveriesDoNotStarveTheSavedOrChangedAddress() {
        val addresses = RoomAddresses()
        addresses.offer("192.168.1.10")
        assertEquals("192.168.1.10", addresses.next())
        addresses.offer("192.168.1.11")
        repeat(5) { addresses.offer("192.168.1.10") }
        assertEquals(setOf("192.168.1.10", "192.168.1.11"), setOf(addresses.next(), addresses.next()))
        addresses.offer("example.org"); addresses.offer("224.0.0.1"); addresses.offer("999.1.1.1")
        assertEquals(setOf("192.168.1.10", "192.168.1.11"), setOf(addresses.next(), addresses.next()))
    }
    @Test fun malformedAndWrongProtocolBeaconsCannotCreateEndpoints() {
        val j = JSONObject().put("type", "CARELINK_ROOM_BEACON").put("version", 2).put("id", "room-123")
        assertEquals("192.168.1.20", LanDiscoveryManager.parseBeacon(j, "192.168.1.20")?.ipAddress)
        assertNull(LanDiscoveryManager.parseBeacon(j, "example.org"))
        assertNull(LanDiscoveryManager.parseBeacon(j.put("version", 1), "192.168.1.20"))
        assertNull(LanDiscoveryManager.parseBeacon(j.put("version", 2).put("controlPort", 1234), "192.168.1.20"))
        assertNull(LanDiscoveryManager.parseBeacon(j.put("controlPort", 9877).put("id", ""), "192.168.1.20"))
    }
    @Test fun failuresGiveDifferentActionsForNetworkTlsAndExpiredQr() {
        assertTrue(ConnectionDiagnostic.describe(ConnectException(), "192.168.1.20", true).contains("isolation"))
        assertTrue(ConnectionDiagnostic.describe(SSLHandshakeException("private diagnostic"), "192.168.1.20", true).contains("TLS"))
        assertTrue(ConnectionDiagnostic.describe(PairingRejected(true), "192.168.1.20", true).contains("nouveau QR"))
        assertFalse(ConnectionDiagnostic.describe(SSLHandshakeException("private diagnostic"), "192.168.1.20", true).contains("private diagnostic"))
    }
}
