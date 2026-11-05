@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PreferencesManager
import com.example.BuildConfig
import com.example.model.*
import com.example.security.AdminPin
import com.example.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Mint = Color(0xFF65E0C0)
private val Amber = Color(0xFFFFD08A)
private val Rose = Color(0xFFFF879D)
private val Ink = Color(0xFF10202B)
private val Quiet = Color(0xFFA9B9C9)
private val Panel = Color(0xFF152338)

private fun RoomStatus.label() = when (this) {
    RoomStatus.VERIFYING -> "À vérifier"
    RoomStatus.CALM -> "Capture récente · niveau bas"
    RoomStatus.ACTIVITY -> "Activité sonore"
    RoomStatus.ALERT -> "Seuil sonore dépassé"
    RoomStatus.DISCONNECTED -> "Surveillance interrompue"
    RoomStatus.MIC_ERROR -> "Capture non vérifiée"
    RoomStatus.DEGRADED -> "Surveillance dégradée"
}
private fun RoomStatus.tint() = when (this) {
    RoomStatus.CALM -> Mint
    RoomStatus.ACTIVITY, RoomStatus.DEGRADED, RoomStatus.VERIFYING -> Amber
    else -> Rose
}
private fun AlertEventType.label() = when (this) {
    AlertEventType.SOUND_ALERT -> "Événement sonore"
    AlertEventType.DISCONNECTION -> "Connexion interrompue"
    AlertEventType.MIC_ERROR -> "Incident microphone"
    AlertEventType.LOW_BATTERY -> "Batterie faible"
    AlertEventType.SERVICE_INTERRUPTED -> "Incident technique"
    AlertEventType.TEST -> "Test de transmission"
}
private fun clock(time: Long) = SimpleDateFormat("dd MMM · HH:mm:ss", Locale.FRANCE).format(Date(time))

