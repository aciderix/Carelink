@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.example.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.model.*
import com.example.security.AdminPin
import com.example.security.PinThrottle
import com.example.service.*
import com.example.ui.theme.Care
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------------------------
// Labels and tones
// ---------------------------------------------------------------------------------------------

private fun RoomStatus.tone() = when (this) {
    RoomStatus.CALM -> Tone.OK
    RoomStatus.ACTIVITY, RoomStatus.DEGRADED, RoomStatus.VERIFYING -> Tone.WARNING
    else -> Tone.CRITICAL
}
private fun RoomStatus.label() = when (this) {
    RoomStatus.VERIFYING -> "À vérifier"
    RoomStatus.CALM -> "Capture récente · niveau bas"
    RoomStatus.ACTIVITY -> "Activité sonore"
    RoomStatus.ALERT -> "Seuil sonore dépassé"
    RoomStatus.DISCONNECTED -> "Surveillance interrompue"
    RoomStatus.MIC_ERROR -> "Capture non vérifiée"
    RoomStatus.DEGRADED -> "Surveillance dégradée"
}
private fun RoomStatus.shortLabel() = when (this) {
    RoomStatus.CALM -> "Calme"
    RoomStatus.ACTIVITY -> "Activité sonore"
    RoomStatus.ALERT -> "Seuil dépassé"
    RoomStatus.DISCONNECTED -> "Hors ligne"
    RoomStatus.MIC_ERROR -> "Microphone indisponible"
    RoomStatus.DEGRADED -> "Mode dégradé"
    RoomStatus.VERIFYING -> "Vérification en cours"
}
private fun AlertEventType.label() = when (this) {
    AlertEventType.SOUND_ALERT -> "Événement sonore"
    AlertEventType.DISCONNECTION -> "Connexion interrompue"
    AlertEventType.MIC_ERROR -> "Incident microphone"
    AlertEventType.LOW_BATTERY -> "Batterie faible"
    AlertEventType.SERVICE_INTERRUPTED -> "Incident technique"
    AlertEventType.TEST -> "Test de transmission"
}
private fun AlertEventType.icon(): ImageVector = when (this) {
    AlertEventType.SOUND_ALERT -> Icons.Outlined.GraphicEq
    AlertEventType.DISCONNECTION -> Icons.Outlined.WifiOff
    AlertEventType.MIC_ERROR -> Icons.Outlined.MicOff
    AlertEventType.LOW_BATTERY -> Icons.Outlined.BatteryAlert
    AlertEventType.SERVICE_INTERRUPTED -> Icons.Outlined.ReportProblem
    AlertEventType.TEST -> Icons.Outlined.Science
}
private fun clock(time: Long) = SimpleDateFormat("dd MMM · HH:mm:ss", Locale.FRANCE).format(Date(time))
private fun battery(room: RoomUnitState) =
    "${if (room.batteryPercent >= 0) "${room.batteryPercent} %" else "inconnue"}${if (room.isCharging) " · en charge" else ""}"
private val naturalOrder = Comparator<String> { a, b ->
    val regex = Regex("\\d+|\\D+")
    val left = regex.findAll(a.lowercase(Locale.FRANCE)).map { it.value }.toList()
    val right = regex.findAll(b.lowercase(Locale.FRANCE)).map { it.value }.toList()
    for (i in 0 until minOf(left.size, right.size)) {
        val x = left[i]; val y = right[i]
        val result = if (x[0].isDigit() && y[0].isDigit()) (x.toBigIntegerOrNull() ?: 0.toBigInteger()).compareTo(y.toBigIntegerOrNull() ?: 0.toBigInteger()) else x.compareTo(y)
        if (result != 0) return@Comparator result
    }
    left.size - right.size
}

// ---------------------------------------------------------------------------------------------
// Application root
// ---------------------------------------------------------------------------------------------

