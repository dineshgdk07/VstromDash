package dev.gdk.bumblebee.ui.fuel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyDialog
import dev.gdk.bumblebee.ui.components.ChunkyDialogConfirmStyle
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.EmptyState
import dev.gdk.bumblebee.ui.components.ForgeSheet
import dev.gdk.bumblebee.ui.components.ForgeTopBar
import dev.gdk.bumblebee.ui.components.MonthSwitcher
import dev.gdk.bumblebee.ui.components.PillChip
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
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.ShapeSm
import dev.gdk.bumblebee.ui.theme.ShapeXs
import dev.gdk.bumblebee.ui.theme.Stroke
import dev.gdk.bumblebee.ui.theme.Surface
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.TextDisabled
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FuelLogScreen(
    onBack: () -> Unit,
    model: FuelLogViewModel = viewModel(),
) {
    val state by model.ui.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    FuelLogBody(
        state = state,
        actions = FuelActions(
            onBack = onBack,
            onMonth = model::showMonth,
            onDay = model::selectDate,
            onLog = model::openForm,
            onCloseForm = model::closeForm,
            onOdo = model::setOdo,
            onDate = model::setDate,
            onBarsAfter = model::setBarsAfter,
            onLitres = model::setLitres,
            onRupees = model::setRupees,
            onNote = model::setNote,
            onFullTank = model::setFullTank,
            onSave = model::saveFill,
            onEdit = model::beginEdit,
            onDelete = model::deleteEntry,
            onConfirmOutlier = model::confirmOutlier,
        ),
    )
}

/** Everything the page can ask the view model to do. */
private class FuelActions(
    val onBack: () -> Unit = {},
    val onMonth: (YearMonth) -> Unit = {},
    val onDay: (String) -> Unit = {},
    val onLog: () -> Unit = {},
    val onCloseForm: () -> Unit = {},
    val onOdo: (String) -> Unit = {},
    val onDate: (String) -> Unit = {},
    val onBarsAfter: (String) -> Unit = {},
    val onLitres: (String) -> Unit = {},
    val onRupees: (String) -> Unit = {},
    val onNote: (String) -> Unit = {},
    val onFullTank: (Boolean) -> Unit = {},
    val onSave: () -> Unit = {},
    val onEdit: (FuelLogEntry) -> Unit = {},
    val onDelete: (Long) -> Unit = {},
    val onConfirmOutlier: (Long) -> Unit = {},
)

@Composable
private fun FuelLogBody(state: FuelLogUiState, actions: FuelActions) {
    var pendingDelete by remember { mutableStateOf<FuelLogEntry?>(null) }
    val lastFill = state.entries.firstOrNull { it.isFill }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ForgeTopBar(
            title = "Fuel log",
            onBack = actions.onBack,
            trailing = {
                ChunkyButton(text = "Log", onClick = actions.onLog, icon = Icons.Rounded.LocalGasStation, style = ChunkyStyle.Lime, compact = true)
            },
        )
        if (state.status.isNotBlank()) {
            Text(state.status, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        StatTriple(
            headlineLabel = "Range",
            headlineValue = state.rangeKm?.let { "est. ${it.toInt()} km" } ?: "—",
            columns = listOf(
                StatTripleColumn(state.tankKmPerL?.let { "%.1f".format(it) } ?: "—", "est. km/L", StatTone.Plain),
                StatTripleColumn(lastFillValue(lastFill), "last fill", StatTone.Plain),
                StatTripleColumn(
                    state.rupeesPerKm?.let { "%.2f".format(it) + if (state.costPartial) "*" else "" } ?: "—",
                    "₹/km",
                    StatTone.Plain,
                ),
            ),
        )
        TankCard(state)

        if (state.entries.isEmpty()) {
            EmptyState(
                headline = "No fill-ups yet",
                subtitle = "Linked rides write bar drops when the gauge steps down, and a fill when bars go up. You only log the pump visit.",
                primaryLabel = "Log a fill",
                onPrimary = actions.onLog,
            )
        } else {
            MonthSwitcher(month = state.month, onMonthChange = actions.onMonth)
            MonthInsights(state.insights)
            FuelCalendar(state.month, state.marks, state.selectedDate, actions.onDay)
            History(state, onEdit = actions.onEdit, onDelete = { pendingDelete = it })
        }

        if (state.tankTrend.size >= 2) KmPerLChart(state.tankTrend)
        if (state.tanks.isNotEmpty()) TankList(state.tanks, actions.onConfirmOutlier)
    }

    if (state.formOpen) FillSheet(state, actions)

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
                actions.onDelete(entry.id)
            },
        )
    }
}

