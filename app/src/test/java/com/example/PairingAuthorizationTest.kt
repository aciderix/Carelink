package com.example

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.data.CredentialVault
import com.example.security.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.security.cert.CertificateFactory
import java.time.Duration

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class PairingAuthorizationTest {
    private class MemoryVault : CredentialVault {
        val entries = mutableMapOf<String, String>()
        override fun read(name: String) = entries[name]
        override fun write(name: String, value: String) { entries[name] = value }
    }
    private lateinit var room: PairingStore
    private lateinit var monitor: PairingStore
    @Before fun prepare() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        room = PairingStore(context, MemoryVault()); monitor = PairingStore(context, MemoryVault())
        room.cancelEnrollment()
    }
    @After fun cleanup() { room.cancelEnrollment() }
    private fun code(): String {
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(javaClass.getResourceAsStream("/certificates/room.pem"))
        return room.createCode(Base64.encodeToString(cert.encoded, Base64.NO_WRAP), "192.168.1.10")
    }
    @Test fun enrollmentIsSingleUseAndCredentialIsBoundToOneDevice() {
        monitor.importCode(code())
        val offered = monitor.rooms().single()
        val credential = room.authorize("monitor-A", offered.credential, true)
        assertNotEquals(offered.credential, credential)
        assertEquals(credential, room.authorize("monitor-A", credential, false))
        assertTrue(runCatching { room.authorize("monitor-B", credential, false) }.isFailure)
        assertTrue(runCatching { room.authorize("monitor-B", offered.credential, true) }.isFailure)
        room.revoke("monitor-A")
        assertFalse(room.stillAuthorized("monitor-A", credential))
        assertTrue(runCatching { room.authorize("monitor-A", credential, false) }.isFailure)
    }
    @Test fun anExpiredOrCancelledCodeCannotAuthorizeAnyone() {
        monitor.importCode(code()); val offered = monitor.rooms().single()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(121))
        assertTrue(runCatching { room.authorize("monitor", offered.credential, true) }.isFailure)
        monitor.importCode(code()); val second = monitor.rooms().single()
        room.cancelEnrollment()
        assertTrue(runCatching { room.authorize("monitor", second.credential, true) }.isFailure)
    }
    @Test fun unknownAndWrongCredentialsAreRejected() {
        assertTrue(runCatching { room.authorize("unknown", "A".repeat(43), false) }.isFailure)
        assertTrue(runCatching { monitor.importCode("CL1.old") }.isFailure)
        assertTrue(runCatching { monitor.importCode("CL2.corrupt") }.isFailure)
    }
}