@Composable
fun CareLinkApp(vm: MainViewModel, startRole: (DeviceRole) -> Unit,
    requestMicrophone: (() -> Unit) -> Unit, requestCamera: (() -> Unit) -> Unit) {
    val role by vm.role.collectAsStateWithLifecycle()
    val codeProtectionEnabled by vm.codeProtectionEnabled.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val room by vm.room.collectAsStateWithLifecycle()
    val monitor by vm.monitor.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val code by vm.pairingCode.collectAsStateWithLifecycle()
    val seconds by vm.pairingSeconds.collectAsStateWithLifecycle()
    val peers by vm.peers.collectAsStateWithLifecycle()
    val openRequest by vm.openRoomRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message, monitor.diagnostic) {
        (message ?: monitor.diagnostic)?.let { snackbar.showSnackbar(it, withDismissAction = true); vm.message.value = null }
    }
    var tab by remember { mutableIntStateOf(0) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var addRoom by remember { mutableStateOf(false) }
    var scan by remember { mutableStateOf(false) }
    var talkId by remember { mutableStateOf<String?>(null) }
    var confirmLabel by remember { mutableStateOf<String?>(null) }
    var protectedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pinDialog by remember { mutableStateOf(false) }
    var setupPin by remember { mutableStateOf(false) }
    LaunchedEffect(openRequest) {
        openRequest?.let { id -> detailId = id; tab = 0; addRoom = false; scan = false; vm.openRoomRequest.value = null }
    }
    /**
     * Sensitive actions require the professional code when protection is on. Without protection, real actions are
     * confirmed once while navigation ([confirm] = false) happens immediately.
     */
    fun protect(label: String, confirm: Boolean = true, action: () -> Unit) {
        if (!vm.prefs.isRoleLocked && !confirm) { action(); return }
        protectedAction = action; confirmLabel = label
        if (vm.prefs.isRoleLocked) {
            setupPin = vm.prefs.pinHash.isBlank()
            pinDialog = true
        }
    }
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { tab = 0; pinDialog = false; setupPin = false; confirmLabel = null; protectedAction = null }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (pinDialog) {
        val setupMode = setupPin
        val cancel = { pinDialog = false; setupPin = false; confirmLabel = null; protectedAction = null }
        BackHandler(onBack = cancel)
        ProfessionalCodeScreen(setupMode, confirmLabel, cancel, lockedUntil = { if (setupMode) 0L else vm.prefs.pinLockedUntil }) { pin ->
            if (!setupMode && System.currentTimeMillis() < vm.prefs.pinLockedUntil) return@ProfessionalCodeScreen false
            val accepted = withContext(Dispatchers.Default) { setupMode || AdminPin.verify(pin, vm.prefs.pinHash) }
            if (!setupMode) {
                if (accepted) { vm.prefs.pinFailures = 0; vm.prefs.pinLockedUntil = 0L }
                else {
                    val failures = vm.prefs.pinFailures + 1
                    vm.prefs.pinFailures = failures
                    vm.prefs.pinLockedUntil = System.currentTimeMillis() + PinThrottle.lockoutMs(failures)
                }
            }
            if (accepted) {
                if (setupMode) {
                    vm.prefs.pinHash = withContext(Dispatchers.Default) { AdminPin.hash(pin) }
                    vm.setCodeProtection(true)
                    setupPin = false
                    vm.message.value = "Réglages protégés"
                }
                pinDialog = false
                val action = protectedAction; protectedAction = null; confirmLabel = null; action?.invoke()
            }
            accepted
        }
        return
    }
    val selected = monitor.monitoredRooms.find { it.id == detailId }
    BackHandler(detailId != null) { detailId = null }
    BackHandler(addRoom) { addRoom = false; scan = false }
    BackHandler(tab != 0 && detailId == null && !addRoom) { tab = 0 }
    LaunchedEffect(tab) { if (tab == 2) vm.reloadPeers() }
    val openSettings = { protect("Ouvrir les réglages professionnels ?", confirm = false) { tab = 2; detailId = null } }
    CareLinkScaffold(role, monitor, tab, role != DeviceRole.NONE && !addRoom,
        back = if (addRoom || detailId != null) ({ if (addRoom) { addRoom = false; scan = false } else detailId = null }) else null,
        pause = { paused ->
            if (paused && vm.prefs.isRoleLocked) protect("Mettre les alertes et rappels de ce veilleur en pause ?") { vm.setMonitoringPaused(true) }
            else vm.setMonitoringPaused(paused)
        },
        navigate = { next -> if (next == 2) openSettings() else { tab = next; detailId = null } },
        snackbar = snackbar) { body ->
            val wide = body.widthIn(max = 1280.dp).fillMaxSize()
            val form = body.widthIn(max = 720.dp).fillMaxSize()
            when {
                role == DeviceRole.NONE -> Welcome(body.widthIn(max = 960.dp).fillMaxSize(), startRole)
                addRoom -> AddRoomScreen(form, monitor, scan,
                    startScan = { requestCamera { if (addRoom) scan = true } },
                    submit = { vm.importPairing(it); scan = false; addRoom = false },
                    error = { vm.message.value = it; scan = false },
                    cancel = { addRoom = false; scan = false })
                tab == 2 -> SettingsPanel(form, vm, role, codeProtectionEnabled, themeMode, monitor.notificationWarnings, peers,
                    stop = { protect("Arrêter la surveillance sur ce téléphone ?") { vm.stop(); tab = 0 } },
                    enableLock = {
                        protectedAction = { vm.setCodeProtection(true); vm.message.value = "Protection par code activée" }
                        confirmLabel = "Activer la protection des réglages ?"
                        setupPin = vm.prefs.pinHash.isBlank(); pinDialog = true
                    },
                    disableLock = { protect("Désactiver la protection des réglages ?") { vm.setCodeProtection(false); vm.message.value = "Protection désactivée" } },
                    test = { protect("Envoyer un événement de test ?") { vm.test() } },
                    pair = { protect("Ouvrir un appairage à usage unique pendant deux minutes ?", confirm = false) { vm.newPairing() } },
                    revoke = { id -> protect("Révoquer l’accès de ce veilleur ?") { vm.revoke(id) } })
                role == DeviceRole.ROOM_UNIT -> RoomHomePanel(wide, room, vm.prefs.roomName,
                    resume = { startRole(DeviceRole.ROOM_UNIT) },
                    settings = openSettings,
                    pair = { protect("Appairer un poste veilleur ?", confirm = false) { vm.newPairing() } })
                tab == 1 -> EventList(wide, monitor.history, monitor.pendingActions, vm::eventActions)
                selected != null -> RoomDetailPanel(wide, selected, monitor,
                    listen = { vm.listen(selected.id) }, talk = { talkId = selected.id },
                    reconnect = { vm.reconnect(selected.id) }, eventAction = vm::eventActions,
                    threshold = { value -> protect("Appliquer un seuil relatif de $value ?") { vm.remoteThreshold(selected.id, value) } },
                    updateAddress = { address -> protect("Utiliser cette adresse réseau pour la chambre ?") { vm.updateAddress(selected.id, address) } },
                    pair = { protect("Reprendre l’appairage avec un nouveau QR ?", confirm = false) { addRoom = true } },
                    forget = { protect("Retirer cette chambre des appareils attendus ? Ses événements seront conservés.") { vm.forget(selected.id); detailId = null } })
                else -> MonitorDashboard(wide, monitor, add = { protect("Appairer une nouvelle chambre ?", confirm = false) { addRoom = true } },
                    select = { detailId = it }, eventAction = vm::eventActions, resume = { startRole(DeviceRole.MONITOR_UNIT) })
            }
    }
    if (confirmLabel != null && !pinDialog) AlertDialog(onDismissRequest = { confirmLabel = null; protectedAction = null },
        icon = { Icon(Icons.Outlined.Shield, null) }, title = { Text("Confirmer l’action") },
        text = { Text(confirmLabel!!) }, confirmButton = { TextButton(onClick = {
            val action = protectedAction; confirmLabel = null; protectedAction = null; action?.invoke()
        }) { Text("Confirmer") } }, dismissButton = { TextButton(onClick = { confirmLabel = null; protectedAction = null }) { Text("Annuler") } })
    if (code != null) PairingDialog(code!!, seconds, cancel = vm::cancelPairing, copy = {
        val clip = ClipData.newPlainText("Appairage CareLink", code)
        if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    })
    if (talkId != null) {
        val id = talkId!!
        TalkDialog(monitor.monitoredRooms.find { it.id == id }?.name ?: "Chambre", monitor.isTransmittingVoice,
            press = { holding -> requestMicrophone { if (holding()) vm.speak(id) } }, release = vm::stopSpeaking,
            close = { vm.stopSpeaking(); talkId = null })
    }
}

// ---------------------------------------------------------------------------------------------
// Frame: top bar, navigation, paused mode
// ---------------------------------------------------------------------------------------------

private val destinationLabels = listOf("Surveillance", "Événements", "Réglages")
private val destinationIcons = listOf(Icons.Outlined.GridView to Icons.Filled.GridView,
    Icons.Outlined.Notifications to Icons.Filled.Notifications, Icons.Outlined.Settings to Icons.Filled.Settings)

@Composable internal fun CareLinkScaffold(role: DeviceRole, monitor: MonitorMonitoringService.MonitorServiceState,
    tab: Int, showNavigation: Boolean, back: (() -> Unit)? = null,
    pause: (Boolean) -> Unit = {},
    navigate: (Int) -> Unit, snackbar: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (Modifier) -> Unit) {
    val paused = role == DeviceRole.MONITOR_UNIT && monitor.isPaused
    val current = MaterialTheme.colorScheme
    val care = Care.colors
    val dark = Care.isDark
    val colors = if (paused) current.copy(primary = care.paused, onPrimary = care.onPaused, primaryContainer = care.pausedContainer,
        onPrimaryContainer = care.paused, secondaryContainer = care.pausedContainer, onSecondaryContainer = care.paused,
        background = if (dark) Color(0xFF13100C) else Color(0xFFFBF7F2), surface = if (dark) Color(0xFF13100C) else Color(0xFFFBF7F2),
        surfaceContainerLow = if (dark) Color(0xFF19140F) else Color(0xFFFFFBF7)) else current
    MaterialTheme(colorScheme = colors) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val rail = maxWidth >= 840.dp || (maxWidth >= 600.dp && maxHeight < 480.dp)
            val compactHeader = maxWidth < 330.dp || (back != null && maxWidth < 350.dp) || (LocalDensity.current.fontScale > 1.15f && maxWidth < 420.dp)
            val destinations = if (role == DeviceRole.MONITOR_UNIT) listOf(0, 1, 2) else listOf(0, 2)
            val count = AlertGroup.active(monitor.activeAlerts).size
            val navColors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.onSurface,
                unselectedIconColor = care.textSecondary, unselectedTextColor = care.textSecondary)
            val railColors = NavigationRailItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.onSurface,
                unselectedIconColor = care.textSecondary, unselectedTextColor = care.textSecondary)
            Scaffold(containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbar) }, topBar = {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.statusBarsPadding()) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = if (back != null) 4.dp else 16.dp, end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (back != null) IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                                Row(Modifier.weight(1f).testTag("header_title"), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    BrandMark(28.dp)
                                    Column {
                                        Text("CareLink", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(when (role) { DeviceRole.MONITOR_UNIT -> "Veilleur"; DeviceRole.ROOM_UNIT -> "Chambre"; DeviceRole.NONE -> "Configuration" },
                                            style = MaterialTheme.typography.labelMedium, color = care.textSecondary, maxLines = 1)
                                    }
                                }
                                if (role == DeviceRole.MONITOR_UNIT && showNavigation) MonitorPauseButton(paused, monitor.isRunning, compactHeader, pause)
                            }
                            if (paused) Row(Modifier.fillMaxWidth().background(care.pausedContainer).padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Pause, null, Modifier.size(16.dp), tint = care.paused)
                                Text("Surveillance en pause · alertes suspendues", color = care.paused, style = MaterialTheme.typography.labelLarge)
                            } else HorizontalDivider(color = care.border)
                        }
                    }
                }, bottomBar = {
                    if (showNavigation && !rail) Column {
                        HorizontalDivider(color = care.border)
                        NavigationBar(Modifier.testTag("navigation_bar"), containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                            destinations.forEach { index -> NavigationBarItem(selected = tab == index, onClick = { navigate(index) }, colors = navColors,
                                icon = { DestinationIcon(index, tab == index, count) }, label = { Text(destinationLabels[index]) }) }
                        }
                    }
                }) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    if (showNavigation && rail) {
                        NavigationRail(Modifier.fillMaxHeight().testTag("navigation_rail"), containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            windowInsets = WindowInsets(0, 0, 0, 0)) {
                            Spacer(Modifier.height(8.dp))
                            destinations.forEach { index -> NavigationRailItem(selected = tab == index, onClick = { navigate(index) }, colors = railColors,
                                icon = { DestinationIcon(index, tab == index, count) }, label = { Text(destinationLabels[index]) }) }
                        }
                        VerticalDivider(color = care.border)
                    }
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) { content(Modifier) }
                }
            }
        }
    }
}

