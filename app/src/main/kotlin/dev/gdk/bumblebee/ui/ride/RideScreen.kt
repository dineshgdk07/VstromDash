package dev.gdk.bumblebee.ui.ride

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.BuildConfig
import dev.gdk.bumblebee.ride.SpeedSource
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.DropdownLabel
import dev.gdk.bumblebee.ui.components.EmptyState
import dev.gdk.bumblebee.ui.components.PillChip
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.components.StatTone
import dev.gdk.bumblebee.ui.components.StatTriple
import dev.gdk.bumblebee.ui.components.StatTripleColumn
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.NumberLarge
import dev.gdk.bumblebee.ui.theme.TextDisabled
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import dev.gdk.bumblebee.ui.theme.Warning

private enum class RideTab { Overview, Speed, Fuel, Route }

private enum class RidePeriod(val label: String) {
    Today("Today"),
    Week("7 days"),
    Month("Month"),
    ;

    fun next(): RidePeriod = entries[(ordinal + 1) % entries.size]
}

@Composable
fun RideScreen(
    model: RideViewModel = viewModel(),
    onOpenExplorer: () -> Unit = {},
) {
    val state by model.ui.collectAsStateWithLifecycle()
    val detail = state.detail
    if (detail != null) {
        BackHandler { model.closeDetail() }
        RideDetailPane(detail = detail, onBack = { model.closeDetail() })
        return
    }
    RideArchive(
        state = state,
        onOpenExplorer = onOpenExplorer,
        onOpenRide = { model.openRide(it) },
        onStart = { model.startGpsRide() },
        onStop = { model.stopRide() },
        onEndPaused = { model.endPausedRide() },
        onDemo = { model.startDemo() },
    )
}

@Composable
private fun RideArchive(
    state: RideUiState,
    onOpenExplorer: () -> Unit,
    onOpenRide: (Long) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onEndPaused: () -> Unit,
    onDemo: () -> Unit,
) {
    val tabs = rideTabs(state)
    var tab by remember { mutableStateOf(RideTab.Overview) }
    if (tab !in tabs) tab = RideTab.Overview
    var period by remember { mutableStateOf(RidePeriod.Today) }
    val noRides = state.recent.isEmpty() && state.phase != RidePhase.LIVE && state.phase != RidePhase.PAUSED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RideTitle(state)
        if (tabs.size > 1) {
            TabRow(tabs = tabs, selected = tab, onSelect = { tab = it })
        }
        when (tab) {
            RideTab.Overview -> {
                if (noRides) {
                    EmptyState(
                        headline = "No rides yet",
                        subtitle = "The road's not going to ride itself.",
                        primaryLabel = "Start ride",
                        onPrimary = onStart,
                        linkLabel = if (BuildConfig.DEBUG && !state.linked) "Run a demo" else null,
                        onLink = if (BuildConfig.DEBUG && !state.linked) onDemo else null,
                    )
                } else {
                    PeriodScope(period = period, onCycle = { period = period.next() })
                    DistanceTriple(state, period)
                    if (state.phase == RidePhase.LIVE) LiveSpeed(state)
                    if (state.phase == RidePhase.PAUSED) PausedNote(state)
                    state.headline?.let { InsightCard(it, featured = true) }
                    if (state.recent.isNotEmpty()) {
                        SectionLabel(text = "Recent")
                        state.recent.forEach { row ->
                            RideListItem(row, onClick = { onOpenRide(row.id) })
                        }
                        ChunkyButton(
                            text = "All rides",
                            onClick = onOpenExplorer,
                            style = ChunkyStyle.Outline,
                        )
                    }
                    Text(state.status, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    RideControls(state, onStart, onStop, onEndPaused, onDemo)
                }
            }
            RideTab.Speed -> {
                if (state.phase == RidePhase.LIVE) LiveSpeed(state)
                if (state.lastSpeeds.size >= 2) SpeedTimeCard(state.lastSpeeds)
            }
            RideTab.Fuel -> FuelFacts(state)
            RideTab.Route -> {
                if (state.lastTrack.size >= 2) {
                    TrackPathCard(state.lastTrack, speeds = state.lastSpeeds, title = "Last ride")
                }
                ParkedRow(state.parked)
            }
        }
    }
}

private fun rideTabs(state: RideUiState): List<RideTab> = buildList {
    add(RideTab.Overview)
    if (state.lastSpeeds.size >= 2 || state.phase == RidePhase.LIVE) add(RideTab.Speed)
    if (state.tripAKm != null || state.tripBKm != null || state.economyKml != null || state.rangeKm != null) {
        add(RideTab.Fuel)
    }
    if (state.lastTrack.size >= 2 || state.parked != null) add(RideTab.Route)
}

@Composable
private fun RideTitle(state: RideUiState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = when (state.phase) {
                    RidePhase.LIVE -> "Live ride"
                    RidePhase.PAUSED -> "Paused"
                    else -> "Ride"
                },
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
            Text(
                text = when (state.phase) {
                    RidePhase.LIVE -> "What's happening now"
                    RidePhase.PAUSED -> "Paused ${state.pausedMin} min"
                    RidePhase.SUMMARY -> "This session"
                    RidePhase.IDLE -> "Ready when you are"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        if (state.phase == RidePhase.LIVE) {
            Text("REC", style = MaterialTheme.typography.labelLarge, color = Danger)
        }
        if (state.phase == RidePhase.PAUSED) {
            Text("PAUSE", style = MaterialTheme.typography.labelLarge, color = Warning)
        }
    }
}

@Composable
private fun TabRow(tabs: List<RideTab>, selected: RideTab, onSelect: (RideTab) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { item ->
            PillChip(
                text = item.name,
                selected = item == selected,
                onClick = { onSelect(item) },
            )
        }
    }
}

