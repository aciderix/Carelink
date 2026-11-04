package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.AlertEventType
import com.example.model.RoomAlertEvent
import com.example.model.RoomStatus
import com.example.model.RoomUnitState
import com.example.ui.components.RoomCard
import com.example.ui.theme.CryAlertRed
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorDashboardScreen(
    rooms: List<RoomUnitState>,
    activeAlerts: List<RoomAlertEvent>,
    activeListeningRoomId: String?,
    localIp: String,
    onRoomClick: (RoomUnitState) -> Unit,
    onToggleListen: (RoomUnitState) -> Unit,
    onTalkBackClick: (RoomUnitState) -> Unit,
    onAcknowledgeAlert: (String) -> Unit,
    onAcknowledgeAll: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchRole: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectedCount = rooms.count { it.status != RoomStatus.DISCONNECTED }
    val disconnectedCount = rooms.count { it.status == RoomStatus.DISCONNECTED }

    // Sort rooms priority: 1. Alert, 2. Disconnected, 3. Mic error, 4. Activity, 5. Calm
    val sortedRooms = rooms.sortedWith(
        compareBy(
            { room ->
                when (room.status) {
                    RoomStatus.ALERT -> 0
                    RoomStatus.DISCONNECTED -> 1
                    RoomStatus.MIC_ERROR -> 2
                    RoomStatus.ACTIVITY -> 3
                    RoomStatus.CALM -> 4
                }
            },
            { it.name }
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Tableau de Bord Veilleur",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Réseau local Wi-Fi • $localIp",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onSwitchRole,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("parent_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Changer de rôle"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("parent_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Paramètres"
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
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary Chips Row: Connectées, Déconnectées, Alertes
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Connected Card
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(SafeGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "$connectedCount",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Connectée(s)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Disconnected Card
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (disconnectedCount > 0) CryAlertRed else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (disconnectedCount > 0) CryAlertRed else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "$disconnectedCount",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (disconnectedCount > 0) CryAlertRed else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Déconnectée(s)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Active Alerts Card
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = if (activeAlerts.isNotEmpty()) CryAlertRed.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (activeAlerts.isNotEmpty()) CryAlertRed else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = if (activeAlerts.isNotEmpty()) CryAlertRed else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "${activeAlerts.size}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeAlerts.isNotEmpty()) CryAlertRed else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Alerte(s)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Active Multi-Room Alerts Banner List (Independent, uncollapsed alerts)
            if (activeAlerts.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Alertes en cours non acquittées (${activeAlerts.size}) :",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = CryAlertRed
                        )
                        if (activeAlerts.size > 1) {
                            TextButton(onClick = onAcknowledgeAll) {
                                Text(
                                    text = "Tout acquitter",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SkyBluePrimary
                                )
                            }
                        }
                    }
                }

                items(activeAlerts, key = { it.eventId }) { alert ->
                    val alertRoom = rooms.find { it.id == alert.roomId }
                    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    val timeStr = timeFormat.format(Date(alert.timestamp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("alert_banner_${alert.roomId}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (alert.eventType) {
                                AlertEventType.SOUND_ALERT -> CryAlertRed
                                AlertEventType.DISCONNECTION -> Color(0xFF8B2B2B)
                                else -> WarningAmber
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when (alert.eventType) {
                                            AlertEventType.SOUND_ALERT -> Icons.Default.Warning
                                            AlertEventType.DISCONNECTION -> Icons.Default.WifiOff
                                            else -> Icons.Default.Warning
                                        },
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = alert.roomName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = when (alert.eventType) {
                                                AlertEventType.SOUND_ALERT -> "Niveau sonore : ${alert.levelReachedDb} dB (seuil ${alert.thresholdDb} dB)"
                                                AlertEventType.DISCONNECTION -> "Perte de connexion Wi-Fi"
                                                AlertEventType.MIC_ERROR -> "Incident microphone"
                                                AlertEventType.LOW_BATTERY -> "Batterie faible"
                                                AlertEventType.SERVICE_INTERRUPTED -> "Service interrompu"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.9f)
                                        )
                                    }
                                }

                                Text(
                                    text = timeStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Action buttons: Listen, Intercom, and Acknowledge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (alertRoom != null && alert.eventType != AlertEventType.DISCONNECTION) {
                                    IconButton(
                                        onClick = { onToggleListen(alertRoom) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Headphones,
                                            contentDescription = "Écouter",
                                            tint = Color.White
                                        )
                                    }
                                    IconButton(
                                        onClick = { onTalkBackClick(alertRoom) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "Interphone",
                                            tint = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                OutlinedButton(
                                    onClick = { onAcknowledgeAlert(alert.roomId) },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.White
                                    ),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Acquitter")
                                }
                            }
                        }
                    }
                }
            }

            // List of Monitored Rooms
            if (sortedRooms.isNotEmpty()) {
                item {
                    Text(
                        text = "Chambres sous surveillance (${sortedRooms.size}) :",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(sortedRooms, key = { it.id }) { room ->
                    RoomCard(
                        unit = room,
                        isListening = activeListeningRoomId == room.id,
                        onCardClick = { onRoomClick(room) },
                        onToggleListen = { onToggleListen(room) },
                        onTalkBackClick = { onTalkBackClick(room) }
                    )
                }
            } else {
                // Empty state
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(SkyBluePrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = SkyBluePrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "En attente de connexion des chambres...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Lancez CareLink en mode « Appareil Chambre » sur les téléphones installés dans les chambres des résidents.\n\nConnectés au même réseau Wi-Fi local, ils apparaîtront ici automatiquement avec leur niveau sonore en temps réel.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
