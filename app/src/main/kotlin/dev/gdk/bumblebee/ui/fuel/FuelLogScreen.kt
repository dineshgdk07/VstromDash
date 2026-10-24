package dev.gdk.bumblebee.ui.fuel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyDialog
import dev.gdk.bumblebee.ui.components.ChunkyDialogConfirmStyle
import dev.gdk.bumblebee.ui.components.ChunkyIconButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.EmptyState
import dev.gdk.bumblebee.ui.components.ForgeTopBar
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.components.StatTone
import dev.gdk.bumblebee.ui.components.StatTriple
import dev.gdk.bumblebee.ui.components.StatTripleColumn
import dev.gdk.bumblebee.ui.theme.BlockLavender
import dev.gdk.bumblebee.ui.theme.BlockYellow
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.ShapeXs
import dev.gdk.bumblebee.ui.theme.Stroke
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.TextDisabled
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun FuelLogScreen(
    onBack: () -> Unit,
    model: FuelLogViewModel = viewModel(),
) {
    val state by model.ui.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    FuelLogBody(
        state = state,
        onBack = onBack,
        onOdo = model::setOdo,
        onDate = model::setDate,
        onBarsAfter = model::setBarsAfter,
        onLitres = model::setLitres,
        onRupees = model::setRupees,
        onNote = model::setNote,
        onFullTank = model::setFullTank,
        onSave = model::saveFill,
        onCancelEdit = model::cancelEdit,
        onEdit = model::beginEdit,
        onDelete = model::deleteEntry,
        onConfirmOutlier = model::confirmOutlier,
    )
}

