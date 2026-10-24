package dev.gdk.bumblebee.ui.explorer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.PlotPoint
import dev.gdk.bumblebee.ride.RideMergeMath
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.EmptyState
import dev.gdk.bumblebee.ui.components.ForgeSheet
import dev.gdk.bumblebee.ui.components.InsightPager
import dev.gdk.bumblebee.ui.components.ListRow
import dev.gdk.bumblebee.ui.components.ListRowChevron
import dev.gdk.bumblebee.ui.components.MonthSwitcher
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.components.StatPill
import dev.gdk.bumblebee.ui.components.StatTone
import dev.gdk.bumblebee.ui.components.StatTriple
import dev.gdk.bumblebee.ui.components.StatTripleColumn
import dev.gdk.bumblebee.ui.ride.RideArchiveRow
import dev.gdk.bumblebee.ui.ride.RideCalendar
import dev.gdk.bumblebee.ui.ride.RideDetailPane
import dev.gdk.bumblebee.ui.ride.RideRow
import dev.gdk.bumblebee.ride.GoalView
import dev.gdk.bumblebee.ui.ride.formatClock
import dev.gdk.bumblebee.ui.ride.formatKm
import dev.gdk.bumblebee.ui.theme.BlockLavender
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.ShapeXs
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.ShapeLg
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.Stroke as Hairline
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import java.time.YearMonth
import kotlin.math.cos
import kotlin.math.max

@Composable
fun ExplorerScreen(
    model: ExplorerViewModel = viewModel(),
    onOpenRecap: (Int) -> Unit = {},
    onOpenMonthRecap: (YearMonth) -> Unit = {},
    onOpenDiscover: (Discover) -> Unit = {},
    onCompare: (Long, Long) -> Unit = {},
) {
    val state by model.ui.collectAsStateWithLifecycle()
    val detail = state.detail
    if (detail != null) {
        BackHandler { model.closeDetail() }
        RideDetailPane(
            detail,
            onBack = { model.closeDetail() },
            onSplit = { model.splitOpenRide() },
            onRename = { model.renameRide(detail.row.id, it) },
        )
        return
    }
    ExplorerBody(
        state = state,
        onShowMonth = model::showMonth,
        onSelectDate = model::selectDate,
        onFocusDate = model::focusDate,
        onMergeJourney = model::mergeSuggestedJourney,
        onMergeSelected = model::mergeSelected,
        onOpenRide = model::openRide,
        onToggleSelected = model::toggleSelected,
        onOpenRecap = onOpenRecap,
        onOpenMonthRecap = onOpenMonthRecap,
        onOpenDiscover = onOpenDiscover,
        onCompare = onCompare,
        onSetGoal = model::setGoal,
    )
}