// ---- tank now ----

/** The tank right now: a big gauge, what's left, and when to refuel. */
@Composable
private fun TankCard(state: FuelLogUiState) {
    val footnotes = buildList {
        if (state.tankRough) add("Litres from bars until you log a fill. V-Strom tank is 12 L.")
        if (state.fillBeforeService) add("Fill before the next service stop — the remaining range reaches the shop window.")
        if (state.costPartial) add("Some fills had no price, so ₹/km is partial.")
    }
    val facts = buildList {
        state.tankLitres?.let { add((if (state.tankRough) "est. " else "") + "%.1f L left".format(it)) }
        state.kmPerBarAvg?.let { add("%.0f km a bar".format(it)) }
        state.refuelLabel?.let { add("Refuel $it".lowercase().replaceFirstChar { c -> c.uppercase() }) }
    }
    if (state.fuelBars == null && facts.isEmpty() && footnotes.isEmpty() && state.tankSummary.isBlank() && state.clusterSummary.isBlank()) return
    SectionCard(header = { SectionLabel(text = "Tank now") }) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.fuelBars?.let { bars ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Gauge(bars, Modifier.weight(1f), height = 18.dp)
                    Text("$bars/6", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }
            }
            if (facts.isNotEmpty()) {
                Text(facts.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
            if (state.tankSummary.isNotBlank()) Text(state.tankSummary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            if (state.clusterSummary.isNotBlank()) Text(state.clusterSummary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            footnotes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
        }
    }
}

/** Six bar segments, filled up to [bars]. Low tanks turn red. */
@Composable
private fun Gauge(bars: Int, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 8.dp) {
    val filled = bars.coerceIn(0, 6)
    val color = when {
        filled <= 1 -> Danger
        filled <= 2 -> BlockYellow
        else -> Lime
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (bar in 1..6) {
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .background(if (bar <= filled) color else SurfaceRaised, ShapeXs),
            )
        }
    }
}

// ---- month ----

@Composable
private fun MonthInsights(insights: FuelMonthInsights) {
    if (insights.fills == 0 && insights.drops == 0) {
        SectionCard {
            Text("No pump visits this month.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        return
    }
    SectionCard(header = { SectionLabel(text = "This month") }) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InsightTile(
                    label = "Spent",
                    value = insights.spend?.let { "₹%,.0f".format(it) + if (insights.spendPartial) "*" else "" } ?: "—",
                    note = insights.spendVsPrevPct?.let { trend(it, "vs last month") },
                    modifier = Modifier.weight(1f),
                )
                InsightTile(
                    label = "Fuel",
                    value = insights.litres?.let { "%.1f L".format(it) } ?: "—",
                    note = "${insights.fills} fill${if (insights.fills == 1) "" else "s"}",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InsightTile(
                    label = "Pump price",
                    value = insights.rupeesPerLitre?.let { "₹%.1f/L".format(it) } ?: "—",
                    note = insights.priceChangePct?.let { trend(it, "since last fill") },
                    modifier = Modifier.weight(1f),
                )
                InsightTile(
                    label = "Economy",
                    value = insights.kmPerL?.let { "%.1f km/L".format(it) } ?: "—",
                    note = insights.rupeesPerKm?.let { "₹%.2f a km".format(it) },
                    modifier = Modifier.weight(1f),
                )
            }
            val lines = buildList {
                insights.km?.let { add("$it km between fills") }
                if (insights.bestKmPerL != null && insights.bestLabel != null) {
                    add("Best tank ${"%.1f".format(insights.bestKmPerL)} km/L on ${insights.bestLabel}")
                }
                if (insights.drops > 0) add("${insights.drops} gauge drop${if (insights.drops == 1) "" else "s"}")
            }
            if (lines.isNotEmpty()) {
                Text(lines.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

private fun trend(pct: Int, what: String): String = when {
    pct > 0 -> "▲ $pct% $what"
    pct < 0 -> "▼ ${-pct}% $what"
    else -> "Same as before"
}

@Composable
private fun InsightTile(label: String, value: String, note: String?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(SurfaceRaised, ShapeMd)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.titleLarge, color = TextPrimary, maxLines = 1)
        if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
    }
}

/** The month as a calendar: a pump dot on fill days, a red tick on gauge drops. Tap a day to see only it. */
@Composable
private fun FuelCalendar(month: YearMonth, marks: Map<String, DayMarks>, selected: String?, onSelect: (String) -> Unit) {
    val today = remember { LocalDate.now().toString() }
    val lead = (month.atDay(1).dayOfWeek.value + 6) % 7
    val days = month.lengthOfMonth()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(dev.gdk.bumblebee.ui.theme.ShapeLg)
            .background(Surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        repeat((lead + days + 6) / 7) { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { c ->
                    val idx = r * 7 + c - lead
                    if (idx !in 0 until days) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val date = month.atDay(idx + 1).toString()
                        DayCell(
                            day = idx + 1,
                            mark = marks[date],
                            selected = date == selected,
                            today = date == today,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(date) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, mark: DayMarks?, selected: Boolean, today: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fill = (mark?.fills ?: 0) > 0
    val drop = (mark?.drops ?: 0) > 0
    Column(
        modifier
            .aspectRatio(1f)
            .clip(ShapeSm)
            .background(
                when {
                    selected -> Lime
                    fill -> Lime.copy(alpha = 0.14f)
                    else -> Color.Transparent
                },
            )
            .then(if (today && !selected) Modifier.border(1.dp, Stroke, ShapeSm) else Modifier)
            .clickable(enabled = mark != null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "$day",
            style = MaterialTheme.typography.labelMedium,
            color = when {
                selected -> OnBlock
                mark != null -> TextPrimary
                else -> TextDisabled
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(6.dp)) {
            if (fill) Box(Modifier.size(5.dp).background(if (selected) OnBlock else Lime, CircleShape))
            if (drop) Box(Modifier.size(width = 6.dp, height = 2.dp).align(Alignment.CenterVertically).background(if (selected) OnBlock else Danger, ShapePill))
        }
    }
}

// ---- history ----

private val dayHeader = DateTimeFormatter.ofPattern("EEE, d MMM")
private val timeOfDay = DateTimeFormatter.ofPattern("h:mm a")

@Composable
private fun History(state: FuelLogUiState, onEdit: (FuelLogEntry) -> Unit, onDelete: (FuelLogEntry) -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    if (state.shown.isEmpty()) {
        Text(
            if (state.selectedDate != null) "Nothing logged that day." else "Nothing logged this month.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        return
    }
    val byDay = state.shown.groupBy { FuelMonth.dateOf(it, zone) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(
            text = if (state.selectedDate != null) "That day" else "History",
            count = state.shown.count { it.isFill },
        )
        byDay.forEach { (date, entries) ->
            Text(dayHeader.format(date).uppercase(), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            entries.forEach { entry ->
                val time = timeOfDay.format(Instant.ofEpochMilli(entry.ts).atZone(zone))
                if (entry.isFill) {
                    FillRow(entry, time, onEdit = { onEdit(entry) }, onDelete = { onDelete(entry) })
                } else {
                    DropRow(entry, time, onDelete = { onDelete(entry) })
                }
            }
        }
    }
}

/** A pump visit: what went in, what it cost, and what the last tank did. Edit and delete live in the ⋮ menu. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FillRow(entry: FuelLogEntry, time: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    val gained = entry.barsAfter - entry.barsBefore
    val title = when {
        gained > 0 && entry.barsBefore > 0 -> "Filled ${entry.barsBefore} → ${entry.barsAfter}"
        else -> "Filled to ${entry.barsAfter}/6"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ShapeMd)
            .background(Surface)
            .clickable(onClick = onEdit)
            .padding(start = 14.dp, top = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(Icons.Rounded.LocalGasStation, Lime)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(
                    "$time  ·  %,d km".format(entry.odometerKm) + if (entry.riderOwned) "" else "  ·  from cluster",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(entry.rupees?.let { "₹%,.0f".format(it) } ?: "—", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                entry.litres?.let { Text("%.1f L".format(it), style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
            }
            RowMenu(onEdit = onEdit, onDelete = onDelete)
        }
        Column(Modifier.padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FillGauge(entry.barsBefore, entry.barsAfter)
            val chips = buildList {
                entry.kmSincePrevFill?.let { add("$it km") }
                entry.tankKmPerL?.let { add("%.1f km/L".format(it)) }
                entry.rupeesPerLitre?.let { add("₹%.1f/L".format(it)) }
                if (!entry.isFullTank) add("Part fill")
            }
            if (chips.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { MetricChip(it) }
                }
            }
            entry.note?.takeIf { it.isNotBlank() }?.let {
                Text(
                    "“${it.trim()}”",
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A gauge step the cluster wrote on its own: one quiet line. */
@Composable
private fun DropRow(entry: FuelLogEntry, time: String, onDelete: () -> Unit) {
    val lost = (entry.barsBefore - entry.barsAfter).coerceAtLeast(1)
    val title = buildString {
        append(if (lost == 1) "1 bar" else "$lost bars")
        entry.kmSincePrevEvent?.let { append(" in $it km") }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(Icons.AutoMirrored.Rounded.TrendingDown, Danger, small = true)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text("$time  ·  %,d km".format(entry.odometerKm), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Gauge(entry.barsAfter, Modifier.width(72.dp), height = 6.dp)
        RowMenu(onEdit = null, onDelete = onDelete)
    }
}

@Composable
private fun RowIcon(icon: ImageVector, tint: Color, small: Boolean = false) {
    Box(
        Modifier
            .size(if (small) 32.dp else 40.dp)
            .background(tint.copy(alpha = 0.14f), ShapeSm),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(if (small) 18.dp else 22.dp))
    }
}

@Composable
private fun RowMenu(onEdit: (() -> Unit)?, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = TextSecondary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = SurfaceRaised) {
            if (onEdit != null) {
                DropdownMenuItem(
                    text = { Text("Edit", color = TextPrimary) },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null, tint = TextPrimary) },
                    onClick = {
                        open = false
                        onEdit()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Delete", color = Danger) },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = Danger) },
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}

/** Bars before the fill in yellow, the bars it added in lime. */
@Composable
private fun FillGauge(before: Int, after: Int) {
    val lo = before.coerceIn(0, 6)
    val hi = after.coerceIn(0, 6)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
        for (bar in 1..6) {
            val color = when {
                bar <= lo -> BlockYellow
                bar <= hi -> Lime
                else -> SurfaceRaised
            }
            Box(Modifier.weight(1f).height(6.dp).background(color, ShapeXs))
        }
    }
}

@Composable
private fun MetricChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = TextPrimary,
        modifier = Modifier
            .background(SurfaceRaised, ShapePill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// ---- tanks ----

@Composable
private fun TankList(tanks: List<TankHistoryRow>, onConfirmOutlier: (Long) -> Unit) {
    SectionCard(header = { SectionLabel(text = "Per tank") }) {
        Column(Modifier.padding(top = 8.dp)) {
            tanks.forEachIndexed { i, tank ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Stroke))
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tank.whenLabel, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Text(
                            buildString {
                                append("%.0f km · %.1f L".format(tank.km, tank.litres))
                                tank.rupeesPerKm?.let { append(" · ₹%.2f/km".format(it)) }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("%.1f".format(tank.kmPerL), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        val delta = tank.deltaPct
                        Text(
                            if (delta == null || delta == 0) "km/L" else "km/L  ${if (delta > 0) "▲" else "▼"} ${kotlin.math.abs(delta)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                delta == null || delta == 0 -> TextSecondary
                                delta > 0 -> Lime
                                else -> Danger
                            },
                        )
                    }
                }
                if (tank.outlier) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Check this fill?", style = MaterialTheme.typography.bodySmall, color = Danger, modifier = Modifier.weight(1f))
                        ChunkyButton(text = "Keep", onClick = { onConfirmOutlier(tank.closingFillId) }, style = ChunkyStyle.Outline, compact = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun KmPerLChart(values: List<Double>) {
    val peak = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    SectionCard(header = { SectionLabel(text = "km/L per tank") }) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().height(140.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.width(32.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Text("%.0f".format(peak), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("0", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        repeat(3) { Box(Modifier.fillMaxWidth().height(1.dp).background(Stroke)) }
                    }
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                        values.forEach { kmPerL ->
                            val fraction = (kmPerL / peak).toFloat().coerceIn(0.04f, 1f)
                            Box(Modifier.weight(1f).fillMaxHeight(fraction).background(BlockLavender, ShapeXs))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 32.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                values.indices.forEach { index ->
                    Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ---- log / edit ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FillSheet(state: FuelLogUiState, actions: FuelActions) {
    val editing = state.editingId != null
    ForgeSheet(
        title = if (editing) "Edit fill" else "Log a fill",
        onDismiss = actions.onCloseForm,
        primaryLabel = if (editing) "Save changes" else "Save fill",
        onPrimary = actions.onSave,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (editing) {
                    "Change odo, date, bars, litres, rupees, or the comment."
                } else {
                    "Litres, rupees and a comment are optional. If the cluster already saw bars go up at this odo, this merges into that fill."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Text("Bars after fill", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..6).forEach { bars ->
                    PillChip("$bars", state.barsAfter == bars.toString(), onClick = { actions.onBarsAfter(bars.toString()) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FuelField(state.litres, actions.onLitres, "Litres", "", KeyboardType.Decimal, modifier = Modifier.weight(1f))
                FuelField(state.rupees, actions.onRupees, "₹ paid", "", KeyboardType.Decimal, modifier = Modifier.weight(1f))
            }
            val litres = state.litres.toDoubleOrNull()
            val rupees = state.rupees.toDoubleOrNull()
            if (litres != null && rupees != null && litres > 0 && rupees > 0) {
                Text("₹%.1f a litre".format(rupees / litres), style = MaterialTheme.typography.labelMedium, color = Lime)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FuelField(state.odo, actions.onOdo, "Odo km", state.odometerKm?.toString() ?: "", KeyboardType.Number, modifier = Modifier.weight(1f))
                FuelField(state.date, actions.onDate, "Date", "yyyy-MM-dd", KeyboardType.Text, modifier = Modifier.weight(1f))
            }
            FuelField(
                value = state.note,
                onValue = actions.onNote,
                label = "Comment",
                placeholder = "HP Whitefield, full tank, slightly short-changed…",
                keyboard = KeyboardType.Text,
                singleLine = false,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Full tank", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                Switch(checked = state.fullTank, onCheckedChange = actions.onFullTank)
            }
            state.formError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Danger) }
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
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        placeholder = if (placeholder.isEmpty()) null else ({ Text(placeholder) }),
        modifier = modifier,
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
            FuelLogBody(state = FuelLogUiState(), actions = FuelActions())
        }
    }
}

@Preview
@Composable
private fun FuelLogFilledPreview() {
    val fill = FuelLogEntry(
        id = 1,
        ts = System.currentTimeMillis(),
        whenLabel = "12 Oct · 9:00 AM",
        type = "REFUEL",
        isFill = true,
        odometerKm = 12400,
        barsBefore = 2,
        barsAfter = 6,
        litres = 8.4,
        rupees = 850.0,
        note = "HP Whitefield",
        kmSincePrevFill = 240,
        tankKmPerL = 28.6,
        rupeesPerLitre = 101.0,
        rupeesPerKm = 3.5,
        riderOwned = true,
        kmSincePrevEvent = 40,
        story = "",
    )
    val drop = fill.copy(id = 2, isFill = false, type = "DROP", barsBefore = 6, barsAfter = 5, litres = null, rupees = null, note = null)
    BumblebeeTheme {
        Column(Modifier.background(Canvas)) {
            FuelLogBody(
                state = FuelLogUiState(
                    fuelBars = 5,
                    rangeKm = 160.0,
                    tankKmPerL = 32.4,
                    rupeesPerKm = 3.1,
                    tankLitres = 6.2,
                    tankRough = true,
                    refuelLabel = "This week",
                    tankTrend = listOf(28.0, 32.4, 30.1),
                    entries = listOf(drop, fill),
                    shown = listOf(drop, fill),
                    marks = mapOf(LocalDate.now().toString() to DayMarks(1, 1)),
                    insights = FuelMonthInsights(fills = 1, drops = 1, spend = 850.0, litres = 8.4, rupeesPerLitre = 101.0, kmPerL = 28.6),
                ),
                actions = FuelActions(),
            )
        }
    }
}
