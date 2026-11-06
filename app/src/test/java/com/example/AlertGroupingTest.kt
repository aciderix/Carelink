package com.example

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.EventStore
import com.example.model.*
import com.example.service.AlertManager
import com.example.service.CareLinkNotificationHelper
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class AlertGroupingTest {
    private lateinit var context: Context
    private lateinit var store: EventStore
    private lateinit var manager: AlertManager
    private fun sound(id: String, time: Long, acknowledged: Boolean = false) = RoomAlertEvent(eventId = id,
        roomId = "room", roomName = "Chambre 12", timestamp = time, eventType = AlertEventType.SOUND_ALERT,
        isAcknowledged = acknowledged, remote = true)
    @Before fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        context.getSystemService(NotificationManager::class.java).cancelAll()
        context.deleteDatabase("carelink-events.db"); store = EventStore(context); manager = AlertManager(context, store)
    }
    @After fun finish() { manager.release(); store.close(); context.deleteDatabase("carelink-events.db") }
    private fun notifications() = context.getSystemService(NotificationManager::class.java).activeNotifications
        .filter { it.tag?.startsWith("carelink:") == true }
    @Test fun repeatedNoiseKeepsTwoJournalEntriesButOneActiveGroupAndNotification() {
        manager.triggerAlert(sound("first", 1000)); manager.triggerAlert(sound("second", 2000))
        assertEquals(2, store.all("monitor").size)
        val group = AlertGroup.active(manager.history.value).single()
        assertEquals(2, group.occurrences)
        assertEquals(listOf("first", "second"), group.events.map { it.eventId })
        assertEquals(1, notifications().size)
        assertEquals(CareLinkNotificationHelper.groupTag("room", AlertEventType.SOUND_ALERT), notifications().single().tag)
        manager.transitionMany(group.events.map { it.eventId }, false)
        assertEquals(setOf("first", "second"), store.pendingCommandIds())
        assertTrue(notifications().isEmpty())
    }
    @Test fun newNoiseAfterAcknowledgementReactivatesOneGroupAndIsNotSilencedByOldReceipts() {
        val first = sound("first", 1000); val second = sound("second", 2000)
        manager.triggerAlert(first); manager.triggerAlert(second)
        manager.transitionMany(listOf("first", "second"), false)
        manager.triggerAlert(first.copy(isAcknowledged = true, revision = 1))
        manager.triggerAlert(second.copy(isAcknowledged = true, revision = 1))
        assertTrue(AlertGroup.active(manager.history.value).single().isAcknowledged)
        assertTrue(notifications().isEmpty())
        manager.triggerAlert(sound("third", 3000))
        val group = AlertGroup.active(manager.history.value).single()
        assertEquals(3, group.occurrences)
        assertFalse(group.isAcknowledged)
        assertEquals(1, notifications().size)
        manager.triggerAlert(first.copy(isAcknowledged = true, revision = 1))
        assertFalse(AlertGroup.active(manager.history.value).single().isAcknowledged)
        assertEquals(1, notifications().size)
    }
    @Test fun resolvingASnapshotLeavesALaterEpisodeActiveAndQueuedActionsSurviveRestart() {
        val first = sound("first", 1000); val second = sound("second", 2000)
        manager.triggerAlert(first); manager.triggerAlert(second)
        val snapshot = AlertGroup.active(manager.history.value).single().events.map { it.eventId }
        manager.triggerAlert(sound("third", 3000))
        manager.transitionMany(snapshot, true)
        assertEquals(setOf("first", "second"), store.pendingCommandIds())
        assertEquals(1, notifications().size) // The third occurrence was not part of the action.
        manager.release(); manager = AlertManager(context, store)
        assertEquals(setOf("first", "second"), store.pendingCommandIds())
        assertEquals(1, notifications().size)
        manager.triggerAlert(first.copy(isAcknowledged = true, resolved = true, resolvedAt = 4000, revision = 1))
        manager.triggerAlert(second.copy(isAcknowledged = true, resolved = true, resolvedAt = 4000, revision = 1))
        assertTrue(store.pendingCommandIds().isEmpty())
        assertEquals(listOf("third"), AlertGroup.active(manager.history.value).single().events.map { it.eventId })
        assertEquals(3, manager.history.value.size)
    }
    @Test fun differentRoomsAndTechnicalCausesKeepIndependentGroups() {
        manager.triggerAlert(sound("noise", 1000))
        manager.triggerAlert(sound("other", 1000).copy(roomId = "other-room"))
        manager.triggerAlert(sound("microphone", 1000).copy(eventType = AlertEventType.MIC_ERROR))
        assertEquals(3, AlertGroup.active(manager.history.value).size)
        manager.transition("noise", false)
        assertEquals(2, notifications().size)
        assertEquals(setOf("noise"), store.pendingCommandIds())
    }
    @Test fun aLateCallbackFromAStoppedMonitorIsRecoveredByTheNextMonitorOnRetransmission() {
        val previous = manager
        previous.release()
        manager = AlertManager(context, store)
        val late = sound("late", 1000)
        previous.triggerAlert(late) // Persistence from a callback already in flight during shutdown.
        assertTrue(notifications().isEmpty())
        manager.triggerAlert(late)
        assertEquals(1, notifications().size)
        assertEquals(1, manager.history.value.size)
    }
}