@Composable
fun CareLinkApp(vm: MainViewModel, startRole: (DeviceRole) -> Unit,
    requestMicrophone: (() -> Unit) -> Unit, requestCamera: (() -> Unit) -> Unit, openRoom: String?) {
    val role by vm.role.collectAsStateWithLifecycle()
    val codeProtectionEnabled by vm.codeProtectionEnabled.collectAsStateWithLifecycle()
    val room by vm.room.collectAsStateWithLifecycle()
    val monitor by vm.monitor.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val code by vm.pairingCode.collectAsStateWithLifecycle()
    val seconds by vm.pairingSeconds.collectAsStateWithLifecycle()
    val peers by vm.peers.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message, monitor.diagnostic) {
        (message ?: monitor.diagnostic)?.let { snackbar.showSnackbar(it, withDismissAction = true); vm.message.value = null }
    }
    var tab by remember { mutableIntStateOf(0) }
    var detailId by remember { mutableStateOf(openRoom) }
    var addRoom by remember { mutableStateOf(false) }
    var scan by remember { mutableStateOf(false) }
    var talkId by remember { mutableStateOf<String?>(null) }
    var confirmLabel by remember { mutableStateOf<String?>(null) }
    var protectedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pinDialog by remember { mutableStateOf(false) }
    var setupPin by remember { mutableStateOf(false) }
    fun protect(label: String, action: () -> Unit) {
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
        ProfessionalCodeScreen(setupMode, confirmLabel, cancel) { pin ->
            val accepted = withContext(Dispatchers.Default) { setupMode || AdminPin.verify(pin, vm.prefs.pinHash) }
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
    LaunchedEffect(tab) { if (tab == 2) vm.reloadPeers() }
    CareLinkScaffold(role, monitor, tab, role != DeviceRole.NONE && !addRoom,
        back = if (addRoom || detailId != null) ({ if (addRoom) { addRoom = false; scan = false } else detailId = null }) else null,
        settings = if (role != DeviceRole.NONE && !addRoom) ({ protect("Ouvrir les réglages professionnels ?") { tab = 2; detailId = null } }) else null,
        navigate = { next -> if (next == 2) protect("Ouvrir les réglages professionnels ?") { tab = 2; detailId = null } else { tab = next; detailId = null } },
        snackbar = snackbar) { body ->
            val width = body.widthIn(max = 1280.dp).fillMaxSize()
            val form = body.widthIn(max = 720.dp).fillMaxSize()
            when {
                role == DeviceRole.NONE -> Welcome(body.widthIn(max = 1000.dp).fillMaxSize(), startRole)
                addRoom -> AddRoomScreen(form, monitor, scan,
                    startScan = { requestCamera { if (addRoom) scan = true } },
                    submit = { vm.importPairing(it); scan = false; addRoom = false },
                    error = { vm.message.value = it; scan = false },
                    cancel = { addRoom = false; scan = false })
                tab == 2 -> SettingsPanel(form, vm, role, codeProtectionEnabled, monitor.notificationWarnings, peers,
                    stop = { protect("Arrêter la surveillance sur ce téléphone ?") { vm.stop(); tab = 0 } },
                    enableLock = {
                        protectedAction = { vm.setCodeProtection(true); vm.message.value = "Protection par code activée" }
                        confirmLabel = "Activer la protection des réglages ?"
                        setupPin = vm.prefs.pinHash.isBlank(); pinDialog = true
                    },
                    disableLock = { protect("Désactiver la protection des réglages ?") { vm.setCodeProtection(false); vm.message.value = "Protection désactivée" } },
                    test = { protect("Envoyer un événement de test ?") { vm.test() } },
                    pair = { protect("Ouvrir un appairage à usage unique pendant deux minutes ?") { vm.newPairing() } },
                    revoke = { id -> protect("Révoquer l’accès de ce veilleur ?") { vm.revoke(id) } })
                role == DeviceRole.ROOM_UNIT -> RoomHomePanel(width, room, vm.prefs.roomName,
                    resume = { startRole(DeviceRole.ROOM_UNIT) },
                    settings = { protect("Ouvrir les réglages professionnels ?") { tab = 2 } },
                    pair = { protect("Appairer un poste veilleur ?") { vm.newPairing() } })
                tab == 1 -> EventList(width, monitor.history, monitor.pendingActions, vm::eventActions)
                selected != null -> RoomDetailPanel(width, selected, monitor,
                    listen = { vm.listen(selected.id) }, talk = { talkId = selected.id },
                    reconnect = { vm.reconnect(selected.id) }, eventAction = vm::eventActions,
                    threshold = { value -> protect("Appliquer un seuil relatif de $value ?") { vm.remoteThreshold(selected.id, value) } },
                    updateAddress = { address -> protect("Utiliser cette adresse réseau pour la chambre ?") { vm.updateAddress(selected.id, address) } },
                    pair = { protect("Reprendre l’appairage avec un nouveau QR ?") { addRoom = true } },
                    forget = { protect("Retirer cette chambre des appareils attendus ? Ses événements seront conservés.") { vm.forget(selected.id); detailId = null } })
                else -> MonitorDashboard(width, monitor, add = { protect("Appairer une nouvelle chambre ?") { addRoom = true } },
                    select = { detailId = it }, eventAction = vm::eventActions, resume = { startRole(DeviceRole.MONITOR_UNIT) })
            }
    }
    if (confirmLabel != null && !pinDialog) AlertDialog(onDismissRequest = { confirmLabel = null; protectedAction = null },
        icon = { Icon(Icons.Default.Shield, null, tint = Amber) }, title = { Text("Action professionnelle") },
        text = { Text(confirmLabel!!) }, confirmButton = { TextButton(onClick = {
            val action = protectedAction; confirmLabel = null; protectedAction = null; action?.invoke()
        }) { Text("Confirmer") } }, dismissButton = { TextButton(onClick = { confirmLabel = null; protectedAction = null }) { Text("Annuler") } })
    if (code != null) AlertDialog(onDismissRequest = vm::cancelPairing, title = { Text("Appairer ce téléphone") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sur le veilleur : Ajouter une chambre → Scanner. Faites cette opération en présence des deux téléphones.")
            val bitmap = remember(code) { pairingBitmap(code!!) }
            Image(bitmap.asImageBitmap(), "QR d’appairage confidentiel", Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)))
            Text("Usage unique · expire dans $seconds s", color = Amber, fontWeight = FontWeight.Bold)
            TextButton(onClick = {
                val clip = ClipData.newPlainText("Appairage CareLink", code)
                if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            }) { Text("Copier le code à la place") }
        } }, confirmButton = { TextButton(onClick = vm::cancelPairing) { Text("Fermer et annuler le code") } })
    if (talkId != null) {
        val id = talkId!!
        var holding by remember { mutableStateOf(false) }
        DisposableEffect(id) { onDispose { vm.stopSpeaking() } }
        AlertDialog(onDismissRequest = { vm.stopSpeaking(); talkId = null }, title = { Text("Interphone") },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(monitor.monitoredRooms.find { it.id == id }?.name ?: "Chambre", style = MaterialTheme.typography.titleMedium)
                Text(if (monitor.isTransmittingVoice) "Microphone actif · transmission vers la chambre" else if (holding) "Connexion en cours…" else "Maintenez le bouton pour parler. Relâchez pour couper. Durée maximale : 30 secondes.", textAlign = TextAlign.Center)
                Box(Modifier.size(128.dp).clip(RoundedCornerShape(40.dp)).background(if (monitor.isTransmittingVoice) Mint else Panel)
                    .pointerInput(id) { detectTapGestures(onPress = {
                        holding = true; requestMicrophone { if (holding) vm.speak(id) }
                        try { tryAwaitRelease() } finally { holding = false; vm.stopSpeaking() }
                    }) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Mic, "Maintenir pour parler", tint = if (monitor.isTransmittingVoice) Ink else Mint, modifier = Modifier.size(48.dp))
                }
                Text("L’écoute distante est coupée pendant la parole. L’analyse de la chambre est suspendue pendant l’interphone.", style = MaterialTheme.typography.bodySmall, color = Amber)
            } }, confirmButton = { TextButton(onClick = { vm.stopSpeaking(); talkId = null }) { Text("Terminer") } })
    }
}

