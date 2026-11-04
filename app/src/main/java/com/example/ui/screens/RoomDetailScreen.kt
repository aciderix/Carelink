package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.RoomStatus
import com.example.model.RoomUnitState
import com.example.ui.components.DecibelMeter
import com.example.ui.theme.CryAlertRed
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailScreen(
    unit: RoomUnitState,
    isListening: Boolean,
    onBack: () -> Unit,
    onToggleListen: () -> Unit,
    onTalkBackClick: () -> Unit,
    onThresholdChanged: (Int) -> Unit,
    onAcknowledgeAlert: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val isDisconnected = unit.status == RoomStatus.DISCONNECTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = unit.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Surveillance détaillée • IP ${unit.ipAddress}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepNavy,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = DeepNavy
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Status Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (unit.status) {
                        RoomStatus.ALERT -> CryAlertRed.copy(alpha = 0.2f)
                        RoomStatus.DISCONNECTED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                        RoomStatus.MIC_ERROR -> WarningAmber.copy(alpha = 0.2f)
                        RoomStatus.ACTIVITY -> WarningAmber.copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                ),
                border = BorderStroke(
                    1.5.dp,
                    when (unit.status) {
                        RoomStatus.ALERT -> CryAlertRed
                        RoomStatus.DISCONNECTED -> MaterialTheme.colorScheme.error
                        RoomStatus.MIC_ERROR -> WarningAmber
                        RoomStatus.ACTIVITY -> WarningAmber
                        else -> MaterialTheme.colorScheme.outline
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (unit.status) {
                                            RoomStatus.ALERT -> CryAlertRed
                                            RoomStatus.DISCONNECTED -> Color.Gray
                                            RoomStatus.MIC_ERROR -> WarningAmber
                                            RoomStatus.ACTIVITY -> WarningAmber
                                            else -> SafeGreen
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (unit.status) {
                                    RoomStatus.ALERT -> "Alerte sonore / Dépassement !"
                                    RoomStatus.DISCONNECTED -> "Chambre déconnectée"
                                    RoomStatus.MIC_ERROR -> "Microphone défaillant"
                                    RoomStatus.ACTIVITY -> "Activité modérée"
                                    else -> "Chambre calme / silence"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = when (unit.status) {
                                    RoomStatus.ALERT -> CryAlertRed
                                    RoomStatus.DISCONNECTED -> MaterialTheme.colorScheme.error
                                    RoomStatus.MIC_ERROR -> WarningAmber
                                    RoomStatus.ACTIVITY -> WarningAmber
                                    else -> SafeGreen
                                }
                            )
                        }

                        // Telemetry chips
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (unit.wifiSignalDbm != 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Wifi,
                                            contentDescription = null,
                                            tint = SkyBluePrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "${unit.wifiSignalDbm} dBm",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }

                            if (unit.batteryPercent >= 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (unit.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                                            contentDescription = null,
                                            tint = if (unit.batteryPercent < 20 && !unit.isCharging) CryAlertRed else SafeGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "${unit.batteryPercent}%",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (!isDisconnected) {
                        DecibelMeter(
                            currentDb = unit.currentDecibels,
                            thresholdDb = unit.noiseThresholdDb,
                            isCompact = false
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Remote Threshold Adjustment Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Seuil de sensibilité acoustique :",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${unit.noiseThresholdDb} dB",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = SkyBluePrimary
                            )
                        }

                        Slider(
                            value = unit.noiseThresholdDb.toFloat(),
                            onValueChange = { onThresholdChanged(it.toInt()) },
                            valueRange = 35f..85f,
                            colors = SliderDefaults.colors(
                                thumbColor = SkyBluePrimary,
                                activeTrackColor = SkyBluePrimary
                            )
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "L'appareil ne répond plus au heartbeat. Vérifiez l'alimentation électrique ou la couverture Wi-Fi de la chambre.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Acknowledge alert button if in alert or requested
            if (onAcknowledgeAlert != null && (unit.status == RoomStatus.ALERT || unit.status == RoomStatus.DISCONNECTED)) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onAcknowledgeAlert,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = SkyBluePrimary
                    ),
                    border = BorderStroke(1.5.dp, SkyBluePrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Acquitter l'alerte pour cette chambre")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons: Écoute directe & Interphone
            if (!isDisconnected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onToggleListen,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isListening) SkyBluePrimary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isListening) Color(0xFF003549) else MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.VolumeUp else Icons.Default.Headphones,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isListening) "Couper écoute" else "Écouter en direct")
                    }

                    Button(
                        onClick = onTalkBackClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SkyBluePrimary,
                            contentColor = Color(0xFF003549)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Interphone")
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