@Composable private fun DestinationIcon(index: Int, selected: Boolean, count: Int) {
    if (index == 1) EventNavigationIcon(count, selected)
    else Icon(if (selected) destinationIcons[index].second else destinationIcons[index].first, null)
}

@Composable internal fun EventNavigationIcon(count: Int, selected: Boolean = false) {
    val icon = if (selected) Icons.Filled.Notifications else Icons.Outlined.Notifications
    if (count > 0) BadgedBox(badge = { Badge(containerColor = Care.colors.critical) { Text(if (count > 99) "99+" else count.toString()) } }) {
        Icon(icon, "Événements en cours : $count")
    } else Icon(icon, null)
}

@Composable private fun MonitorPauseButton(paused: Boolean, enabled: Boolean, compact: Boolean, change: (Boolean) -> Unit) {
    val help = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val label = if (paused) "Reprendre la surveillance" else "Mettre la surveillance en pause"
    val care = Care.colors
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), state = help, enableUserInput = false,
        tooltip = { PlainTooltip { Text("Suspend les alertes et rappels de ce veilleur. Le journal est conservé. Touchez à nouveau pour reprendre.") } }) {
        Surface(color = if (paused) care.paused else Color.Transparent, shape = MaterialTheme.shapes.medium,
            border = if (paused) null else BorderStroke(1.dp, care.borderStrong),
            modifier = Modifier.heightIn(min = 44.dp).widthIn(min = 44.dp).clip(MaterialTheme.shapes.medium)
                .combinedClickable(enabled = enabled, role = Role.Button, onClickLabel = label,
                    onLongClickLabel = "Expliquer la pause", onLongClick = { scope.launch { help.show() } }, onClick = { change(!paused) })
                .semantics { contentDescription = label; stateDescription = if (paused) "Pause active" else "Surveillance active" }) {
            val tint = if (paused) care.onPaused else if (enabled) MaterialTheme.colorScheme.onSurface else care.textMuted
            Row(Modifier.padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null, Modifier.size(20.dp), tint = tint)
                if (!compact) Text(if (paused) "Reprendre" else "Pause", color = tint, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable private fun TapInfo(explanation: String, content: @Composable (() -> Unit) -> Unit) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(explanation) } }, state = state, enableUserInput = false) {
        content { if (state.isVisible) state.dismiss() else scope.launch { state.show() } }
    }
}

// ---------------------------------------------------------------------------------------------
// First launch
// ---------------------------------------------------------------------------------------------

@Composable private fun Welcome(modifier: Modifier, start: (DeviceRole) -> Unit) {
    BoxWithConstraints(modifier) {
        val twoColumns = maxWidth >= 720.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Choisissez le rôle de cet appareil", style = MaterialTheme.typography.headlineMedium)
                Text("Le rôle peut être changé plus tard dans les réglages. Les chambres ne s’associent qu’avec votre autorisation, par QR.",
                    color = Care.colors.textSecondary)
            }
            val monitor: @Composable (Modifier) -> Unit = { m -> RoleCard(m, "Poste veilleur", "Téléphone du personnel de nuit.", Icons.Outlined.Dashboard,
                listOf("Reçoit les événements et alertes", "Suit l’état de chaque chambre", "Écoute et interphone"),
                "Utiliser comme poste veilleur") { start(DeviceRole.MONITOR_UNIT) } }
            val room: @Composable (Modifier) -> Unit = { m -> RoleCard(m, "Téléphone chambre", "Appareil fixe placé dans la chambre.", Icons.Outlined.Sensors,
                listOf("Mesure le niveau sonore relatif", "Transmet les événements aux veilleurs", "Aucun enregistrement conservé"),
                "Utiliser comme téléphone chambre") { start(DeviceRole.ROOM_UNIT) } }
            if (twoColumns) Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                monitor(Modifier.weight(1f).fillMaxHeight()); room(Modifier.weight(1f).fillMaxHeight())
            } else { monitor(Modifier); room(Modifier) }
            Text("CareLink complète les procédures humaines ; il ne remplace ni la présence ni les rondes.",
                style = MaterialTheme.typography.bodySmall, color = Care.colors.textMuted)
        }
    }
}

@Composable private fun RoleCard(modifier: Modifier, title: String, body: String, icon: ImageVector, points: List<String>,
    action: String, onClick: () -> Unit) {
    Panel(modifier, contentPadding = PaddingValues(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Care.colors.textSecondary)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            points.forEach { point ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = Care.colors.textSecondary)
                    Text(point, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.weight(1f, fill = false))
        Button(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium) { Text(action) }
    }
}

// ---------------------------------------------------------------------------------------------
// Monitor: overview and rooms
// ---------------------------------------------------------------------------------------------

