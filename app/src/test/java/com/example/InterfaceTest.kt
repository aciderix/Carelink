package com.example

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.lifecycle.ViewModelProvider
import com.example.security.AdminPin
import com.example.ui.MainViewModel
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InterfaceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun resetPost() {
        compose.activityRule.scenario.onActivity {
            ViewModelProvider(it)[MainViewModel::class.java].apply {
                stop(); prefs.pinHash = ""; setCodeProtection(false)
            }
        }
    }
    private fun screenshot(name: String) {
        compose.activityRule.scenario.onActivity { it.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file = File("build/screenshots/$name.png").apply { parentFile!!.mkdirs() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun welcomeIsReadableAndExplainsBothRoles() {
        compose.onNodeWithText("Poste veilleur").assertIsDisplayed()
        screenshot("welcome")
        compose.onNodeWithText("Téléphone chambre").performScrollTo().assertIsDisplayed()
    }
    @Test fun monitorDoesNotInventRoomsAndHonorsDisabledCodeEvenWhenAPinIsStored() {
        val hash = AdminPin.hash("123456")
        compose.runOnIdle {
            ViewModelProvider(compose.activity)[MainViewModel::class.java].apply {
                prefs.pinHash = hash; setCodeProtection(false)
            }
        }
        compose.onAllNodesWithText("Utiliser ce mode")[0].performClick()
        compose.onNodeWithContentDescription("Captures récentes").assertIsDisplayed()
        compose.onNodeWithText("Aucune chambre appairée. Ajoutez les téléphones attendus avant votre prise de poste.").performScrollTo().assertIsDisplayed()
        screenshot("monitor-empty")
        compose.onNodeWithText("Ajouter une chambre").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ajouter une chambre").performClick()
        compose.onNodeWithText("Code professionnel").assertDoesNotExist()
        compose.onNodeWithText("Confirmer").performClick()
        compose.onNodeWithText("Scanner le QR").assertIsDisplayed()
    }
    @Test fun disablingCodeInSettingsUpdatesTheButtonAndRemovesEnrollmentPinRequests() {
        val hash = AdminPin.hash("123456")
        lateinit var model: MainViewModel
        compose.runOnIdle {
            model = ViewModelProvider(compose.activity)[MainViewModel::class.java]
            model.prefs.pinHash = hash; model.setCodeProtection(true)
        }
        compose.onAllNodesWithText("Utiliser ce mode")[0].performClick()
        compose.onNodeWithText("Réglages").performClick()
        compose.onNodeWithText("Accès professionnel").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("123456")
        compose.onNodeWithText("Valider").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Réglages du poste").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Désactiver la protection par code"))
        compose.onNodeWithText("Désactiver la protection par code").performClick()
        compose.onNodeWithText("Accès professionnel").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("123456")
        compose.onNodeWithText("Valider").performClick()
        compose.waitUntil(10000) { !model.prefs.isRoleLocked }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Protéger les actions par un code"))
        compose.onNodeWithText("Protéger les actions par un code").assertIsDisplayed()
        compose.onNodeWithText("Surveillance").performClick()
        compose.onNodeWithText("Ajouter une chambre").performScrollTo().performClick()
        compose.onNodeWithText("Code professionnel").assertDoesNotExist()
        compose.onNodeWithText("Confirmer").performClick()
        compose.onNodeWithText("Scanner le QR").assertIsDisplayed()
    }
}
