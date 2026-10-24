package dev.gdk.bumblebee.ui.garage

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.R
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.garage.GarageFiles
import dev.gdk.bumblebee.ride.CareBand
import dev.gdk.bumblebee.ui.components.BlockColor
import dev.gdk.bumblebee.ui.components.BlockTile
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyIconButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.IconTile
import dev.gdk.bumblebee.ui.components.PillChip
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.ShapeSm
import dev.gdk.bumblebee.ui.theme.Stroke
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.TextDisabled
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import dev.gdk.bumblebee.ui.theme.Warning
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val DOC_KINDS = listOf("RC", "INSURANCE", "PUC", "WARRANTY", "INVOICE", "PERMIT", "OTHER")
private val PLAN_KINDS = listOf("SERVICE", "OIL", "CHAIN", "BRAKES", "TYRES", "BATTERY", "OTHER")
private val SERVICE_KINDS = listOf("OIL", "CHAIN", "BRAKES", "TYRES", "BATTERY", "SERVICE", "OTHER")

@Composable
fun GarageScreen(
    model: GarageViewModel = viewModel(),
    onOpenFuel: () -> Unit = {},
    onOpenTransfer: () -> Unit = {},
) {
    val state by model.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.all { it }) model.startScan()
    }
    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { model.pickDocFile(it) }
    val servicePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { model.pickServiceFile(it) }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.onTestSosPermission(granted)
    }

    fun requestThenScan() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (perms.isEmpty()) model.startScan()
        else permissionLauncher.launch(perms.toTypedArray())
    }

    GarageBody(
        state = state,
        onOpenFuel = onOpenFuel,
        onOpenTransfer = onOpenTransfer,
        onScan = { if (state.scanning) model.stopScan() else requestThenScan() },
        onConnect = model::connectSaved,
        onDisconnect = model::disconnectBike,
        onForget = model::forgetBike,
        onPair = model::pair,
        onRiderName = model::setRiderName,
        onSaveRiderName = model::saveRiderName,
        onDocKind = model::setDocKind,
        onDocTitle = model::setDocTitle,
        onDocNumber = model::setDocNumber,
        onDocExpiry = model::setDocExpiry,
        onDocNote = model::setDocNote,
        onPickDoc = { docPicker.launch("*/*") },
        onSaveDoc = model::saveDocument,
        onClearDoc = model::clearDocumentForm,
        onEditDoc = model::editDocument,
        onDeleteDoc = model::deleteDocument,
        onOpenFile = { path, mime ->
            GarageFiles.viewIntent(context, path, mime)?.let { context.startActivity(it) }
        },
        onPlanKind = model::setPlanKind,
        onPlanTitle = model::setPlanTitle,
        onPlanKm = model::setPlanKm,
        onPlanDays = model::setPlanDays,
        onPlanDueOn = model::setPlanDueOn,
        onPlanLastOdo = model::setPlanLastOdo,
        onPlanLastOn = model::setPlanLastOn,
        onSavePlan = model::savePlan,
        onClearPlan = model::clearPlanForm,
        onEditPlan = model::editPlan,
        onDeletePlan = model::deletePlan,
        onServiceKind = model::applyServiceKind,
        onServiceItem = model::setServiceItem,
        onServiceOdo = model::setServiceOdo,
        onServiceDate = model::setServiceDate,
        onServiceShop = model::setServiceShop,
        onServiceRupees = model::setServiceRupees,
        onServiceNote = model::setServiceNote,
        onServicePlan = model::setServicePlan,
        onPickBill = { servicePicker.launch("*/*") },
        onLogService = model::logServiceWork,
        onClearService = model::clearServiceForm,
        onEditService = model::editService,
        onDeleteService = model::deleteService,
        onReminders = model::setRemindersEnabled,
        onRemindDays = model::setRemindDays,
        onRemindKm = model::setRemindKm,
        onSaveReminders = model::saveReminders,
        onPause = model::setPauseWindow,
        onAutoConnect = model::setAutoConnect,
        onSkipAuto = model::skipAutoConnectToday,
        onSos = model::setSosContacts,
        onSensitivity = model::setCrashSensitivity,
        onBlood = model::setBloodGroup,
        onMedical = model::setMedicalNote,
        onSaveSafety = model::saveSafety,
        onTestSos = { smsLauncher.launch(Manifest.permission.SEND_SMS) },
        onCluster = model::setMapsNavToCluster,
        onAppPermissions = {
            val uri = Uri.fromParts("package", context.packageName, null)
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
        },
        onLocationSettings = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
        onNotificationAccess = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        onBatterySettings = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
    )
}