@Composable internal fun CareLinkScaffold(role: DeviceRole, monitor: MonitorMonitoringService.MonitorServiceState,
    tab: Int, showNavigation: Boolean, back: (() -> Unit)? = null, settings: (() -> Unit)? = null,
    navigate: (Int) -> Unit, snackbar: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = maxWidth >= 840.dp || (maxWidth >= 600.dp && maxHeight < 480.dp)
        val destinations = if (role == DeviceRole.MONITOR_UNIT) listOf(0, 1, 2) else listOf(0, 2)
        val labels = listOf("Surveillance", "Événements", "Réglages")
        val icons = listOf(Icons.Default.SpaceDashboard, Icons.Default.Notifications, Icons.Default.Tune)
        val count = AlertGroup.active(monitor.activeAlerts).size
        val healthy = monitor.monitoredRooms.count { it.isMicOperational && it.status in listOf(RoomStatus.CALM, RoomStatus.ACTIVITY, RoomStatus.ALERT) }
        val unchecked = AlertGroup.active(monitor.activeAlerts).count { !it.isAcknowledged }
        Scaffold(containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) }, topBar = {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (back != null) IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                        if (role == DeviceRole.MONITOR_UNIT && showNavigation) {
                            CompactMetric("Captures récentes", "$healthy/${monitor.monitoredRooms.size}", Icons.Default.Sensors, Mint,
                                "Captures récentes : $healthy sur ${monitor.monitoredRooms.size} chambres. Le microphone transmet des échantillons récents.")
                            CompactMetric("À vérifier", unchecked.toString(), Icons.Default.PriorityHigh, if (unchecked > 0) Rose else Quiet,
                                "À vérifier : $unchecked alerte(s) en attente de prise en charge. Touchez une chambre ou ouvrez les événements.")
                        }
                        Row(Modifier.weight(1f).testTag("header_title"), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CareLinkIcon()
                            Text("CareLink", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (settings != null) IconButton(settings) { Icon(Icons.Default.Settings, "Réglages et diagnostic") }
                    }
                }
            }, bottomBar = {
                if (showNavigation && !rail) NavigationBar(Modifier.testTag("navigation_bar"), containerColor = Panel) {
                    destinations.forEach { index -> NavigationBarItem(selected = tab == index, onClick = { navigate(index) },
                        icon = { if (index == 1) EventNavigationIcon(count) else Icon(icons[index], null) }, label = { Text(labels[index]) }) }
                }
            }) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (showNavigation && rail) {
                    NavigationRail(Modifier.fillMaxHeight().testTag("navigation_rail"), containerColor = Panel, windowInsets = WindowInsets(0, 0, 0, 0)) {
                        destinations.forEach { index -> NavigationRailItem(selected = tab == index, onClick = { navigate(index) },
                            icon = { if (index == 1) EventNavigationIcon(count) else Icon(icons[index], null) }, label = { Text(labels[index]) }) }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) { content(Modifier) }
            }
        }
    }
}

@Composable private fun AddRoomScreen(modifier: Modifier, state: MonitorMonitoringService.MonitorServiceState,
    scan: Boolean, startScan: () -> Unit, submit: (String) -> Unit, error: (String) -> Unit, cancel: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val known = state.monitoredRooms.map { it.id }.toSet()
    val nearby = state.nearbyRooms.filterNot { it.id in known }
    Column(modifier.fillMaxHeight().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Ajouter une chambre", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Ouvrez un nouveau QR sur le téléphone chambre. Le QR autorise le premier accès ; un appareil détecté n’est pas encore appairé.", color = Quiet)
        if (scan) PairingCamera(Modifier.fillMaxWidth().height(300.dp), onCode = submit, onError = error)
        else Button(startScan, Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Icon(Icons.Default.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text("Scanner le QR")
        }
        OutlinedTextField(input, { input = it.take(12000) }, label = { Text("Ou coller le code CL2…") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
        OutlinedButton(onClick = { submit(input) }, enabled = input.startsWith("CL2."), modifier = Modifier.fillMaxWidth()) { Text("Appairer avec le code") }
        HorizontalDivider(color = Color(0xFF2D4059))
        Text("Chambres détectées sur le réseau", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(state.discoveryDiagnostic, style = MaterialTheme.typography.bodySmall, color = Quiet)
        if (nearby.isEmpty()) Text("Aucune nouvelle chambre détectée. Le scan QR fonctionne aussi sans découverte réseau.", style = MaterialTheme.typography.bodyMedium, color = Quiet)
        nearby.forEach { found -> Text("Chambre détectée · ${found.id.take(8)} · ${found.ipAddress}", color = Mint) }
        OutlinedButton(cancel, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Annuler") }
    }
}

@Composable private fun CareLinkIcon(dimension: androidx.compose.ui.unit.Dp = 28.dp) {
    val context = LocalContext.current
    val size = with(LocalDensity.current) { dimension.roundToPx() }
    val icon = remember(context, size) {
        context.applicationInfo.loadIcon(context.packageManager).toBitmap(size, size).asImageBitmap()
    }
    Image(icon, contentDescription = null, modifier = Modifier.size(dimension).clip(RoundedCornerShape(8.dp)))
}

private suspend fun delayForPin() = kotlinx.coroutines.delay(1500)

@Composable private fun Welcome(modifier: Modifier, start: (DeviceRole) -> Unit) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Choisissez le rôle de ce téléphone. Les chambres s’associent uniquement avec votre autorisation.", color = Quiet)
        FeatureCard("Poste veilleur", "Recevoir les événements, suivre les chambres attendues et échanger avec un résident.", Icons.Default.SpaceDashboard, Mint) { start(DeviceRole.MONITOR_UNIT) }
        FeatureCard("Téléphone chambre", "Capturer l’activité sonore et transmettre les événements aux veilleurs appairés.", Icons.Default.Sensors, Amber) { start(DeviceRole.ROOM_UNIT) }
    }
}
@Composable private fun FeatureCard(title: String, body: String, icon: ImageVector, tint: Color, action: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(32.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(body, color = Quiet)
            Button(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), colors = ButtonDefaults.buttonColors(containerColor = tint, contentColor = Ink)) { Text("Utiliser ce mode", fontWeight = FontWeight.Bold) }
        }
    }
}
@Composable private fun Note(text: String, warning: Boolean = false) {
    Surface(color = if (warning) Color(0xFF3E2D20) else Panel, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (warning) Icons.Default.WarningAmber else Icons.Default.Info, null, tint = if (warning) Amber else Mint)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = if (warning) Amber else Quiet)
        }
    }
}
@Composable internal fun EventNavigationIcon(count: Int) {
    if (count > 0) BadgedBox(badge = { Badge { Text(if (count > 99) "99+" else count.toString()) } }) {
        Icon(Icons.Default.Notifications, "Événements en cours : $count")
    } else Icon(Icons.Default.Notifications, null)
}

