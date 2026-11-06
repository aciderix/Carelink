package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.model.*
import com.example.service.MonitorMonitoringService.MonitorServiceState
import com.example.ui.*
import com.example.ui.theme.CareLinkTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders a realistic shift in both themes and the event list, for visual review of the design. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val base = RoomUnitState("r", "Chambre 12", "192.168.1.20", status = RoomStatus.CALM, isMicOperational = true,
        currentDecibels = 38, batteryPercent = 83, lastSeenTimestamp = 1000)
    private val rooms = listOf(
        base.copy(id = "r2", name = "Chambre 2", currentDecibels = 61, status = RoomStatus.ALERT),
        base.copy(id = "r10", name = "Chambre 10", status = RoomStatus.DISCONNECTED, isMicOperational = false),
        base.copy(id = "r12"),
        base.copy(id = "r14", name = "Chambre 14", currentDecibels = 49, status = RoomStatus.ACTIVITY),
        base.copy(id = "r16", name = "Chambre 16", batteryPercent = 12),
    )
    private val events = listOf(
        RoomAlertEvent("e1", "r2", "Chambre 2", timestamp = 1_700_000_000_000, levelReachedDb = 64, eventType = AlertEventType.SOUND_ALERT),
        RoomAlertEvent("e2", "r2", "Chambre 2", timestamp = 1_700_000_060_000, levelReachedDb = 61, eventType = AlertEventType.SOUND_ALERT),
        RoomAlertEvent("e3", "r10", "Chambre 10", timestamp = 1_700_000_100_000, eventType = AlertEventType.DISCONNECTION, isAcknowledged = true),
    )
    private val state = MonitorServiceState(isRunning = true, monitoredRooms = rooms, activeAlerts = events, history = events)

    private fun screenshot(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("build/screenshots/$name.png").apply { parentFile!!.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun dashboard(mode: ThemeMode) = compose.setContent { CareLinkTheme(mode) {
        CareLinkScaffold(DeviceRole.MONITOR_UNIT, state, 0, true, navigate = {}) { MonitorDashboard(it.fillMaxSize(), state, {}, {}, { _, _ -> }, {}) }
    } }

    @Test fun darkShiftOrdersRoomsByUrgencyThenNaturalName() {
        dashboard(ThemeMode.DARK)
        val two = compose.onNodeWithText("Chambre 2").getUnclippedBoundsInRoot()
        val ten = compose.onNodeWithText("Chambre 10").getUnclippedBoundsInRoot()
        val twelve = compose.onNodeWithText("Chambre 12").getUnclippedBoundsInRoot()
        val sixteen = compose.onNodeWithText("Chambre 16").getUnclippedBoundsInRoot()
        assert(two.top < ten.top && ten.top < twelve.top && twelve.top < sixteen.top)
        compose.onNodeWithText("Batterie 12 %").assertIsDisplayed()
        screenshot("design-dashboard-dark")
    }
    @Test fun lightThemeKeepsTheSameStructure() {
        dashboard(ThemeMode.LIGHT)
        compose.onNodeWithTag("overview").assertIsDisplayed()
        screenshot("design-dashboard-light")
    }
    @Test fun eventListGroupsEpisodesAndOffersTheNextAction() {
        compose.setContent { CareLinkTheme {
            CareLinkScaffold(DeviceRole.MONITOR_UNIT, state, 1, true, navigate = {}) { EventList(it.fillMaxSize(), events, emptySet()) { _, _ -> } }
        } }
        compose.onNodeWithText("2 occurrences", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Prendre en charge").assertIsDisplayed()
        compose.onNodeWithText("Marquer comme résolu").assertIsDisplayed()
        screenshot("design-events")
    }
}
