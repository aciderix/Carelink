package com.example

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.EventStore
import com.example.model.*
import com.example.service.AlertManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class AlertAcknowledgementTest {
    @Test fun takingResponsibilityOfflineMutesTheEventAcrossReplayAndRestartButStillRequiresConfirmation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("carelink-events.db")
        val store = EventStore(context)
        val notifications = context.getSystemService(NotificationManager::class.java)
        val event = RoomAlertEvent(roomId = "room", roomName = "Chambre", eventType = AlertEventType.SOUND_ALERT, remote = true)
        var manager = AlertManager(context, store)
        fun visible() = notifications.activeNotifications.any { it.tag == event.eventId }
        try {
            manager.triggerAlert(event)
            assertTrue(visible())
            manager.transition(event.eventId, false)
            assertFalse(visible())
            assertEquals("ACK", store.commands("room").single().second)
            assertFalse(store.get("monitor", event.eventId)!!.isAcknowledged)
            manager.triggerAlert(event.copy(revision = 1))
            assertFalse(visible())
            manager.release(); manager = AlertManager(context, store)
            assertFalse(visible())
            manager.triggerAlert(event.copy(isAcknowledged = true, revision = 2))
            assertTrue(store.commands("room").isEmpty())
            assertTrue(manager.history.value.single().isAcknowledged)
            assertFalse(visible())
        } finally { manager.release(); store.close(); context.deleteDatabase("carelink-events.db") }
    }
}