@Composable private fun TapInfo(label: String, explanation: String, content: @Composable (() -> Unit) -> Unit) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(explanation) } }, state = state, enableUserInput = false) {
        content { if (state.isVisible) state.dismiss() else scope.launch { state.show() } }
    }
}

@Composable private fun CompactMetric(label: String, value: String, icon: ImageVector, color: Color, explanation: String) {
    TapInfo(label, explanation) { show ->
        Surface(onClick = show, color = Panel, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.widthIn(min = 44.dp).heightIn(min = 48.dp).semantics { contentDescription = label }) {
            Column(Modifier.padding(horizontal = 5.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                Text(value, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable internal fun MonitorDashboard(modifier: Modifier, state: MonitorMonitoringService.MonitorServiceState,
    add: () -> Unit, select: (String) -> Unit, eventAction: (List<RoomAlertEvent>, Boolean) -> Unit, resume: () -> Unit) {
    LazyVerticalGrid(columns = GridCells.Adaptive(300.dp), modifier = modifier,
        contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!state.isRunning) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Note("Service arrêté. Reprenez la surveillance et vérifiez les autorisations.", true)
                Button(resume) { Text("Reprendre") }
            }
        }
        if (state.notificationWarnings.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Note(state.notificationWarnings.joinToString("\n"), true) }
        if (state.monitoredRooms.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Note("Aucune chambre appairée. Ajoutez les téléphones attendus avant votre prise de poste.") }
        gridItems(state.monitoredRooms.sortedWith(compareBy<RoomUnitState> { room ->
            when {
                state.activeAlerts.any { it.roomId == room.id && !it.isAcknowledged } -> 0
                room.status in listOf(RoomStatus.DISCONNECTED, RoomStatus.MIC_ERROR) -> 1
                state.activeAlerts.any { it.roomId == room.id } -> 2
                else -> 3
            }
        }.thenBy { it.name }), key = { it.id }) { room ->
            CompactRoomCard(room, state.activeAlerts.filter { it.roomId == room.id }, state.pendingActions,
                open = { select(room.id) }, action = eventAction)
        }
        item(span = { GridItemSpan(maxLineSpan) }) { OutlinedButton(add, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Ajouter une chambre") } }
    }
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
    val color = if (alarm) Rose else if (unresolved.isNotEmpty() || room.enrollmentPending) Amber else room.status.tint()
    val status = when {
        room.enrollmentPending -> "Appairage à terminer"
        room.status == RoomStatus.DISCONNECTED -> "Hors ligne"
        room.status == RoomStatus.MIC_ERROR -> "Microphone indisponible"
        waiting -> "Confirmation en attente"
        unchecked.isNotEmpty() -> "À vérifier"
        unresolved.isNotEmpty() -> "Pris en charge"
        room.status == RoomStatus.CALM -> "Calme"
        room.status == RoomStatus.ACTIVITY -> "Activité sonore"
        room.status == RoomStatus.ALERT -> "Seuil dépassé"
        room.status == RoomStatus.DEGRADED -> "Mode dégradé"
        else -> "Vérification en cours"
    }
    val tooltip = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(roomInformation(room, unresolved)) } }, state = tooltip, enableUserInput = false) {
        Card(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).combinedClickable(role = Role.Button,
            onClickLabel = "Ouvrir ${room.name}", onClick = open, onLongClickLabel = "Informations de ${room.name}",
            onLongClick = { scope.launch { tooltip.show() } }), shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
            colors = CardDefaults.cardColors(containerColor = if (alarm) Color(0xFF3B2635) else if (unresolved.isNotEmpty()) Color(0xFF332E26) else Panel)) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(room.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(status, color = color, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (targets.isNotEmpty()) IconButton(onClick = { action(targets.filterNot { it.eventId in pending }, unchecked.isEmpty()) },
                    enabled = !waiting, modifier = Modifier.size(48.dp)) {
                    Icon(if (waiting) Icons.Default.HourglassTop else if (unchecked.isNotEmpty()) Icons.Default.TaskAlt else Icons.Default.DoneAll,
                        if (waiting) "Confirmation en attente pour ${room.name}" else if (unchecked.isNotEmpty()) "Prendre en charge ${room.name}" else "Résoudre les alertes de ${room.name}", tint = color)
                }
            }
        }
    }
}
@Composable private fun Level(level: Int, threshold: Int, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (enabled) level.toString() else "—", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Column(horizontalAlignment = Alignment.End) {
                TapInfo("Niveau relatif", "Le niveau dépend du microphone et de son placement. CareLink n’effectue pas d’étalonnage en dB SPL. Réglez le seuil dans les réglages de la chambre après un essai sonore représentatif.") { show ->
                    TextButton(show) { Text("Niveau relatif"); Spacer(Modifier.width(6.dp)); Icon(Icons.Default.Info, "Informations sur le niveau sonore", Modifier.size(16.dp)) }
                }
                Text("Seuil $threshold", color = Quiet, style = MaterialTheme.typography.labelMedium)
            }
        }
        LinearProgressIndicator(progress = { if (enabled) level.coerceIn(0, 91) / 91f else 0f }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), color = if (level >= threshold) Rose else Mint, trackColor = Color(0xFF2D4059))
    }
}
@Composable private fun AdaptiveRoomPanels(modifier: Modifier, heading: @Composable () -> Unit, primary: @Composable ColumnScope.() -> Unit,
    secondary: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier) {
        if (maxWidth >= 760.dp) Column(Modifier.fillMaxSize().padding(20.dp).testTag("room_panels_split"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            heading()
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).testTag("room_primary"), verticalArrangement = Arrangement.spacedBy(16.dp), content = primary)
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).testTag("room_secondary"), verticalArrangement = Arrangement.spacedBy(16.dp), content = secondary)
            }
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("room_panels_single"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            heading(); primary(); secondary()
        }
    }
}

