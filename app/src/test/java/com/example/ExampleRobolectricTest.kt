package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.data.TelemetryHelper
import com.example.model.AlertEventType
import com.example.model.DeviceRole
import com.example.model.RoomAlertEvent
import com.example.model.RoomStatus
import com.example.model.RoomUnitState
import com.example.service.AlertManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun readStringFromContext_matchesAppName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("CareLink", appName)
    }

    @Test
    fun preferencesManager_persistentInstallationId() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs1 = PreferencesManager(context)
        val id1 = prefs1.installationId
        assertNotNull(id1)
        assertTrue(id1.isNotBlank())

        val prefs2 = PreferencesManager(context)
        val id2 = prefs2.installationId
        assertEquals("Installation ID must be persistent across instances", id1, id2)
    }

    @Test
    fun preferencesManager_defaultValuesAndThresholdPerRoom() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)
        assertEquals(DeviceRole.NONE, prefs.role)
        assertEquals(55, prefs.defaultSensitivityDb)
        assertTrue(prefs.isVibrationAlertEnabled)
        assertTrue(prefs.isSoundAlertEnabled)

        prefs.setThresholdForRoom("room_12", 68)
        assertEquals(68, prefs.getThresholdForRoom("room_12"))
        assertEquals(55, prefs.getThresholdForRoom("room_other"))
    }

    @Test
    fun alertManager_multipleSimultaneousAlertsAndIndependentAcknowledge() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val alertManager = AlertManager(context)

        val alertRoom1 = RoomAlertEvent(
            eventId = "evt_1",
            roomId = "room_1",
            roomName = "Chambre 1",
            timestamp = System.currentTimeMillis(),
            levelReachedDb = 72,
            thresholdDb = 55,
            eventType = AlertEventType.SOUND_ALERT,
            durationSeconds = 2
        )

        val alertRoom2 = RoomAlertEvent(
            eventId = "evt_2",
            roomId = "room_2",
            roomName = "Chambre 2",
            timestamp = System.currentTimeMillis(),
            levelReachedDb = 68,
            thresholdDb = 55,
            eventType = AlertEventType.DISCONNECTION,
            durationSeconds = 1
        )

        alertManager.triggerAlert(alertRoom1)
        alertManager.triggerAlert(alertRoom2)

        var activeList = alertManager.activeAlertsFlow.value
        assertEquals("Both alerts must be active simultaneously", 2, activeList.size)

        // Acknowledge Room 1 only
        alertManager.acknowledgeAlert("room_1")
        activeList = alertManager.activeAlertsFlow.value
        assertEquals("Only Room 2 should remain active", 1, activeList.size)
        assertEquals("room_2", activeList[0].roomId)

        // Acknowledge All
        alertManager.acknowledgeAll()
        activeList = alertManager.activeAlertsFlow.value
        assertTrue("All alerts should be cleared", activeList.isEmpty())

        alertManager.release()
    }

    @Test
    fun telemetryHelper_batteryReadingGraceful() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val batteryInfo = TelemetryHelper.getBatteryInfo(context)
        assertNotNull(batteryInfo)
        // Robolectric returns -1 percentage when battery intent is not mocked, handled gracefully
        assertTrue(batteryInfo.percentage in -1..100)
    }

    @Test
    fun roomUnitState_statusCheck() {
        val state = RoomUnitState(
            id = "room_1",
            name = "Chambre 101",
            ipAddress = "192.168.1.50",
            status = RoomStatus.CALM,
            currentDecibels = 24,
            noiseThresholdDb = 55
        )
        assertEquals(RoomStatus.CALM, state.status)
        assertEquals(24, state.currentDecibels)
        assertFalse(state.isAudioStreaming)
    }
}