@Composable internal fun MonitorDashboard(modifier: Modifier, state: MonitorMonitoringService.MonitorServiceState,
    add: () -> Unit, select: (String) -> Unit, eventAction: (List<RoomAlertEvent>, Boolean) -> Unit, resume: () -> Unit) {
    val rooms = state.monitoredRooms
    LazyVerticalGrid(columns = GridCells.Adaptive(300.dp), modifier = modifier,
        contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!state.isRunning) item(span = { GridItemSpan(maxLineSpan) }) {
            Notice("Service de surveillance arrêté. Reprenez la surveillance et vérifiez les autorisations.", Tone.CRITICAL) {
                Button(resume, shape = MaterialTheme.shapes.medium) { Text("Reprendre") }
            }
        }
        if (state.notificationWarnings.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Notice("Alertes possiblement inaudibles : " + state.notificationWarnings.joinToString(" · "), Tone.WARNING)
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Overview(state) }
        if (rooms.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Panel(contentPadding = PaddingValues(24.dp)) {
                IconBadge(Icons.Outlined.MeetingRoom, Tone.NEUTRAL)
                Text("Aucune chambre appairée", style = MaterialTheme.typography.titleMedium)
                Text("Ajoutez les téléphones attendus avant votre prise de poste.", color = Care.colors.textSecondary)
                Button(add, Modifier.heightIn(min = 44.dp), shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Ajouter une chambre")
                }
            }
        } else item(span = { GridItemSpan(maxLineSpan) }) {
            SectionLabel("Chambres · ${rooms.size}", Modifier.padding(top = 4.dp)) {
                TextButton(add) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Ajouter une chambre") }
            }
        }
        gridItems(rooms.sortedWith(compareBy<RoomUnitState> { room ->
            when {
                state.activeAlerts.any { it.roomId == room.id && !it.isAcknowledged } -> 0
                room.status in listOf(RoomStatus.DISCONNECTED, RoomStatus.MIC_ERROR) -> 1
                state.activeAlerts.any { it.roomId == room.id } -> 2
                else -> 3
            }
        }.thenBy(naturalOrder) { it.name }), key = { it.id }) { room ->
            CompactRoomCard(room, state.activeAlerts.filter { it.roomId == room.id }, state.pendingActions,
                open = { select(room.id) }, action = eventAction)
        }
    }
}

/** Shift overview: three plainly labelled figures instead of cryptic header badges. */
@Composable private fun Overview(state: MonitorMonitoringService.MonitorServiceState) {
    val total = state.monitoredRooms.size
    val healthy = state.monitoredRooms.count { it.isMicOperational && it.status in listOf(RoomStatus.CALM, RoomStatus.ACTIVITY, RoomStatus.ALERT) }
    val unchecked = AlertGroup.active(state.activeAlerts).count { !it.isAcknowledged }
    val offline = state.monitoredRooms.count { it.status in listOf(RoomStatus.DISCONNECTED, RoomStatus.MIC_ERROR) }
    Panel(Modifier.testTag("overview"), contentPadding = PaddingValues(0.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Metric(Modifier.weight(1f), "Captures récentes", "$healthy/$total",
                if (total == 0) Tone.NEUTRAL else if (healthy == total) Tone.OK else Tone.WARNING,
                "Captures récentes : $healthy sur $total chambres. Le microphone transmet des échantillons récents.")
            VerticalDivider(color = Care.colors.border)
            Metric(Modifier.weight(1f), "À vérifier", unchecked.toString(), if (unchecked > 0) Tone.CRITICAL else Tone.NEUTRAL,
                "À vérifier : $unchecked alerte(s) en attente de prise en charge. Touchez une chambre ou ouvrez les événements.")
            VerticalDivider(color = Care.colors.border)
            Metric(Modifier.weight(1f), "Hors ligne", offline.toString(), if (offline > 0) Tone.CRITICAL else Tone.NEUTRAL,
                "Hors ligne : $offline chambre(s) sans connexion ou sans microphone vérifié.")
        }
    }
}

@Composable private fun Metric(modifier: Modifier, label: String, value: String, tone: Tone, explanation: String) {
    Box(modifier.fillMaxHeight()) { TapInfo(explanation) { show ->
        Column(Modifier.fillMaxSize().clickable(role = Role.Button, onClickLabel = "Expliquer", onClick = show)
            .semantics { contentDescription = label }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Care.colors.textSecondary, maxLines = 2)
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                color = if (tone == Tone.NEUTRAL) MaterialTheme.colorScheme.onSurface else tone.color())
        }
    } }
}

private fun roomInformation(room: RoomUnitState, events: List<RoomAlertEvent>): String = buildString {
    appendLine(room.status.label())
    if (room.diagnostic.isNotBlank()) appendLine(room.diagnostic)
    appendLine("Dernière donnée : ${if (room.lastSeenTimestamp > 0) clock(room.lastSeenTimestamp) else "aucune"}")
    appendLine("Batterie : ${if (room.batteryPercent >= 0) "${room.batteryPercent}%" else "inconnue"}${if (room.isCharging) " · en charge" else ""}")
    room.temperatureC?.let { appendLine("Température : ${"%.1f".format(Locale.FRANCE, it)} °C") }
    appendLine("Niveau relatif : ${room.currentDecibels} · seuil ${room.noiseThresholdDb}")
    appendLine("Adresse : ${room.ipAddress}")
    if (events.isNotEmpty()) append("${events.size} occurrence(s) en cours · dernière ${clock(events.maxOf { it.timestamp })}")
}.trim()

@Composable internal fun CompactRoomCard(room: RoomUnitState, events: List<RoomAlertEvent>, pending: Set<String>,
    open: () -> Unit, action: (List<RoomAlertEvent>, Boolean) -> Unit) {
    val unresolved = events.filterNot { it.resolved }
    val unchecked = unresolved.filterNot { it.isAcknowledged }
    val targets = if (unchecked.isNotEmpty()) unchecked else unresolved
    val waiting = targets.isNotEmpty() && targets.all { it.eventId in pending }
    val technical = room.status in listOf(RoomStatus.DISCONNECTED, RoomStatus.MIC_ERROR)
    val alarm = technical || unchecked.any { it.eventId !in pending } || (room.status == RoomStatus.ALERT && unresolved.isEmpty())
    val tone = if (alarm) Tone.CRITICAL else if (unresolved.isNotEmpty() || room.enrollmentPending) Tone.WARNING else room.status.tone()
    val status = when {
        room.enrollmentPending -> "Appairage à terminer"
        room.status == RoomStatus.DISCONNECTED -> "Hors ligne"
        room.status == RoomStatus.MIC_ERROR -> "Microphone indisponible"
        waiting -> "Confirmation en attente"
        unchecked.isNotEmpty() -> "À vérifier"
        unresolved.isNotEmpty() -> "Pris en charge"
        else -> room.status.shortLabel()
    }
    val lowBattery = room.batteryPercent in 0..19 && !room.isCharging
    val detail = when {
        unresolved.isNotEmpty() -> AlertGroup.active(unresolved).joinToString(" · ") {
            it.eventType.label() + if (it.occurrences > 1) " ×${it.occurrences}" else "" }
        room.batteryPercent in 0..19 && !room.isCharging -> "Batterie ${room.batteryPercent} %"
        else -> null
    }
    var resolving by remember { mutableStateOf<List<RoomAlertEvent>?>(null) }
    val tooltip = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(roomInformation(room, unresolved)) } }, state = tooltip, enableUserInput = false) {
        Panel(Modifier.clip(MaterialTheme.shapes.large).combinedClickable(role = Role.Button,
            onClickLabel = "Ouvrir ${room.name}", onClick = open, onLongClickLabel = "Informations de ${room.name}",
            onLongClick = { scope.launch { tooltip.show() } }), tone = tone,
            container = if (alarm) tone.container() else MaterialTheme.colorScheme.surfaceContainer,
            contentPadding = PaddingValues(start = 14.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(room.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StatusDot(tone)
                        Text(status, color = if (tone == Tone.OK) Care.colors.textSecondary else tone.color(),
                            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (unresolved.isEmpty() && lowBattery) Care.colors.warning else Care.colors.textSecondary) }
                }
                if (targets.isNotEmpty()) {
                    val description = if (waiting) "Confirmation en attente pour ${room.name}" else if (unchecked.isNotEmpty()) "Prendre en charge ${room.name}" else "Résoudre les alertes de ${room.name}"
                    val padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    val semantic = Modifier.heightIn(min = 40.dp).semantics { contentDescription = description }
                    when {
                        waiting -> OutlinedButton({}, semantic, enabled = false, contentPadding = padding, shape = MaterialTheme.shapes.medium) {
                            Icon(Icons.Outlined.HourglassTop, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("En attente") }
                        unchecked.isNotEmpty() -> Button({ action(targets.filterNot { it.eventId in pending }, false) }, semantic, contentPadding = padding,
                            shape = MaterialTheme.shapes.medium, colors = ButtonDefaults.buttonColors(containerColor = Care.colors.critical,
                                contentColor = if (Care.isDark) Color(0xFF2B0A09) else Color.White)) { Text("Prendre en charge") }
                        else -> OutlinedButton({ resolving = targets.filterNot { it.eventId in pending } }, semantic, contentPadding = padding,
                            shape = MaterialTheme.shapes.medium) { Text("Résoudre") }
                    }
                }
            }
            if (room.isMicOperational && !technical) LevelMeter(room.currentDecibels, room.noiseThresholdDb, true, height = 4.dp)
        }
    }
    resolving?.let { snapshot ->
        ResolveDialog(snapshot.size, room.name, dismiss = { resolving = null }) { resolving = null; action(snapshot, true) }
    }
}

