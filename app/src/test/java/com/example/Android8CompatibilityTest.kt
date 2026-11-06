package com.example

import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.network.NetworkUtils
import com.example.ui.MainViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowWifiInfo
import java.net.InetAddress

/** Android 8 (API 26), as on the Galaxy Tab A 2016: the interface and the local address must work there too. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w800dp-h1280dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Android8CompatibilityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun reset() {
        compose.activityRule.scenario.onActivity { ViewModelProvider(it)[MainViewModel::class.java].apply { stop(); setCodeProtection(false) } }
    }
    @Test fun localAddressIsFoundEvenWhenConnectivityManagerReportsNoWifiNetwork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wifi = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        shadowOf(wifi).setConnectionInfo(ShadowWifiInfo.newInstance().also { shadowOf(it).setInetAddress(InetAddress.getByName("192.168.1.42")) })
        val link = NetworkUtils.localLink(context)
        assertNotNull(link)
        assertTrue(NetworkUtils.getLocalIpAddress(context).isNotBlank())
    }
    @Test fun welcomeMonitorDashboardAndAddRoomRenderOnAndroid8() {
        compose.onNodeWithText("Utiliser comme poste veilleur").performClick()
        compose.onNodeWithContentDescription("Captures récentes").assertIsDisplayed()
        compose.onNodeWithText("Ajouter une chambre").performClick()
        compose.onNodeWithText("Scanner le QR").assertIsDisplayed()
        compose.onNodeWithText("Annuler").performScrollTo().performClick()
        compose.onNodeWithText("Réglages").performClick()
        compose.onNodeWithText("Réglages du poste").assertIsDisplayed()
    }
}
