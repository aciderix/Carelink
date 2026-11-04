package com.example

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import com.example.model.*
import com.example.network.*
import com.example.security.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Two identities and stores, the real RoomServer and MonitorClient, using the actual LAN interface. */
@RunWith(AndroidJUnit4::class)
class MonitorConnectionInstrumentedTest {
    private class Device(base: Context, val prefix: String) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(prefix + name, mode)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?) = super.openOrCreateDatabase(prefix + name, mode, factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?) = super.openOrCreateDatabase(prefix + name, mode, factory, errorHandler)
        override fun deleteDatabase(name: String) = super.deleteDatabase(prefix + name)
    }
    @Test fun qrCompletesOnLanAndDeliversTelemetryAndAnEvent() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val roomContext = Device(base, "lan-room-")
        val monitorContext = Device(base, "lan-monitor-")
        val roomPrefs = PreferencesManager(roomContext)
        val monitorPrefs = PreferencesManager(monitorContext)
        assertNotEquals(roomPrefs.installationId, monitorPrefs.installationId)
        val roomPairing = PairingStore(roomContext)
        val monitorPairing = PairingStore(monitorContext)
        val roomDb = EventStore(roomContext)
        val monitorDb = EventStore(monitorContext)
        val address = NetworkUtils.getLocalIpAddress(base)
        assertTrue("Emulator must expose a local network interface", RoomAddresses.isLocalAddress(address))
        val code = roomPairing.createCode(SecureTransport(roomContext).certificate(), address)
        monitorPairing.importCode(code)
        val event = RoomAlertEvent(roomId = roomPrefs.installationId, roomName = "LAN test", eventType = AlertEventType.TEST, remote = true)
        roomDb.put("room", event)
        val server = RoomServer(roomContext, roomDb)
        val packets = AtomicInteger()
        server.telemetry = {
            val starting = packets.getAndIncrement() == 0
            JSONObject().put("id", roomPrefs.installationId).put("name", "LAN test").put("decibels", 40).put("threshold", 55)
                .put("status", if (starting) "VERIFYING" else "CALM").put("isMicOperational", !starting)
                .put("captureAgeMs", if (starting) Long.MAX_VALUE else 50L).put("samples", if (starting) 0 else 9000)
                .put("battery", 90).put("isCharging", true).put("diagnostic", "")
        }
        val client = MonitorClient(monitorContext, monitorDb)
        val telemetry = CountDownLatch(2)
        val startup = CountDownLatch(1)
        val delivered = CountDownLatch(1)
        val errors = CopyOnWriteArrayList<String>()
        client.onRoomTelemetryReceived = {
            if (it.id == roomPrefs.installationId && it.status == RoomStatus.CALM) telemetry.countDown()
            if (it.status == RoomStatus.VERIFYING && !it.isMicOperational) startup.countDown()
        }
        client.onRoomAlertReceived = { monitorDb.put("monitor", it); if (it.eventId == event.eventId) delivered.countDown() }
        client.onRoomDisconnected = { _, message -> errors.add(message) }
        try {
            server.start()
            client.connectToRoom(roomPrefs.installationId, address)
            val connected = telemetry.await(40, TimeUnit.SECONDS)
            assertTrue("No telemetry: $errors", connected)
            assertEquals("Startup verification must not become a microphone alarm", 0L, startup.count)
            assertTrue("Event not delivered", delivered.await(10, TimeUnit.SECONDS))
            assertFalse(monitorPairing.rooms().single().enrolling)
            assertEquals(listOf(monitorPrefs.installationId), roomPairing.monitors())
            assertEquals(1, server.connectedMonitors)
            assertNotNull(monitorDb.get("monitor", event.eventId))
        } finally {
            client.disconnectAll(); server.stop(); roomPairing.cancelEnrollment()
            monitorPairing.forget(roomPrefs.installationId); roomPairing.revoke(monitorPrefs.installationId)
        }
    }
}
