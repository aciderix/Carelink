package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.security.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket

/** Exercises AndroidKeyStore, real TLS, exact pinning, one-time enrollment and revocation. */
@RunWith(AndroidJUnit4::class)
class SecureTransportInstrumentedTest {
    @Test fun encryptedSessionEnrollsOnceAndRevocationRejectsTheDevice() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PairingStore(context)
        val transport = SecureTransport(context)
        store.importCode(store.createCode(transport.certificate(), "127.0.0.1"))
        val offered = store.rooms().first()
        val executor = Executors.newSingleThreadExecutor()
        val server = transport.server(0)
        try {
            val response = executor.submit<String> {
                transport.accept(server.accept() as SSLSocket, "control").use { session ->
                    val payload = session.receiveJson(); session.send(JSONObject().put("echo", payload.getString("message")))
                    session.deviceId
                }
            }
            try {
                transport.connect(offered, "control", server.localPort).use { session ->
                    session.send(JSONObject().put("message", "CareLink authenticated test"))
                    assertEquals("CareLink authenticated test", session.receiveJson().getString("echo"))
                }
            } catch (failure: Exception) {
                val serverFailure = runCatching { response.get(2, TimeUnit.SECONDS) }.exceptionOrNull()
                throw AssertionError("Android TLS negotiation failed on server", serverFailure?.cause ?: failure)
            }
            val deviceId = response.get(15, TimeUnit.SECONDS)
            assertFalse(store.rooms().first { it.id == offered.id }.enrolling)
            assertTrue(store.monitors().contains(deviceId))
            val replay = executor.submit<Boolean> {
                try { transport.accept(server.accept() as SSLSocket, "control").close(); false }
                catch (_: Exception) { true }
            }
            runCatching { transport.connect(offered, "control", server.localPort).close() }
            assertTrue(replay.get(15, TimeUnit.SECONDS))
            store.revoke(deviceId)
            val rejected = executor.submit<Boolean> {
                val socket = server.accept() as SSLSocket
                try { transport.accept(socket, "control").close(); false }
                catch (_: Exception) { true } finally { socket.close() }
            }
            assertTrue(runCatching { transport.connect(store.rooms().first { it.id == offered.id }, "control", server.localPort).close() }.isFailure)
            assertTrue(rejected.get(15, TimeUnit.SECONDS))
        } finally { server.close(); executor.shutdownNow(); store.forget(offered.id); store.cancelEnrollment() }
    }

    @Test fun pairingRejectsLegacyAndMalformedCodes() {
        val store = PairingStore(ApplicationProvider.getApplicationContext())
        assertTrue(runCatching { store.importCode("CL1.untrusted") }.isFailure)
        assertTrue(runCatching { store.importCode("CL2.invalid") }.isFailure)
    }
}