@Composable
private fun GarageBody(
    state: GarageUiState,
    onOpenFuel: () -> Unit,
    onOpenTransfer: () -> Unit,
    onScan: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
    onPair: (String) -> Unit,
    onRiderName: (String) -> Unit,
    onSaveRiderName: () -> Unit,
    onDocKind: (String) -> Unit,
    onDocTitle: (String) -> Unit,
    onDocNumber: (String) -> Unit,
    onDocExpiry: (String) -> Unit,
    onDocNote: (String) -> Unit,
    onPickDoc: () -> Unit,
    onSaveDoc: () -> Unit,
    onClearDoc: () -> Unit,
    onEditDoc: (DocCard) -> Unit,
    onDeleteDoc: (Long) -> Unit,
    onOpenFile: (String, String?) -> Unit,
    onPlanKind: (String) -> Unit,
    onPlanTitle: (String) -> Unit,
    onPlanKm: (String) -> Unit,
    onPlanDays: (String) -> Unit,
    onPlanDueOn: (String) -> Unit,
    onPlanLastOdo: (String) -> Unit,
    onPlanLastOn: (String) -> Unit,
    onSavePlan: () -> Unit,
    onClearPlan: () -> Unit,
    onEditPlan: (PlanCard) -> Unit,
    onDeletePlan: (Long) -> Unit,
    onServiceKind: (String) -> Unit,
    onServiceItem: (String) -> Unit,
    onServiceOdo: (String) -> Unit,
    onServiceDate: (String) -> Unit,
    onServiceShop: (String) -> Unit,
    onServiceRupees: (String) -> Unit,
    onServiceNote: (String) -> Unit,
    onServicePlan: (Long?) -> Unit,
    onPickBill: () -> Unit,
    onLogService: () -> Unit,
    onClearService: () -> Unit,
    onEditService: (ServiceCard) -> Unit,
    onDeleteService: (Long) -> Unit,
    onReminders: (Boolean) -> Unit,
    onRemindDays: (String) -> Unit,
    onRemindKm: (String) -> Unit,
    onSaveReminders: () -> Unit,
    onPause: (Int) -> Unit,
    onAutoConnect: (Boolean) -> Unit,
    onSkipAuto: () -> Unit,
    onSos: (String) -> Unit,
    onSensitivity: (String) -> Unit,
    onBlood: (String) -> Unit,
    onMedical: (String) -> Unit,
    onSaveSafety: () -> Unit,
    onTestSos: () -> Unit,
    onCluster: (Boolean) -> Unit,
    onAppPermissions: () -> Unit,
    onLocationSettings: () -> Unit,
    onNotificationAccess: () -> Unit,
    onBatterySettings: () -> Unit,
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var docsY by remember { mutableIntStateOf(0) }
    var plansY by remember { mutableIntStateOf(0) }
    var prefsY by remember { mutableIntStateOf(0) }
    var linkY by remember { mutableIntStateOf(0) }
    val linked = state.connection is ConnectionState.Ready
    val showCarousel = state.upcoming.isNotEmpty() || !linked

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("How is the motorcycle", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text(state.status, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

        SectionCard {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(ShapeSm)
                        .background(OnBlock),
                ) {
                    Image(
                        painter = painterResource(R.drawable.vstrom_hero),
                        contentDescription = "V-Strom 250 SX",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("V-Strom 250 SX", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        if (linked) "Connected" else "Not connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("MAC  ${state.bikeMac ?: "—"}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text("Odo  ${state.odometerKm?.let { "%,d km".format(it) } ?: "—"}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                Text("Trip A  ${state.tripAKm?.let { String.format("%.1f km", it) } ?: "—"}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                Text("Trip B  ${state.tripBKm?.let { String.format("%.1f km", it) } ?: "—"}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                Text("Fuel  ${state.fuelBars?.let { "$it bars" } ?: "—"}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            }
        }

        if (showCarousel) {
            if (state.upcoming.isNotEmpty()) {
                Text(
                    "Due soon and overdue, from papers and plans.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!linked) {
                    BlockTile(
                        title = "Pair the bike",
                        color = BlockColor.Mint,
                        onClick = { scope.launch { scroll.animateScrollTo(linkY) } },
                        modifier = Modifier.width(168.dp).height(120.dp),
                    )
                }
                state.upcoming.forEach { item ->
                    BlockTile(
                        title = item.label,
                        color = when (item.band) {
                            CareBand.OVERDUE -> BlockColor.Coral
                            CareBand.DUE_SOON -> BlockColor.Yellow
                            else -> BlockColor.Mint
                        },
                        onClick = { scope.launch { scroll.animateScrollTo(plansY) } },
                        modifier = Modifier.width(168.dp).height(120.dp),
                    )
                }
            }
        }

        SectionCard(
            header = {
                Text("BIKE", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            },
        ) {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(
                    listOf(
                        Tile("Fuel", Icons.Rounded.LocalGasStation, onOpenFuel),
                        Tile("Documents", Icons.Rounded.Description) { scope.launch { scroll.animateScrollTo(docsY) } },
                        Tile("Service", Icons.Rounded.Build) { scope.launch { scroll.animateScrollTo(plansY) } },
                        Tile("Prefs", Icons.Rounded.Settings) { scope.launch { scroll.animateScrollTo(prefsY) } },
                    ),
                    listOf(
                        Tile("Transfer", Icons.Rounded.SwapHoriz, onOpenTransfer),
                    ),
                ).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { tile ->
                            IconTile(icon = tile.icon, label = tile.label, onClick = tile.onClick)
                        }
                        repeat(4 - row.size) { Box(Modifier.width(64.dp)) }
                    }
                }
            }
        }

        SectionCard(
            Modifier.onGloballyPositioned { docsY = it.positionInParent().y.toInt() },
            header = { SectionLabel(text = "Documents") },
        ) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Keep RC, insurance, PUC, warranty, invoices. Expiry is optional — add it when the paper can expire, and a reminder fires in the window below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                KindChips(DOC_KINDS, state.docKind, onDocKind)
                GarageField(state.docTitle, onDocTitle, "Title")
                GarageField(state.docNumber, onDocNumber, "Number / policy (optional)")
                GarageField(state.docExpiry, onDocExpiry, "Expiry yyyy-MM-dd (optional)")
                GarageField(state.docNote, onDocNote, "Note")
                ChunkyButton(
                    text = if (state.docHasFile) "Replace file" else "Attach file",
                    onClick = onPickDoc,
                    style = ChunkyStyle.Outline,
                )
                ChunkyButton(
                    text = if (state.docId == null) "Save paper" else "Update paper",
                    onClick = onSaveDoc,
                    style = ChunkyStyle.Lime,
                )
                if (state.docId != null) {
                    ChunkyButton(text = "New paper", onClick = onClearDoc, style = ChunkyStyle.Outline)
                }
                state.documents.forEach { doc ->
                    CareCard(
                        kicker = doc.kind,
                        story = doc.label + (doc.number?.let { "  ·  $it" } ?: ""),
                        band = doc.band,
                        onEdit = { onEditDoc(doc) },
                        onDelete = { onDeleteDoc(doc.id) },
                        onOpen = doc.filePath?.let { path -> { onOpenFile(path, doc.mime) } },
                    )
                }
            }
        }

        SectionCard(
            Modifier.onGloballyPositioned { plansY = it.positionInParent().y.toInt() },
            header = { SectionLabel(text = "Maintenance plans") },
        ) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Interval in km, calendar days, or a due date — mix them. Tyres can be “October”, pads “8000 km”. Logging work rolls the matching plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                KindChips(PLAN_KINDS, state.planKind, onPlanKind)
                GarageField(state.planTitle, onPlanTitle, "Plan name")
                GarageField(state.planKm, onPlanKm, "Every km (optional)", KeyboardType.Number)
                GarageField(state.planDays, onPlanDays, "Every days (optional)", KeyboardType.Number)
                GarageField(state.planDueOn, onPlanDueOn, "Due on yyyy-MM-dd (optional)")
                GarageField(state.planLastOdo, onPlanLastOdo, "Last done odo", KeyboardType.Number)
                GarageField(state.planLastOn, onPlanLastOn, "Last done date")
                ChunkyButton(
                    text = if (state.planId == null) "Save plan" else "Update plan",
                    onClick = onSavePlan,
                    style = ChunkyStyle.Lime,
                )
                if (state.planId != null) {
                    ChunkyButton(text = "New plan", onClick = onClearPlan, style = ChunkyStyle.Outline)
                }
                state.plans.forEach { plan ->
                    CareCard(
                        kicker = plan.kind,
                        story = plan.label,
                        band = plan.band,
                        onEdit = { onEditPlan(plan) },
                        onDelete = { onDeletePlan(plan.id) },
                    )
                }
            }
        }

        SectionCard(header = { SectionLabel(text = "Service history") }) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Shop, rupees, date, and a bill photo. Pick a plan so the next due km/date moves when you log.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                KindChips(
                    SERVICE_KINDS,
                    selectedKind(state.serviceItem, state.plans, state.servicePlanId),
                    onServiceKind,
                )
                GarageField(state.serviceItem, onServiceItem, "Work done")
                GarageField(state.serviceOdo, onServiceOdo, "Odo km", KeyboardType.Number, state.odometerKm?.toString() ?: "")
                GarageField(state.serviceDate, onServiceDate, "Date yyyy-MM-dd")
                GarageField(state.serviceShop, onServiceShop, "Shop")
                GarageField(state.serviceRupees, onServiceRupees, "₹ paid", KeyboardType.Decimal)
                GarageField(state.serviceNote, onServiceNote, "Note")
                if (state.plans.isNotEmpty()) {
                    Text("Apply to plan", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    KindChips(
                        kinds = listOf("NONE") + state.plans.map { it.title },
                        selected = state.plans.firstOrNull { it.id == state.servicePlanId }?.title ?: "NONE",
                        onSelect = { label -> onServicePlan(state.plans.firstOrNull { it.title == label }?.id) },
                        raw = true,
                    )
                }
                ChunkyButton(text = "Bill", onClick = onPickBill, style = ChunkyStyle.Outline)
                ChunkyButton(
                    text = if (state.serviceId == null) "Log work" else "Update work",
                    onClick = onLogService,
                    style = ChunkyStyle.Lime,
                )
                if (state.serviceId != null) {
                    ChunkyButton(text = "New log", onClick = onClearService, style = ChunkyStyle.Outline)
                }
                state.runningRupeesPerKm?.let {
                    Text(
                        "Ownership ₹${"%.2f".format(it)}/km" + if (state.runningPartial) " (partial prices)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                state.serviceLogs.forEach { log ->
                    CareCard(
                        kicker = log.whenLabel,
                        story = log.story + (log.note?.let { "  ·  $it" } ?: ""),
                        band = CareBand.OK,
                        onEdit = { onEditService(log) },
                        onDelete = { onDeleteService(log.id) },
                        onOpen = log.filePath?.let { path -> { onOpenFile(path, null) } },
                    )
                }
            }
        }

        SectionCard(
            Modifier.onGloballyPositioned { prefsY = it.positionInParent().y.toInt() },
            header = { SectionLabel(text = "Reminders") },
        ) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Notify overdue and due-soon items",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = state.remindersEnabled, onCheckedChange = onReminders)
                }
                GarageField(state.remindDays, onRemindDays, "Days ahead", KeyboardType.Number)
                GarageField(state.remindKm, onRemindKm, "Km ahead", KeyboardType.Number)
                ChunkyButton(text = "Save reminder window", onClick = onSaveReminders, style = ChunkyStyle.Lime)
            }
        }

        SectionCard(header = { SectionLabel(text = "Ride and safety") }) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "A stop inside the pause window stays one ride. Crash alerts are a best-effort aid, not a certified emergency device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Text("Pause window", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Off", 15 to "15", 30 to "30", 45 to "45", 60 to "60", 120 to "120").forEach { (min, label) ->
                        PillChip(text = label, selected = state.pauseWindowMin == min, onClick = { onPause(min) })
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Auto-connect when I turn on the bike",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = state.autoConnect, onCheckedChange = onAutoConnect)
                }
                ChunkyButton(text = "Do not auto-connect today", onClick = onSkipAuto, style = ChunkyStyle.Outline)
                Text(
                    "On Xiaomi, Oppo and similar phones, allow Autostart and set battery to No restrictions, or the link dies with the screen off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                GarageField(state.sosContacts, onSos, "Emergency numbers, up to 3", singleLine = false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("LOW", "NORMAL", "HIGH").forEach { level ->
                        PillChip(
                            text = level,
                            selected = state.crashSensitivity == level,
                            onClick = { onSensitivity(level) },
                        )
                    }
                }
                GarageField(state.bloodGroup, onBlood, "Blood group")
                GarageField(state.medicalNote, onMedical, "Medical note", singleLine = false)
                ChunkyButton(text = "Save safety details", onClick = onSaveSafety, style = ChunkyStyle.Lime)
                ChunkyButton(text = "Test SOS", onClick = onTestSos, style = ChunkyStyle.Outline)
            }
        }

        SectionCard(
            Modifier.onGloballyPositioned { linkY = it.positionInParent().y.toInt() },
            header = { SectionLabel(text = "Link") },
        ) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GarageField(state.riderName, onRiderName, "Rider name")
                ChunkyButton(text = "Save name", onClick = onSaveRiderName, style = ChunkyStyle.Lime)
                ChunkyButton(
                    text = if (state.scanning) "Stop scan" else "Scan",
                    onClick = onScan,
                    style = ChunkyStyle.Lime,
                )
                ChunkyButton(text = "Connect", onClick = onConnect, enabled = state.bikeMac != null, style = ChunkyStyle.Outline)
                ChunkyButton(text = "Disconnect", onClick = onDisconnect, style = ChunkyStyle.Outline)
                ChunkyButton(text = "Forget", onClick = onForget, enabled = state.bikeMac != null, style = ChunkyStyle.Danger)
                state.devices.forEach { bike ->
                    val label = buildString {
                        append(bike.name ?: bike.mac)
                        if (bike.looksLikeSuzuki) append("  · Suzuki")
                        if (bike.bonded) append("  · bonded")
                        if (bike.rssi != Int.MIN_VALUE) append("  · ${bike.rssi} dBm")
                    }
                    Text(
                        text = label,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPair(bike.mac) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        }

        SectionCard(header = { SectionLabel(text = "Fuel") }) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    state.rangeKm?.let { "est. range  ${it.toInt()} km" } ?: "Range after bar drops on linked rides.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
                Text(
                    buildString {
                        append(state.tankKmPerL?.let { "est. ${"%.1f".format(it)} km/L" } ?: "Tank km/L after two fills")
                        append("  ·  ")
                        append(state.rupeesPerKm?.let { "₹${"%.2f".format(it)}/km" } ?: "₹/km after priced fills")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }

        SectionCard(header = { SectionLabel(text = "Cluster nav") }) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Turn arrows on the bike come from Google Maps notifications, not a Maps API. Leave this on while riding with Maps in the background.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Maps notifications → cluster",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = state.mapsNavToCluster, onCheckedChange = onCluster)
                }
            }
        }

        SectionCard(header = { SectionLabel(text = "Permissions") }) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Open system settings if a pillar stays unknown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                ChunkyButton(text = "App permissions", onClick = onAppPermissions, style = ChunkyStyle.Outline)
                ChunkyButton(text = "Location", onClick = onLocationSettings, style = ChunkyStyle.Outline)
                ChunkyButton(text = "Notification access", onClick = onNotificationAccess, style = ChunkyStyle.Outline)
                ChunkyButton(text = "Battery optimisation", onClick = onBatterySettings, style = ChunkyStyle.Outline)
            }
        }
    }
}

