package dev.gdk.bumblebee.ui.garage

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.garage.GarageFiles
import dev.gdk.bumblebee.ride.CareBand
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.theme.ErrorRed
import dev.gdk.bumblebee.ui.theme.ForgeCard
import dev.gdk.bumblebee.ui.theme.ForgeWhite
import dev.gdk.bumblebee.ui.theme.LabelMute
import dev.gdk.bumblebee.ui.theme.SuccessGreen
import dev.gdk.bumblebee.ui.theme.SuzukiYellow
import dev.gdk.bumblebee.ui.theme.WarningAmber
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("GARAGE", style = MaterialTheme.typography.labelLarge, color = SuzukiYellow)
        Text("How is the motorcycle", style = MaterialTheme.typography.titleLarge)
        Text(state.status, style = MaterialTheme.typography.bodyMedium, color = LabelMute)
        ChunkyButton(text = "Transfer the bike", onClick = onOpenTransfer, style = ChunkyStyle.Lime)

        if (state.upcoming.isNotEmpty()) {
            Section("UPCOMING") {
                Text("Due soon and overdue, from papers and plans.", style = MaterialTheme.typography.bodySmall, color = LabelMute)
                state.upcoming.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BandDot(item.band)
                        Text(item.label, style = MaterialTheme.typography.bodyMedium, color = ForgeWhite, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Section("DOCUMENTS") {
            Text(
                "Keep RC, insurance, PUC, warranty, invoices. Expiry is optional — add it when the paper can expire, and a reminder fires in the window below.",
                style = MaterialTheme.typography.bodySmall,
                color = LabelMute,
            )
            ChipRow(DOC_KINDS, state.docKind, model::setDocKind)
            OutlinedTextField(state.docTitle, model::setDocTitle, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(state.docNumber, model::setDocNumber, label = { Text("Number / policy (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                state.docExpiry,
                model::setDocExpiry,
                label = { Text("Expiry yyyy-MM-dd (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(state.docNote, model::setDocNote, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { docPicker.launch("*/*") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(if (state.docHasFile) "  Replace file" else "  Attach file")
                }
                Button(onClick = { model.saveDocument() }, modifier = Modifier.weight(1f)) {
                    Text(if (state.docId == null) "Save paper" else "Update paper")
                }
            }
            if (state.docId != null) {
                OutlinedButton(onClick = { model.clearDocumentForm() }, modifier = Modifier.fillMaxWidth()) { Text("New paper") }
            }
            state.documents.forEach { doc ->
                CareCard(
                    kicker = doc.kind,
                    story = doc.label + (doc.number?.let { "  ·  $it" } ?: ""),
                    band = doc.band,
                    onEdit = { model.editDocument(doc) },
                    onDelete = { model.deleteDocument(doc.id) },
                    onOpen = doc.filePath?.let { path ->
                        {
                            GarageFiles.viewIntent(context, path, doc.mime)?.let { context.startActivity(it) }
                        }
                    },
                )
            }
        }

        Section("MAINTENANCE PLANS") {
            Text(
                "Interval in km, calendar days, or a due date — mix them. Tyres can be “October”, pads “8000 km”. Logging work rolls the matching plan.",
                style = MaterialTheme.typography.bodySmall,
                color = LabelMute,
            )
            ChipRow(PLAN_KINDS, state.planKind, model::setPlanKind)
            OutlinedTextField(state.planTitle, model::setPlanTitle, label = { Text("Plan name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                state.planKm,
                model::setPlanKm,
                label = { Text("Every km (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                state.planDays,
                model::setPlanDays,
                label = { Text("Every days (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                state.planDueOn,
                model::setPlanDueOn,
                label = { Text("Due on yyyy-MM-dd (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                state.planLastOdo,
                model::setPlanLastOdo,
                label = { Text("Last done odo") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                state.planLastOn,
                model::setPlanLastOn,
                label = { Text("Last done date") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { model.savePlan() }, modifier = Modifier.weight(1f)) {
                    Text(if (state.planId == null) "Save plan" else "Update plan")
                }
                if (state.planId != null) {
                    OutlinedButton(onClick = { model.clearPlanForm() }, modifier = Modifier.weight(1f)) { Text("New plan") }
                }
            }
            state.plans.forEach { plan ->
                CareCard(
                    kicker = plan.kind,
                    story = plan.label,
                    band = plan.band,
                    onEdit = { model.editPlan(plan) },
                    onDelete = { model.deletePlan(plan.id) },
                )
            }
        }

        Section("SERVICE HISTORY") {
            Text(
                "Shop, rupees, date, and a bill photo. Pick a plan so the next due km/date moves when you log.",
                style = MaterialTheme.typography.bodySmall,
                color = LabelMute,
            )
            ChipRow(SERVICE_KINDS, selectedKind(state.serviceItem, state.plans, state.servicePlanId), onSelect = model::applyServiceKind)
            OutlinedTextField(state.serviceItem, model::setServiceItem, label = { Text("Work done") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                state.serviceOdo,
                model::setServiceOdo,
                label = { Text("Odo km") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(state.odometerKm?.toString() ?: "") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(state.serviceDate, model::setServiceDate, label = { Text("Date yyyy-MM-dd") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(state.serviceShop, model::setServiceShop, label = { Text("Shop") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                state.serviceRupees,
                model::setServiceRupees,
                label = { Text("₹ paid") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(state.serviceNote, model::setServiceNote, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (state.plans.isNotEmpty()) {
                Text("Apply to plan", style = MaterialTheme.typography.labelSmall, color = LabelMute)
                ChipRow(
                    kinds = listOf("NONE") + state.plans.map { it.title },
                    selected = state.plans.firstOrNull { it.id == state.servicePlanId }?.title ?: "NONE",
                    onSelect = { label ->
                        model.setServicePlan(state.plans.firstOrNull { it.title == label }?.id)
                    },
                    raw = true,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { servicePicker.launch("*/*") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("  Bill")
                }
                Button(onClick = { model.logServiceWork() }, modifier = Modifier.weight(1f)) {
                    Text(if (state.serviceId == null) "Log work" else "Update work")
                }
            }
            if (state.serviceId != null) {
                OutlinedButton(onClick = { model.clearServiceForm() }, modifier = Modifier.fillMaxWidth()) { Text("New log") }
            }
            state.runningRupeesPerKm?.let {
                Text(
                    "Ownership ₹${"%.2f".format(it)}/km" + if (state.runningPartial) " (partial prices)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = LabelMute,
                )
            }
            state.serviceLogs.forEach { log ->
                CareCard(
                    kicker = log.whenLabel,
                    story = log.story + (log.note?.let { "  ·  $it" } ?: ""),
                    band = CareBand.OK,
                    onEdit = { model.editService(log) },
                    onDelete = { model.deleteService(log.id) },
                    onOpen = log.filePath?.let { path ->
                        {
                            GarageFiles.viewIntent(context, path, null)?.let { context.startActivity(it) }
                        }
                    },
                )
            }
        }

        Section("REMINDERS") {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Notify overdue and due-soon items", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = state.remindersEnabled, onCheckedChange = { model.setRemindersEnabled(it) })
            }
            OutlinedTextField(
                state.remindDays,
                model::setRemindDays,
                label = { Text("Days ahead") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                state.remindKm,
                model::setRemindKm,
                label = { Text("Km ahead") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Button(onClick = { model.saveReminders() }, modifier = Modifier.fillMaxWidth()) { Text("Save reminder window") }
        }

        Section("RIDE AND SAFETY") {
            Text(
                "A stop inside the pause window stays one ride. Crash alerts are a best-effort aid, not a certified emergency device.",
                style = MaterialTheme.typography.bodySmall,
                color = LabelMute,
            )
            Text("Pause window", style = MaterialTheme.typography.labelLarge, color = SuzukiYellow)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(0 to "Off", 15 to "15", 30 to "30", 45 to "45", 60 to "60", 120 to "120").forEach { (min, label) ->
                    val selected = state.pauseWindowMin == min
                    TextButton(onClick = { model.setPauseWindow(min) }) {
                        Text(if (selected) "$label ·" else label)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-connect when I turn on the bike", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = state.autoConnect, onCheckedChange = model::setAutoConnect)
            }
            OutlinedButton(onClick = { model.skipAutoConnectToday() }, modifier = Modifier.fillMaxWidth()) {
                Text("Do not auto-connect today")
            }
            Text("On Xiaomi, Oppo and similar phones, allow Autostart and set battery to No restrictions, or the link dies with the screen off.", style = MaterialTheme.typography.bodySmall, color = LabelMute)
            OutlinedTextField(
                value = state.sosContacts,
                onValueChange = model::setSosContacts,
                label = { Text("Emergency numbers, up to 3") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("LOW", "NORMAL", "HIGH").forEach { level ->
                    TextButton(onClick = { model.setCrashSensitivity(level) }) {
                        Text(if (state.crashSensitivity == level) level else level.lowercase().replaceFirstChar { it.titlecase() })
                    }
                }
            }
            OutlinedTextField(
                value = state.bloodGroup,
                onValueChange = model::setBloodGroup,
                label = { Text("Blood group") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.medicalNote,
                onValueChange = model::setMedicalNote,
                label = { Text("Medical note") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { model.saveSafety() }, modifier = Modifier.fillMaxWidth()) { Text("Save safety details") }
            val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                model.onTestSosPermission(granted)
            }
            OutlinedButton(
                onClick = { smsLauncher.launch(Manifest.permission.SEND_SMS) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Test SOS") }
        }

        Section("BIKE") {
            Text("V-Strom 250 SX", style = MaterialTheme.typography.titleMedium)
            val link = if (state.connection is ConnectionState.Ready) "Connected" else "Not connected"
            Text(
                link,
                color = if (state.connection is ConnectionState.Ready) SuccessGreen else LabelMute,
                style = MaterialTheme.typography.labelLarge,
            )
            Text("MAC  ${state.bikeMac ?: "—"}", style = MaterialTheme.typography.bodySmall, color = LabelMute)
            Text("Odo  ${state.odometerKm?.let { "%,d km".format(it) } ?: "—"}", style = MaterialTheme.typography.bodySmall)
            Text("Trip A  ${state.tripAKm?.let { String.format("%.1f km", it) } ?: "—"}", style = MaterialTheme.typography.bodySmall)
            Text("Trip B  ${state.tripBKm?.let { String.format("%.1f km", it) } ?: "—"}", style = MaterialTheme.typography.bodySmall)
            Text("Fuel  ${state.fuelBars?.let { "$it bars" } ?: "—"}", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = state.riderName,
                onValueChange = model::setRiderName,
                label = { Text("Rider name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(onClick = { model.saveRiderName() }) { Text("Save name") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { if (state.scanning) model.stopScan() else requestThenScan() }) {
                    Text(if (state.scanning) "Stop scan" else "Scan")
                }
                OutlinedButton(onClick = { model.connectSaved() }, enabled = state.bikeMac != null) {
                    Text("Connect")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { model.disconnectBike() }) { Text("Disconnect") }
                OutlinedButton(onClick = { model.forgetBike() }, enabled = state.bikeMac != null) { Text("Forget") }
            }
            state.devices.forEach { bike ->
                val label = buildString {
                    append(bike.name ?: bike.mac)
                    if (bike.looksLikeSuzuki) append("  · Suzuki")
                    if (bike.bonded) append("  · bonded")
                    if (bike.rssi != Int.MIN_VALUE) append("  · ${bike.rssi} dBm")
                }
                Text(
                    text = label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { model.pair(bike.mac) }
                        .padding(vertical = 8.dp),
                )
            }
        }

        Section("FUEL") {
            Text(
                state.rangeKm?.let { "Range now  ${it.toInt()} km" }
                    ?: "Range after bar drops on linked rides.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                buildString {
                    append(state.tankKmPerL?.let { "Tank ${"%.1f".format(it)} km/L" } ?: "Tank km/L after two fills")
                    append("  ·  ")
                    append(state.rupeesPerKm?.let { "₹${"%.2f".format(it)}/km" } ?: "₹/km after priced fills")
                },
                style = MaterialTheme.typography.bodySmall,
                color = LabelMute,
            )
            Button(onClick = onOpenFuel, modifier = Modifier.fillMaxWidth()) { Text("Open fuel log") }
        }

        Section("CLUSTER NAV") {
            Text(
                "Turn arrows on the bike come from Google Maps notifications, not a Maps API. Leave this on while riding with Maps in the background.",
                style = MaterialTheme.typography.bodySmall,
                color = LabelMute,
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Maps notifications → cluster", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = state.mapsNavToCluster, onCheckedChange = { model.setMapsNavToCluster(it) })
            }
        }

        Section("PERMISSIONS") {
            Text("Open system settings if a pillar stays unknown.", style = MaterialTheme.typography.bodySmall, color = LabelMute)
            OutlinedButton(onClick = {
                val uri = Uri.fromParts("package", context.packageName, null)
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
            }) { Text("App permissions") }
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }) { Text("Location") }
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) { Text("Notification access") }
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }) { Text("Battery optimisation") }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(ForgeCard)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = SuzukiYellow)
        content()
    }
}

@Composable
private fun ChipRow(kinds: List<String>, selected: String, onSelect: (String) -> Unit, raw: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        kinds.forEach { kind ->
            val on = kind.equals(selected, ignoreCase = true)
            Text(
                text = if (raw) kind else prettyKind(kind),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(if (on) SuzukiYellow else ForgeWhite.copy(alpha = 0.08f))
                    .clickable { onSelect(kind) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = if (on) Color.Black else ForgeWhite,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
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
            .clip(MaterialTheme.shapes.medium)
            .background(ForgeWhite.copy(alpha = 0.04f))
            .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BandDot(band)
                Text(kicker, style = MaterialTheme.typography.labelSmall, color = bandColor(band))
            }
            Text(story, style = MaterialTheme.typography.bodyMedium, color = ForgeWhite)
        }
        if (onOpen != null) {
            IconButton(onClick = onOpen, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Open", tint = SuzukiYellow, modifier = Modifier.size(18.dp))
            }
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = SuzukiYellow, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = LabelMute, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun BandDot(band: CareBand) {
    Box(
        Modifier
            .size(8.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(bandColor(band)),
    )
}

private fun bandColor(band: CareBand): Color = when (band) {
    CareBand.OVERDUE -> ErrorRed
    CareBand.DUE_SOON -> WarningAmber
    CareBand.OK -> SuccessGreen
    CareBand.UNKNOWN -> LabelMute
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
