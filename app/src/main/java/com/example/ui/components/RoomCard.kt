package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.RoomStatus
import com.example.model.RoomUnitState
import com.example.ui.theme.CryAlertRed
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.WarningAmber

@Composable
fun RoomCard(
    unit: RoomUnitState,
    isListening: Boolean,
    onCardClick: () -> Unit,
    onToggleListen: () -> Unit,
    onTalkBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAlert = unit.status == RoomStatus.ALERT
    val isDisconnected = unit.status == RoomStatus.DISCONNECTED
    val isMicError = unit.status == RoomStatus.MIC_ERROR

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isAlert) 0.35f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alertPulse"
    )

    val cardBorderColor by animateColorAsState(
        targetValue = when (unit.status) {
            RoomStatus.ALERT -> CryAlertRed
            RoomStatus.DISCONNECTED -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            RoomStatus.MIC_ERROR -> WarningAmber
            RoomStatus.ACTIVITY -> WarningAmber
            else -> MaterialTheme.colorScheme.outline
        },
        label = "cardBorderColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
            .testTag("room_card_${unit.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isAlert -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                isDisconnected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(if (isAlert) 2.dp else 1.dp, cardBorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Room Name, Status Chip, Battery & Wi-Fi RSSI
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
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
                            .alpha(pulseAlpha)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = unit.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when (unit.status) {
                                RoomStatus.ALERT -> "Alerte sonore / Dépassement !"
                                RoomStatus.DISCONNECTED -> "Chambre déconnectée (hors ligne)"
                                RoomStatus.MIC_ERROR -> "Incident microphone"
                                RoomStatus.ACTIVITY -> "Activité sonore modérée"
                                else -> "Calme / Silence"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when (unit.status) {
                                RoomStatus.ALERT -> CryAlertRed
                                RoomStatus.DISCONNECTED -> Color.Gray
                                RoomStatus.MIC_ERROR -> WarningAmber
                                RoomStatus.ACTIVITY -> WarningAmber
                                else -> SafeGreen
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Battery & Wi-Fi Real Telemetry Badges
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isDisconnected) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WifiOff,
                                    contentDescription = "Déconnecté",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Injoignable",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else {
                        // Wi-Fi signal
                        if (unit.wifiSignalDbm != 0) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Wifi,
                                        contentDescription = "Signal Wi-Fi",
                                        tint = SkyBluePrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${unit.wifiSignalDbm} dBm",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        // Battery
                        if (unit.batteryPercent >= 0) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (unit.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                                        contentDescription = "Batterie",
                                        tint = if (unit.batteryPercent < 20 && !unit.isCharging) CryAlertRed else SafeGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${unit.batteryPercent}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!isDisconnected) {
                DecibelMeter(
                    currentDb = unit.currentDecibels,
                    thresholdDb = unit.noiseThresholdDb,
                    isCompact = false
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons Bar (Écouter + Interphone)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onToggleListen,
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isListening) SkyBluePrimary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isListening) Color(0xFF003549) else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("listen_button_${unit.id}")
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.VolumeUp else Icons.Default.Headphones,
                            contentDescription = "Écoute",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isListening) "Écoute active" else "Écouter",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    FilledTonalButton(
                        onClick = onTalkBackClick,
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = SkyBluePrimary.copy(alpha = 0.2f),
                            contentColor = SkyBluePrimary
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("talk_button_${unit.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Interphone",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Interphone",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Tentatives de reconnexion automatique en cours...",
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
