@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
import androidx.core.graphics.drawable.toBitmap
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
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    CareLinkIcon()
                    Text("CareLink", fontWeight = FontWeight.Bold)
                }
            }, navigationIcon = {
                if (addRoom || detailId != null) IconButton(onClick = { if (addRoom) { addRoom = false; scan = false } else detailId = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            }, actions = {
                if (role != DeviceRole.NONE && !addRoom) IconButton(onClick = { protect("Ouvrir les réglages professionnels ?") { tab = 2; detailId = null } }) { Icon(Icons.Default.Tune, "Réglages et diagnostic") }
            })
        },
        bottomBar = {
            if (role != DeviceRole.NONE && !addRoom) NavigationBar(containerColor = Panel) {
                listOf("Surveillance" to Icons.Default.SpaceDashboard, "Événements" to Icons.Default.Notifications,
                    "Réglages" to Icons.Default.Tune).forEachIndexed { i, entry ->
                    NavigationBarItem(selected = tab == i, onClick = { if (i == 2) protect("Ouvrir les réglages professionnels ?") { tab = 2; detailId = null } else { tab = i; detailId = null } },
                        icon = { if (i == 1 && monitor.activeAlerts.any { !it.isAcknowledged }) BadgedBox(badge = { Badge { Text(monitor.activeAlerts.count { !it.isAcknowledged }.toString()) } }) { Icon(entry.second, null) }
                            else Icon(entry.second, null) }, label = { Text(entry.first) })
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val width = Modifier.widthIn(max = 900.dp).fillMaxWidth()
            when {
                role == DeviceRole.NONE -> Welcome(width, startRole)
                addRoom -> AddRoomScreen(width, monitor, scan,
                    startScan = { requestCamera { if (addRoom) scan = true } },
                    submit = { vm.importPairing(it); scan = false; addRoom = false },
                    error = { vm.message.value = it; scan = false },
                    cancel = { addRoom = false; scan = false })
                tab == 2 -> SettingsPanel(width, vm, role, codeProtectionEnabled, monitor.notificationWarnings, peers,
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
                role == DeviceRole.ROOM_UNIT -> RoomHome(width, room, vm,
                    resume = { startRole(DeviceRole.ROOM_UNIT) },
                    pair = { protect("Appairer un poste veilleur ?") { vm.newPairing() } })
                tab == 1 -> EventList(width, monitor.history, monitor.pendingActions, vm::eventAction)
                selected != null -> RoomDetail(width, selected, monitor, vm,
                    listen = { vm.listen(selected.id) }, talk = { talkId = selected.id },
                    threshold = { value -> protect("Appliquer un seuil relatif de $value ?") { vm.remoteThreshold(selected.id, value) } },
                    updateAddress = { address -> protect("Utiliser cette adresse réseau pour la chambre ?") { vm.updateAddress(selected.id, address) } },
                    pair = { protect("Reprendre l’appairage avec un nouveau QR ?") { addRoom = true } },
                    forget = { protect("Retirer cette chambre des appareils attendus ? Ses événements seront conservés.") { vm.forget(selected.id); detailId = null } })
                else -> Dashboard(width, monitor, add = { protect("Appairer une nouvelle chambre ?") { addRoom = true } },
                    select = { detailId = it }, events = { tab = 1 }, resume = { startRole(DeviceRole.MONITOR_UNIT) })
            }
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

@Composable private fun CareLinkIcon() {
    val context = LocalContext.current
    val size = with(LocalDensity.current) { 36.dp.roundToPx() }
    val icon = remember(context, size) {
        context.applicationInfo.loadIcon(context.packageManager).toBitmap(size, size).asImageBitmap()
    }
    Image(icon, contentDescription = null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
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
@Composable private fun Dashboard(modifier: Modifier, state: MonitorMonitoringService.MonitorServiceState, add: () -> Unit, select: (String) -> Unit, events: () -> Unit, resume: () -> Unit) {
    val healthy = state.monitoredRooms.count { it.isMicOperational && it.status in listOf(RoomStatus.CALM, RoomStatus.ACTIVITY, RoomStatus.ALERT) }
    LazyColumn(modifier, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Votre veille", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text("Chambres attendues · connexions sécurisées", color = Quiet) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("Captures récentes", "$healthy / ${state.monitoredRooms.size}", Mint, Modifier.weight(1f))
                Metric("À vérifier", state.activeAlerts.count { !it.isAcknowledged }.toString(), Amber, Modifier.weight(1f))
            }
        }
        if (!state.isRunning) item { Note("Service arrêté. Reprenez la surveillance et vérifiez les autorisations.", true); Button(resume) { Text("Reprendre") } }
        if (state.notificationWarnings.isNotEmpty()) item { Note(state.notificationWarnings.joinToString("\n"), true) }
        if (state.activeAlerts.isNotEmpty()) item {
            Card(onClick = events, colors = CardDefaults.cardColors(containerColor = Color(0xFF442736)), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.NotificationsActive, null, tint = Rose)
                    Column(Modifier.weight(1f)) { Text("${state.activeAlerts.size} événement(s) en cours", fontWeight = FontWeight.Bold); Text("Consulter et prendre en charge", color = Quiet) }
                    Icon(Icons.Default.ChevronRight, "Ouvrir les événements")
                }
            }
        }
        if (state.monitoredRooms.isEmpty()) item { Note("Aucune chambre appairée. Ajoutez les téléphones attendus avant votre prise de poste.") }
        items(state.monitoredRooms.sortedBy { when(it.status) { RoomStatus.DISCONNECTED, RoomStatus.MIC_ERROR -> 0; RoomStatus.ALERT -> 1; else -> 2 } }, key = { it.id }) { room ->
            Card(onClick = { select(room.id) }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(room.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, "Détails de la chambre")
                    }
                    Text(if (room.enrollmentPending) "Appairage à terminer" else room.status.label(), color = room.status.tint(), fontWeight = FontWeight.SemiBold)
                    if (room.diagnostic.isNotBlank()) Text(room.diagnostic, color = Quiet, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text(if (room.lastSeenTimestamp == 0L) "Aucune donnée validée" else "Dernière donnée : ${clock(room.lastSeenTimestamp)}", style = MaterialTheme.typography.labelMedium, color = Quiet)
                    }
                    if (room.batteryPercent >= 0) Text("Batterie ${room.batteryPercent}%${if (room.isCharging) " · en charge" else ""}", style = MaterialTheme.typography.labelMedium, color = Quiet)
                }
            }
        }
        item { OutlinedButton(add, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Ajouter une chambre") } }
        item { Note("Une capture récente ne garantit pas que le microphone est bien placé. Effectuez le test acoustique prévu à la prise de poste.") }
    }
}
@Composable private fun Metric(label: String, value: String, tint: Color, modifier: Modifier) {
    Surface(modifier, color = Panel, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = tint, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = Quiet)
        }
    }
}
@Composable private fun Level(level: Int, threshold: Int, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (enabled) level.toString() else "—", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Column(horizontalAlignment = Alignment.End) { Text("Niveau relatif", fontWeight = FontWeight.Medium); Text("Seuil $threshold · non calibré", color = Quiet, style = MaterialTheme.typography.labelMedium) }
        }
        LinearProgressIndicator(progress = { if (enabled) level.coerceIn(0, 91) / 91f else 0f }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), color = if (level >= threshold) Rose else Mint, trackColor = Color(0xFF2D4059))
    }
}
@Composable private fun RoomHome(modifier: Modifier, state: RoomMonitoringService.RoomServiceState, vm: MainViewModel, resume: () -> Unit, pair: () -> Unit) {
    LazyColumn(modifier, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text(state.roomName.ifBlank { vm.prefs.roomName }, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text("Téléphone chambre", color = Quiet) }
        item { Note(if (!state.isRunning) "Surveillance arrêtée : aucune capture vérifiée." else state.errorMessage ?: "${state.connectedMonitors} veilleur(s) connecté(s) · ${state.status.label()}", !state.isRunning || state.errorMessage != null || state.connectedMonitors == 0) }
        if (!state.isRunning) item { Button(resume, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Autoriser et reprendre") } }
        item { Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(24.dp)) { Level(state.currentDecibels, state.thresholdDb, state.isMicOperational) } } }
        item { Text("Le son reste sur le réseau local. Aucun enregistrement audio n’est conservé.", color = Quiet) }
        item { Note("Adresse Wi-Fi : ${state.localAddress.ifBlank { "indisponible" }} · port 9877\nLa découverte utilise mDNS et UDP. Sur un réseau invité ou isolé, l’équipe informatique doit autoriser les communications entre téléphones.") }
        state.connectionDiagnostic?.let { error -> item { Note(error, true) } }
        item { OutlinedButton(pair, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = state.isRunning) { Icon(Icons.Default.QrCode2, null); Spacer(Modifier.width(8.dp)); Text("Appairer un veilleur") } }
        item { Note("Pour modifier la chambre, le seuil ou tester la transmission, ouvrez les réglages professionnels. Le bouton Retour et l’extinction de l’écran n’arrêtent pas volontairement le service.") }
    }
}
@Composable private fun RoomDetail(modifier: Modifier, room: RoomUnitState, state: MonitorMonitoringService.MonitorServiceState, vm: MainViewModel,
    listen: () -> Unit, talk: () -> Unit, threshold: (Int) -> Unit, updateAddress: (String) -> Unit, pair: () -> Unit, forget: () -> Unit) {
    var value by remember(room.noiseThresholdDb) { mutableFloatStateOf(room.noiseThresholdDb.toFloat()) }
    var editAddress by remember { mutableStateOf(false) }
    var address by remember(room.ipAddress) { mutableStateOf(room.ipAddress) }
    LazyColumn(modifier, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text(room.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text(room.status.label(), color = room.status.tint()) }
        if (room.diagnostic.isNotBlank()) item { Note(room.diagnostic, true) }
        if (room.enrollmentPending) item {
            Note("Le QR a été lu, mais la chambre n’a pas encore confirmé l’accès. La surveillance de cette chambre n’est pas active ; aucune alarme de coupure n’est déclenchée à cette étape.")
            OutlinedButton(pair, Modifier.fillMaxWidth()) { Text("Scanner un nouveau QR") }
        }
        item {
            Text("Connexion locale · ${room.ipAddress.ifBlank { "adresse inconnue" }}:9877", color = Quiet)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { vm.reconnect(room.id) }) { Text("Réessayer") }
                TextButton(onClick = { editAddress = !editAddress }) { Text("Corriger l’adresse") }
            }
            if (editAddress) {
                OutlinedTextField(address, { address = it.take(80) }, label = { Text("IP affichée sur le téléphone chambre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { updateAddress(address); editAddress = false }) { Text("Utiliser cette adresse") }
            }
        }
        item { Level(room.currentDecibels, room.noiseThresholdDb, room.isMicOperational) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(listen, Modifier.weight(1f).heightIn(min = 52.dp), enabled = room.isMicOperational || state.activeListeningRoomId == room.id) { Text(if (state.activeListeningRoomId == room.id) "Couper l’écoute" else "Écouter") }
                OutlinedButton(talk, Modifier.weight(1f).heightIn(min = 52.dp), enabled = room.status != RoomStatus.DISCONNECTED && room.status != RoomStatus.VERIFYING) { Text("Interphone") }
            }
        }
        item { Note("Dernière donnée validée : ${if (room.lastSeenTimestamp > 0) clock(room.lastSeenTimestamp) else "aucune"}\nÉchantillons : ${room.samplesCaptured}\nBatterie : ${room.batteryPercent.takeIf { it >= 0 } ?: "inconnue"}%${room.temperatureC?.let { " · $it °C" } ?: ""}") }
        item {
            Text("Seuil relatif", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Valeur confirmée : ${room.noiseThresholdDb} · nouveau réglage : ${value.toInt()}", color = Quiet)
            Slider(value, { value = it }, valueRange = 35f..85f)
            OutlinedButton(onClick = { threshold(value.toInt()) }, enabled = room.status != RoomStatus.DISCONNECTED) { Text("Appliquer le réglage") }
        }
        items(state.activeAlerts.filter { it.roomId == room.id }, key = { it.eventId }) { EventCard(it, vm::eventAction, it.eventId in state.pendingActions) }
        item { TextButton(forget) { Text("Retirer des chambres attendues", color = Rose) } }
    }
}
@Composable private fun EventList(modifier: Modifier, events: List<RoomAlertEvent>, pending: Set<String>, action: (RoomAlertEvent, Boolean) -> Unit) {
    var history by remember { mutableStateOf(false) }
    val displayed = events.filter { if (history) it.resolved else !it.resolved }.sortedWith(compareBy<RoomAlertEvent> { it.isAcknowledged }.thenByDescending { it.timestamp })
    LazyColumn(modifier, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Événements", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text("Chaque événement conserve son identité et son heure.", color = Quiet) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { FilterChip(!history, { history = false }, label = { Text("En cours") }); FilterChip(history, { history = true }, label = { Text("Résolus · 7 jours") }) } }
        if (displayed.isEmpty()) item { Note(if (history) "Aucun événement résolu dans l’historique conservé." else "Aucun événement en cours. Vérifiez également l’état des chambres attendues.") }
        if (!history && displayed.isNotEmpty()) item { Note("Prendre en charge coupe les rappels de cet événement. Une panne continue ne crée pas de nouvelle alerte après sa clôture ; son état reste affiché. Une connexion rétablie clôt automatiquement l’incident de coupure.") }
        items(displayed, key = { it.eventId }) { EventCard(it, action, it.eventId in pending) }
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
