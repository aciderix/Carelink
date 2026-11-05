package com.example

import android.graphics.Bitmap
import java.io.File
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.model.*
import com.example.service.MonitorMonitoringService.MonitorServiceState
import com.example.ui.MonitorDashboard
import com.example.ui.CareLinkScaffold
import com.example.ui.EventNavigationIcon
import com.example.ui.theme.MyApplicationTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompactDashboardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val room = RoomUnitState("room", "Chambre 12", "192.168.1.20", status = RoomStatus.CALM,
        batteryPercent = 83, isMicOperational = true, lastSeenTimestamp = 1000, diagnostic = "Capture récente")
    private fun event(id: String, ack: Boolean = false) = RoomAlertEvent(eventId = id, roomId = room.id,
        roomName = room.name, timestamp = 1000, eventType = AlertEventType.SOUND_ALERT, isAcknowledged = ack)
    @Composable private fun Dashboard(modifier: Modifier, state: MonitorServiceState, add: () -> Unit, select: (String) -> Unit,
        eventAction: (List<RoomAlertEvent>, Boolean) -> Unit, resume: () -> Unit) {
        CareLinkScaffold(DeviceRole.MONITOR_UNIT, state, 0, true, settings = {}, navigate = {}) {
            MonitorDashboard(modifier, state, add, select, eventAction, resume)
        }
    }
    @Test fun roomShortcutAcknowledgesEveryDisplayedEpisodeThenResolvesWithoutOpeningTheRoom() {
        val active = mutableStateOf(listOf(event("one"), event("two")))
        val calls = mutableListOf<Pair<List<String>, Boolean>>()
        var opened = false
        compose.setContent {
            MyApplicationTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Dashboard(Modifier.fillMaxSize(), MonitorServiceState(isRunning = true, monitoredRooms = listOf(room,
                        room.copy(id = "room-14", name = "Chambre 14"), room.copy(id = "room-16", name = "Chambre 16", status = RoomStatus.DISCONNECTED, isMicOperational = false)), activeAlerts = active.value),
                        add = {}, select = { opened = true }, eventAction = { events, resolve -> calls.add(events.map { it.eventId } to resolve) }, resume = {})
                }
            }
        }
        compose.onNodeWithText("Chambre 12").assertIsDisplayed()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File("build/screenshots/monitor-alerts.png").apply { parentFile!!.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("Batterie", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Prendre en charge Chambre 12").performClick()
        assertEquals(listOf("one", "two"), calls.single().first)
        assertFalse(calls.single().second)
        assertFalse(opened)
        compose.runOnIdle { active.value = listOf(event("one", true), event("two", true)) }
        compose.onNodeWithText("Pris en charge").assertIsDisplayed()
        compose.onNodeWithContentDescription("Résoudre les alertes de Chambre 12").performClick()
        assertTrue(calls.last().second)
        assertFalse(opened)
    }
    @Test fun compactMetricsExplainTheirMeaningOnTap() {
        compose.setContent { MyApplicationTheme {
            Dashboard(Modifier.fillMaxSize(), MonitorServiceState(isRunning = true, monitoredRooms = listOf(room)), {}, {}, { _, _ -> }, {})
        } }
        compose.onNodeWithContentDescription("Captures récentes").performClick()
        compose.onNodeWithText("Captures récentes : 1 sur 1 chambres. Le microphone transmet des échantillons récents.").assertIsDisplayed()
    }
    @Test fun roomDetailsAppearOnLongPress() {
        compose.setContent { MyApplicationTheme {
            Dashboard(Modifier.fillMaxSize(), MonitorServiceState(isRunning = true, monitoredRooms = listOf(room)), {}, {}, { _, _ -> }, {})
        } }
        compose.onNodeWithText("Batterie : 83%", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Chambre 12").performTouchInput { longClick() }
        compose.onNodeWithText("Batterie : 83%", substring = true).assertIsDisplayed()
    }
    @Test fun eventBadgeCountsActiveGroupsIncludingAcknowledgedGroups() {
        val grouped = AlertGroup.active(listOf(event("one", true), event("two", true)))
        compose.setContent { MyApplicationTheme { EventNavigationIcon(grouped.size) } }
        compose.onNodeWithContentDescription("Événements en cours : 1").assertIsDisplayed()
    }
}
