package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.model.DeviceRole
import com.example.model.RoomStatus
import com.example.model.RoomUnitState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun preferencesManager_defaultValues() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)
        assertEquals(DeviceRole.NONE, prefs.role)
        assertEquals(55, prefs.defaultSensitivityDb)
        assertTrue(prefs.isVibrationAlertEnabled)
        assertTrue(prefs.isSoundAlertEnabled)
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