@Composable
private fun ExplorerBody(
    state: ExplorerUiState,
    onShowMonth: (YearMonth) -> Unit,
    onSelectDate: (String?) -> Unit,
    onFocusDate: (String) -> Unit,
    onMergeJourney: (List<Long>) -> Unit,
    onMergeSelected: () -> Unit,
    onOpenRide: (Long) -> Unit,
    onToggleSelected: (Long) -> Unit,
    onOpenRecap: (Int) -> Unit = {},
    onOpenMonthRecap: (YearMonth) -> Unit = {},
    onOpenDiscover: (Discover) -> Unit = {},
    onCompare: (Long, Long) -> Unit = {},
    onSetGoal: (Double?) -> Unit = {},
) {
    var goalOpen by remember { mutableStateOf(false) }
    var reviewOpen by remember(state.month, state.selectedDate) { mutableStateOf(false) }
    val day = state.day
    val patterns = patternPages(state)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Explorer",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (state.streak.current > 0) {
                    StatPill(
                        icon = Icons.Rounded.LocalFireDepartment,
                        value = if (state.streak.current == 1) "1 day" else "${state.streak.current} days",
                        contentDescription = "Current ride streak",
                    )
                }
            }
            MonthSwitcher(month = state.month, onMonthChange = onShowMonth)
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StatTriple(
                columns = listOf(
                    StatTripleColumn(kmValue(state.monthKm), "Km", StatTone.Plain),
                    StatTripleColumn(
                        if (state.monthRideCount == 0) "—" else state.monthRideCount.toString(),
                        "Rides",
                        StatTone.Plain,
                    ),
                    StatTripleColumn(
                        if (state.monthRideCount == 0) "—" else formatClock(state.monthMovingSec),
                        "Moving",
                        StatTone.Plain,
                    ),
                ),
            )
            state.vsPrevious?.let { caption ->
                Text(caption, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            GoalLine(state.goal, onClick = { goalOpen = true })
        }

        RideCalendar(
            month = state.month,
            kmByDate = state.kmByDate,
            selected = state.selectedDate,
            onSelect = { onSelectDate(it) },
        )

        if (state.journeys.isNotEmpty()) {
            SectionCard {
                val count = state.journeys.size
                ListRow(
                    title = if (count == 1) "1 outing looks split" else "$count outings look split",
                    subtitle = "Review",
                    onClick = { reviewOpen = true },
                    showDivider = false,
                    trailing = { ListRowChevron() },
                )
            }
        }

        RideList(
            state = state,
            day = day,
            onSelectDate = onSelectDate,
            onShowMonth = onShowMonth,
            onMergeSelected = onMergeSelected,
            onOpenRide = onOpenRide,
            onToggleSelected = onToggleSelected,
            onCompare = onCompare,
        )

        if (patterns.isNotEmpty()) {
            InsightPager(pageCount = patterns.size) { page ->
                val kind = patterns[page]
                if (kind == PatternKind.RECAP) {
                    PatternCard(monthName(state.month) + " recap", onClick = { onOpenMonthRecap(state.month) }) {
                        MonthRecap(state)
                    }
                } else {
                    PatternCard(kind.title) {
                        PatternBody(kind, state)
                    }
                }
            }
        }

        DiscoverStrip(onOpen = onOpenDiscover, onYearRecap = { onOpenRecap(state.month.year) })
    }

    if (goalOpen) {
        GoalSheet(
            current = state.goal?.goalKm,
            onSave = {
                goalOpen = false
                onSetGoal(it)
            },
            onDismiss = { goalOpen = false },
        )
    }

    if (reviewOpen && state.journeys.isNotEmpty()) {
        ForgeSheet(
            title = "Review splits",
            onDismiss = { reviewOpen = false },
            primaryLabel = "Done",
            onPrimary = { reviewOpen = false },
        ) {
            state.journeys.forEachIndexed { index, card ->
                if (index > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .height(1.dp)
                            .background(Hairline),
                    )
                }
                Column(
                    Modifier.padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(card.caption, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    card.legs.forEach { leg ->
                        Text(leg, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    ChunkyButton(
                        text = "Merge ${card.rideIds.size} into one",
                        onClick = {
                            reviewOpen = false
                            onMergeJourney(card.rideIds)
                        },
                        style = ChunkyStyle.Lime,
                    )
                    ChunkyButton(
                        text = "That day",
                        onClick = {
                            reviewOpen = false
                            onFocusDate(card.dateIso)
                        },
                        style = ChunkyStyle.Outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun RideList(
    state: ExplorerUiState,
    day: DayDigest?,
    onSelectDate: (String?) -> Unit,
    onShowMonth: (YearMonth) -> Unit,
    onMergeSelected: () -> Unit,
    onOpenRide: (Long) -> Unit,
    onToggleSelected: (Long) -> Unit,
    onCompare: (Long, Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (day != null) {
            val caption = day.story.ifBlank { day.title }
            if (caption.isNotBlank()) {
                Text(
                    caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SectionLabel(text = if (day != null) "Rides this day" else "Rides this month")
        if (state.selectedIds.size >= 2) {
            val picked = state.rides.filter { it.id in state.selectedIds }
            val preview = RideMergeMath.preview(
                km = picked.map { it.km },
                movingSec = picked.map { it.movingSec },
                startTs = picked.map { it.startTs },
                endTs = picked.map { it.endTs ?: it.startTs },
            )
            if (preview != null) {
                Text(
                    "${formatKm(preview.km)} · ${formatClock(preview.movingSec)} moving · ${preview.stops} stops",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
            }
            ChunkyButton(
                text = "Merge ${preview?.rideCount ?: state.selectedIds.size}",
                onClick = onMergeSelected,
                style = ChunkyStyle.Lime,
            )
            if (state.selectedIds.size == 2) {
                val (a, b) = state.selectedIds.toList()
                ChunkyButton(text = "Compare these two", onClick = { onCompare(a, b) }, style = ChunkyStyle.Outline)
            }
        }
        if (state.rides.isEmpty()) {
            EmptyState(
                headline = if (day != null) "NO RIDES THAT DAY" else "NO RIDES THIS MONTH",
                subtitle = if (day != null) {
                    "Nothing saved on this date."
                } else {
                    "The road's not going to ride itself."
                },
                primaryLabel = if (day != null) "Whole month" else "Previous month",
                onPrimary = {
                    if (day != null) onSelectDate(null) else onShowMonth(state.month.minusMonths(1))
                },
            )
        } else {
            SectionCard {
                state.rides.forEachIndexed { index, row ->
                    RideArchiveRow(
                        row = row,
                        track = state.tracks[row.id].orEmpty(),
                        selected = row.id in state.selectedIds,
                        showDivider = index < state.rides.lastIndex,
                        onClick = {
                            if (state.selectedIds.isEmpty()) onOpenRide(row.id) else onToggleSelected(row.id)
                        },
                        onLongClick = { onToggleSelected(row.id) },
                    )
                }
            }
        }
    }
}

private enum class PatternKind(val title: String) {
    RECAP("Month recap"),
    MONTHS("Last 6 months"),
    WEEKDAYS("Weekdays"),
    TIME("Time of day"),
    ROUTES("Repeat routes"),
    LONGEST("Longest ride"),
}

/** The month's recap leads; the patterns follow. */
private fun patternPages(state: ExplorerUiState): List<PatternKind> = buildList {
    if (state.monthRideCount > 0) add(PatternKind.RECAP)
    if (state.monthTotals.count { it.km > 0 } >= 2) add(PatternKind.MONTHS)
    if (state.weekdayKm.any { it > 0.0 }) add(PatternKind.WEEKDAYS)
    if (state.timeOfDayKm.any { it > 0.0 }) add(PatternKind.TIME)
    if (state.routes.isNotEmpty()) add(PatternKind.ROUTES)
    if (state.longest != null) add(PatternKind.LONGEST)
}

@Composable
private fun PatternCard(
    title: String,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ShapeLg)
            .background(BlockLavender)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = {
            Text(title, style = MaterialTheme.typography.titleMedium, color = OnBlock)
            content()
        },
    )
}

@Composable
private fun PatternBody(kind: PatternKind, state: ExplorerUiState) {
    when (kind) {
        PatternKind.WEEKDAYS -> KmBars(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), state.weekdayKm)
        PatternKind.TIME -> KmBars(listOf("AM", "PM", "Eve", "Night"), state.timeOfDayKm)
        PatternKind.ROUTES -> RouteLines(state)
        PatternKind.LONGEST -> LongestLine(state.longest)
        PatternKind.RECAP -> MonthRecap(state)
        PatternKind.MONTHS -> MonthBars(state)
    }
}

/** Km in each of the last six months, this one highlighted, with where its pace lands. */
@Composable
private fun MonthBars(state: ExplorerUiState) {
    val totals = state.monthTotals
    val peak = maxOf(totals.maxOfOrNull { it.km } ?: 0.0, state.projectedKm ?: 0.0).coerceAtLeast(1.0)
    Row(
        Modifier.fillMaxWidth().height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        totals.forEach { t ->
            val current = t.month == state.month
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (t.km > 0) String.format("%.0f", t.km) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnBlock,
                    maxLines = 1,
                )
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                    val projected = state.projectedKm?.takeIf { current }
                    if (projected != null) {
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight((projected / peak).toFloat().coerceIn(0.02f, 1f))
                                .background(OnBlock.copy(alpha = 0.15f), ShapeXs),
                        )
                    }
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight((t.km / peak).toFloat().coerceIn(0.02f, 1f))
                            .background(if (current) OnBlock else OnBlock.copy(alpha = 0.45f), ShapeXs),
                    )
                }
                Text(
                    t.month.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnBlock,
                )
            }
        }
    }
    state.projectedKm?.let {
        Text(
            "On pace for ${String.format("%.0f", it)} km this month",
            style = MaterialTheme.typography.bodyMedium,
            color = OnBlock,
        )
    }
}

/** The monthly goal as a thin bar and what it takes a day; a quiet link when there is none. */
@Composable
private fun GoalLine(goal: GoalView?, onClick: () -> Unit) {
    if (goal == null) {
        Text(
            "Set a monthly goal",
            style = MaterialTheme.typography.labelLarge,
            color = Lime,
            modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
        )
        return
    }
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(8.dp).clip(ShapePill).background(SurfaceRaised)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(goal.fraction.coerceAtLeast(0.02f)).background(Lime, ShapePill))
        }
        Text(
            when {
                goal.done -> String.format("Goal of %.0f km reached", goal.goalKm)
                goal.perDayKm != null -> String.format("%.0f of %.0f km goal · %.0f km a day to make it", goal.km, goal.goalKm, goal.perDayKm)
                else -> String.format("%.0f of %.0f km goal", goal.km, goal.goalKm)
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (goal.done) Lime else TextSecondary,
        )
    }
}

@Composable
private fun GoalSheet(current: Double?, onSave: (Double?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current?.let { String.format("%.0f", it) } ?: "") }
    ForgeSheet(title = "Monthly goal", onDismiss = onDismiss, primaryLabel = "Save goal", onPrimary = { onSave(text.toDoubleOrNull()) }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Km you want to ride each month. The month card tracks it.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(5) },
                label = { Text("Km a month") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            if (current != null) {
                ChunkyButton(text = "Remove goal", onClick = { onSave(null) }, style = ChunkyStyle.Outline, compact = true)
            }
        }
    }
}