@Composable private fun DisclosurePanel(title: String, expanded: Boolean, toggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (expanded) "Replier" else "Déplier", onClick = toggle)
            .heightIn(min = 56.dp).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Replier $title" else "Déplier $title", tint = Quiet)
        }
        if (expanded) Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun RoomHeading(name: String, status: String, color: Color) {
    FlowRow(Modifier.fillMaxWidth().testTag("room_heading"), horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, Modifier.align(Alignment.CenterVertically).testTag("room_title"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp), modifier = Modifier.align(Alignment.CenterVertically).testTag("room_status")) {
            Text(status, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = color, style = MaterialTheme.typography.bodyMedium)
        }
    }
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

@Composable internal fun RoomHomePanel(modifier: Modifier, state: RoomMonitoringService.RoomServiceState, fallbackName: String,
    resume: () -> Unit, settings: () -> Unit, pair: () -> Unit) {
    var information by remember { mutableStateOf(false) }
    AdaptiveRoomPanels(modifier, heading = {
        RoomHeading(state.roomName.ifBlank { fallbackName }, if (!state.isRunning) "Surveillance arrêtée" else state.status.shortLabel(), if (state.isRunning) state.status.tint() else Rose)
    }, primary = {
        Card(Modifier.fillMaxWidth().testTag("room_level"), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Level(state.currentDecibels, state.thresholdDb, state.isMicOperational)
                if (!state.isRunning) Button(resume, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Reprendre la surveillance") }
                else if (state.errorMessage != null) TapInfo("Incident de capture", state.errorMessage) { show ->
                    TextButton(show) { Icon(Icons.Default.WarningAmber, null, tint = Rose); Spacer(Modifier.width(8.dp)); Text("Vérifier le microphone", color = Rose) }
                }
                if (state.isCaregiverTalking) Text("Interphone actif", color = Amber)
            }
        }
    }, secondary = {
        Card(Modifier.fillMaxWidth().testTag("room_monitors"), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("${state.connectedMonitors} veilleur(s) connecté(s)", style = MaterialTheme.typography.titleMedium, color = if (state.connectedMonitors > 0) Mint else Amber)
                OutlinedButton(pair, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = state.isRunning) {
                    Icon(Icons.Default.QrCode2, null); Spacer(Modifier.width(8.dp)); Text("Appairer un veilleur")
                }
            }
        }
        OutlinedButton(settings, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Tune, null); Spacer(Modifier.width(8.dp)); Text("Régler la sensibilité") }
        DisclosurePanel("Informations de l’appareil", information, { information = !information }) {
            Text("Adresse Wi-Fi : ${state.localAddress.ifBlank { "indisponible" }}", color = Quiet)
            Text("Échantillons : ${state.samples}", color = Quiet)
            Text("Dernière capture : ${if (state.captureAgeMs == Long.MAX_VALUE) "aucune" else "${state.captureAgeMs / 1000} s"}", color = Quiet)
            state.connectionDiagnostic?.let { Text(it, color = Amber) }
            state.errorMessage?.let { Text(it, color = Rose) }
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
    AdaptiveRoomPanels(modifier, heading = {
        RoomHeading(room.name, if (room.enrollmentPending) "Appairage à terminer" else room.status.shortLabel(), room.status.tint())
    }, primary = {
        Card(Modifier.fillMaxWidth().testTag("room_level"), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Level(room.currentDecibels, room.noiseThresholdDb, room.isMicOperational)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(listen, Modifier.weight(1f).heightIn(min = 48.dp), enabled = room.isMicOperational || state.activeListeningRoomId == room.id) { Text(if (state.activeListeningRoomId == room.id) "Couper l’écoute" else "Écouter") }
                    OutlinedButton(talk, Modifier.weight(1f).heightIn(min = 48.dp), enabled = !room.enrollmentPending && room.status != RoomStatus.DISCONNECTED && room.status != RoomStatus.VERIFYING) { Text("Interphone") }
                }
            }
        }
        groups.forEach { GroupedEventCard(it, state.pendingActions, eventAction, showRoomName = false) }
    }, secondary = {
        DisclosurePanel("Sensibilité", sensitivity, { sensitivity = !sensitivity }) {
            Text("Seuil relatif : ${value.toInt()}", color = Mint)
            Slider(value, { value = it }, valueRange = 35f..85f)
            OutlinedButton(onClick = { threshold(value.toInt()) }, enabled = !room.enrollmentPending && room.status !in listOf(RoomStatus.DISCONNECTED, RoomStatus.VERIFYING) && value.toInt() != room.noiseThresholdDb,
                modifier = Modifier.fillMaxWidth()) { Text("Appliquer le réglage") }
        }
        DisclosurePanel("Informations de l’appareil", information, { information = !information }) {
            Text("Batterie : ${if (room.batteryPercent >= 0) "${room.batteryPercent}%" else "inconnue"}${if (room.isCharging) " · en charge" else ""}", color = Quiet)
            room.temperatureC?.let { Text("Température : ${"%.1f".format(Locale.FRANCE, it)} °C", color = Quiet) }
            Text("Dernière donnée : ${if (room.lastSeenTimestamp > 0) clock(room.lastSeenTimestamp) else "aucune"}", color = Quiet)
            Text("Échantillons : ${room.samplesCaptured}", color = Quiet)
            if (room.diagnostic.isNotBlank()) Text(room.diagnostic, color = if (room.isMicOperational) Quiet else Amber)
        }
        DisclosurePanel("Connexion", connection, { connection = !connection }) {
            Text(room.ipAddress.ifBlank { "Adresse inconnue" }, color = Quiet)
            if (room.enrollmentPending) {
                Text("Accès en attente de confirmation par la chambre.", color = Amber)
                OutlinedButton(pair, Modifier.fillMaxWidth()) { Text("Scanner un nouveau QR") }
            }
            if (needsConnection && room.diagnostic.isNotBlank()) Text(room.diagnostic, color = Amber)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(reconnect) { Text("Réessayer") }
                TextButton({ editAddress = !editAddress }) { Text("Modifier l’IP") }
            }
            if (editAddress) {
                OutlinedTextField(address, { address = it.take(80) }, label = { Text("IP du téléphone chambre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { updateAddress(address); editAddress = false }, modifier = Modifier.fillMaxWidth()) { Text("Utiliser cette adresse") }
            }
            TextButton(forget) { Text("Retirer cette chambre", color = Rose) }
        }
    })
}
@Composable private fun EventList(modifier: Modifier, events: List<RoomAlertEvent>, pending: Set<String>, action: (List<RoomAlertEvent>, Boolean) -> Unit) {
    var history by remember { mutableStateOf(false) }
    val groups = AlertGroup.active(events)
    val resolved = events.filter { it.resolved }.sortedByDescending { it.timestamp }
    LazyColumn(modifier, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Événements", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { FilterChip(!history, { history = false }, label = { Text("En cours") }); FilterChip(history, { history = true }, label = { Text("Résolus · 7 jours") }) } }
        if (history) {
            if (resolved.isEmpty()) item { Note("Aucun événement résolu dans l’historique conservé.") }
            items(resolved, key = { it.eventId }) { EventCard(it, { event, resolve -> action(listOf(event), resolve) }, it.eventId in pending) }
        } else {
            if (groups.isEmpty()) item { Note("Aucune alerte en cours.") }
            items(groups, key = { it.key }) { GroupedEventCard(it, pending, action) }
        }
    }
}

@Composable private fun GroupedEventCard(group: AlertGroup, pending: Set<String>, action: (List<RoomAlertEvent>, Boolean) -> Unit, showRoomName: Boolean = true) {
    var resolving by remember { mutableStateOf<List<RoomAlertEvent>?>(null) }
    val unchecked = group.events.filterNot { it.isAcknowledged }
    val targets = if (unchecked.isNotEmpty()) unchecked else group.events
    val waiting = targets.all { it.eventId in pending }
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = if (group.isAcknowledged) Panel else Color(0xFF3B2635))) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showRoomName) Text(group.roomName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(group.eventType.label(), color = if (group.isAcknowledged) Mint else Amber)
            Text(if (group.occurrences > 1) "${group.occurrences} occurrences · dernière ${clock(group.lastTimestamp)}" else clock(group.lastTimestamp), color = Quiet, style = MaterialTheme.typography.bodySmall)
            if (group.isAcknowledged) Text("Pris en charge", color = Mint)
            Button(onClick = { if (unchecked.isNotEmpty()) action(targets.filterNot { it.eventId in pending }, false) else resolving = group.events.toList() },
                enabled = !waiting, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (waiting) "Confirmation en attente…" else if (unchecked.isNotEmpty()) "Prendre en charge" else "Marquer comme résolu")
            }
        }
    }
    resolving?.let { snapshot ->
        AlertDialog(onDismissRequest = { resolving = null }, title = { Text("Résoudre cette alerte ?") },
            text = { Text("Clôturer les ${snapshot.size} occurrence(s) de cette alerte ?") },
            confirmButton = { TextButton(onClick = { resolving = null; action(snapshot, true) }) { Text("Confirmer la résolution") } },
            dismissButton = { TextButton(onClick = { resolving = null }) { Text("Annuler") } })
    }
}
@Composable private fun EventCard(event: RoomAlertEvent, action: (RoomAlertEvent, Boolean) -> Unit, pending: Boolean = false) {
    var resolve by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = if (event.isAcknowledged) Panel else Color(0xFF3B2635))) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(event.roomName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(event.eventType.label(), color = if (event.isAcknowledged) Mint else Amber, fontWeight = FontWeight.SemiBold)
            Text(clock(event.timestamp), color = Quiet, style = MaterialTheme.typography.labelMedium)
            Text(event.notes)
            if (event.eventType == AlertEventType.SOUND_ALERT) Text("Pic relatif : ${event.levelReachedDb}" +
                if (event.durationSeconds > 0) " · épisode ${event.durationSeconds} s" else " · durée non finalisée", color = Quiet, style = MaterialTheme.typography.bodySmall)
            if (event.isAcknowledged) Text("Pris en charge par ${event.acknowledgedBy.takeLast(12)}", color = Mint, style = MaterialTheme.typography.bodySmall)
            if (!event.resolved) {
                if (!event.isAcknowledged) Button(onClick = { action(event, false) }, enabled = !pending, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (pending) "Confirmation en attente…" else "Prendre en charge") }
                else OutlinedButton(onClick = { resolve = true }, enabled = !pending, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (pending) "Confirmation en attente…" else "Marquer comme résolu") }
                if (event.remote) Text("L’action est conservée et confirmée par la chambre, même après une coupure.", color = Quiet, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (resolve) AlertDialog(onDismissRequest = { resolve = false }, title = { Text("Résoudre cet événement ?") }, text = { Text("Vérifiez la situation et l’état de surveillance avant de clôturer. Cette action restera dans l’historique.") },
        confirmButton = { TextButton(onClick = { resolve = false; action(event, true) }) { Text("Confirmer la résolution") } }, dismissButton = { TextButton(onClick = { resolve = false }) { Text("Annuler") } })
}
@Composable private fun SettingsPanel(modifier: Modifier, vm: MainViewModel, role: DeviceRole, codeProtectionEnabled: Boolean, warnings: List<String>, peers: List<String>, stop: () -> Unit,
    enableLock: () -> Unit, disableLock: () -> Unit, test: () -> Unit, pair: () -> Unit, revoke: (String) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(vm.prefs.roomName) }
    var threshold by remember { mutableFloatStateOf(vm.prefs.defaultSensitivityDb.toFloat()) }
    var duration by remember { mutableFloatStateOf(vm.prefs.minNoiseDurationMs.toFloat()) }
    var sound by remember { mutableStateOf(vm.prefs.isSoundAlertEnabled) }
    var repeat by remember { mutableStateOf(vm.prefs.isAlertRepeatEnabled) }
    var interval by remember { mutableFloatStateOf(vm.prefs.alertRepeatIntervalSeconds.toFloat()) }
    var label by remember { mutableStateOf(vm.prefs.deviceLabel) }
    // When code protection is enabled this page is guarded, and it is left on backgrounding.
    fun saveRoom() {
        vm.roomSettings(name, threshold.toInt(), duration.toInt())
        vm.message.value = "Réglages enregistrés"
    }
    LazyColumn(modifier, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("Réglages du poste", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text("Configuration et disponibilité", color = Quiet) }
        item { Note("Version ${BuildConfig.VERSION_NAME} · Utilisez la même version sur les chambres et les veilleurs.") }
        if (role == DeviceRole.ROOM_UNIT) {
            item { OutlinedTextField(name, { name = it.take(80) }, label = { Text("Repère de chambre — évitez le nom du résident") }, modifier = Modifier.fillMaxWidth()) }
            item { Text("Seuil relatif : ${threshold.toInt()}"); Slider(threshold, { threshold = it }, valueRange = 35f..85f); Text("Ce niveau n’est pas une mesure en dB SPL.", color = Quiet) }
            item { Text("Dépassement minimal : ${duration.toInt()} ms"); Slider(duration, { duration = it }, valueRange = 100f..3000f) }
            item { Button(::saveRoom, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Enregistrer les réglages de la chambre") } }
            item { OutlinedButton(pair, Modifier.fillMaxWidth()) { Text("Nouvel appairage veilleur") } }
            item { OutlinedButton(onClick = test, modifier = Modifier.fillMaxWidth()) { Text("Envoyer un événement de test") }; Text("Ne vérifie pas le microphone : effectuez aussi un essai acoustique réel.", color = Quiet, style = MaterialTheme.typography.bodySmall) }
            item { Text("Veilleurs autorisés", style = MaterialTheme.typography.titleMedium) }
            items(peers, key = { it }) { id -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Poste …${id.takeLast(8)}", Modifier.weight(1f)); TextButton(onClick = { revoke(id) }) { Text("Révoquer", color = Rose) } } }
        } else {
            item { OutlinedTextField(label, { label = it.take(80); vm.prefs.deviceLabel = label }, label = { Text("Nom de ce poste") }, modifier = Modifier.fillMaxWidth()) }
            item { Note(if (warnings.isEmpty()) "Contrôles système disponibles. Effectuez un essai sonore réel avant la prise de poste." else warnings.joinToString("\n"), warnings.isNotEmpty()) }
            item { OutlinedButton(onClick = { context.startActivity(if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) }, modifier = Modifier.fillMaxWidth()) { Text("Ouvrir les notifications Android") } }
            item { SettingSwitch("Signal sonore et vibrant CareLink", sound) { sound = it; vm.prefs.isSoundAlertEnabled = it } }
            item { Text("Le canal Android peut aussi être rendu silencieux. Ses sons et vibrations se règlent dans Android.", color = Quiet, style = MaterialTheme.typography.bodySmall) }
            item { SettingSwitch("Rappels jusqu’à prise en charge", repeat) { repeat = it; vm.prefs.isAlertRepeatEnabled = it } }
            if (repeat) item { Text("Rappel : ${interval.toInt()} secondes"); Slider(interval, { interval = it; vm.prefs.alertRepeatIntervalSeconds = it.toInt() }, valueRange = 10f..120f) }
        }
        item { HorizontalDivider(color = Color(0xFF2D4059)) }
        item { Text("Protection des manipulations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item { OutlinedButton(if (codeProtectionEnabled) disableLock else enableLock, Modifier.fillMaxWidth()) { Text(if (codeProtectionEnabled) "Désactiver la protection par code" else "Protéger les actions par un code") } }
        item { Text(if (codeProtectionEnabled) "Code activé pour les réglages et les actions sensibles." else "Code désactivé : les actions sont confirmées sans saisie de code. Le QR reste nécessaire pour autoriser un téléphone.", color = Quiet, style = MaterialTheme.typography.bodySmall) }
        item { Note("Après un redémarrage ou un arrêt forcé, ouvrez CareLink pour reprendre et contrôler la surveillance. Les événements non résolus sont conservés ; seuls les événements résolus expirent après 7 jours.") }
        item { OutlinedButton(stop, Modifier.fillMaxWidth().heightIn(min = 52.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose)) { Text("Arrêter et changer de rôle") } }
        item { Text("En complément des procédures humaines. La disponibilité du Wi-Fi, le placement du téléphone et l’audibilité des alertes doivent être vérifiés sur site.", color = Quiet, style = MaterialTheme.typography.bodySmall) }
    }

}
@Composable private fun SettingSwitch(label: String, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(enabled, change) }
}

@Composable private fun ProfessionalCodeScreen(setup: Boolean, action: String?, cancel: () -> Unit, verify: suspend (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val mask = remember { PasswordVisualTransformation() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Icon(Icons.Default.Lock, null, tint = Mint, modifier = Modifier.size(36.dp))
                Text(if (setup) "Protéger les réglages" else "Accès professionnel", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                action?.let { Text(it, color = Amber) }
                Text(if (setup) "Choisissez un code d’au moins 6 chiffres. Conservez-le dans la procédure du service." else "Saisissez le code professionnel pour confirmer.", color = Quiet)
                OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(12) }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Code professionnel") }, singleLine = true, enabled = !busy, visualTransformation = mask,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword))
                if (setup) OutlinedTextField(repeat, { repeat = it.filter(Char::isDigit).take(12) }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirmer le code") }, singleLine = true, enabled = !busy, visualTransformation = mask,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword))
                error?.let { Text(it, color = Rose) }
                Button(onClick = {
                    if (pin.length < 6 || (setup && pin != repeat)) { error = "Au moins 6 chiffres et une confirmation identique sont nécessaires" }
                    else {
                        busy = true
                        scope.launch {
                            try { if (!verify(pin)) { error = "Code incorrect"; delayForPin() } }
                            catch (_: Exception) { error = "Impossible de vérifier ou enregistrer le code" }
                            finally { busy = false }
                        }
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (busy) "Vérification…" else "Valider")
                }
                TextButton(onClick = cancel, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Annuler") }
            }
        }
    }
}
