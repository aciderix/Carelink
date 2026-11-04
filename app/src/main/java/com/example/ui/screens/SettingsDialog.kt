package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.PreferencesManager
import com.example.model.DeviceRole
import com.example.service.AlertManager
import com.example.ui.theme.SkyBluePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    currentRole: DeviceRole,
    localIp: String,
    isWifi: Boolean,
    onSwitchRole: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val alertManager = remember { AlertManager(context) }

    var vibrateEnabled by remember { mutableStateOf(prefs.isVibrationAlertEnabled) }
    var soundEnabled by remember { mutableStateOf(prefs.isSoundAlertEnabled) }
    var repeatAlertEnabled by remember { mutableStateOf(prefs.isAlertRepeatEnabled) }
    var repeatIntervalSec by remember { mutableFloatStateOf(prefs.alertRepeatIntervalSeconds.toFloat()) }
    var roleLocked by remember { mutableStateOf(prefs.isRoleLocked) }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(16.dp)
        ) {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(scrollState)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Paramètres CareLink",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Surveillance acoustique MAS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Network Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isWifi) "Réseau local Wi-Fi actif" else "Wi-Fi non détecté",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Adresse IP : $localIp",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Alerts Section
                Text(
                    text = "Gestion des alertes soignants",
                    style = MaterialTheme.typography.labelLarge,
                    color = SkyBluePrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Vibration, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Vibration sur alerte", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = vibrateEnabled,
                        onCheckedChange = {
                            vibrateEnabled = it
                            prefs.isVibrationAlertEnabled = it
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = SkyBluePrimary)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Sonnerie d'alerte", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = {
                            soundEnabled = it
                            prefs.isSoundAlertEnabled = it
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = SkyBluePrimary)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Répéter tant que non acquitté", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = repeatAlertEnabled,
                        onCheckedChange = {
                            repeatAlertEnabled = it
                            prefs.isAlertRepeatEnabled = it
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = SkyBluePrimary)
                    )
                }

                if (repeatAlertEnabled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Intervalle de répétition : ${repeatIntervalSec.toInt()} secondes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = repeatIntervalSec,
                        onValueChange = {
                            repeatIntervalSec = it
                            prefs.alertRepeatIntervalSeconds = it.toInt()
                        },
                        valueRange = 10f..120f,
                        steps = 10,
                        colors = SliderDefaults.colors(
                            thumbColor = SkyBluePrimary,
                            activeTrackColor = SkyBluePrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Test Alert Feedback
                FilledTonalButton(
                    onClick = { alertManager.testAlertFeedback() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Notifications, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tester sonnerie et vibration")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Open Android Notification Settings
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Paramètres système de notification")
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Security & Role Section
                Text(
                    text = "Sécurité & Verrouillage",
                    style = MaterialTheme.typography.labelLarge,
                    color = SkyBluePrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Verrouiller le rôle", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Empêche les manipulations involontaires en chambre",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = roleLocked,
                        onCheckedChange = {
                            roleLocked = it
                            prefs.isRoleLocked = it
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = SkyBluePrimary)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Role Switch Button
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onSwitchRole()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Devices, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (currentRole == DeviceRole.ROOM_UNIT) "Passer en mode Poste Veilleur" else "Changer de rôle (Chambre / Veilleur)"
                    )
                }
            }
        }
    }
}
