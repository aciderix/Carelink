package com.example

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.EventStore
import com.example.data.PreferencesManager
import com.example.model.*
import com.example.service.AlertManager
import com.example.service.CareLinkNotificationHelper
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class MonitorPauseTest {
    private lateinit var context: Context
    private lateinit var store: EventStore
    private lateinit var manager: AlertManager
    private lateinit var prefs: PreferencesManager
    private lateinit var notifications: NotificationManager
    private fun sound(id: String) = RoomAlertEvent(eventId = id, roomId = "room", roomName = "Chambre 12",
        eventType = AlertEventType.SOUND_ALERT, remote = true)
    private fun alarms() = notifications.activeNotifications.filter { it.id == 2000 || it.id == 1003 }
    @Before fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        prefs = PreferencesManager(context).apply { isMonitoringPaused = false; role = DeviceRole.MONITOR_UNIT }
        notifications = context.getSystemService(NotificationManager::class.java)
        notifications.cancelAll(); context.deleteDatabase("carelink-events.db")
        store = EventStore(context); manager = AlertManager(context, store)
    }
    @After fun finish() {
        manager.release(); store.close(); notifications.cancelAll()
        prefs.isMonitoringPaused = false; prefs.role = DeviceRole.NONE
        context.deleteDatabase("carelink-events.db")
    }

    @Test fun pauseCancelsCurrentAlarmsAndBlocksIncomingEventsAndRemindersWithoutChangingTheirState() {
        manager.triggerAlert(sound("before"))
        CareLinkNotificationHelper.restartReminder(context)
        notifications.notify(1002, CareLinkNotificationHelper.ongoing(context, "CareLink", "Veilleur"))
        assertEquals(2, alarms().size)
        manager.setMonitoringPaused(true)
        manager.triggerAlert(sound("during"))
        manager.incident(RoomUnitState("offline", "Chambre 14", "192.168.1.14"), AlertEventType.DISCONNECTION, "Hors ligne")
        manager.refreshNotifications(remind = true)
        assertTrue(alarms().isEmpty())
        assertTrue(notifications.activeNotifications.any { it.id == 1002 })
        assertEquals(3, manager.history.value.size)
        assertTrue(manager.history.value.none { it.isAcknowledged || it.resolved })
        assertTrue(store.pendingCommandIds().isEmpty())
        assertEquals(DeviceRole.MONITOR_UNIT, prefs.role)
    }

    @Test fun pauseSurvivesManagerAndJournalRestartAndStillAcceptsDurableReceipts() {
        manager.setMonitoringPaused(true)
        val event = sound("during")
        manager.triggerAlert(event)
        manager.release(); store.close()
        store = EventStore(context); manager = AlertManager(context, store)
        manager.triggerAlert(event.copy(revision = 1))
        manager.triggerAlert(sound("after-restart"))
        assertTrue(PreferencesManager(context).isMonitoringPaused)
        assertEquals(2, store.all("monitor").size)
        assertEquals(1, store.get("monitor", event.eventId)!!.revision)
        assertTrue(alarms().isEmpty())
    }

    @Test fun resumeRestoresUnresolvedGroupsButHonorsResolutionAndPendingAcknowledgements() {
        manager.setMonitoringPaused(true)
        manager.triggerAlert(sound("noise"))
        manager.triggerAlert(sound("pending").copy(roomId = "pending-room"))
        manager.transition("pending", false)
        manager.triggerAlert(sound("resolved").copy(roomId = "resolved-room", resolved = true, isAcknowledged = true))
        manager.setMonitoringPaused(false)
        assertFalse(prefs.isMonitoringPaused)
        assertEquals(listOf(CareLinkNotificationHelper.groupTag("room", AlertEventType.SOUND_ALERT)), alarms().map { it.tag })
        assertEquals(setOf("pending"), store.pendingCommandIds())
        manager.triggerAlert(sound("new").copy(roomId = "new-room"))
        assertEquals(2, alarms().size)
    }

    @Test fun callbackRacingWithPauseCannotLeaveAnAlarmPostedAfterPauseCompletes() {
        val start = CountDownLatch(1)
        val errors = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val worker = thread {
            try { start.await(); repeat(20) { manager.triggerAlert(sound("race-$it")) } }
            catch (error: Throwable) { errors.add(error) }
        }
        start.countDown()
        manager.setMonitoringPaused(true)
        worker.join(10000)
        assertFalse(worker.isAlive)
        assertTrue(errors.isEmpty())
        assertEquals(20, store.all("monitor").size)
        assertTrue(alarms().isEmpty())
    }

    @Test fun pausedMonitorDoesNotRaiseRestartAlarmsButRoomRoleRemainsIndependent() {
        manager.setMonitoringPaused(true)
        CareLinkNotificationHelper.restartReminder(context)
        assertTrue(alarms().isEmpty())
        prefs.role = DeviceRole.ROOM_UNIT
        CareLinkNotificationHelper.restartReminder(context)
        assertTrue(alarms().any { it.id == 1003 })
    }
}