@Composable
private fun FuelLogBody(
    state: FuelLogUiState,
    onBack: () -> Unit,
    onOdo: (String) -> Unit,
    onDate: (String) -> Unit,
    onBarsAfter: (String) -> Unit,
    onLitres: (String) -> Unit,
    onRupees: (String) -> Unit,
    onNote: (String) -> Unit,
    onFullTank: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
    onEdit: (FuelLogEntry) -> Unit,
    onDelete: (Long) -> Unit,
    onConfirmOutlier: (Long) -> Unit,
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<FuelLogEntry?>(null) }
    val lastFill = state.entries.firstOrNull { it.isFill }
    val fills = state.entries.count { it.isFill }
    val drops = state.entries.count { !it.isFill }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ForgeTopBar(title = "Fuel log", onBack = onBack)
        Text(
            state.status,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        StatTriple(
            headlineLabel = "Range",
            headlineValue = state.rangeKm?.let { "est. ${it.toInt()} km" } ?: "—",
            columns = listOf(
                StatTripleColumn(
                    value = state.tankKmPerL?.let { "%.1f".format(it) } ?: "—",
                    caption = "est. km/L",
                    tone = StatTone.Plain,
                ),
                StatTripleColumn(
                    value = lastFillValue(lastFill),
                    caption = "last fill",
                    tone = StatTone.Plain,
                ),
                StatTripleColumn(
                    value = state.rupeesPerKm?.let {
                        "%.2f".format(it) + if (state.costPartial) "*" else ""
                    } ?: "—",
                    caption = "₹/km",
                    tone = StatTone.Plain,
                ),
            ),
        )
        TankNotes(state)

        if (state.entries.isEmpty()) {
            EmptyState(
                headline = "No fill-ups yet",
                subtitle = "Linked rides write bar drops when the gauge steps down, and a fill when bars go up. You only log the pump visit.",
                primaryLabel = "Log a fill",
                onPrimary = { scope.launch { scroll.animateScrollTo(scroll.maxValue) } },
            )
        }

        FillForm(
            state = state,
            onOdo = onOdo,
            onDate = onDate,
            onBarsAfter = onBarsAfter,
            onLitres = onLitres,
            onRupees = onRupees,
            onNote = onNote,
            onFullTank = onFullTank,
            onSave = onSave,
            onCancelEdit = onCancelEdit,
        )

        if (state.tankTrend.size >= 2) {
            KmPerLChart(state.tankTrend)
        }
        if (state.tanks.isNotEmpty()) {
            SectionCard(header = { SectionLabel(text = "Per tank") }) {
                Column(
                    Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    state.tanks.forEach { tank ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                buildString {
                                    append(tank.whenLabel)
                                    append("  ·  ${"%.0f".format(tank.km)} km  ·  ${"%.1f".format(tank.litres)} L  ·  ${"%.1f".format(tank.kmPerL)} km/L")
                                    tank.rupeesPerKm?.let { append("  ·  ₹${"%.2f".format(it)}/km") }
                                    tank.deltaPct?.let { append("  ·  ${if (it > 0) "+" else ""}$it%") }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                            )
                            if (tank.outlier) {
                                Text(
                                    "Check this fill?",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Danger,
                                )
                                ChunkyButton(
                                    text = "Keep in averages",
                                    onClick = { onConfirmOutlier(tank.closingFillId) },
                                    style = ChunkyStyle.Outline,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.entries.isNotEmpty()) {
            SectionCard(
                header = {
                    SectionLabel(
                        text = "History",
                        count = fills,
                    )
                },
            ) {
                Column(
                    Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "$drops bar drop${if (drops == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    state.entries.forEach { entry ->
                        EntryRow(
                            entry = entry,
                            onEdit = { onEdit(entry) },
                            onDelete = { pendingDelete = entry },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        ChunkyDialog(
            title = if (entry.isFill) "Delete this fill" else "Delete this drop",
            body = "The log drops it. ${entry.whenLabel}.",
            dismissLabel = "Keep",
            confirmLabel = "Delete",
            confirmStyle = ChunkyDialogConfirmStyle.Danger,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                onDelete(entry.id)
            },
        )
    }
}

@Composable
private fun TankNotes(state: FuelLogUiState) {
    val lines = buildList {
        state.kmPerBarAvg?.let { add("km/bar  ${"%.0f".format(it)}") }
        state.monthSpend?.let { add("This month  ₹${"%.0f".format(it)}") }
        if (state.tankLitres != null) {
            val litres = "${"%.1f".format(state.tankLitres)} L"
            add("Tank  " + if (state.tankRough) "est. $litres" else litres)
        }
        state.refuelLabel?.let { add("Refuel  $it") }
    }
    val footnotes = buildList {
        if (state.tankRough) add("Litres from bars until you log a fill. V-Strom tank is 12 L.")
        if (state.fillBeforeService) add("Fill before the next service stop — the remaining range reaches the shop window.")
        if (state.costPartial) add("Some fills had no price, so ₹/km is partial.")
    }
    if (lines.isEmpty() && footnotes.isEmpty() && state.tankSummary.isBlank() && state.clusterSummary.isBlank()) {
        return
    }
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            lines.forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
            if (state.tankSummary.isNotBlank()) {
                Text(state.tankSummary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            if (state.clusterSummary.isNotBlank()) {
                Text(state.clusterSummary, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
            footnotes.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun FillForm(
    state: FuelLogUiState,
    onOdo: (String) -> Unit,
    onDate: (String) -> Unit,
    onBarsAfter: (String) -> Unit,
    onLitres: (String) -> Unit,
    onRupees: (String) -> Unit,
    onNote: (String) -> Unit,
    onFullTank: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    val editing = state.editingId != null
    SectionCard(header = { SectionLabel(text = if (editing) "Edit fill" else "Log a fill") }) {
        Column(
            Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (editing) {
                    "Change odo, date, bars, litres, rupees, or the comment. Delete removes the fill."
                } else {
                    "Litres, rupees, and a comment are optional. If the cluster already saw bars go up at this odo, this save merges into that fill."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            FuelField(state.odo, onOdo, "Odo km", state.odometerKm?.toString() ?: "", KeyboardType.Number)
            FuelField(state.date, onDate, "Date yyyy-MM-dd", "", KeyboardType.Text)
            FuelField(state.barsAfter, onBarsAfter, "Bars after fill", "", KeyboardType.Number)
            FuelField(state.litres, onLitres, "Litres", "", KeyboardType.Decimal)
            FuelField(state.rupees, onRupees, "₹ paid", "", KeyboardType.Decimal)
            FuelField(
                value = state.note,
                onValue = onNote,
                label = "Comment",
                placeholder = "HP Whitefield, full tank, slightly short-changed…",
                keyboard = KeyboardType.Text,
                singleLine = false,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Full tank", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                Switch(checked = state.fullTank, onCheckedChange = onFullTank)
            }
            ChunkyButton(
                text = if (editing) "Save changes" else "Save fill",
                onClick = onSave,
                style = ChunkyStyle.Lime,
            )
            if (editing) {
                ChunkyButton(
                    text = "Cancel edit",
                    onClick = onCancelEdit,
                    style = ChunkyStyle.Outline,
                )
            }
        }
    }
}

@Composable
private fun FuelField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboard: KeyboardType,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        placeholder = if (placeholder.isEmpty()) null else ({ Text(placeholder) }),
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        maxLines = if (singleLine) 1 else 6,
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
private fun KmPerLChart(values: List<Double>) {
    val peak = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    SectionCard(header = { SectionLabel(text = "km/L") }) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().height(140.dp), verticalAlignment = Alignment.Bottom) {
                Column(
                    Modifier.width(32.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("%.0f".format(peak), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("0", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        repeat(3) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Stroke))
                        }
                    }
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        values.forEach { kmPerL ->
                            val fraction = (kmPerL / peak).toFloat().coerceIn(0.04f, 1f)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(fraction)
                                    .background(BlockLavender, ShapeXs),
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 32.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                values.indices.forEach { index ->
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: FuelLogEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val kind = when {
        entry.isFill && entry.riderOwned -> "YOU"
        entry.isFill -> "CLUSTER"
        else -> "DROP"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "$kind  ·  ${entry.whenLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = if (entry.isFill) Lime else Danger,
            )
            Text(entry.story, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            FuelBarStrip(before = entry.barsBefore, after = entry.barsAfter, fill = entry.isFill)
            Text(
                "%,d km".format(entry.odometerKm) + (entry.note?.let { "  ·  $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (entry.isFill) {
                ChunkyIconButton(
                    icon = Icons.Rounded.Edit,
                    onClick = onEdit,
                    contentDescription = "Edit",
                    style = ChunkyStyle.Lime,
                )
            }
            ChunkyIconButton(
                icon = Icons.Rounded.Delete,
                onClick = onDelete,
                contentDescription = "Delete",
            )
        }
    }
}

@Composable
private fun FuelBarStrip(before: Int, after: Int, fill: Boolean) {
    val lo = before.coerceIn(0, 6)
    val hi = after.coerceIn(0, 6)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
        for (bar in 1..6) {
            val color = when {
                fill && bar <= lo -> BlockYellow
                fill && bar <= hi -> Lime
                !fill && bar <= hi -> BlockYellow
                !fill && bar <= lo -> Danger
                else -> SurfaceRaised
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .background(color, ShapeXs),
            )
        }
    }
}

private fun lastFillValue(entry: FuelLogEntry?): String {
    if (entry == null) return "—"
    entry.litres?.let { return "%.1f L".format(it) }
    entry.rupees?.let { return "₹${"%.0f".format(it)}" }
    return entry.whenLabel.substringBefore(" ·").ifBlank { "—" }
}

@Preview
@Composable
private fun FuelLogEmptyPreview() {
    BumblebeeTheme {
        Column(Modifier.background(Canvas)) {
            FuelLogBody(
                state = FuelLogUiState(),
                onBack = {},
                onOdo = {},
                onDate = {},
                onBarsAfter = {},
                onLitres = {},
                onRupees = {},
                onNote = {},
                onFullTank = {},
                onSave = {},
                onCancelEdit = {},
                onEdit = {},
                onDelete = {},
                onConfirmOutlier = {},
            )
        }
    }
}

@Preview
@Composable
private fun FuelLogFilledPreview() {
    BumblebeeTheme {
        Column(Modifier.background(Canvas)) {
            FuelLogBody(
                state = FuelLogUiState(
                    rangeKm = 160.0,
                    tankKmPerL = 32.4,
                    rupeesPerKm = 3.1,
                    monthSpend = 1800.0,
                    tankLitres = 6.2,
                    tankRough = true,
                    refuelLabel = "This week",
                    tankTrend = listOf(28.0, 32.4, 30.1),
                    tanks = listOf(
                        TankHistoryRow(
                            closingFillId = 1,
                            whenLabel = "12 Oct",
                            km = 240.0,
                            litres = 8.4,
                            kmPerL = 28.6,
                            rupeesPerKm = 3.5,
                            outlier = false,
                            deltaPct = -4,
                        ),
                    ),
                    entries = listOf(
                        FuelLogEntry(
                            id = 1,
                            ts = 1,
                            whenLabel = "12 Oct · 9:00 AM",
                            type = "REFUEL",
                            isFill = true,
                            odometerKm = 12400,
                            barsBefore = 2,
                            barsAfter = 6,
                            litres = 8.4,
                            rupees = 850.0,
                            note = "HP",
                            kmSincePrevFill = 240,
                            tankKmPerL = 28.6,
                            rupeesPerLitre = 101.0,
                            rupeesPerKm = 3.5,
                            riderOwned = true,
                            kmSincePrevEvent = 40,
                            story = "You filled 2 → 6. 240 km since last fill · 28.6 km/L · 8.4 L · ₹850",
                        ),
                    ),
                ),
                onBack = {},
                onOdo = {},
                onDate = {},
                onBarsAfter = {},
                onLitres = {},
                onRupees = {},
                onNote = {},
                onFullTank = {},
                onSave = {},
                onCancelEdit = {},
                onEdit = {},
                onDelete = {},
                onConfirmOutlier = {},
            )
        }
    }
}
