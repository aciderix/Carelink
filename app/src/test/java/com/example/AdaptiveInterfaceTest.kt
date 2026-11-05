package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.model.*
import com.example.service.MonitorMonitoringService.MonitorServiceState
import com.example.service.RoomMonitoringService.RoomServiceState
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdaptiveInterfaceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val room = RoomUnitState("room", "Chambre 12", "192.168.1.20", status = RoomStatus.CALM,
        isMicOperational = true, currentDecibels = 37, batteryPercent = 83, samplesCaptured = 48000,
        lastSeenTimestamp = 1000, diagnostic = "Capture récente")

    private fun dashboard() {
        val rooms = (1..12).map { room.copy(id = "room-$it", name = "Chambre %02d".format(it)) }
        val state = MonitorServiceState(isRunning = true, monitoredRooms = rooms)
        compose.setContent { MyApplicationTheme {
            CareLinkScaffold(DeviceRole.MONITOR_UNIT, state, 0, true, settings = {}, navigate = {}) {
                MonitorDashboard(it.fillMaxSize(), state, {}, {}, { _, _ -> }, {})
            }
        } }
        val recent = compose.onNodeWithContentDescription("Captures récentes").assertIsDisplayed().getUnclippedBoundsInRoot()
        val attention = compose.onNodeWithContentDescription("À vérifier").assertIsDisplayed().getUnclippedBoundsInRoot()
        val title = compose.onNodeWithTag("header_title").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(recent.right <= attention.left)
        assertTrue(attention.right <= title.left)
        compose.onNodeWithContentDescription("Réglages et diagnostic").assertIsDisplayed()
    }

    private fun detail(offline: Boolean = false) {
        val current = if (offline) room.copy(status = RoomStatus.DISCONNECTED, isMicOperational = false, diagnostic = "Connexion interrompue") else room
        val state = MonitorServiceState(isRunning = true, monitoredRooms = listOf(current))
        compose.setContent { MyApplicationTheme {
            CareLinkScaffold(DeviceRole.MONITOR_UNIT, state, 0, true, back = {}, settings = {}, navigate = {}) {
                RoomDetailPanel(it.fillMaxSize(), current, state, {}, {}, {}, { _, _ -> }, {}, {}, {}, {})
            }
        } }
    }

    private fun screenshot(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("build/screenshots/$name.png").apply { parentFile!!.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun phoneHeaderKeepsMetricsBeforeTitleAndListUsesOneColumn() {
        dashboard()
        compose.onNodeWithTag("navigation_bar").assertIsDisplayed()
        compose.onNodeWithTag("navigation_rail").assertDoesNotExist()
        val first = compose.onNodeWithText("Chambre 01").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithText("Chambre 02").getUnclippedBoundsInRoot()
        assertEquals(first.left.value, second.left.value, 1f)
        assertTrue(second.top > first.bottom)
        screenshot("monitor-phone")
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-port-mdpi")
    fun portraitTabletUsesMultipleRoomColumns() {
        dashboard()
        val first = compose.onNodeWithText("Chambre 01").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithText("Chambre 02").getUnclippedBoundsInRoot()
        assertEquals(first.top.value, second.top.value, 1f)
        assertTrue(second.left > first.right)
        screenshot("monitor-tablet-portrait")
    }

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun landscapeTabletUsesRailAndMultipleRoomColumns() {
        dashboard()
        compose.onNodeWithTag("navigation_rail").assertIsDisplayed()
        compose.onNodeWithTag("navigation_bar").assertDoesNotExist()
        val first = compose.onNodeWithText("Chambre 01").getUnclippedBoundsInRoot()
        val third = compose.onNodeWithText("Chambre 03").getUnclippedBoundsInRoot()
        assertEquals(first.top.value, third.top.value, 1f)
        assertTrue(third.left > first.right)
        screenshot("monitor-tablet-landscape")
    }

    @Test @Config(qualifiers = "w800dp-h360dp-land-mdpi")
    fun landscapePhonePreservesVerticalSpaceWithRail() {
        dashboard()
        compose.onNodeWithTag("navigation_rail").assertIsDisplayed()
        compose.onNodeWithText("Chambre 01").assertIsDisplayed()
        screenshot("monitor-phone-landscape")
    }

    @Test fun roomDetailsHideDiagnosticsAndKeepMainActionsAccessible() {
        detail()
        compose.onNodeWithTag("room_panels_single").assertExists()
        compose.onNodeWithText("Écouter").assertIsDisplayed()
        compose.onNodeWithText("Interphone").assertIsDisplayed()
        compose.onNodeWithText("Batterie : 83%").assertDoesNotExist()
        compose.onNodeWithText("Appliquer le réglage").assertDoesNotExist()
        screenshot("room-phone")
        compose.onNodeWithText("Sensibilité").performClick()
        compose.onNodeWithText("Appliquer le réglage").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Informations de l’appareil").performScrollTo().performClick()
        compose.onNodeWithText("Batterie : 83%").performScrollTo().assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletRoomDetailsPlaceSettingsBesideMainActions() {
        detail()
        compose.onNodeWithTag("room_panels_split").assertExists()
        val primary = compose.onNodeWithTag("room_primary").getUnclippedBoundsInRoot()
        val secondary = compose.onNodeWithTag("room_secondary").getUnclippedBoundsInRoot()
        assertTrue(secondary.left > primary.right)
        compose.onNodeWithText("Écouter").assertIsDisplayed()
        compose.onNodeWithText("Connexion").assertIsDisplayed()
        screenshot("room-tablet-landscape")
    }

    @Test fun connectionFailureExposesRecoveryActionsWithoutExpandingPanel() {
        detail(offline = true)
        compose.onNodeWithText("Réessayer").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Modifier l’IP").assertIsDisplayed()
        compose.onNodeWithText("Écouter").performScrollTo().assertIsNotEnabled()
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-port-mdpi")
    fun tabletBedroomHomeKeepsPairingAndSensitivityVisible() {
        compose.setContent { MyApplicationTheme {
            CareLinkScaffold(DeviceRole.ROOM_UNIT, MonitorServiceState(), 0, true, settings = {}, navigate = {}) {
                RoomHomePanel(it.fillMaxSize(), RoomServiceState(isRunning = true, roomName = "Chambre 12", status = RoomStatus.CALM,
                    isMicOperational = true, currentDecibels = 37, connectedMonitors = 2), "Chambre", {}, {}, {})
            }
        } }
        compose.onNodeWithTag("room_panels_split").assertExists()
        compose.onNodeWithText("Appairer un veilleur").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Régler la sensibilité").assertIsDisplayed()
        compose.onNodeWithText("Adresse Wi-Fi", substring = true).assertDoesNotExist()
        screenshot("bedroom-tablet-portrait")
    }
}
