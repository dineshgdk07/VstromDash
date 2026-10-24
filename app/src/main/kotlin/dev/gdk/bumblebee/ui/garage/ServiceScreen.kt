package dev.gdk.bumblebee.ui.garage

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.garage.GarageFiles
import dev.gdk.bumblebee.ride.CareBand
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyDialog
import dev.gdk.bumblebee.ui.components.ChunkyDialogConfirmStyle
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.DateField
import dev.gdk.bumblebee.ui.components.DueMeter
import dev.gdk.bumblebee.ui.components.EmptyState
import dev.gdk.bumblebee.ui.components.ForgeSheet
import dev.gdk.bumblebee.ui.components.ForgeTopBar
import dev.gdk.bumblebee.ui.components.ListRow
import dev.gdk.bumblebee.ui.components.ListRowIcon
import dev.gdk.bumblebee.ui.components.PillChip
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.components.StatusChip
import dev.gdk.bumblebee.ui.components.StatusChipTone
import dev.gdk.bumblebee.ui.garage.documents.decodeThumb
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.ShapeSm
import dev.gdk.bumblebee.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private enum class ServiceTab { Plans, History }

@Composable
fun ServiceScreen(
    onBack: () -> Unit,
    model: ServiceViewModel = viewModel(),
) {
    val state by model.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by remember { mutableStateOf(ServiceTab.Plans) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var pendingPlanDelete by remember { mutableStateOf<PlanCard?>(null) }
    var pendingLogDelete by remember { mutableStateOf<ServiceCard?>(null) }
    val billPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { model.pickServiceFile(it) }
    RefreshOnResume(model::refresh)

    val detail = state.serviceLogs.firstOrNull { it.id == detailId }
    val today = LocalDate.now()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ForgeTopBar(
                title = "Service",
                onBack = onBack,
                trailing = {
                    Box(Modifier.width(150.dp)) {
                        ChunkyButton(
                            text = "Log service",
                            onClick = model::beginLog,
                            compact = true,
                            style = ChunkyStyle.Lime,
                        )
                    }
                },
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.status.isNotBlank()) {
                    Text(state.status, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillChip(text = "Plans", selected = tab == ServiceTab.Plans, onClick = { tab = ServiceTab.Plans })
                    PillChip(text = "History", selected = tab == ServiceTab.History, onClick = { tab = ServiceTab.History })
                }
                if (tab == ServiceTab.Plans) {
                    if (state.plans.isEmpty()) {
                        EmptyState(
                            headline = "No plans yet",
                            subtitle = "Oil, chain, brakes — say when each one is due.",
                            primaryLabel = "Add a plan",
                            onPrimary = model::beginAddPlan,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    } else {
                        state.plans.forEach { plan ->
                            PlanBlock(
                                plan = plan,
                                due = planDueFor(plan, state.odometerKm, today, state.remindKm, state.remindDays),
                                onMarkDone = { model.markDone(plan) },
                                onEdit = { model.beginEditPlan(plan) },
                                onDelete = { pendingPlanDelete = plan },
                            )
                        }
                        ChunkyButton(text = "Add plan", onClick = model::beginAddPlan, style = ChunkyStyle.Outline)
                    }
                } else if (state.serviceLogs.isEmpty()) {
                    EmptyState(
                        headline = "No service yet",
                        subtitle = "Log the work and the next due date moves with it.",
                        primaryLabel = "Log a service",
                        onPrimary = model::beginLog,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                } else {
                    state.runningRupeesPerKm?.let { rate ->
                        Text(
                            "Ownership ₹${"%.2f".format(rate)}/km" + if (state.runningPartial) " (partial prices)" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    serviceYears(state.serviceLogs).forEach { year ->
                        SectionCard(header = { SectionLabel(text = yearTitle(year)) }) {
                            year.visits.forEachIndexed { index, log ->
                                ListRow(
                                    title = log.item.replaceFirstChar { it.uppercase() },
                                    subtitle = visitSubtitle(log),
                                    onClick = { detailId = log.id },
                                    showDivider = index < year.visits.lastIndex,
                                    leading = { BillThumb(log.filePath) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.editingPlan) {
            ForgeSheet(
                title = if (state.planId == null) "Add plan" else "Edit plan",
                onDismiss = model::dismissPlan,
                primaryLabel = if (state.planId == null) "Save" else "Update",
                onPrimary = model::savePlan,
            ) {
                if (state.status.isNotBlank()) {
                    Text(state.status, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                KindChips(PLAN_KINDS, state.planKind, model::setPlanKind)
                GarageField(state.planTitle, model::setPlanTitle, "Plan name", modifier = Modifier.padding(top = 12.dp))
                GarageField(
                    state.planKm,
                    model::setPlanKm,
                    "Every km (optional)",
                    KeyboardType.Number,
                    modifier = Modifier.padding(top = 12.dp),
                )
                GarageField(
                    state.planDays,
                    model::setPlanDays,
                    "Every days (optional)",
                    KeyboardType.Number,
                    modifier = Modifier.padding(top = 12.dp),
                )
                DateField(
                    label = "Due on",
                    value = isoOrNull(state.planDueOn),
                    onValueChange = { model.setPlanDueOn(it.toString()) },
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (state.planDueOn.isNotBlank()) {
                    ChunkyButton(
                        text = "Clear date",
                        onClick = { model.setPlanDueOn("") },
                        compact = true,
                        style = ChunkyStyle.Outline,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                GarageField(
                    state.planLastOdo,
                    model::setPlanLastOdo,
                    "Last done odo",
                    KeyboardType.Number,
                    modifier = Modifier.padding(top = 12.dp),
                )
                DateField(
                    label = "Last done",
                    value = isoOrNull(state.planLastOn),
                    onValueChange = { model.setPlanLastOn(it.toString()) },
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (state.planLastOn.isNotBlank()) {
                    ChunkyButton(
                        text = "Clear date",
                        onClick = { model.setPlanLastOn("") },
                        compact = true,
                        style = ChunkyStyle.Outline,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        if (state.editingLog) {
            ForgeSheet(
                title = if (state.serviceId == null) "Log service" else "Edit service",
                onDismiss = model::dismissLog,
                primaryLabel = if (state.serviceId == null) "Log" else "Update",
                onPrimary = model::logServiceWork,
            ) {
                if (state.status.isNotBlank()) {
                    Text(state.status, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                KindChips(
                    SERVICE_KINDS,
                    selectedKind(state.serviceItem, state.plans, state.servicePlanId),
                    model::applyServiceKind,
                )
                GarageField(state.serviceItem, model::setServiceItem, "Work done", modifier = Modifier.padding(top = 12.dp))
                GarageField(
                    state.serviceOdo,
                    model::setServiceOdo,
                    "Odo km",
                    KeyboardType.Number,
                    placeholder = state.odometerKm?.toString() ?: "",
                    modifier = Modifier.padding(top = 12.dp),
                )
                DateField(
                    label = "Date",
                    value = isoOrNull(state.serviceDate),
                    onValueChange = { model.setServiceDate(it.toString()) },
                    modifier = Modifier.padding(top = 12.dp),
                )
                GarageField(state.serviceShop, model::setServiceShop, "Shop", modifier = Modifier.padding(top = 12.dp))
                val shops = shopSuggestions(state.serviceLogs, state.serviceShop)
                if (shops.isNotEmpty()) {
                    KindChips(shops, state.serviceShop, model::setServiceShop, raw = true)
                }
                GarageField(
                    state.serviceRupees,
                    model::setServiceRupees,
                    "₹ paid",
                    KeyboardType.Decimal,
                    modifier = Modifier.padding(top = 12.dp),
                )
                GarageField(
                    state.serviceNote,
                    model::setServiceNote,
                    "Note",
                    singleLine = false,
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (state.plans.isNotEmpty()) {
                    Text(
                        "Apply to plan",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    KindChips(
                        kinds = listOf("NONE") + state.plans.map { it.title },
                        selected = state.plans.firstOrNull { it.id == state.servicePlanId }?.title ?: "NONE",
                        onSelect = { label -> model.setServicePlan(state.plans.firstOrNull { it.title == label }?.id) },
                        raw = true,
                    )
                }
                val billAttached = state.serviceFilePending || !state.serviceExistingFile.isNullOrBlank()
                ChunkyButton(
                    text = if (billAttached) "Replace bill" else "Attach bill",
                    onClick = { billPicker.launch("*/*") },
                    style = ChunkyStyle.Outline,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        if (detail != null && !state.editingLog && !state.editingPlan) {
            ForgeSheet(
                title = detail.item.replaceFirstChar { it.uppercase() },
                onDismiss = { detailId = null },
                primaryLabel = "Edit",
                onPrimary = { model.beginEditLog(detail) },
            ) {
                BillPreview(detail.filePath)
                Text(visitSubtitle(detail), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                detail.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
                val billPath = detail.filePath
                if (!billPath.isNullOrBlank()) {
                    ChunkyButton(
                        text = "Open bill",
                        onClick = {
                            GarageFiles.viewIntent(context, billPath, null)?.let { context.startActivity(it) }
                        },
                        style = ChunkyStyle.Outline,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                ChunkyButton(
                    text = "Delete",
                    onClick = { pendingLogDelete = detail },
                    style = ChunkyStyle.Danger,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }

    pendingPlanDelete?.let { plan ->
        ChunkyDialog(
            title = "Delete this plan?",
            body = "${plan.title} leaves the list. Logged work stays in history.",
            dismissLabel = "Keep",
            confirmLabel = "Delete",
            onDismiss = { pendingPlanDelete = null },
            onConfirm = {
                model.deletePlan(plan.id)
                pendingPlanDelete = null
            },
            confirmStyle = ChunkyDialogConfirmStyle.Danger,
        )
    }
    pendingLogDelete?.let { log ->
        ChunkyDialog(
            title = "Delete this visit?",
            body = "${log.item.replaceFirstChar { it.uppercase() }} on ${log.whenLabel} comes off the history.",
            dismissLabel = "Keep",
            confirmLabel = "Delete",
            onDismiss = { pendingLogDelete = null },
            onConfirm = {
                model.deleteService(log.id)
                if (detailId == log.id) detailId = null
                pendingLogDelete = null
            },
            confirmStyle = ChunkyDialogConfirmStyle.Danger,
        )
    }
}

@Composable
private fun PlanBlock(
    plan: PlanCard,
    due: PlanDue,
    onMarkDone: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(plan.title, style = MaterialTheme.typography.titleMedium)
            DueMeter(
                usedLabel = due.usedLabel,
                totalLabel = due.totalLabel,
                fraction = due.fraction,
                tone = due.tone,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(
                    tone = due.tone,
                    text = if (due.tone == StatusChipTone.NoExpiry) "NO INTERVAL" else null,
                )
                Text(
                    due.caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    ChunkyButton(text = "Mark done", onClick = onMarkDone, compact = true, style = ChunkyStyle.Lime)
                }
                Box(Modifier.weight(1f)) {
                    ChunkyButton(text = "Edit", onClick = onEdit, compact = true, style = ChunkyStyle.Outline)
                }
            }
            ChunkyButton(text = "Delete", onClick = onDelete, compact = true, style = ChunkyStyle.Danger)
        }
    }
}

@Composable
private fun BillThumb(path: String?) {
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) { path?.let { decodeThumb(it, 160) } }
    }
    val image = bitmap
    if (image == null) {
        ListRowIcon(Icons.Rounded.Receipt)
    } else {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(ShapeSm),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun BillPreview(path: String?) {
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) { path?.let { decodeThumb(it, 800) } }
    }
    val image = bitmap ?: return
    Image(
        bitmap = image.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(ShapeSm),
        contentScale = ContentScale.FillWidth,
    )
}

private fun planDueFor(plan: PlanCard, odometerKm: Int?, today: LocalDate, remindKm: Int, remindDays: Int): PlanDue =
    planDue(
        currentOdoKm = odometerKm,
        lastDoneOdoKm = plan.lastDoneOdoKm,
        intervalKm = plan.intervalKm,
        lastDoneOn = isoOrNull(plan.lastDoneOnIso ?: ""),
        intervalDays = plan.intervalDays,
        dueOn = isoOrNull(plan.dueOnIso ?: ""),
        today = today,
        remindKm = remindKm,
        remindDays = remindDays,
    )

private fun isoOrNull(value: String): LocalDate? =
    value.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private data class ServiceYearGroup(val year: Int, val visits: List<ServiceCard>)

private fun serviceYears(logs: List<ServiceCard>): List<ServiceYearGroup> {
    val zone = ZoneId.systemDefault()
    return logs.groupBy { Instant.ofEpochMilli(it.ts).atZone(zone).year }
        .map { (year, visits) -> ServiceYearGroup(year, visits) }
}

private fun yearTitle(group: ServiceYearGroup): String {
    val priced = group.visits.mapNotNull { it.rupees }
    val money = if (priced.isEmpty()) null else "₹${String.format(Locale.US, "%,.0f", priced.sum())}"
    val visits = group.visits.size
    val visitWord = if (visits == 1) "1 visit" else "$visits visits"
    val partial = if (priced.isNotEmpty() && priced.size < visits) "partial" else null
    return listOfNotNull(group.year.toString(), money, visitWord, partial).joinToString(" · ")
}

private fun visitSubtitle(log: ServiceCard): String {
    val rupees = log.rupees?.let { "₹${String.format(Locale.US, "%,.0f", it)}" }
    return listOfNotNull(
        log.whenLabel,
        String.format(Locale.US, "%,d km", log.odometerKm),
        log.shop?.takeIf { it.isNotBlank() },
        rupees,
    ).joinToString(" · ")
}

private fun shopSuggestions(logs: List<ServiceCard>, typed: String): List<String> {
    val shops = logs.mapNotNull { it.shop?.trim()?.takeIf { shop -> shop.isNotEmpty() } }
        .distinctBy { it.lowercase() }
    val query = typed.trim()
    val matched = if (query.isEmpty()) {
        shops
    } else {
        shops.filter { it.contains(query, ignoreCase = true) && !it.equals(query, ignoreCase = true) }
    }
    return matched.take(6)
}

@Preview
@Composable
private fun ServicePlanPreview() {
    BumblebeeTheme {
        Column(
            Modifier.background(Canvas).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PlanBlock(
                plan = PlanCard(1, "Engine oil", "OIL", 3000, 180, null, 10_000, "2026-05-04", CareBand.OK, ""),
                due = planDue(
                    currentOdoKm = 12_580,
                    lastDoneOdoKm = 10_000,
                    intervalKm = 3_000,
                    lastDoneOn = LocalDate.of(2026, 5, 4),
                    intervalDays = 180,
                    dueOn = null,
                    today = LocalDate.of(2026, 9, 21),
                ),
                onMarkDone = {},
                onEdit = {},
                onDelete = {},
            )
        }
    }
}

@Preview
@Composable
private fun ServiceHistoryPreview() {
    BumblebeeTheme {
        Box(Modifier.background(Canvas).padding(16.dp).fillMaxWidth()) {
            SectionCard(header = { SectionLabel(text = "2026 · ₹8,450 · 4 visits") }) {
                ListRow(
                    title = "Engine oil",
                    subtitle = "4 May 2026 · 12,400 km · Suzuki · ₹1,800",
                    leading = { ListRowIcon(Icons.Rounded.Receipt) },
                    showDivider = false,
                )
            }
        }
    }
}
