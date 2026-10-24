package dev.gdk.bumblebee.ui.ride.replay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ride.FuelEvent
import dev.gdk.bumblebee.ride.FuelEventType
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.PlotPoint
import dev.gdk.bumblebee.ride.RideEvent
import dev.gdk.bumblebee.ride.RideEventKind
import dev.gdk.bumblebee.ride.RideMoment
import dev.gdk.bumblebee.ride.RideStop
import dev.gdk.bumblebee.ride.RideTimeline
import dev.gdk.bumblebee.ride.SpeedHistogram
import dev.gdk.bumblebee.ride.TelemetrySample
import dev.gdk.bumblebee.ui.components.ChunkyIconButton
import dev.gdk.bumblebee.ui.components.PillChip
import dev.gdk.bumblebee.ui.components.motionAnimationsEnabled
import dev.gdk.bumblebee.ui.ride.formatClock
import dev.gdk.bumblebee.ui.theme.BlockCoral
import dev.gdk.bumblebee.ui.theme.BlockLavender
import dev.gdk.bumblebee.ui.theme.BlockYellow
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.NumberLarge
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.SurfaceSunken
import dev.gdk.bumblebee.ui.theme.TextDisabled
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import dev.gdk.bumblebee.ui.theme.Warning
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class ReplaySeries { SPEED, ALTITUDE, FUEL }

private enum class ReplayAxis { TIME, DISTANCE }

private data class ScrubPin(val x: Float, val ts: Long, val color: Color)

/**
 * Map, moment and graph locked to one [scrubTs]. [compact] is the list form:
 * map and graph only, with no play controls and no dragging.
 *
 * [seekTs] jumps the scrubber when it changes. [seekNonce] repeats a jump to the same time.
 */
@Composable
fun RideReplay(
    timeline: RideTimeline,
    track: List<GpsFix>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    seekTs: Long? = null,
    seekNonce: Int = 0,
) {
    val plot = remember(timeline, track) { replayPlot(timeline, track) }
    val routeLegs = remember(plot) { speedLegs(plot.frames) }
    val routePins = remember(timeline, plot) { mapPins(timeline, plot) }
    var scrubTs by remember(timeline) { mutableLongStateOf(seekTs ?: timeline.startTs) }
    var playing by remember(timeline) { mutableStateOf(false) }
    var rate by remember(timeline) { mutableIntStateOf(1) }
    var series by remember { mutableStateOf(ReplaySeries.SPEED) }
    var axis by remember { mutableStateOf(ReplayAxis.TIME) }
    val motionOn = motionAnimationsEnabled()
    val interactive = !compact

    LaunchedEffect(seekTs, seekNonce) {
        val target = seekTs ?: return@LaunchedEffect
        playing = false
        scrubTs = target.coerceIn(timeline.startTs, timeline.endTs)
    }
    LaunchedEffect(playing, rate, motionOn) {
        if (!playing || !motionOn) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            val step = (now - last).coerceIn(0L, 100L) * rate
            last = now
            val next = scrubTs + step
            if (next >= timeline.endTs) {
                scrubTs = timeline.endTs
                playing = false
                break
            }
            scrubTs = next
        }
    }
    if (interactive) {
        ScrubHaptics(timeline, scrubTs)
    }

    val moment = timeline.at(scrubTs)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (plot.frames.size < 2) {
            Text(
                "No GPS track for this ride",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            RideMapCanvas(
                modifier = Modifier.fillMaxWidth().aspectRatio(1.6f),
                fixes = plot.frames.map { GpsFix(it.ts, it.lat, it.lng) },
                legs = routeLegs,
                pins = routePins,
                marker = moment.position,
                onSelectTs = if (interactive) {
                    { ts ->
                        playing = false
                        scrubTs = ts
                    }
                } else {
                    null
                },
                zoomable = interactive,
            )
        }
        if (!compact) {
            MomentCard(moment, plot.totalKm)
        }
        ReplayGraph(
            timeline = timeline,
            plot = plot,
            moment = moment,
            scrubTs = scrubTs,
            series = series,
            axis = axis,
            interactive = interactive,
            onScrub = { ts ->
                playing = false
                scrubTs = ts.coerceIn(timeline.startTs, timeline.endTs)
            },
        )
        if (!compact) {
            SeriesChips(series) { series = it }
            AxisChips(axis) { axis = it }
            Transport(
                playing = playing,
                rate = rate,
                motionOn = motionOn,
                onPlay = {
                    if (scrubTs >= timeline.endTs) scrubTs = timeline.startTs
                    playing = !playing
                },
                onRate = { rate = it },
                onReset = {
                    playing = false
                    scrubTs = timeline.startTs
                },
            )
        }
    }
}

