package com.example

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
        compose.onNodeWithText("CareLink complète les rondes et les procédures du service. Un niveau sonore ne prouve ni un réveil, ni un besoin d’aide.").performScrollTo().assertIsDisplayed()
    }
    @Test fun monitorDoesNotInventRoomsAndProtectsEnrollment() {
        compose.onAllNodesWithText("Utiliser ce mode")[0].performClick()
        compose.onNodeWithText("Votre veille").assertIsDisplayed()
        compose.onNodeWithText("Aucune chambre appairée. Ajoutez les téléphones attendus avant votre prise de poste.").performScrollTo().assertIsDisplayed()
        screenshot("monitor-empty")
        compose.onNodeWithText("Ajouter une chambre").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ajouter une chambre").performClick()
        compose.onNodeWithText("Protéger les réglages").assertIsDisplayed()
        compose.onNodeWithText("Confirmer le code").assertIsDisplayed()

    }
}
