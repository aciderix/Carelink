package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.example.model.*
import com.example.service.MonitorMonitoringService.MonitorServiceState
import com.example.ui.*
import com.example.ui.theme.CareLinkTheme
import java.io.File
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PauseInterfaceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val calls = mutableListOf<Boolean>()
    private val room = RoomUnitState("room", "Chambre 12", "192.168.1.12", status = RoomStatus.CALM, isMicOperational = true)
    private fun screen(paused: Boolean = false, back: Boolean = false, fontScale: Float = 1f) {
        val state = mutableStateOf(MonitorServiceState(isRunning = true, isPaused = paused, monitoredRooms = listOf(room)))
        compose.setContent { CareLinkTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                CareLinkScaffold(DeviceRole.MONITOR_UNIT, state.value, 0, true, back = if (back) ({}) else null,
                    pause = { calls.add(it); state.value = state.value.copy(isPaused = it) }, navigate = {}) {
                    if (back) RoomDetailPanel(it.fillMaxSize(), room, state.value, {}, {}, {}, { _, _ -> }, {}, {}, {}, {})
                    else MonitorDashboard(it.fillMaxSize(), state.value, {}, {}, { _, _ -> }, {})
                }
            }
        } }
    }
    private fun assertControls(label: String, dashboard: Boolean = true) {
        val pause = compose.onNodeWithContentDescription(label).assertIsDisplayed().getUnclippedBoundsInRoot()
        val title = compose.onNodeWithTag("header_title").getUnclippedBoundsInRoot()
        assertTrue(title.right <= pause.left)
        assertEquals((pause.top.value + pause.bottom.value) / 2, (title.top.value + title.bottom.value) / 2, 1f)
        assertTrue(title.right.value - title.left.value >= 110f)
        compose.onNodeWithText("CareLink").assertIsDisplayed()
        if (dashboard) {
            compose.onNodeWithContentDescription("Captures récentes").assertIsDisplayed()
            compose.onNodeWithContentDescription("À vérifier").assertIsDisplayed()
        }
    }
    private fun screenshot(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("build/screenshots/$name.png").apply { parentFile!!.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun pauseAndResumeStayInTheHeaderAndClearlyChangeTheVisibleMode() {
        screen()
        assertControls("Mettre la surveillance en pause")
        compose.onNodeWithContentDescription("Mettre la surveillance en pause").performClick()
        assertControls("Reprendre la surveillance")
        compose.onNodeWithText("Surveillance en pause · alertes suspendues").assertIsDisplayed()
        screenshot("monitor-paused-phone")
        compose.onNodeWithContentDescription("Reprendre la surveillance").performClick()
        compose.onNodeWithText("Surveillance en pause · alertes suspendues").assertDoesNotExist()
        assertEquals(listOf(true, false), calls)
    }

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun longPressExplainsPauseWithoutChangingItAndTabletGridRemainsVisible() {
        screen(paused = true)
        assertControls("Reprendre la surveillance")
        compose.onNodeWithTag("navigation_rail").assertIsDisplayed()
        compose.onNodeWithText("Chambre 12").assertIsDisplayed()
        screenshot("monitor-paused-tablet")
        compose.onNodeWithContentDescription("Reprendre la surveillance").performTouchInput { longClick() }
        compose.onNodeWithText("Suspend les alertes et rappels de ce veilleur. Le journal est conservé. Touchez à nouveau pour reprendre.").assertIsDisplayed()
        assertTrue(calls.isEmpty())
    }

    @Test fun narrowRoomHeaderKeepsBackAndPauseAccessible() {
        screen(paused = true, back = true)
        assertControls("Reprendre la surveillance", dashboard = false)
        compose.onNodeWithContentDescription("Retour").assertIsDisplayed()
        compose.onNodeWithText("Écouter").assertIsDisplayed()
        compose.onNodeWithText("Interphone").assertIsDisplayed()
        screenshot("room-paused-phone")
    }

    @Test fun largerTextKeepsAllHeaderControlsAccessibleOnSmallPhone() {
        screen(paused = true, fontScale = 1.3f)
        assertControls("Reprendre la surveillance")
        compose.onNodeWithText("Surveillance en pause · alertes suspendues").assertIsDisplayed()
        screenshot("monitor-paused-large-text")
    }
}