@Composable private fun ResolveDialog(count: Int, roomName: String?, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, icon = { Icon(Icons.Outlined.TaskAlt, null) },
        title = { Text(if (roomName != null) "Résoudre les alertes de $roomName ?" else "Résoudre cette alerte ?") },
        text = { Text("Clôturer $count occurrence(s) en cours. Vérifiez la situation et l’état de surveillance avant de clôturer ; l’action reste dans l’historique.") },
        confirmButton = { TextButton(onClick = confirm) { Text("Confirmer la résolution") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Annuler") } })
}

// ---------------------------------------------------------------------------------------------
// Room views (monitor detail and room phone)
// ---------------------------------------------------------------------------------------------

@Composable private fun LevelPanel(level: Int, threshold: Int, enabled: Boolean, actions: @Composable ColumnScope.() -> Unit) {
    Panel(Modifier.testTag("room_level"), contentPadding = PaddingValues(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                TapInfo("Le niveau dépend du microphone et de son placement. CareLink n’effectue pas d’étalonnage en dB SPL. Réglez le seuil dans les réglages de la chambre après un essai sonore représentatif.") { show ->
                    Row(Modifier.clip(MaterialTheme.shapes.small).clickable(role = Role.Button, onClick = show).padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Niveau relatif", style = MaterialTheme.typography.labelLarge, color = Care.colors.textSecondary)
                        Icon(Icons.Outlined.Info, "Informations sur le niveau sonore", Modifier.size(16.dp), tint = Care.colors.textSecondary)
                    }
                }
                Text(if (enabled) level.toString() else "—", style = MaterialTheme.typography.displaySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Seuil", style = MaterialTheme.typography.labelMedium, color = Care.colors.textSecondary)
                Text(threshold.toString(), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
            }
        }
        LevelMeter(level, threshold, enabled)
        actions()
    }
}

@Composable private fun AdaptiveRoomPanels(modifier: Modifier, heading: @Composable () -> Unit, primary: @Composable ColumnScope.() -> Unit,
    secondary: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier) {
        if (maxWidth >= 760.dp) Column(Modifier.fillMaxSize().padding(20.dp).testTag("room_panels_split"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            heading()
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).testTag("room_primary"), verticalArrangement = Arrangement.spacedBy(12.dp), content = primary)
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).testTag("room_secondary"), verticalArrangement = Arrangement.spacedBy(12.dp), content = secondary)
            }
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("room_panels_single"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            heading(); primary(); secondary()
        }
    }
}

@Composable private fun RoomHeading(name: String, status: String, tone: Tone) {
    FlowRow(Modifier.fillMaxWidth().testTag("room_heading"), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, Modifier.align(Alignment.CenterVertically).testTag("room_title"), style = MaterialTheme.typography.headlineMedium)
        StatusPill(status, tone, Modifier.align(Alignment.CenterVertically).testTag("room_status"))
    }
}

@Composable internal fun RoomHomePanel(modifier: Modifier, state: RoomMonitoringService.RoomServiceState, fallbackName: String,
    resume: () -> Unit, settings: () -> Unit, pair: () -> Unit) {
    var information by remember { mutableStateOf(false) }
    AdaptiveRoomPanels(modifier, heading = {
        RoomHeading(state.roomName.ifBlank { fallbackName }, if (!state.isRunning) "Surveillance arrêtée" else state.status.shortLabel(),
            if (state.isRunning) state.status.tone() else Tone.CRITICAL)
    }, primary = {
        LevelPanel(state.currentDecibels, state.thresholdDb, state.isMicOperational) {
            if (!state.isRunning) Button(resume, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium) { Text("Reprendre la surveillance") }
            else if (state.errorMessage != null) Notice(state.errorMessage, Tone.CRITICAL)
            if (state.isCaregiverTalking) Notice("Interphone actif : un veilleur parle dans la chambre.", Tone.WARNING)
            OutlinedButton(settings, Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Régler la sensibilité")
            }
        }
    }, secondary = {
        Panel(Modifier.testTag("room_monitors"), contentPadding = PaddingValues(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(Icons.Outlined.Devices, if (state.connectedMonitors > 0) Tone.OK else Tone.WARNING)
                Column(Modifier.weight(1f)) {
                    Text(when (state.connectedMonitors) { 0 -> "Aucun veilleur connecté"; 1 -> "1 veilleur connecté"; else -> "${state.connectedMonitors} veilleurs connectés" },
                        style = MaterialTheme.typography.titleMedium)
                    Text(if (state.connectedMonitors > 0) "Les événements sont transmis en direct." else "Les événements sont conservés jusqu’à la reconnexion.",
                        style = MaterialTheme.typography.bodySmall, color = Care.colors.textSecondary)
                }
            }
            OutlinedButton(pair, Modifier.fillMaxWidth().heightIn(min = 44.dp), enabled = state.isRunning, shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Outlined.QrCode2, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Appairer un veilleur")
            }
        }
        DisclosurePanel("Informations de l’appareil", information, { information = !information },
            summary = "Adresse, échantillons, dernière capture") {
            InfoRow("Adresse Wi-Fi", state.localAddress.ifBlank { "indisponible" })
            InfoRow("Échantillons", state.samples.toString())
            InfoRow("Dernière capture", if (state.captureAgeMs == Long.MAX_VALUE) "aucune" else "il y a ${state.captureAgeMs / 1000} s")
            state.connectionDiagnostic?.let { Notice(it, Tone.WARNING) }
        }
    })
}