private data class Tile(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
private fun KindChips(
    kinds: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    raw: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        kinds.forEach { kind ->
            PillChip(
                text = if (raw) kind else prettyKind(kind),
                selected = kind.equals(selected, ignoreCase = true),
                onClick = { onSelect(kind) },
            )
        }
    }
}

@Composable
private fun GarageField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    keyboard: KeyboardType = KeyboardType.Text,
    placeholder: String = "",
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        placeholder = if (placeholder.isEmpty()) null else ({ Text(placeholder) }),
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = ShapeMd,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = Lime,
            unfocusedBorderColor = Stroke,
            focusedLabelColor = Lime,
            unfocusedLabelColor = TextSecondary,
            cursorColor = Lime,
            focusedPlaceholderColor = TextDisabled,
            unfocusedPlaceholderColor = TextDisabled,
        ),
    )
}

@Composable
private fun CareCard(
    kicker: String,
    story: String,
    band: CareBand,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpen: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceRaised, ShapeMd)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(kicker, style = MaterialTheme.typography.labelSmall, color = bandColor(band))
            Text(story, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
        if (onOpen != null) {
            ChunkyIconButton(icon = Icons.AutoMirrored.Rounded.OpenInNew, onClick = onOpen, contentDescription = "Open")
        }
        ChunkyIconButton(icon = Icons.Rounded.Edit, onClick = onEdit, contentDescription = "Edit", style = ChunkyStyle.Lime)
        ChunkyIconButton(icon = Icons.Rounded.Delete, onClick = onDelete, contentDescription = "Delete")
    }
}

