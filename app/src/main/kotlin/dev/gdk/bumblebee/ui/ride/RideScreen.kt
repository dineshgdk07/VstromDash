package dev.gdk.bumblebee.ui.ride

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.BuildConfig
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.RideTimeline
import dev.gdk.bumblebee.ride.SpeedSource
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.EmptyState
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.components.StatTone
import dev.gdk.bumblebee.ui.components.StatTriple
import dev.gdk.bumblebee.ui.components.StatTripleColumn
import dev.gdk.bumblebee.ui.ride.replay.RideReplay
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.NumberLarge
import dev.gdk.bumblebee.ui.theme.TextDisabled
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import dev.gdk.bumblebee.ui.theme.Warning

@Composable
fun RideScreen(
    model: RideViewModel = viewModel(),
    onOpenExplorer: () -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as DashApp
    val pendingRide by app.pendingRideId.collectAsStateWithLifecycle()
    LaunchedEffect(pendingRide) {
        val id = pendingRide ?: return@LaunchedEffect
        app.pendingRideId.value = null
        model.openRide(id)
    }
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
    val noRides = state.recent.isEmpty() && state.phase != RidePhase.LIVE && state.phase != RidePhase.PAUSED
    val last = state.recent.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RideTitle(state)
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
            DistanceTriple(state)
            if (state.phase == RidePhase.LIVE) LiveSpeed(state)
            if (state.phase == RidePhase.PAUSED) PausedNote(state)
            state.headline?.let { InsightCard(it, featured = true) }
            if (last != null && state.lastTrack.size >= 2) {
                SectionCard(
                    modifier = Modifier.clickable { onOpenRide(last.id) },
                    header = { SectionLabel(text = "Last ride") },
                ) {
                    RideReplay(
                        timeline = state.lastTimeline,
                        track = state.lastTrack,
                        compact = true,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            if (state.recent.isNotEmpty()) {
                SectionCard(header = { SectionLabel(text = "Recent") }) {
                    Column(Modifier.padding(top = 8.dp)) {
                        state.recent.forEachIndexed { index, row ->
                            RideArchiveRow(
                                row = row,
                                track = state.recentThumbs[row.id].orEmpty(),
                                onClick = { onOpenRide(row.id) },
                                showDivider = index < state.recent.lastIndex,
                            )
                        }
                    }
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
private fun DistanceTriple(state: RideUiState) {
    StatTriple(
        headlineLabel = "Today",
        headlineValue = formatKm(state.todayKm),
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
    val previewTrack = listOf(
        GpsFix(1_700_000_000_000, 12.97, 77.59),
        GpsFix(1_700_003_600_000, 12.99, 77.61),
    )
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
                            odoStartKm = 12_340,
                            odoEndKm = 12_387,
                        ),
                    ),
                    lastTrack = previewTrack,
                    lastTimeline = RideTimeline.build(
                        track = previewTrack,
                        startTs = previewTrack.first().ts,
                        endTs = previewTrack.last().ts,
                        odoStartKm = 12_340,
                    ),
                    recentThumbs = mapOf(1L to previewTrack),
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
