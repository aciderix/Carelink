package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.EventStore
import com.example.model.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class EventStoreTest {
    private lateinit var context: Context
    private lateinit var store: EventStore
    private fun event(id: String = "episode") = RoomAlertEvent(id, "room", "Chambre 12", timestamp = 1000, eventType = AlertEventType.SOUND_ALERT, remote = true)
    @Before fun prepare() { context = ApplicationProvider.getApplicationContext(); context.deleteDatabase("carelink-events.db"); store = EventStore(context) }
    @After fun close() { store.close(); context.deleteDatabase("carelink-events.db") }
    @Test fun outageAndProcessRestartPreserveUndeliveredEvent() {
        store.put("room", event()); store.close(); store = EventStore(context)
        assertEquals(listOf("episode"), store.pending("monitor").map { it.eventId })
        store.received("episode", "monitor", 0)
        assertTrue(store.pending("monitor").isEmpty())
        assertEquals(1, store.pending("another-monitor").size)
    }
    @Test fun repeatedPacketsKeepOneEventAndOriginalTime() {
        assertTrue(store.put("monitor", event()))
        assertFalse(store.put("monitor", event()))
        assertEquals(1, store.all("monitor").size)
        assertEquals(1000L, store.all("monitor").single().timestamp)
    }
    @Test fun severalIncidentsInOneRoomNeverReplaceEachOther() {
        store.put("monitor", event("sound"))
        store.put("monitor", event("microphone").copy(eventType = AlertEventType.MIC_ERROR))
        store.transition("monitor", "sound", "night-team", false)
        val events = store.all("monitor")
        assertEquals(2, events.size)
        assertFalse(events.single { it.eventId == "microphone" }.isAcknowledged)
        assertTrue(events.single { it.eventId == "sound" }.isAcknowledged)
        assertFalse(events.single { it.eventId == "sound" }.resolved)
    }
    @Test fun oldReceiptCannotHideNewAcknowledgementState() {
        store.put("room", event()); store.received("episode", "monitor", 0)
        store.transition("room", "episode", "monitor-2", false)
        store.received("episode", "monitor", 0) // Delayed receipt from before the transition.
        assertEquals(1, store.pending("monitor").size)
        store.received("episode", "monitor", 1)
        assertTrue(store.pending("monitor").isEmpty())
    }
    @Test fun measurementUpdateIsDeliveredAfterInitialReceiptAndPreservesHumanState() {
        store.put("room", event()); store.received("episode", "monitor", 0)
        store.transition("room", "episode", "night-team", false)
        store.measurements("episode", 79, 8)
        val update = store.pending("monitor").single()
        assertTrue(update.isAcknowledged)
        assertEquals(79, update.levelReachedDb)
        assertEquals(8, update.durationSeconds)
        assertEquals(2, update.revision)
    }
    @Test fun aReusedIdentifierCannotChangeRoomOrKind() {
        store.put("monitor", event())
        assertTrue(runCatching { store.put("monitor", event().copy(roomId = "other")) }.isFailure)
        assertEquals("room", store.get("monitor", "episode")!!.roomId)
    }
    @Test fun replayCannotUndoResolution() {
        store.put("monitor", event()); store.transition("monitor", "episode", "night-team", true)
        store.put("monitor", event())
        assertTrue(store.get("monitor", "episode")!!.resolved)
        assertTrue(store.get("monitor", "episode")!!.isAcknowledged)
    }
    @Test fun pendingHumanActionSurvivesRestartAndRequiresConfirmation() {
        store.put("monitor", event()); store.request("episode", "room", "RESOLVE")
        store.close(); store = EventStore(context)
        assertEquals("RESOLVE", store.commands("room").single().second)
        store.confirm(event().copy(isAcknowledged = true))
        assertEquals(1, store.commands("room").size)
        store.confirm(event().copy(isAcknowledged = true, resolved = true))
        assertTrue(store.commands("room").isEmpty())
    }
    @Test fun retentionNeverDeletesUnresolvedEvents() {
        store.put("room", event("pending")); store.put("room", event("finished"))
        store.transition("room", "finished", "monitor", true)
        store.prune(System.currentTimeMillis() + 8L * 24 * 3600 * 1000)
        assertEquals(listOf("pending"), store.all("room").map { it.eventId })
    }
    @Test fun oneContinuousOutageCannotRealertAfterAcknowledgementClosureOrRestart() {
        val room = RoomUnitState("room", "Chambre 12", "192.168.1.20")
        val first = store.incident(room, AlertEventType.DISCONNECTION, "No network")!!
        store.transition("monitor", first.eventId, "night-team", false)
        assertNull(store.incident(room, AlertEventType.DISCONNECTION, "Still no network"))
        store.transition("monitor", first.eventId, "night-team", true)
        assertNull(store.incident(room, AlertEventType.DISCONNECTION, "Still disconnected"))
        store.close(); store = EventStore(context)
        assertNull(store.incident(room, AlertEventType.DISCONNECTION, "Restart during outage"))
        store.recoverIncident(room.id, AlertEventType.DISCONNECTION)
        val next = store.incident(room, AlertEventType.DISCONNECTION, "A new outage")!!
        assertNotEquals(first.eventId, next.eventId)
    }
    @Test fun networkRecoveryClosesLocalOutageButKeepsSoundAndMicrophoneEvents() {
        val room = RoomUnitState("room", "Chambre 12", "192.168.1.20")
        store.put("monitor", event("sound"))
        store.incident(room, AlertEventType.MIC_ERROR, "Microphone failed")
        val outage = store.incident(room, AlertEventType.DISCONNECTION, "Disconnected")!!
        assertEquals(listOf(outage.eventId), store.recoverIncident(room.id, AlertEventType.DISCONNECTION).map { it.eventId })
        assertTrue(store.get("monitor", outage.eventId)!!.resolved)
        assertFalse(store.get("monitor", "sound")!!.resolved)
        assertFalse(store.all("monitor").single { it.eventType == AlertEventType.MIC_ERROR }.resolved)
    }
}