@Composable internal fun RoomDetailPanel(modifier: Modifier, room: RoomUnitState, state: MonitorMonitoringService.MonitorServiceState,
    listen: () -> Unit, talk: () -> Unit, reconnect: () -> Unit, eventAction: (List<RoomAlertEvent>, Boolean) -> Unit,
    threshold: (Int) -> Unit, updateAddress: (String) -> Unit, pair: () -> Unit, forget: () -> Unit) {
    var value by remember(room.id, room.noiseThresholdDb) { mutableFloatStateOf(room.noiseThresholdDb.toFloat()) }
    var sensitivity by remember(room.id) { mutableStateOf(false) }
    var information by remember(room.id) { mutableStateOf(false) }
    val needsConnection = room.enrollmentPending || room.status in listOf(RoomStatus.DISCONNECTED, RoomStatus.VERIFYING, RoomStatus.DEGRADED)
    var connection by remember(room.id) { mutableStateOf(needsConnection) }
    LaunchedEffect(needsConnection) { if (needsConnection) connection = true }
    var editAddress by remember(room.id) { mutableStateOf(false) }
    var address by remember(room.id, room.ipAddress) { mutableStateOf(room.ipAddress) }
    val groups = AlertGroup.active(state.activeAlerts.filter { it.roomId == room.id })
    val listening = state.activeListeningRoomId == room.id
    val reachable = !room.enrollmentPending && room.status !in listOf(RoomStatus.DISCONNECTED, RoomStatus.VERIFYING)
    AdaptiveRoomPanels(modifier, heading = {
        RoomHeading(room.name, if (room.enrollmentPending) "Appairage à terminer" else room.status.shortLabel(),
            if (room.enrollmentPending) Tone.WARNING else room.status.tone())
    }, primary = {
        LevelPanel(room.currentDecibels, room.noiseThresholdDb, room.isMicOperational) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(listen, Modifier.weight(1f).heightIn(min = 48.dp), enabled = room.isMicOperational || listening, shape = MaterialTheme.shapes.medium) {
                    Icon(if (listening) Icons.Outlined.HeadsetOff else Icons.Outlined.Headphones, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (listening) "Couper l’écoute" else "Écouter")
                }
                OutlinedButton(talk, Modifier.weight(1f).heightIn(min = 48.dp), enabled = reachable, shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Outlined.Mic, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Interphone")
                }
            }
        }
        if (groups.isNotEmpty()) SectionLabel("Événements en cours · ${groups.size}")
        groups.forEach { GroupedEventCard(it, state.pendingActions, eventAction, showRoomName = false) }
    }, secondary = {
        DisclosurePanel("Sensibilité", sensitivity, { sensitivity = !sensitivity }, summary = "Seuil relatif ${room.noiseThresholdDb}") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Seuil relatif", Modifier.weight(1f), color = Care.colors.textSecondary)
                Text(value.toInt().toString(), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
            }
            Slider(value, { value = it }, valueRange = 35f..85f)
            OutlinedButton(onClick = { threshold(value.toInt()) }, enabled = reachable && value.toInt() != room.noiseThresholdDb,
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Text("Appliquer le réglage") }
        }
        DisclosurePanel("Informations de l’appareil", information, { information = !information }, summary = "Batterie ${battery(room)}") {
            InfoRow("Batterie", battery(room), if (room.batteryPercent in 0..19 && !room.isCharging) Care.colors.warning else MaterialTheme.colorScheme.onSurface)
            room.temperatureC?.let { InfoRow("Température", "${"%.1f".format(Locale.FRANCE, it)} °C") }
            InfoRow("Dernière donnée", if (room.lastSeenTimestamp > 0) clock(room.lastSeenTimestamp) else "aucune")
            InfoRow("Échantillons", room.samplesCaptured.toString())
            if (room.diagnostic.isNotBlank()) Text(room.diagnostic, style = MaterialTheme.typography.bodySmall,
                color = if (room.isMicOperational) Care.colors.textSecondary else Care.colors.warning)
        }
        DisclosurePanel("Connexion", connection, { connection = !connection }, summary = room.ipAddress.ifBlank { "Adresse inconnue" }) {
            InfoRow("Adresse IP", room.ipAddress.ifBlank { "inconnue" })
            if (room.enrollmentPending) {
                Notice("Accès en attente de confirmation par la chambre.", Tone.WARNING)
                OutlinedButton(pair, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Text("Scanner un nouveau QR") }
            }
            if (needsConnection && room.diagnostic.isNotBlank()) Notice(room.diagnostic, Tone.WARNING)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(reconnect, shape = MaterialTheme.shapes.medium) { Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Réessayer") }
                TextButton({ editAddress = !editAddress }) { Text("Modifier l’IP") }
            }
            if (editAddress) {
                OutlinedTextField(address, { address = it.take(80) }, label = { Text("IP du téléphone chambre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { updateAddress(address); editAddress = false }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Text("Utiliser cette adresse") }
            }
            HorizontalDivider(color = Care.colors.border)
            TextButton(forget) { Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp), tint = Care.colors.critical); Spacer(Modifier.width(6.dp)); Text("Retirer cette chambre", color = Care.colors.critical) }
        }
    })
}

// ---------------------------------------------------------------------------------------------
// Events
// ---------------------------------------------------------------------------------------------

@Composable internal fun EventList(modifier: Modifier, events: List<RoomAlertEvent>, pending: Set<String>, action: (List<RoomAlertEvent>, Boolean) -> Unit) {
    var history by remember { mutableStateOf(false) }
    val groups = AlertGroup.active(events)
    val resolved = events.filter { it.resolved }.sortedByDescending { it.timestamp }
    LazyColumn(modifier.widthIn(max = 880.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Événements", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp)) }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(!history, { history = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("En cours · ${groups.size}") }
                SegmentedButton(history, { history = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Résolus · 7 jours") }
            }
        }
        if (history) {
            if (resolved.isEmpty()) item { Notice("Aucun événement résolu dans l’historique conservé.") }
            items(resolved, key = { it.eventId }) { HistoryCard(it) }
        } else {
            if (groups.isEmpty()) item { Notice("Aucune alerte en cours.", Tone.OK) }
            items(groups, key = { it.key }) { GroupedEventCard(it, pending, action) }
        }
    }
}

@Composable private fun GroupedEventCard(group: AlertGroup, pending: Set<String>, action: (List<RoomAlertEvent>, Boolean) -> Unit, showRoomName: Boolean = true) {
    var resolving by remember { mutableStateOf<List<RoomAlertEvent>?>(null) }
    val unchecked = group.events.filterNot { it.isAcknowledged }
    val targets = if (unchecked.isNotEmpty()) unchecked else group.events
    val waiting = targets.all { it.eventId in pending }
    val tone = if (group.isAcknowledged) Tone.WARNING else Tone.CRITICAL
    Panel(tone = tone) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(group.eventType.icon(), null, Modifier.size(20.dp), tint = tone.color())
            Column(Modifier.weight(1f)) {
                if (showRoomName) Text(group.roomName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(group.eventType.label(), style = if (showRoomName) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                    color = if (showRoomName) Care.colors.textSecondary else MaterialTheme.colorScheme.onSurface)
            }
            StatusPill(if (group.isAcknowledged) "Pris en charge" else "À vérifier", if (group.isAcknowledged) Tone.WARNING else Tone.CRITICAL)
        }
        Text(if (group.occurrences > 1) "${group.occurrences} occurrences · dernière ${clock(group.lastTimestamp)}" else clock(group.lastTimestamp),
            color = Care.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        if (unchecked.isNotEmpty()) Button(onClick = { action(targets.filterNot { it.eventId in pending }, false) },
            enabled = !waiting, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = Care.colors.critical, contentColor = if (Care.isDark) Color(0xFF2B0A09) else Color.White)) {
            Text(if (waiting) "Confirmation en attente…" else "Prendre en charge")
        } else OutlinedButton(onClick = { resolving = group.events.toList() }, enabled = !waiting,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = MaterialTheme.shapes.medium) {
            Text(if (waiting) "Confirmation en attente…" else "Marquer comme résolu")
        }
    }
    resolving?.let { snapshot -> ResolveDialog(snapshot.size, null, dismiss = { resolving = null }) { resolving = null; action(snapshot, true) } }
}