@Composable
private fun PeriodScope(period: RidePeriod, onCycle: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        DropdownLabel(text = period.label, onClick = onCycle)
    }
}

@Composable
private fun DistanceTriple(state: RideUiState, period: RidePeriod) {
    val headline = when (period) {
        RidePeriod.Today -> formatKm(state.todayKm)
        RidePeriod.Week -> formatKm(state.weekKm)
        RidePeriod.Month -> formatKm(state.monthKm)
    }
    StatTriple(
        headlineLabel = "Distance",
        headlineValue = headline,
        columns = listOf(
            StatTripleColumn(formatKm(state.todayKm), "Today", StatTone.Neutral),
            StatTripleColumn(formatKm(state.weekKm), "7 days", StatTone.Neutral),
            StatTripleColumn(formatKm(state.monthKm), "Month", StatTone.Neutral),
        ),
    )
    if (state.pbKmh > 0) {
        Text(
            text = "PB ${state.pbKmh} km/h",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun LiveSpeed(state: RideUiState) {
    val source = when (state.speed.source) {
        SpeedSource.GPS -> "GPS"
        SpeedSource.BLE -> "BLE"
        SpeedSource.NONE -> "no speed"
    }
    val tone = when (state.speed.source) {
        SpeedSource.GPS -> Lime
        SpeedSource.BLE -> TextSecondary
        SpeedSource.NONE -> TextDisabled
    }
    SectionCard {
        Text(
            text = state.speed.speedKmh.toString(),
            style = NumberLarge,
            color = TextPrimary,
        )
        Text("km/h", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(
            text = buildString {
                append(source)
                state.heading?.let { append("  ·  $it") }
                append("  ·  ${formatClock(state.elapsedSec)}")
                append("  ·  ${formatKm(state.distanceKm)}")
            },
            style = MaterialTheme.typography.labelMedium,
            color = tone,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun PausedNote(state: RideUiState) {
    SectionCard {
        Text(
            "Ride paused · ${state.pausedMin} min · resumes when you start the bike",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
        Text(
            "${formatKm(state.distanceKm)}  ·  ${formatClock(state.movingSec)} moving  ·  ${formatClock(state.elapsedSec)} elapsed",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun FuelFacts(state: RideUiState) {
    SectionCard(header = { SectionLabel(text = "From the cluster") }) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FuelLine("Trip A", state.tripAKm?.let { String.format("%.1f km", it) })
            FuelLine("Trip B", state.tripBKm?.let { String.format("%.1f km", it) })
            FuelLine("km/L", state.economyKml?.let { String.format("%.1f", it) })
            FuelLine("Range", state.rangeKm?.let { "est. ${it.toInt()} km" })
        }
    }
}

@Composable
private fun FuelLine(label: String, value: String?) {
    if (value == null) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
    }
}

@Composable
private fun RideControls(
    state: RideUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onEndPaused: () -> Unit,
    onDemo: () -> Unit,
) {
    when (state.phase) {
        RidePhase.PAUSED -> {
            Text(state.status, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            ChunkyButton(text = "End ride now", onClick = onEndPaused, style = ChunkyStyle.Danger)
        }
        RidePhase.LIVE -> {
            if (state.linked || state.demoRunning) {
                Text(
                    if (state.demoRunning) {
                        "Demo is writing a full archive. It stops on its own."
                    } else {
                        "This ride stays open through a stop. End it from the pause, or wait out the Garage pause window."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            } else {
                HoldToStopButton(onConfirm = onStop)
            }
        }
        RidePhase.IDLE, RidePhase.SUMMARY -> {
            ChunkyButton(text = "Start ride", onClick = onStart, enabled = !state.demoRunning, style = ChunkyStyle.Lime)
            if (BuildConfig.DEBUG) {
                ChunkyButton(
                    text = if (state.demoRunning) "Capturing demo…" else "Developer demo",
                    onClick = onDemo,
                    enabled = !state.demoRunning && !state.linked,
                    style = ChunkyStyle.Outline,
                )
            }
        }
    }
}

@Composable
private fun HoldToStopButton(onConfirm: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        kotlinx.coroutines.delay(800)
        if (pressed) onConfirm()
    }
    ChunkyButton(
        text = if (pressed) "Keep holding…" else "Hold to stop",
        onClick = {},
        style = ChunkyStyle.Danger,
        interactionSource = interaction,
    )
}

@Preview
@Composable
private fun RideEmptyPreview() {
    BumblebeeTheme {
        Column(Modifier.background(Canvas)) {
            RideArchive(
                state = RideUiState(),
                onOpenExplorer = {},
                onOpenRide = {},
                onStart = {},
                onStop = {},
                onEndPaused = {},
                onDemo = {},
            )
        }
    }
}

@Preview
@Composable
private fun RideArchivePreview() {
    BumblebeeTheme {
        Column(Modifier.background(Canvas)) {
            RideArchive(
                state = RideUiState(
                    todayKm = 42.0,
                    weekKm = 180.0,
                    monthKm = 640.0,
                    pbKmh = 98,
                    economyKml = 32.4,
                    rangeKm = 160.0,
                    recent = listOf(
                        RideRow(
                            id = 1,
                            startTs = 1_700_000_000_000,
                            endTs = 1_700_003_600_000,
                            km = 42.6,
                            movingSec = 3600,
                            idleSec = 400,
                            maxSpeedKmh = 98,
                            avgMovingSpeedKmh = 42.0,
                            headline = "Out along the ridge and back",
                        ),
                    ),
                ),
                onOpenExplorer = {},
                onOpenRide = {},
                onStart = {},
                onStop = {},
                onEndPaused = {},
                onDemo = {},
            )
        }
    }
}
