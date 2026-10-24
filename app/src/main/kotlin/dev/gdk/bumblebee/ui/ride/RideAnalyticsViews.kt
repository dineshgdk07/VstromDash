package dev.gdk.bumblebee.ui.ride

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.RideAnalytics
import dev.gdk.bumblebee.ride.RideCost
import dev.gdk.bumblebee.ride.RideScore
import dev.gdk.bumblebee.ride.TrackPlot
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.theme.BlockLavender
import dev.gdk.bumblebee.ui.theme.BlockYellow
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.NumberMedium
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.Stroke as GridStroke
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import dev.gdk.bumblebee.ui.theme.Warning
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "1h 35m" or "40 min", for durations read in a sentence. */
private fun spoken(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "$m min"
}

/** Ninety minutes on the move is when a break is worth suggesting. */
private const val BREAK_AFTER_SEC = 90 * 60

/** The live ride's extras: the ghost, fuel ahead, a break nudge, and the splits so far. */
@Composable
internal fun LiveExtras(state: RideUiState) {
    val since = state.sinceStopSec
    if (since != null && since >= BREAK_AFTER_SEC) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(BlockYellow.copy(alpha = 0.16f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Coffee, contentDescription = null, tint = BlockYellow)
            Text(
                "${spoken(since)} since your last stop. Time for a chai stop?",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
            )
        }
    }
    val lines = buildList {
        state.ghost?.let { ghost ->
            val ahead = ghost.deltaSec < 0
            add(
                Triple(
                    Icons.Rounded.Timer,
                    "${RideAnalytics.ghostLabel(ghost.deltaSec)} vs your best on this road",
                    if (ahead) Lime else if (ghost.deltaSec > 0) Danger else TextSecondary,
                ),
            )
        }
        val range = state.rangeKm
        val minutes = state.fuelMinutes
        if (range != null && minutes != null) {
            add(
                Triple(
                    Icons.Rounded.LocalGasStation,
                    "Range ${range.toInt()} km · fuel stop in ~${spoken(minutes * 60)} at this pace",
                    if (minutes < 30) Warning else TextPrimary,
                ),
            )
        }
    }
    if (lines.isEmpty() && state.splits.isEmpty()) return
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            lines.forEach { (icon, text, tone) -> LiveLine(icon, text, tone) }
            if (state.splits.isNotEmpty()) {
                Text("SPLITS", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                val best = state.splits.maxOf { it.avgKmh }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.splits.asReversed().forEach { split ->
                        Column(
                            Modifier
                                .background(SurfaceRaised, ShapeMd)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                String.format(Locale.US, "%.0f km", split.km),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                            Text(formatClock(split.sec), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(
                                String.format(Locale.US, "%.0f km/h", split.avgKmh),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (split.avgKmh == best && state.splits.size > 1) Lime else TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveLine(icon: ImageVector, text: String, tone: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tone)
    }
}

/** The smoothness score as a ring, with what took points off. */
@Composable
internal fun RideScoreCard(score: RideScore) {
    val tone = when {
        score.score >= 80 -> Lime
        score.score >= 60 -> BlockYellow
        else -> Danger
    }
    SectionCard(header = { SectionLabel(text = "Ride score") }) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(72.dp)) {
                    val stroke = 7.dp.toPx()
                    drawArc(GridStroke, -90f, 360f, false, style = Stroke(stroke))
                    drawArc(tone, -90f, 360f * score.score / 100f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Text("${score.score}", style = NumberMedium, color = TextPrimary)
            }
            Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    when {
                        score.score >= 80 -> "Smooth"
                        score.score >= 60 -> "Mixed"
                        else -> "Stop-start"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                score.reasons.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
            }
        }
    }
}

/** How the ride sits among the rider's others. */
@Composable
internal fun UsualCard(lines: List<String>, cost: RideCost?) {
    val costLine = cost?.let { c ->
        listOfNotNull(
            c.litres?.let { String.format(Locale.US, "~%.1f L", it) },
            c.rupees?.let { String.format(Locale.US, "₹%,.0f", it) },
            c.rupeesPerKm?.let { String.format(Locale.US, "₹%.2f a km", it) },
        ).joinToString(" · ")
    }
    if (lines.isEmpty() && costLine.isNullOrBlank()) return
    SectionCard(header = { SectionLabel(text = "Compared to usual") }) {
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            lines.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(Lime, CircleShape))
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.padding(start = 10.dp))
                }
            }
            if (!costLine.isNullOrBlank()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .background(SurfaceRaised, ShapeMd)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.LocalGasStation, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    Text("This ride cost  $costLine", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}

private val chartClock = DateTimeFormatter.ofPattern("h:mm a")

/**
 * Speed as a line over the elevation as a filled hill, along the ride's time. Drag or tap to read
 * a moment and move the replay there.
 */
@Composable
internal fun SpeedElevationChart(track: List<GpsFix>, onSeek: (Long) -> Unit) {
    val points = remember(track) { TrackPlot.downsample(track.filter { it.speedMps != null || it.altitudeM != null }, 300) }
    if (points.size < 2 || points.none { it.speedMps != null }) return
    val t0 = points.first().ts
    val span = (points.last().ts - t0).coerceAtLeast(1L)
    val peak = points.maxOf { (it.speedMps ?: 0f) * 3.6f }.coerceAtLeast(10f)
    val alts = points.mapNotNull { it.altitudeM }
    val lo = alts.minOrNull()
    val hi = alts.maxOrNull()
    val climb = if (lo != null && hi != null) hi - lo else null
    var cursor by remember(track) { mutableStateOf<Int?>(null) }
    fun pick(x: Float, width: Float) {
        val ts = t0 + (span * (x / width).coerceIn(0f, 1f)).toLong()
        val i = points.indexOfFirst { it.ts >= ts }.let { if (it < 0) points.lastIndex else it }
        cursor = i
        onSeek(points[i].ts)
    }
    SectionCard(header = { SectionLabel(text = "Speed and elevation") }) {
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val at = cursor?.let { points.getOrNull(it) }
            Row(Modifier.fillMaxWidth()) {
                Text(
                    if (at == null) "Drag to read any moment; the replay follows" else chartClock.format(Instant.ofEpochMilli(at.ts).atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                if (at != null) {
                    Text(
                        listOfNotNull(
                            at.speedMps?.let { String.format(Locale.US, "%.0f km/h", it * 3.6f) },
                            at.altitudeM?.let { String.format(Locale.US, "%.0f m", it) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                    )
                }
            }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .pointerInput(points) { detectTapGestures { pick(it.x, size.width.toFloat()) } }
                    .pointerInput(points) {
                        detectHorizontalDragGestures { change, _ -> pick(change.position.x, size.width.toFloat()) }
                    },
            ) {
                fun x(ts: Long) = (ts - t0).toFloat() / span * size.width
                repeat(3) { i ->
                    val y = size.height * (i + 1) / 4f
                    drawLine(GridStroke, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                if (lo != null && hi != null && hi - lo >= 5) {
                    val hill = Path().apply {
                        moveTo(0f, size.height)
                        points.forEach { p ->
                            val a = p.altitudeM ?: return@forEach
                            lineTo(x(p.ts), size.height - ((a - lo) / (hi - lo)).toFloat() * size.height * 0.6f)
                        }
                        lineTo(size.width, size.height)
                        close()
                    }
                    drawPath(hill, BlockLavender.copy(alpha = 0.28f))
                }
                val line = Path()
                var started = false
                points.forEach { p ->
                    val v = (p.speedMps ?: return@forEach) * 3.6f
                    val o = Offset(x(p.ts), size.height - v / peak * size.height * 0.92f)
                    if (!started) line.moveTo(o.x, o.y) else line.lineTo(o.x, o.y)
                    started = true
                }
                drawPath(line, Lime, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                cursor?.let { i ->
                    val cx = x(points[i].ts)
                    drawLine(TextPrimary, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1.5.dp.toPx())
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(Lime, String.format(Locale.US, "Speed · top %.0f km/h", peak))
                if (climb != null && climb >= 5) Legend(BlockLavender, String.format(Locale.US, "Elevation · %.0f m range", climb))
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 12.dp, height = 4.dp).background(color, ShapePill))
        Text(text, style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(start = 6.dp))
    }
}
