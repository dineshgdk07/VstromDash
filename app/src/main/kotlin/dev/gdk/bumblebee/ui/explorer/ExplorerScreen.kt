package dev.gdk.bumblebee.ui.explorer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyIconButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.EmptyState
import dev.gdk.bumblebee.ui.components.PillChip
import dev.gdk.bumblebee.ui.components.PillChipRow
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.ride.MixBar
import dev.gdk.bumblebee.ui.ride.PeriodChip
import dev.gdk.bumblebee.ui.ride.RideCalendar
import dev.gdk.bumblebee.ui.ride.RideDetailPane
import dev.gdk.bumblebee.ui.ride.RideListItem
import dev.gdk.bumblebee.ui.ride.RideRow
import dev.gdk.bumblebee.ui.ride.formatClock
import dev.gdk.bumblebee.ui.ride.formatKm
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.ScreenGutter
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun ExplorerScreen(model: ExplorerViewModel = viewModel()) {
    val state by model.ui.collectAsStateWithLifecycle()
    val detail = state.detail
    if (detail != null) {
        BackHandler { model.closeDetail() }
        RideDetailPane(detail, onBack = { model.closeDetail() }, onSplit = { model.splitOpenRide() })
        return
    }
    ExplorerBody(
        state = state,
        onShiftMonth = model::shiftMonth,
        onSelectDate = model::selectDate,
        onMergeJourney = model::mergeSuggestedJourney,
        onMergeSelected = model::mergeSelected,
        onOpenRide = model::openRide,
        onToggleSelected = model::toggleSelected,
    )
}