/** The month at a glance: its numbers, every track on one map, and the way into the full recap. */
@Composable
private fun MonthRecap(state: ExplorerUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        RecapNumber(kmValue(state.monthKm), "km")
        RecapNumber(state.monthRideCount.toString(), if (state.monthRideCount == 1) "ride" else "rides")
        RecapNumber(formatClock(state.monthMovingSec), "moving")
        state.longest?.let { RecapNumber(String.format("%.0f", it.km), "longest km") }
    }
    val tracks = state.tracks.values.filter { it.size >= 2 }
    if (tracks.isNotEmpty()) MonthMap(tracks)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Open recap", style = MaterialTheme.typography.labelLarge, color = OnBlock)
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = OnBlock, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RecapNumber(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, color = OnBlock)
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnBlock.copy(alpha = 0.7f))
    }
}

private fun monthName(month: YearMonth): String =
    month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())

@Composable
private fun KmBars(labels: List<String>, values: List<Double>) {
    val peak = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    labels.zip(values).forEach { (label, km) ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = OnBlock,
                modifier = Modifier.width(40.dp),
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(ShapePill)
                    .background(OnBlock.copy(alpha = 0.2f)),
            ) {
                if (km > 0) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(((km / peak).toFloat()).coerceIn(0.04f, 1f))
                            .background(OnBlock),
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteLines(state: ExplorerUiState) {
    state.routes.take(3).forEach { route ->
        Text(
            buildString {
                append("${route.runCount} runs")
                route.medianKm?.let { append(" · median ${"%.1f".format(it)} km") }
                route.litresEst?.let { append(" · est. ${"%.1f".format(it)} L") }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = OnBlock,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LongestLine(ride: LongestRide?) {
    if (ride == null) return
    Text(ride.label, style = MaterialTheme.typography.bodyMedium, color = OnBlock)
    Text(
        "${formatKm(ride.km)} · ${formatClock(ride.movingSec)} moving",
        style = MaterialTheme.typography.bodyMedium,
        color = OnBlock,
    )
}

@Composable
private fun MonthMap(tracks: List<List<GpsFix>>) {
    val paths = remember(tracks) { projectTracks(tracks) }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(140.dp),
    ) {
        paths.forEach { points ->
            if (points.size < 2) return@forEach
            val path = Path()
            points.forEachIndexed { index, point ->
                val at = Offset(point.x * size.width, point.y * size.height)
                if (index == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
            }
            drawPath(
                path,
                OnBlock,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

private fun projectTracks(tracks: List<List<GpsFix>>): List<List<PlotPoint>> {
    val usable = tracks.filter { it.size >= 2 }
    if (usable.isEmpty()) return emptyList()
    val points = usable.flatten()
    val minLat = points.minOf { it.lat }
    val maxLat = points.maxOf { it.lat }
    val minLng = points.minOf { it.lng }
    val maxLng = points.maxOf { it.lng }
    val scaleLng = cos(Math.toRadians((minLat + maxLat) / 2.0)).coerceAtLeast(0.2)
    val dLat = max(maxLat - minLat, 1e-5)
    val dLng = max((maxLng - minLng) * scaleLng, 1e-5)
    val span = max(dLat, dLng)
    val padLat = (span - dLat) / 2.0
    val padLng = (span - dLng) / 2.0
    return usable.map { ride ->
        ride.map { point ->
            val x = (((point.lng - minLng) * scaleLng + padLng) / span).toFloat()
            val y = (1.0 - ((point.lat - minLat + padLat) / span)).toFloat()
            PlotPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        }
    }
}

private fun kmValue(km: Double): String = if (km <= 0.0) "—" else String.format("%.0f", km)

@Preview
@Composable
private fun ExplorerEmptyPreview() {
    BumblebeeTheme {
        Column(Modifier.background(Canvas)) {
            ExplorerBody(
                state = ExplorerUiState(monthTitle = "October 2026"),
                onShowMonth = {},
                onSelectDate = {},
                onFocusDate = {},
                onMergeJourney = {},
                onMergeSelected = {},
                onOpenRide = {},
                onToggleSelected = {},
            )
        }
    }
}

@Preview
@Composable
private fun ExplorerMonthPreview() {
    BumblebeeTheme {
        Column(Modifier.background(Canvas)) {
            ExplorerBody(
                state = ExplorerUiState(
                    monthTitle = "October 2026",
                    monthKm = 42.0,
                    monthRideCount = 2,
                    monthMovingSec = 1800,
                    vsPrevious = "vs Sep +12%",
                    kmByDate = mapOf("2026-10-12" to 12.0, "2026-10-18" to 30.0),
                    weekdayKm = listOf(0.0, 12.0, 0.0, 0.0, 30.0, 0.0, 0.0),
                    longest = LongestRide(id = 1, label = "18 Oct", km = 30.0, movingSec = 2400),
                    streak = dev.gdk.bumblebee.ride.StreakInfo(5, 9),
                    rides = listOf(
                        RideRow(
                            id = 1,
                            startTs = 1,
                            endTs = 2,
                            km = 12.4,
                            movingSec = 1800,
                            idleSec = 200,
                            maxSpeedKmh = 62,
                            avgMovingSpeedKmh = 24.0,
                            headline = "12 Oct · morning",
                            odoStartKm = 12_340,
                            odoEndKm = 12_352,
                        ),
                    ),
                    journeys = listOf(
                        JourneyCard(
                            rideIds = listOf(1, 2),
                            dateIso = "2026-10-18",
                            caption = "Odo kept going after an engine-off",
                            legs = listOf("08:10 · 12.0 km", "09:40 · 18.0 km"),
                            totalKm = 30,
                        ),
                    ),
                ),
                onShowMonth = {},
                onSelectDate = {},
                onFocusDate = {},
                onMergeJourney = {},
                onMergeSelected = {},
                onOpenRide = {},
                onToggleSelected = {},
            )
        }
    }
}