private fun bandColor(band: CareBand) = when (band) {
    CareBand.OVERDUE -> Danger
    CareBand.DUE_SOON -> Warning
    CareBand.OK -> Lime
    CareBand.UNKNOWN -> TextSecondary
}

private fun selectedKind(item: String, plans: List<PlanCard>, planId: Long?): String {
    plans.firstOrNull { it.id == planId }?.kind?.let { return it }
    val t = item.lowercase()
    return when {
        t.contains("oil") -> "OIL"
        t.contains("chain") -> "CHAIN"
        t.contains("brake") -> "BRAKES"
        t.contains("tyre") || t.contains("tire") -> "TYRES"
        t.contains("battery") -> "BATTERY"
        t.contains("service") -> "SERVICE"
        else -> "OTHER"
    }
}

internal fun documentStatus(iso: String, today: LocalDate = LocalDate.now()): String {
    if (iso.isBlank()) return "not set"
    val date = try {
        LocalDate.parse(iso.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (_: DateTimeParseException) {
        return "use yyyy-MM-dd"
    }
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
    return when {
        days < 0 -> "expired"
        days <= 30 -> "expires in $days days"
        else -> "ok"
    }
}

@Preview
@Composable
private fun GaragePreview() {
    BumblebeeTheme {
        Column(Modifier.background(Canvas)) {
            GarageBody(
                state = GarageUiState(
                    connection = ConnectionState.Ready,
                    bikeMac = "80:C4:1B:47:82:BE",
                    odometerKm = 20520,
                    fuelBars = 4,
                    upcoming = listOf(UpcomingItem("oil", "Oil due soon", CareBand.DUE_SOON)),
                    rangeKm = 140.0,
                ),
                onOpenFuel = {},
                onOpenTransfer = {},
                onScan = {},
                onConnect = {},
                onDisconnect = {},
                onForget = {},
                onPair = {},
                onRiderName = {},
                onSaveRiderName = {},
                onDocKind = {},
                onDocTitle = {},
                onDocNumber = {},
                onDocExpiry = {},
                onDocNote = {},
                onPickDoc = {},
                onSaveDoc = {},
                onClearDoc = {},
                onEditDoc = {},
                onDeleteDoc = {},
                onOpenFile = { _, _ -> },
                onPlanKind = {},
                onPlanTitle = {},
                onPlanKm = {},
                onPlanDays = {},
                onPlanDueOn = {},
                onPlanLastOdo = {},
                onPlanLastOn = {},
                onSavePlan = {},
                onClearPlan = {},
                onEditPlan = {},
                onDeletePlan = {},
                onServiceKind = {},
                onServiceItem = {},
                onServiceOdo = {},
                onServiceDate = {},
                onServiceShop = {},
                onServiceRupees = {},
                onServiceNote = {},
                onServicePlan = {},
                onPickBill = {},
                onLogService = {},
                onClearService = {},
                onEditService = {},
                onDeleteService = {},
                onReminders = {},
                onRemindDays = {},
                onRemindKm = {},
                onSaveReminders = {},
                onPause = {},
                onAutoConnect = {},
                onSkipAuto = {},
                onSos = {},
                onSensitivity = {},
                onBlood = {},
                onMedical = {},
                onSaveSafety = {},
                onTestSos = {},
                onCluster = {},
                onAppPermissions = {},
                onLocationSettings = {},
                onNotificationAccess = {},
                onBatterySettings = {},
            )
        }
    }
}