@Composable
private fun ExplorerBody(
    state: ExplorerUiState,
    onShiftMonth: (Int) -> Unit,
    onSelectDate: (String?) -> Unit,
    onMergeJourney: (List<Long>) -> Unit,
    onMergeSelected: () -> Unit,
    onOpenRide: (Long) -> Unit,
    onToggleSelected: (Long) -> Unit,
) {
    val day = state.day
    val monthLabel = state.monthTitle.ifBlank {
        "${state.month.month.name.lowercase().replaceFirstChar { it.titlecase() }} ${state.month.year}"
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Explorer", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text("Where you've been", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

        DayFilterRow(
            month = state.month,
            riddenDays = state.kmByDate.keys,
            selectedDate = state.selectedDate,
            onSelectDate = onSelectDate,
        )

        if (state.journeys.isNotEmpty()) {
            SectionCard(header = { SectionLabel(text = "One outing, several sessions") }) {
                Column(
                    Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Odo kept going after an engine-off, including rides that ran past midnight. Merge if that was one outing — chai, petrol, a wait — not a new trip.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    state.journeys.forEach { card ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(SurfaceRaised, ShapeMd)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(card.caption, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            card.legs.forEach { leg ->
                                Text(leg, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            ChunkyButton(
                                text = "Merge ${card.rideIds.size} into one",
                                onClick = { onMergeJourney(card.rideIds) },
                                style = ChunkyStyle.Lime,
                            )
                            ChunkyButton(
                                text = "That day",
                                onClick = { onSelectDate(card.dateIso) },
                                style = ChunkyStyle.Outline,
                            )
                        }
                    }
                }
            }
        }

        SectionCard(header = { SectionLabel(text = "Streak") }) {
            Row(
                Modifier.padding(top = 12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PeriodChip("Streak", if (state.streak.current > 0) "${state.streak.current} days" else "—", Modifier.weight(1f))
                PeriodChip("Best streak", if (state.streak.longest > 0) "${state.streak.longest}" else "—", Modifier.weight(1f))
            }
        }
        MixBar("Weekdays this month", listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), state.weekdayKm)
        MixBar("Time of day", listOf("AM", "PM", "Eve", "Night"), state.timeOfDayKm)
        RecapCard(state)
        if (state.routes.isNotEmpty()) {
            SectionCard(header = { SectionLabel(text = "Repeat routes") }) {
                Column(
                    Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Same corridor, GPS snapped to ~100 m cells. Litres are from fill km/L when we have it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    state.routes.take(5).forEach { route ->
                        Text(
                            buildString {
                                append("${route.runCount} runs")
                                route.medianKm?.let { append("  ·  median ${"%.1f".format(it)} km") }
                                route.litresEst?.let { append("  ·  est. ${"%.1f".format(it)} L") }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                        )
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ChunkyIconButton(
                icon = Icons.Rounded.ChevronLeft,
                onClick = { onShiftMonth(-1) },
                contentDescription = "Previous month",
            )
            Text(
                monthLabel,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            ChunkyIconButton(
                icon = Icons.Rounded.ChevronRight,
                onClick = { onShiftMonth(1) },
                contentDescription = "Next month",
            )
        }
        RideCalendar(
            month = state.month,
            kmByDate = state.kmByDate,
            selected = state.selectedDate,
            onSelect = { onSelectDate(it) },
            monthKm = state.monthKm,
            monthDays = state.monthDays,
        )

        if (day != null) {
            StoryCard(label = "That day", title = day.title, story = day.story) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PeriodChip("Km", formatKm(day.km), Modifier.weight(1f))
                    PeriodChip("Rides", day.rides.toString(), Modifier.weight(1f))
                    PeriodChip("Moving", formatClock(day.movingSec), Modifier.weight(1f))
                }
                if (day.maxSpeedKmh > 0) {
                    Text(
                        "Fastest moment ${day.maxSpeedKmh} km/h",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                ChunkyButton(
                    text = "Whole month",
                    onClick = { onSelectDate(null) },
                    style = ChunkyStyle.Outline,
                )
            }
        } else {
            StoryCard(label = "This month", title = monthLabel, story = state.monthStory)
        }

        if (state.rides.isEmpty()) {
            EmptyState(
                headline = if (day != null) "No rides that day" else "No rides this month",
                subtitle = if (day != null) "No rides saved on this date." else "No rides in this month yet.",
                primaryLabel = if (day != null) "Whole month" else "Previous month",
                onPrimary = { if (day != null) onSelectDate(null) else onShiftMonth(-1) },
            )
        } else {
            SectionLabel(text = if (day != null) "Rides this day" else "Rides this month")
            if (state.selectedIds.size >= 2) {
                val picked = state.rides.filter { it.id in state.selectedIds }
                val preview = dev.gdk.bumblebee.ride.RideMergeMath.preview(
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
            }
            state.rides.forEach { row ->
                RideListItem(
                    row,
                    selected = row.id in state.selectedIds,
                    onClick = {
                        if (state.selectedIds.isEmpty()) onOpenRide(row.id) else onToggleSelected(row.id)
                    },
                    onLongClick = { onToggleSelected(row.id) },
                )
            }
        }
    }
}

@Composable
private fun DayFilterRow(
    month: YearMonth,
    riddenDays: Set<String>,
    selectedDate: String?,
    onSelectDate: (String?) -> Unit,
) {
    val days = (riddenDays + listOfNotNull(selectedDate))
        .filter { runCatching { YearMonth.from(LocalDate.parse(it)) == month }.getOrDefault(false) }
        .distinct()
        .sorted()
    val bleed = ScreenGutter
    Box(
        Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val extra = bleed.roundToPx()
                val placeable = measurable.measure(
                    constraints.copy(maxWidth = constraints.maxWidth + extra * 2),
                )
                layout(constraints.maxWidth, placeable.height) {
                    placeable.place(-extra, 0)
                }
            },
    ) {
        PillChipRow(Modifier.fillMaxWidth()) {
            PillChip(
                text = "Month",
                selected = selectedDate == null,
                onClick = { if (selectedDate != null) onSelectDate(null) },
            )
            days.forEach { date ->
                PillChip(
                    text = chipDay(date),
                    selected = selectedDate == date,
                    onClick = { onSelectDate(date) },
                )
            }
        }
    }
}

private fun chipDay(iso: String): String =
    runCatching {
        DateTimeFormatter.ofPattern("d MMM").format(LocalDate.parse(iso))
    }.getOrDefault(iso)

@Composable
private fun RecapCard(state: ExplorerUiState) {
    val wrap = state.wrapped
    SectionCard(header = { SectionLabel(text = "${state.month.year} recap") }) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (wrap == null) {
                Text(
                    "This fills in from finished rides this year. Start a ride or run the demo on Ride.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            } else {
                Text(
                    String.format("%.0f km across %d rides. Longest %.1f km. Top %d km/h.", wrap.totalKm, wrap.rideCount, wrap.longestKm, wrap.topSpeedKmh),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                )
                wrap.busiestWeekday?.let {
                    Text("Busiest day: $it", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                wrap.bestTankKmPerL?.let {
                    Text(
                        "Best tank ${"%.1f".format(it)} km/L" + (wrap.worstTankKmPerL?.let { w -> " · worst ${"%.1f".format(w)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                wrap.litresEst?.let {
                    Text(
                        String.format(
                            "est. %.0f L%s",
                            it,
                            if (wrap.litresFromFills) " from fills" else " — no fill km/L yet",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                wrap.spendRupees?.let {
                    Text(
                        "₹${"%.0f".format(it)} logged" + if (wrap.spendPartial) " (partial — missing prices)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryCard(
    label: String,
    title: String,
    story: String,
    extra: @Composable (() -> Unit)? = null,
) {
    SectionCard(header = { SectionLabel(text = label) }) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title.isNotBlank()) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            }
            if (story.isNotBlank()) {
                Text(story, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            }
            extra?.invoke()
        }
    }
}

@Preview
@Composable
private fun ExplorerEmptyPreview() {
    BumblebeeTheme {
        Column(Modifier.background(Canvas)) {
            ExplorerBody(
                state = ExplorerUiState(monthTitle = "October 2026"),
                onShiftMonth = {},
                onSelectDate = {},
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
                    monthDays = 2,
                    kmByDate = mapOf("2026-10-12" to 12.0, "2026-10-18" to 30.0),
                    weekdayKm = listOf(0.0, 12.0, 0.0, 0.0, 30.0, 0.0, 0.0),
                    monthStory = "Two rides, mostly city.",
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
                        ),
                    ),
                ),
                onShiftMonth = {},
                onSelectDate = {},
                onMergeJourney = {},
                onMergeSelected = {},
                onOpenRide = {},
                onToggleSelected = {},
            )
        }
    }
}
