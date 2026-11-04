package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.DeviceRole
import com.example.ui.MainViewModel
import com.example.ui.components.TalkBackDialog
import com.example.ui.screens.MonitorDashboardScreen
import com.example.ui.screens.RoleSelectionScreen
import com.example.ui.screens.RoomDetailScreen
import com.example.ui.screens.RoomUnitScreen
import com.example.ui.screens.SettingsDialog
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                val micPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        viewModel.updateNetworkInfo()
                    }
                }

                val notifPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { /* optional */ }

                LaunchedEffect(Unit) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.RECORD_AUDIO
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                DisposableEffect(uiState.currentRole) {
                    if (uiState.currentRole == DeviceRole.ROOM_UNIT) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DeepNavy
                ) {
                    when (uiState.currentRole) {
                        DeviceRole.NONE -> {
                            RoleSelectionScreen(
                                localIp = uiState.localIpAddress,
                                isWifi = uiState.isWifiConnected,
                                onSelectRole = { role -> viewModel.selectRole(role) }
                            )
                        }

                        DeviceRole.ROOM_UNIT -> {
                            BackHandler {
                                viewModel.selectRole(DeviceRole.NONE)
                            }

                            RoomUnitScreen(
                                roomName = uiState.roomUnitName,
                                localIp = uiState.localIpAddress,
                                currentDb = uiState.roomCurrentDb,
                                thresholdDb = uiState.roomThresholdDb,
                                status = uiState.roomStatus,
                                isCaregiverTalking = uiState.isCaregiverTalkingToRoom,
                                onChangeRoomName = { viewModel.setRoomUnitName(it) },
                                onThresholdChanged = { viewModel.setRoomThreshold(it) },
                                onSimulateNoise = { viewModel.triggerRoomManualNoiseSpike() },
                                onSwitchRole = { viewModel.selectRole(DeviceRole.NONE) }
                            )
                        }

                        DeviceRole.MONITOR_UNIT -> {
                            if (uiState.selectedDetailRoom != null) {
                                BackHandler {
                                    viewModel.selectDetailRoom(null)
                                }

                                RoomDetailScreen(
                                    unit = uiState.selectedDetailRoom!!,
                                    isListening = uiState.activeListeningRoomId == uiState.selectedDetailRoom!!.id,
                                    onBack = { viewModel.selectDetailRoom(null) },
                                    onToggleListen = { viewModel.toggleAudioListening(uiState.selectedDetailRoom!!) },
                                    onTalkBackClick = { viewModel.openTalkBack(uiState.selectedDetailRoom!!) },
                                    onThresholdChanged = { viewModel.remoteUpdateThreshold(uiState.selectedDetailRoom!!, it) }
                                )
                            } else {
                                BackHandler {
                                    viewModel.selectRole(DeviceRole.NONE)
                                }

                                MonitorDashboardScreen(
                                    rooms = uiState.monitoredRooms,
                                    activeListeningRoomId = uiState.activeListeningRoomId,
                                    activeNoiseAlert = uiState.activeNoiseAlert,
                                    noiseHistory = uiState.noiseHistory,
                                    localIp = uiState.localIpAddress,
                                    onRoomClick = { viewModel.selectDetailRoom(it) },
                                    onToggleListen = { viewModel.toggleAudioListening(it) },
                                    onTalkBackClick = { viewModel.openTalkBack(it) },
                                    onDismissAlert = { viewModel.dismissNoiseAlert() },
                                    onOpenSettings = { viewModel.openSettings() },
                                    onSwitchRole = { viewModel.selectRole(DeviceRole.NONE) }
                                )
                            }
                        }
                    }

                    if (uiState.talkBackRoom != null) {
                        TalkBackDialog(
                            roomName = uiState.talkBackRoom!!.name,
                            onStartSpeaking = { viewModel.startSpeakingToRoom(uiState.talkBackRoom!!) },
                            onStopSpeaking = { viewModel.stopSpeakingToRoom(uiState.talkBackRoom!!) },
                            onDismiss = { viewModel.closeTalkBack() }
                        )
                    }

                    if (uiState.isSettingsOpen) {
                        SettingsDialog(
                            currentRole = uiState.currentRole,
                            localIp = uiState.localIpAddress,
                            isWifi = uiState.isWifiConnected,
                            onSwitchRole = {
                                viewModel.selectRole(DeviceRole.NONE)
                                viewModel.closeSettings()
                            },
                            onDismiss = { viewModel.closeSettings() }
                        )
                    }
                }
            }
        }
    }
}
