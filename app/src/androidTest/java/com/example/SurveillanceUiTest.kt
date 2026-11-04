package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import android.view.WindowManager
import java.io.File
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class SurveillanceUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun welcomeAndEnrollmentRequireProfessionalProtection() {
        compose.activityRule.scenario.onActivity {
            it.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            androidx.lifecycle.ViewModelProvider(it)[com.example.ui.MainViewModel::class.java].stop()
        }
        compose.onNodeWithText("Poste veilleur").assertIsDisplayed()
        compose.onNodeWithText("Téléphone chambre").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("CareLink complète les rondes et les procédures du service. Un niveau sonore ne prouve ni un réveil, ni un besoin d’aide.").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Utiliser ce mode")[0].performScrollTo().performClick()
        compose.onNodeWithText("Votre veille").assertIsDisplayed()
        compose.onNodeWithText("Ajouter une chambre").performScrollTo()
        compose.onNodeWithText("Ajouter une chambre").performClick()
        compose.onNodeWithText("Protéger les réglages").assertIsDisplayed()
        compose.onNodeWithText("Choisissez un code d’au moins 6 chiffres. Conservez-le dans la procédure du service.").assertIsDisplayed()

    }
}