@Composable private fun HistoryCard(event: RoomAlertEvent) {
    Panel(tone = Tone.NEUTRAL) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(event.eventType.icon(), null, Modifier.size(20.dp), tint = Care.colors.textSecondary)
            Column(Modifier.weight(1f)) {
                Text(event.roomName, style = MaterialTheme.typography.titleMedium)
                Text(event.eventType.label(), style = MaterialTheme.typography.bodyMedium, color = Care.colors.textSecondary)
            }
            StatusPill("Résolu", Tone.OK)
        }
        Text(clock(event.timestamp), color = Care.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        if (event.notes.isNotBlank()) Text(event.notes, style = MaterialTheme.typography.bodyMedium)
        if (event.eventType == AlertEventType.SOUND_ALERT) Text("Pic relatif ${event.levelReachedDb}" +
            if (event.durationSeconds > 0) " · épisode ${event.durationSeconds} s" else " · durée non finalisée",
            color = Care.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        if (event.isAcknowledged) Text("Pris en charge par ${event.acknowledgedBy.takeLast(24)}" +
            if (event.resolvedAt > 0) " · résolu le ${clock(event.resolvedAt)}" else "",
            color = Care.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------------------------------------------------------------------------------------
// Pairing, intercom, professional code
// ---------------------------------------------------------------------------------------------

@Composable private fun AddRoomScreen(modifier: Modifier, state: MonitorMonitoringService.MonitorServiceState,
    scan: Boolean, startScan: () -> Unit, submit: (String) -> Unit, error: (String) -> Unit, cancel: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val known = state.monitoredRooms.map { it.id }.toSet()
    val nearby = state.nearbyRooms.filterNot { it.id in known }
    Column(modifier.fillMaxHeight().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Ajouter une chambre", style = MaterialTheme.typography.headlineMedium)
        Panel {
            listOf("Sur le téléphone chambre, touchez « Appairer un veilleur ».",
                "Scannez le QR affiché, en présence des deux appareils.",
                "Attendez la confirmation : la chambre passe alors en surveillance.").forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(step, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (scan) PairingCamera(Modifier.fillMaxWidth().height(300.dp).clip(MaterialTheme.shapes.large), onCode = submit, onError = error)
        else Button(startScan, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) {
            Icon(Icons.Outlined.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text("Scanner le QR")
        }
        SectionLabel("Ou avec le code transmis")
        OutlinedTextField(input, { input = it.take(12000) }, label = { Text("Code CL2…") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
        OutlinedButton(onClick = { submit(input) }, enabled = input.startsWith("CL2."), modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium) { Text("Appairer avec le code") }
        SectionLabel("Détectées sur le réseau")
        Panel {
            if (nearby.isEmpty()) Text("Aucune nouvelle chambre détectée. Le scan du QR fonctionne aussi sans découverte réseau.",
                style = MaterialTheme.typography.bodyMedium, color = Care.colors.textSecondary)
            nearby.forEach { found ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusDot(Tone.ACCENT)
                    Text("Chambre ${found.id.take(8)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(found.ipAddress, style = MaterialTheme.typography.bodySmall, color = Care.colors.textSecondary)
                }
            }
            Text(state.discoveryDiagnostic, style = MaterialTheme.typography.bodySmall, color = Care.colors.textMuted)
        }
        TextButton(cancel, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Annuler") }
    }
}

@Composable private fun PairingDialog(code: String, seconds: Int, cancel: () -> Unit, copy: () -> Unit) {
    AlertDialog(onDismissRequest = cancel, title = { Text("Appairer un veilleur") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sur le veilleur : Ajouter une chambre → Scanner le QR. Restez à proximité des deux appareils.",
                color = Care.colors.textSecondary)
            val bitmap = remember(code) { pairingBitmap(code) }
            Image(bitmap.asImageBitmap(), "QR d’appairage confidentiel", Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Usage unique", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("expire dans $seconds s", style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = if (seconds <= 20) Care.colors.critical else Care.colors.warning)
            }
            LinearProgressIndicator(progress = { seconds / 120f }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = if (seconds <= 20) Care.colors.critical else Care.colors.warning, trackColor = Care.colors.track, drawStopIndicator = {})
            TextButton(copy) { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Copier le code à la place") }
        } }, confirmButton = { TextButton(onClick = cancel) { Text("Fermer et annuler le code") } })
}

@Composable private fun TalkDialog(name: String, transmitting: Boolean, press: (holding: () -> Boolean) -> Unit, release: () -> Unit, close: () -> Unit) {
    var holding by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { release() } }
    val active = MaterialTheme.colorScheme.primary
    AlertDialog(onDismissRequest = close, title = { Text("Interphone · $name") },
        text = { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(if (transmitting) "Microphone actif · transmission vers la chambre" else if (holding) "Connexion en cours…"
                else "Maintenez le bouton pour parler, relâchez pour couper. 30 secondes au maximum.", textAlign = TextAlign.Center,
                color = if (transmitting) active else Care.colors.textSecondary)
            Box(Modifier.size(120.dp).clip(CircleShape).background(if (transmitting) active else MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, if (transmitting) active else Care.colors.borderStrong, CircleShape)
                .pointerInput(Unit) { detectTapGestures(onPress = {
                    holding = true; press { holding }
                    try { tryAwaitRelease() } finally { holding = false; release() }
                }) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Mic, "Maintenir pour parler", tint = if (transmitting) MaterialTheme.colorScheme.onPrimary else active, modifier = Modifier.size(44.dp))
            }
            Text("L’écoute distante est coupée pendant la parole et l’analyse sonore de la chambre est suspendue.",
                style = MaterialTheme.typography.bodySmall, color = Care.colors.textMuted, textAlign = TextAlign.Center)
        } }, confirmButton = { TextButton(onClick = close) { Text("Terminer") } })
}

@Composable private fun ProfessionalCodeScreen(setup: Boolean, action: String?, cancel: () -> Unit, lockedUntil: () -> Long, verify: suspend (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()
    val mask = remember { PasswordVisualTransformation() }
    val remaining = ((lockedUntil() - now + 999) / 1000).coerceAtLeast(0)
    LaunchedEffect(remaining > 0) { while (lockedUntil() > System.currentTimeMillis()) { now = System.currentTimeMillis(); delay(500) }; now = System.currentTimeMillis() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                IconBadge(Icons.Outlined.Lock, size = 48.dp)
                Text(if (setup) "Protéger les réglages" else "Accès professionnel", style = MaterialTheme.typography.headlineMedium)
                action?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                Text(if (setup) "Choisissez un code d’au moins 6 chiffres. Conservez-le dans la procédure du service." else "Saisissez le code professionnel pour confirmer.",
                    color = Care.colors.textSecondary)
                OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(12) }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Code professionnel") }, singleLine = true, enabled = !busy, visualTransformation = mask,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                if (setup) OutlinedTextField(repeat, { repeat = it.filter(Char::isDigit).take(12) }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirmer le code") }, singleLine = true, enabled = !busy, visualTransformation = mask,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                if (remaining > 0) Notice("Trop de codes incorrects. Nouvel essai possible dans $remaining s.", Tone.WARNING)
                else error?.let { Text(it, color = Care.colors.critical, style = MaterialTheme.typography.bodyMedium) }
                Button(onClick = {
                    if (pin.length < 6 || (setup && pin != repeat)) { error = "Au moins 6 chiffres et une confirmation identique sont nécessaires" }
                    else {
                        busy = true
                        scope.launch {
                            try { if (!verify(pin)) { error = "Code incorrect"; pin = ""; delay(1500) } }
                            catch (_: Exception) { error = "Impossible de vérifier ou enregistrer le code" }
                            finally { busy = false; now = System.currentTimeMillis() }
                        }
                    }
                }, enabled = !busy && remaining == 0L, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) {
                    Text(if (busy) "Vérification…" else "Valider")
                }
                TextButton(onClick = cancel, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Annuler") }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------------------------

@Composable private fun SettingsPanel(modifier: Modifier, vm: MainViewModel, role: DeviceRole, codeProtectionEnabled: Boolean, themeMode: ThemeMode,
    warnings: List<String>, peers: List<String>, stop: () -> Unit,
    enableLock: () -> Unit, disableLock: () -> Unit, test: () -> Unit, pair: () -> Unit, revoke: (String) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(vm.prefs.roomName) }
    var threshold by remember { mutableFloatStateOf(vm.prefs.defaultSensitivityDb.toFloat()) }
    var duration by remember { mutableFloatStateOf(vm.prefs.minNoiseDurationMs.toFloat()) }
    var sound by remember { mutableStateOf(vm.prefs.isSoundAlertEnabled) }
    var repeat by remember { mutableStateOf(vm.prefs.isAlertRepeatEnabled) }
    var interval by remember { mutableFloatStateOf(vm.prefs.alertRepeatIntervalSeconds.toFloat()) }
    var label by remember { mutableStateOf(vm.prefs.deviceLabel) }
    val divider: @Composable () -> Unit = { HorizontalDivider(color = Care.colors.border) }
    // When code protection is enabled this page is guarded, and it is left on backgrounding.
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(Modifier.padding(top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Réglages du poste", style = MaterialTheme.typography.headlineMedium)
                Text("Version ${BuildConfig.VERSION_NAME} · utilisez la même version sur les chambres et les veilleurs.",
                    style = MaterialTheme.typography.bodySmall, color = Care.colors.textSecondary)
            }
        }
        if (role == DeviceRole.ROOM_UNIT) {
            item { SectionLabel("Chambre") }
            item {
                Panel {
                    OutlinedTextField(name, { name = it.take(80) }, label = { Text("Repère de chambre") },
                        supportingText = { Text("Évitez le nom du résident.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Seuil relatif", Modifier.weight(1f)); Text(threshold.toInt().toString(), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
                    }
                    Slider(threshold, { threshold = it }, valueRange = 35f..85f)
                    Text("Ce niveau n’est pas une mesure en dB SPL. Réglez-le après un essai sonore représentatif.",
                        style = MaterialTheme.typography.bodySmall, color = Care.colors.textSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Dépassement minimal", Modifier.weight(1f)); Text("${duration.toInt()} ms", style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
                    }
                    Slider(duration, { duration = it }, valueRange = 100f..3000f)
                    Button({ vm.roomSettings(name, threshold.toInt(), duration.toInt()); vm.message.value = "Réglages enregistrés" },
                        Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium) { Text("Enregistrer les réglages de la chambre") }
                }
            }
            item { SectionLabel("Veilleurs") }
            item {
                Panel {
                    SettingRow("Nouvel appairage veilleur", "QR à usage unique, valable deux minutes.") {
                        OutlinedButton(pair, shape = MaterialTheme.shapes.medium) { Icon(Icons.Outlined.QrCode2, null, Modifier.size(18.dp)) }
                    }
                    divider()
                    SettingRow("Envoyer un événement de test", "Ne vérifie pas le microphone : faites aussi un essai acoustique réel.") {
                        OutlinedButton(test, shape = MaterialTheme.shapes.medium) { Text("Tester") }
                    }
                    divider()
                    Text("Veilleurs autorisés", style = MaterialTheme.typography.titleSmall)
                    if (peers.isEmpty()) Text("Aucun veilleur autorisé.", style = MaterialTheme.typography.bodyMedium, color = Care.colors.textSecondary)
                    peers.forEach { id ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Smartphone, null, Modifier.size(18.dp), tint = Care.colors.textSecondary); Spacer(Modifier.width(10.dp))
                            Text("Poste …${id.takeLast(8)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                            TextButton(onClick = { revoke(id) }) { Text("Révoquer", color = Care.colors.critical) }
                        }
                    }
                }
            }
        } else {
            item { SectionLabel("Poste") }
            item {
                Panel {
                    OutlinedTextField(label, { label = it.take(80); vm.prefs.deviceLabel = label }, label = { Text("Nom de ce poste") },
                        supportingText = { Text("Affiché dans le journal lors des prises en charge.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
            item { SectionLabel("Alertes") }
            item {
                Panel {
                    if (warnings.isEmpty()) Notice("Contrôles système disponibles. Effectuez un essai sonore réel avant la prise de poste.", Tone.OK)
                    else Notice(warnings.joinToString("\n"), Tone.WARNING)
                    SettingRow("Signal sonore et vibrant", "Le canal Android peut aussi être rendu silencieux ; ses sons se règlent dans Android.") {
                        Switch(sound, { sound = it; vm.prefs.isSoundAlertEnabled = it })
                    }
                    divider()
                    SettingRow("Rappels jusqu’à prise en charge") { Switch(repeat, { repeat = it; vm.prefs.isAlertRepeatEnabled = it }) }
                    if (repeat) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Intervalle de rappel", Modifier.weight(1f), color = Care.colors.textSecondary)
                            Text("${interval.toInt()} s", style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
                        }
                        Slider(interval, { interval = it; vm.prefs.alertRepeatIntervalSeconds = it.toInt() }, valueRange = 10f..120f)
                    }
                    divider()
                    TextButton(onClick = { context.startActivity(if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Ouvrir les notifications Android")
                    }
                }
            }
        }
        item { SectionLabel("Affichage") }
        item {
            Panel {
                Text("Thème", style = MaterialTheme.typography.bodyLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(ThemeMode.DARK to "Sombre", ThemeMode.LIGHT to "Clair", ThemeMode.SYSTEM to "Système").forEachIndexed { index, (mode, text) ->
                        SegmentedButton(themeMode == mode, { vm.setThemeMode(mode) }, SegmentedButtonDefaults.itemShape(index, 3)) { Text(text) }
                    }
                }
                Text("Le thème sombre limite l’éblouissement pendant les gardes de nuit.", style = MaterialTheme.typography.bodySmall, color = Care.colors.textSecondary)
            }
        }
        item { SectionLabel("Sécurité") }
        item {
            Panel {
                SettingRow("Protection par code", if (codeProtectionEnabled) "Code activé pour les réglages et les actions sensibles."
                    else "Code désactivé : les actions sont confirmées sans saisie. Le QR reste nécessaire pour autoriser un téléphone.") {
                    StatusPill(if (codeProtectionEnabled) "Activée" else "Désactivée", if (codeProtectionEnabled) Tone.OK else Tone.NEUTRAL)
                }
                OutlinedButton(if (codeProtectionEnabled) disableLock else enableLock, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Text(if (codeProtectionEnabled) "Désactiver la protection par code" else "Protéger les actions par un code")
                }
            }
        }
        item { SectionLabel("Maintenance") }
        item {
            Panel {
                Text("Après un redémarrage ou un arrêt forcé, ouvrez CareLink pour reprendre et contrôler la surveillance. Les événements non résolus sont conservés ; seuls les événements résolus expirent après 7 jours.",
                    style = MaterialTheme.typography.bodySmall, color = Care.colors.textSecondary)
                OutlinedButton(stop, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, Care.colors.critical.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Care.colors.critical)) { Text("Arrêter et changer de rôle") }
            }
        }
        item {
            Text("En complément des procédures humaines. La disponibilité du Wi-Fi, le placement du téléphone et l’audibilité des alertes doivent être vérifiés sur site.",
                Modifier.padding(vertical = 8.dp), color = Care.colors.textMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
