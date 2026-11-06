package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.model.DeviceRole
import com.example.ui.CareLinkApp
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()
    private var microphoneAction: (() -> Unit)? = null
    private var cameraAction: (() -> Unit)? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) microphoneAction?.invoke() else model.message.value = "Microphone refusé : capture ou interphone indisponible"
        microphoneAction = null
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) model.message.value = "Notifications refusées : le poste ne peut pas être considéré prêt à alerter."
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraAction?.invoke() else model.message.value = "Caméra refusée : vous pouvez coller le code d’appairage."
        cameraAction = null
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            MyApplicationTheme {
                CareLinkApp(model,
                    startRole = { role ->
                        if (role == DeviceRole.ROOM_UNIT) withMicrophone { model.start(role); requestNotifications() }
                        else { model.start(role); requestNotifications() }
                    }, requestMicrophone = { action -> withMicrophone(action) }, requestCamera = { action ->
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) action()
                        else { cameraAction = action; cameraPermission.launch(Manifest.permission.CAMERA) }
                    }, openRoom = intent.getStringExtra("selected_room_id"))
            }
        }
    }
    override fun onStart() { super.onStart(); model.ready() }
    override fun onStop() { model.stopSpeaking(); model.cancelPairing(); super.onStop() }
    private fun withMicrophone(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) action()
        else { microphoneAction = action; micPermission.launch(Manifest.permission.RECORD_AUDIO) }
    }
    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); recreate() }
}