@Composable
private fun ScrubHaptics(timeline: RideTimeline, scrubTs: Long) {
    val haptic = LocalHapticFeedback.current
    var primed by remember(timeline) { mutableStateOf(false) }
    var lastKm by remember(timeline) { mutableIntStateOf(0) }
    var lastEvent by remember(timeline) { mutableStateOf<Long?>(null) }
    LaunchedEffect(scrubTs) {
        val moment = timeline.at(scrubTs)
        val km = moment.distanceKm.toInt()
        val eventTs = moment.nearestEvent?.ts
        if (primed) {
            if (km != lastKm) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (eventTs != null && eventTs != lastEvent) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        primed = true
        lastKm = km
        lastEvent = eventTs
    }
}

@Composable
private fun MomentCard(moment: RideMoment, totalKm: Double) {
    val tint = moment.speedKmh?.let { bandColor(SpeedHistogram.bandFor(it)) } ?: TextSecondary
    Box(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = 4.dp)
                .background(SurfaceSunken, ShapeMd),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(SurfaceRaised, ShapeMd)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = moment.speedKmh?.toString() ?: "—",
                    style = NumberLarge,
                    color = tint,
                )
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        "${clock(moment)} · +${formatClock(moment.sinceStartSec)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Text(
                        "${"%.1f".format(moment.distanceKm)} km of ${"%.1f".format(totalKm)} km",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                    )
                }
            }
            val odo = moment.odoEstKm
            if (odo != null) {
                Text(
                    "≈ ${"%,.1f".format(odo)} km est.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            val bars = moment.fuelBars?.let { "$it bars" } ?: "— bars"
            val alt = moment.altitudeM?.let { "GPS alt. ${it.roundToInt()} m" } ?: "GPS alt. —"
            Text(
                "$bars · $alt",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            moment.nearestEvent?.let { event ->
                Text(
                    event.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .background(SurfaceSunken, ShapePill)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ReplayGraph(
    timeline: RideTimeline,
    plot: ReplayPlot,
    moment: RideMoment,
    scrubTs: Long,
    series: ReplaySeries,
    axis: ReplayAxis,
    interactive: Boolean,
    onScrub: (Long) -> Unit,
) {
    val maxSpeed = plot.frames.maxOfOrNull { it.speedKmh ?: 0 }?.coerceAtLeast(1) ?: 1
    val alts = plot.frames.mapNotNull { it.altitudeM }
    val minAlt = alts.minOrNull() ?: 0.0
    val maxAlt = alts.maxOrNull() ?: minAlt
    val duration = (timeline.endTs - timeline.startTs).coerceAtLeast(1L)
    val total = plot.totalKm.coerceAtLeast(0.001)
    fun xOf(ts: Long, distanceKm: Double): Float = when (axis) {
        ReplayAxis.TIME -> ((ts - timeline.startTs).toFloat() / duration).coerceIn(0f, 1f)
        ReplayAxis.DISTANCE -> (distanceKm / total).toFloat().coerceIn(0f, 1f)
    }
    fun yOf(speed: Int?, alt: Double?, bars: Int?): Float = when (series) {
        ReplaySeries.SPEED -> 1f - (speed ?: 0).toFloat() / maxSpeed
        ReplaySeries.ALTITUDE -> {
            val span = (maxAlt - minAlt).takeIf { it > 1.0 } ?: 1.0
            1f - (((alt ?: minAlt) - minAlt) / span).toFloat()
        }
        ReplaySeries.FUEL -> 1f - (bars ?: 0).coerceIn(0, 6) / 6f
    }
    val legs = plot.frames.zipWithNext { a, b ->
        val stopped = axis == ReplayAxis.TIME && timeline.events.holdAt((a.ts + b.ts) / 2)
        val color = when {
            stopped -> TextDisabled
            series == ReplaySeries.ALTITUDE -> BlockLavender
            series == ReplaySeries.FUEL -> BlockYellow
            else -> (b.band ?: a.band)?.let { bandColor(it) } ?: TextDisabled
        }
        GraphLeg(PlotPoint(xOf(a.ts, a.distanceKm), yOf(a.speedKmh, a.altitudeM, a.fuelBars)), PlotPoint(xOf(b.ts, b.distanceKm), yOf(b.speedKmh, b.altitudeM, b.fuelBars)), color)
    }
    val pins = remember(timeline, axis, plot) {
        timeline.events.map { event ->
            val at = timeline.at(event.ts)
            ScrubPin(xOf(event.ts, at.distanceKm), event.ts, pinColor(event))
        }
    }
    val cursorFrac = xOf(scrubTs, moment.distanceKm)
    val cursorY = yOf(moment.speedKmh, moment.altitudeM, moment.fuelBars)
    val yMarks = when (series) {
        ReplaySeries.SPEED -> listOf("$maxSpeed", "${maxSpeed / 2}", "0")
        ReplaySeries.ALTITUDE -> listOf("${maxAlt.roundToInt()}", "${((maxAlt + minAlt) / 2).roundToInt()}", "${minAlt.roundToInt()}")
        ReplaySeries.FUEL -> listOf("6", "3", "0")
    }
    val clock = remember { DateTimeFormatter.ofPattern("h:mm") }
    val zone = ZoneId.systemDefault()
    val xStart = when (axis) {
        ReplayAxis.TIME -> clock.format(Instant.ofEpochMilli(timeline.startTs).atZone(zone))
        ReplayAxis.DISTANCE -> "0"
    }
    val xEnd = when (axis) {
        ReplayAxis.TIME -> clock.format(Instant.ofEpochMilli(timeline.endTs).atZone(zone))
        ReplayAxis.DISTANCE -> "${"%.0f".format(plot.totalKm)} km"
    }
    if (plot.frames.size < 2) {
        Text("No speed samples yet", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        return
    }
    Column {
        Row(Modifier.fillMaxWidth().height(132.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.height(132.dp), verticalArrangement = Arrangement.SpaceBetween) {
                yMarks.forEach { mark ->
                    Text(mark, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .height(132.dp)
                    .padding(start = 8.dp)
                    .then(
                        if (!interactive) Modifier
                        else Modifier
                            .pointerInput(pins, axis, duration, total) {
                                detectTapGestures { pos ->
                                    onScrub(
                                        scrubTarget(
                                            x = pos.x,
                                            y = pos.y,
                                            width = size.width.toFloat(),
                                            padLPx = 4.dp.toPx(),
                                            pinBandPx = 20.dp.toPx(),
                                            pinSlopPx = 18.dp.toPx(),
                                            pins = pins,
                                            axis = axis,
                                            startTs = timeline.startTs,
                                            duration = duration,
                                            totalKm = total,
                                            atDistance = { timeline.atDistance(it).ts },
                                        ),
                                    )
                                }
                            }
                            .pointerInput(pins, axis, duration, total) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    onScrub(
                                        scrubTarget(
                                            x = change.position.x,
                                            y = change.position.y,
                                            width = size.width.toFloat(),
                                            padLPx = 4.dp.toPx(),
                                            pinBandPx = 20.dp.toPx(),
                                            pinSlopPx = 18.dp.toPx(),
                                            pins = pins,
                                            axis = axis,
                                            startTs = timeline.startTs,
                                            duration = duration,
                                            totalKm = total,
                                            atDistance = { timeline.atDistance(it).ts },
                                        ),
                                    )
                                }
                            },
                    ),
            ) {
                drawGraphSeries(legs, pins.map { GraphPinMark(it.x, it.color) }, cursorFrac, cursorY)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(xStart, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text(xEnd, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

@Composable
private fun SeriesChips(selected: ReplaySeries, onSelect: (ReplaySeries) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        chip("Speed", selected == ReplaySeries.SPEED) { onSelect(ReplaySeries.SPEED) }
        chip("GPS alt.", selected == ReplaySeries.ALTITUDE) { onSelect(ReplaySeries.ALTITUDE) }
        chip("Fuel", selected == ReplaySeries.FUEL) { onSelect(ReplaySeries.FUEL) }
    }
}

@Composable
private fun AxisChips(selected: ReplayAxis, onSelect: (ReplayAxis) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        chip("Time", selected == ReplayAxis.TIME) { onSelect(ReplayAxis.TIME) }
        chip("Distance", selected == ReplayAxis.DISTANCE) { onSelect(ReplayAxis.DISTANCE) }
    }
}

@Composable
private fun chip(text: String, selected: Boolean, onClick: () -> Unit) {
    PillChip(text = text, selected = selected, onClick = onClick)
}

@Composable
private fun Transport(
    playing: Boolean,
    rate: Int,
    motionOn: Boolean,
    onPlay: () -> Unit,
    onRate: (Int) -> Unit,
    onReset: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChunkyIconButton(
            icon = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (playing) "Pause" else "Play",
            onClick = onPlay,
            enabled = motionOn,
        )
        listOf(1, 4, 16).forEach { speed ->
            PillChip(
                text = "${speed}×",
                selected = rate == speed,
                onClick = { onRate(speed) },
                enabled = motionOn,
            )
        }
        ChunkyIconButton(
            icon = Icons.Rounded.Replay,
            contentDescription = "Reset",
            onClick = onReset,
        )
    }
}

private fun speedLegs(frames: List<ReplayFrame>): List<MapLeg> =
    frames.zipWithNext { a, b ->
        val color = (b.band ?: a.band)?.let { bandColor(it) } ?: TextDisabled
        MapLeg(GpsFix(a.ts, a.lat, a.lng), GpsFix(b.ts, b.lat, b.lng), color)
    }

private fun mapPins(timeline: RideTimeline, plot: ReplayPlot): List<MapPin> {
    val frames = plot.frames
    val pins = mutableListOf(
        MapPin(frames.first().lat, frames.first().lng, MapPinKind.START),
        MapPin(frames.last().lat, frames.last().lng, MapPinKind.END_FLAG),
    )
    kmTickFrames(frames).forEach { tick ->
        pins += MapPin(tick.lat, tick.lng, MapPinKind.TICK)
    }
    timeline.events.forEach { event ->
        val at = timeline.at(event.ts).position ?: return@forEach
        val kind = when (event.kind) {
            RideEventKind.STOP -> MapPinKind.STOP
            RideEventKind.FUEL_DROP, RideEventKind.REFUEL -> MapPinKind.FUEL
            RideEventKind.MAX_SPEED -> return@forEach
        }
        pins += MapPin(at.lat, at.lng, kind)
    }
    return pins
}

private fun pinColor(event: RideEvent): Color = when (event.kind) {
    RideEventKind.STOP -> TextSecondary
    RideEventKind.FUEL_DROP, RideEventKind.REFUEL -> Warning
    RideEventKind.MAX_SPEED -> BlockCoral
}

private fun List<RideEvent>.holdAt(ts: Long): Boolean = any { event ->
    val duration = event.durationSec ?: return@any false
    event.kind == RideEventKind.STOP && ts in event.ts until event.ts + duration * 1_000L
}

private fun scrubTarget(
    x: Float,
    y: Float,
    width: Float,
    padLPx: Float,
    pinBandPx: Float,
    pinSlopPx: Float,
    pins: List<ScrubPin>,
    axis: ReplayAxis,
    startTs: Long,
    duration: Long,
    totalKm: Double,
    atDistance: (Double) -> Long,
): Long {
    val w = width - padLPx
    val pin = pins.minByOrNull { abs(padLPx + it.x * w - x) }
    if (y <= pinBandPx && pin != null && abs(padLPx + pin.x * w - x) <= pinSlopPx) return pin.ts
    val frac = ((x - padLPx) / w).coerceIn(0f, 1f)
    return when (axis) {
        ReplayAxis.TIME -> startTs + (frac * duration).toLong()
        ReplayAxis.DISTANCE -> atDistance(frac * totalKm)
    }
}

private fun clock(moment: RideMoment): String =
    "%02d:%02d".format(moment.clock.hour, moment.clock.minute)

@Preview(showBackground = true, backgroundColor = 0xFF171717)
@Composable
private fun RideReplayPreview() {
    val (timeline, track) = previewRide()
    BumblebeeTheme {
        RideReplay(timeline, track, Modifier.background(Canvas).padding(16.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF171717)
@Composable
private fun RideReplayCompactPreview() {
    val (timeline, track) = previewRide()
    BumblebeeTheme {
        RideReplay(timeline, track, Modifier.background(Canvas).padding(16.dp), compact = true)
    }
}

private fun previewRide(): Pair<RideTimeline, List<GpsFix>> {
    val track = (0..8).map { i ->
        GpsFix(
            ts = i * 2_000L,
            lat = 12.97 + i * 0.01,
            lng = 77.59 + i * 0.004,
            altitudeM = 900.0 + i * 4,
            speedMps = 12f,
        )
    }
    val telemetry = (0..8).map { i ->
        TelemetrySample(
            ts = i * 2_000L,
            speedKmh = 20 + i * 8,
            odometerKm = 12_000,
            tripAKm = 1.0,
            tripBKm = 2.0,
            fuelBars = 6 - i / 3,
            fuelEconKml = null,
        )
    }
    val timeline = RideTimeline.build(
        track = track,
        telemetry = telemetry,
        fuelEvents = listOf(FuelEvent(8_000L, 12_010, 5, 6, FuelEventType.REFUEL)),
        stops = listOf(RideStop(4_000L, 2)),
        odoStartKm = 12_000,
        odoGpsFactor = 1.04,
        startTs = 0L,
        endTs = 16_000L,
        zone = ZoneId.of("UTC"),
    )
    return timeline to track
}
