package dev.gdk.bumblebee.ui.ride

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.map.OsmTileStore
import dev.gdk.bumblebee.ride.CompassCardinal
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.InsightKind
import dev.gdk.bumblebee.ride.InsightLine
import dev.gdk.bumblebee.ride.SpeedTrack
import dev.gdk.bumblebee.ride.TileId
import dev.gdk.bumblebee.ride.TrackInsights
import dev.gdk.bumblebee.ride.TrackPlot
import dev.gdk.bumblebee.ride.WebMercator
import dev.gdk.bumblebee.ui.openParkedMaps
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.ForgeTopBar
import dev.gdk.bumblebee.ui.components.PillChip
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.components.StatTone
import dev.gdk.bumblebee.ui.components.StatTriple
import dev.gdk.bumblebee.ui.components.StatTripleColumn
import dev.gdk.bumblebee.ui.theme.BlockCoral
import dev.gdk.bumblebee.ui.theme.BlockLavender
import dev.gdk.bumblebee.ui.theme.BlockMint
import dev.gdk.bumblebee.ui.theme.BlockPink
import dev.gdk.bumblebee.ui.theme.BlockYellow
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.NumberMedium
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.ShapeLg
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.ShapeSm
import dev.gdk.bumblebee.ui.theme.Stroke as GridStroke
import dev.gdk.bumblebee.ui.theme.Surface
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.TextDisabled
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import dev.gdk.bumblebee.ui.theme.Warning
import dev.gdk.bumblebee.ride.SpeedBand
import kotlin.math.hypot
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TrackPathCard(
    fixes: List<GpsFix>,
    modifier: Modifier = Modifier,
    title: String = "GPS TRACK",
    selectedTs: Long? = null,
    onSelectTs: ((Long) -> Unit)? = null,
    speeds: List<Pair<Long, Int>> = emptyList(),
) {
    val context = LocalContext.current
    val downsampled = remember(fixes) { TrackPlot.downsample(fixes) }
    val bounds = remember(downsampled) { WebMercator.bounds(downsampled) }
    val cover = remember(bounds) { bounds?.let { WebMercator.cover(it) } }
    val tiles by produceState(initialValue = emptyMap<TileId, ImageBitmap>(), cover) {
        val c = cover
        value = if (c == null) emptyMap()
        else OsmTileStore(context).load(c).mapValues { it.value.asImageBitmap() }
    }
    val zoom = remember { mutableFloatStateOf(1f) }
    val pan = remember { mutableStateOf(Offset.Zero) }
    val selected = selectedTs?.let { TrackPlot.nearestIndex(downsampled, it).takeIf { i -> i >= 0 }?.let { i -> downsampled[i] } }
    Column(
        modifier
            .clip(ShapeLg)
            .background(Surface)
            .padding(20.dp),
    ) {
        SectionLabel(text = title)
        if (onSelectTs != null) {
            Text(
                "Pinch to zoom. Tap the line to scrub.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (speeds.isNotEmpty()) {
            Text(
                "Mint under 50 · yellow 50–90 · coral over 90",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (downsampled.size < 2 || cover == null) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.6f), contentAlignment = Alignment.Center) {
                Text("No GPS track for this ride", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        } else {
            val mapMod = Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f)
                .padding(top = 8.dp)
                .clipToBounds()
            Canvas(
                mapMod
                    .pointerInput(downsampled, cover) {
                        detectTransformGestures { _, panChange, zoomChange, _ ->
                            val next = (zoom.floatValue * zoomChange).coerceIn(1f, 12f)
                            if (next > 1.02f || zoom.floatValue > 1.02f) {
                                zoom.floatValue = next
                                pan.value += panChange
                            }
                        }
                    }
                    .then(
                        if (onSelectTs == null) Modifier
                        else Modifier.pointerInput(downsampled, cover) {
                            detectTapGestures { tap ->
                                val z = zoom.floatValue
                                val p = pan.value
                                val world = Offset((tap.x - p.x) / z, (tap.y - p.y) / z)
                                val layout = size
                                val cols = cover.cols
                                val rows = cover.rows
                                val tileScale = minOf(layout.width / (cols * 256f), layout.height / (rows * 256f))
                                val originX = (layout.width - cols * 256f * tileScale) / 2f
                                val originY = (layout.height - rows * 256f * tileScale) / 2f
                                val tileZ = cover.zoom
                                var best = 0
                                var bestD = Float.MAX_VALUE
                                downsampled.forEachIndexed { i, fix ->
                                    val x = originX + (WebMercator.lngToX(fix.lng, tileZ) - cover.x0).toFloat() * 256f * tileScale
                                    val y = originY + (WebMercator.latToY(fix.lat, tileZ) - cover.y0).toFloat() * 256f * tileScale
                                    val d = hypot(world.x - x, world.y - y)
                                    if (d < bestD) {
                                        bestD = d
                                        best = i
                                    }
                                }
                                onSelectTs(downsampled[best].ts)
                            }
                        },
                    ),
            ) {
                withTransform({
                    translate(pan.value.x, pan.value.y)
                    scale(zoom.floatValue, zoom.floatValue)
                }) {
                    val cols = cover.cols
                    val rows = cover.rows
                    val tileScale = minOf(size.width / (cols * 256f), size.height / (rows * 256f))
                    val originX = (size.width - cols * 256f * tileScale) / 2f
                    val originY = (size.height - rows * 256f * tileScale) / 2f
                    val z = cover.zoom
                    tiles.forEach { (id, bmp) ->
                        val left = originX + (id.x - cover.x0) * 256f * tileScale
                        val top = originY + (id.y - cover.y0) * 256f * tileScale
                        val side = (256f * tileScale).toInt().coerceAtLeast(1)
                        drawImage(
                            bmp,
                            dstOffset = IntOffset(left.toInt(), top.toInt()),
                            dstSize = IntSize(side, side),
                        )
                    }
                    drawRect(Color.Black.copy(alpha = 0.35f))
                    fun mercator(fix: GpsFix): Offset {
                        val x = originX + (WebMercator.lngToX(fix.lng, z) - cover.x0).toFloat() * 256f * tileScale
                        val y = originY + (WebMercator.latToY(fix.lat, z) - cover.y0).toFloat() * 256f * tileScale
                        return Offset(x, y)
                    }
                    val segments = SpeedTrack.build(downsampled, speeds)
                    if (segments.isEmpty()) {
                        val path = Path().apply {
                            val first = mercator(downsampled.first())
                            moveTo(first.x, first.y)
                            for (i in 1 until downsampled.size) {
                                val o = mercator(downsampled[i])
                                lineTo(o.x, o.y)
                            }
                        }
                        drawPath(path, BlockCoral, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    } else {
                        for (seg in segments) {
                            val a = mercator(seg.start)
                            val b = mercator(seg.end)
                            drawLine(speedSeriesColor(seg.speedKmh), a, b, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                        }
                    }
                    drawCircle(Lime, 5.dp.toPx(), mercator(downsampled.first()))
                    drawCircle(Danger, 5.dp.toPx(), mercator(downsampled.last()))
                    selected?.let { drawCircle(OnBlock, 7.dp.toPx(), mercator(it)) }
                    selected?.let { drawCircle(Lime, 4.dp.toPx(), mercator(it)) }
                }
            }
        }
    }
}

@Composable
fun SpeedTimeCard(
    samples: List<Pair<Long, Int>>,
    modifier: Modifier = Modifier,
    selectedTs: Long? = null,
    onSelectTs: ((Long) -> Unit)? = null,
) {
    val points = remember(samples) { TrackPlot.speedSeries(samples) }
    Column(
        modifier
            .clip(ShapeLg)
            .background(Surface)
            .padding(20.dp),
    ) {
        SectionLabel(text = "Speed")
        if (onSelectTs != null) {
            Text(
                "Drag to scrub.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (points.size < 2) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Text("No speed samples yet", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        } else {
            val t0 = samples.first().first
            val t1 = samples.last().first
            val dt = (t1 - t0).coerceAtLeast(1L)
            val maxKmh = samples.maxOf { it.second }.coerceAtLeast(1)
            fun tsAt(frac: Float) = t0 + (frac.coerceIn(0f, 1f) * dt).toLong()
            val selFrac = selectedTs?.let { ((it - t0).toFloat() / dt).coerceIn(0f, 1f) }
            val axisClock = remember { DateTimeFormatter.ofPattern("h:mm") }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.height(132.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    listOf(maxKmh, maxKmh / 2, 0).forEach { mark ->
                        Text("$mark", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
                Canvas(
                Modifier
                    .weight(1f)
                    .height(132.dp)
                    .padding(start = 8.dp)
                    .then(
                        if (onSelectTs == null) Modifier
                        else Modifier.pointerInput(samples) {
                            detectTapGestures { pos ->
                                val padL = 4.dp.toPx()
                                val w = size.width - padL
                                onSelectTs(tsAt(((pos.x - padL) / w)))
                            }
                        }.pointerInput(samples) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val padL = 4.dp.toPx()
                                val w = size.width - padL
                                onSelectTs(tsAt(((change.position.x - padL) / w)))
                            }
                        },
                    ),
            ) {
                val padL = 4.dp.toPx()
                val padB = 6.dp.toPx()
                val w = size.width - padL
                val h = size.height - padB
                fun pt(p: dev.gdk.bumblebee.ride.PlotPoint) = Offset(padL + p.x * w, p.y * h)
                for (i in 1..3) {
                    val y = h * (i / 4f)
                    drawLine(GridStroke, Offset(padL, y), Offset(size.width, y))
                }
                val line = Path().apply {
                    val first = pt(points.first())
                    moveTo(first.x, first.y)
                    for (i in 1 until points.size) {
                        val o = pt(points[i])
                        lineTo(o.x, o.y)
                    }
                }
                drawPath(line, BlockLavender, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                selFrac?.let { f ->
                    val x = padL + f * w
                    drawLine(GridStroke, Offset(x, 0f), Offset(x, h), strokeWidth = 1.5.dp.toPx())
                    val idx = TrackPlot.nearestIndex(
                        samples.map { GpsFix(it.first, 0.0, 0.0) },
                        selectedTs ?: t0,
                    )
                    val y = if (idx in points.indices) pt(points[idx]).y else h / 2
                    drawCircle(OnBlock, 7.dp.toPx(), Offset(x, y))
                    drawCircle(Lime, 4.dp.toPx(), Offset(x, y))
                }
            }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    axisClock.format(Instant.ofEpochMilli(t0).atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    axisClock.format(Instant.ofEpochMilli(t1).atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

private fun speedSeriesColor(kmh: Int): Color = when {
    kmh < 50 -> BlockMint
    kmh < 90 -> BlockYellow
    else -> BlockCoral
}

@Composable
fun RideCalendar(
    month: YearMonth,
    kmByDate: Map<String, Double>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    monthKm: Double = kmByDate.values.sum(),
    monthDays: Int = kmByDate.count { it.value > 0 },
) {
    val today = remember { java.time.LocalDate.now().toString() }
    val peak = (kmByDate.values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    val first = month.atDay(1)
    val lead = (first.dayOfWeek.value + 6) % 7
    val days = month.lengthOfMonth()
    Column(
        modifier
            .clip(ShapeLg)
            .background(Surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    month.month.name.lowercase().replaceFirstChar { it.titlecase() } + "  ${month.year}",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    if (monthDays == 0) "No riding days this month yet."
                    else "$monthDays riding ${if (monthDays == 1) "day" else "days"} · ${String.format("%.0f", monthKm)} km",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        val cells = lead + days
        val rows = (cells + 6) / 7
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { c ->
                    val idx = r * 7 + c - lead
                    if (idx in 0 until days) {
                        val date = month.atDay(idx + 1).toString()
                        val km = kmByDate[date] ?: 0.0
                        val on = km > 0
                        val sel = date == selected
                        val isToday = date == today
                        val heat = (km / peak).toFloat().coerceIn(0f, 1f)
                        val longer = heat >= 0.5f
                        Box(
                            Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(ShapeSm)
                                .background(
                                    when {
                                        sel || (on && longer) -> BlockYellow
                                        on -> SurfaceRaised
                                        else -> SurfaceRaised
                                    },
                                )
                                .then(
                                    if (isToday && !sel) Modifier.border(1.dp, Lime, ShapeSm)
                                    else Modifier,
                                )
                                .clickable(enabled = on || isToday) { if (on) onSelect(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            val onYellow = sel || (on && longer)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "${idx + 1}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = when {
                                        onYellow -> OnBlock
                                        on -> TextPrimary
                                        else -> TextDisabled
                                    },
                                )
                                if (on) {
                                    Text(
                                        if (km >= 10) String.format("%.0f", km) else String.format("%.1f", km),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (onYellow) OnBlock else TextSecondary,
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f).height(52.dp))
                    }
                }
            }
        }
        Text(
            "Yellow days are the longer ones. Lime outline is today.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
fun PeriodChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(ShapeSm)
            .background(Surface)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
    }
}

@Composable
internal fun InsightCard(line: InsightLine, featured: Boolean = false, onJump: ((Long) -> Unit)? = null) {
    val jumpTo = line.atTs
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(ShapeLg)
            .background(Surface)
            .then(if (jumpTo != null && onJump != null) Modifier.clickable { onJump(jumpTo) } else Modifier),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(if (line.kind == InsightKind.INSIGHT) Lime else GridStroke),
        )
        Column(Modifier.padding(14.dp).weight(1f)) {
            Text(line.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(line.body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            if (jumpTo != null && onJump != null) {
                Text("Show this moment on the map", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
internal fun ParkedRow(parked: GpsFix?) {
    val context = LocalContext.current
    if (parked == null) return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ShapeLg)
            .background(Surface)
            .clickable { openParkedMaps(context, parked) }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("WHERE'S THE BIKE", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
                String.format("%.5f, %.5f", parked.lat, parked.lng),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Text("Last parked GPS · tap for maps", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextSecondary)
    }
}

@Composable
internal fun RideListItem(
    row: RideRow,
    onClick: () -> Unit,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val total = (row.movingSec + row.idleSec).coerceAtLeast(1)
    val movingFrac = row.movingSec.toFloat() / total
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ShapeLg)
            .background(Surface)
            .then(if (selected) Modifier.border(1.5.dp, Lime, ShapeLg) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatWhen(row.startTs), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            row.name?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatKm(row.km), style = NumberMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                Text(formatTimeRange(row.startTs, row.endTs), style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            }
            Text(row.headline, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 2)
            Box(
                Modifier.fillMaxWidth().height(6.dp).clip(ShapePill).background(SurfaceRaised),
            ) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(movingFrac.coerceIn(0.05f, 1f)).background(Lime),
                )
            }
            Text(
                buildString {
                    append("${formatClock(row.movingSec)} moving")
                    if (row.idleSec > 0) append("  ·  ${formatClock(row.idleSec)} waiting")
                    if (row.maxSpeedKmh > 0) append("  ·  top ${row.maxSpeedKmh}")
                    if (row.avgMovingSpeedKmh > 0) append("  ·  avg ${String.format("%.0f", row.avgMovingSpeedKmh)}")
                    row.fuelBarsEnd?.let { append("  ·  fuel $it/6") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = "Open ride",
            tint = TextSecondary,
            modifier = Modifier.padding(end = 12.dp),
        )
    }
}

private enum class DetailTab { Overview, Speed, Fuel, Route }

@Composable
fun RideDetailPane(detail: RideDetail, onBack: () -> Unit, onSplit: (() -> Unit)? = null) {
    val context = LocalContext.current
    val peakTs = remember(detail.track) { TrackInsights.peak(detail.track)?.first?.ts }
    var selectedTs by remember(detail.row.id) {
        mutableStateOf(peakTs ?: detail.track.lastOrNull()?.ts ?: detail.speeds.lastOrNull()?.first)
    }
    val fix = selectedTs?.let { ts ->
        val i = TrackPlot.nearestIndex(detail.track, ts)
        detail.track.getOrNull(i)
    }
    val speedAt = selectedTs?.let { ts ->
        val fromSamples = detail.speeds.minByOrNull { kotlin.math.abs(it.first - ts) }?.second
        fromSamples ?: fix?.let { TrackInsights.kmh(it).toInt() }
    }
    val intoSec = selectedTs?.let { ((it - detail.row.startTs) / 1000L).toInt().coerceAtLeast(0) }
    val tabs = buildList {
        add(DetailTab.Overview)
        if (detail.speeds.size >= 2 || detail.bands.isNotEmpty()) add(DetailTab.Speed)
        if (detail.fuelBarsStart != null || detail.fuelBarsEnd != null || detail.bikeEconKml != null ||
            detail.tankKmPerL != null || detail.litresEst != null || detail.rupeesPerKm != null ||
            detail.tripAKm != null || detail.tripBKm != null
        ) {
            add(DetailTab.Fuel)
        }
        if (detail.track.size >= 2 || detail.parked != null) add(DetailTab.Route)
    }
    var tab by remember(detail.row.id) { mutableStateOf(DetailTab.Overview) }
    if (tab !in tabs) tab = DetailTab.Overview
    val avg = if (detail.row.avgMovingSpeedKmh > 0) {
        String.format("%.0f", detail.row.avgMovingSpeedKmh)
    } else {
        "—"
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ForgeTopBar(title = formatWhen(detail.row.startTs), onBack = onBack)
        Text(
            "How this ride went",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
        )
        if (tabs.size > 1) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                tabs.forEach { item ->
                    PillChip(text = item.name, selected = item == tab, onClick = { tab = item })
                }
            }
        }
        if (detail.canSplit && onSplit != null) {
            ChunkyButton(text = "Split back", onClick = onSplit, style = ChunkyStyle.Neutral)
        }
        when (tab) {
            DetailTab.Overview -> {
                StatTriple(
                    headlineLabel = "This ride",
                    columns = listOf(
                        StatTripleColumn(formatKm(detail.row.km), "Distance", StatTone.Neutral),
                        StatTripleColumn(formatClock(detail.row.movingSec), "Moving", StatTone.Neutral),
                        StatTripleColumn(avg, "Avg", StatTone.Neutral),
                    ),
                )
                if (detail.overnight) {
                    Text(
                        "Continued overnight. Counted on the day it started.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
                if (detail.story.isNotBlank()) {
                    SectionCard(header = { SectionLabel(text = "The story") }) {
                        Text(
                            detail.story,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                if (detail.stopLines.isNotEmpty()) {
                    SectionCard(header = { SectionLabel(text = "Stops") }) {
                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            detail.stopLines.forEach {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            }
                        }
                    }
                }
                if (detail.insights.isNotEmpty()) {
                    SectionLabel(text = "What stood out")
                    Text(
                        "A lime mark changed how we'd tell the ride. Tap a speed note to jump the map.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    detail.insights.forEach { InsightCard(it, onJump = { selectedTs = it }) }
                }
                detail.odoEndKm?.let {
                    Text(
                        "Odometer ${"%,d".format(it)} km",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
                DetailShare(detail, context)
            }
            DetailTab.Speed -> {
                SpeedTimeCard(detail.speeds, selectedTs = selectedTs, onSelectTs = { selectedTs = it })
                Text(
                    "Top ${detail.row.maxSpeedKmh} km/h",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
                if (detail.bands.isNotEmpty()) {
                    Text(
                        "Tap a band to jump to its fastest sample.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Histogram(detail.bands) { share ->
                        TrackInsights.peakInBand(detail.speeds, share.band)?.let { selectedTs = it.first }
                    }
                }
            }
            DetailTab.Fuel -> DetailFuel(detail)
            DetailTab.Route -> {
                if (fix != null && intoSec != null) {
                    SectionCard(header = { SectionLabel(text = "This moment") }) {
                        Text(
                            buildString {
                                append(speedAt?.let { "$it km/h" } ?: "—")
                                fix.bearingDeg?.let { CompassCardinal.label(it) }?.let { append("  ·  $it") }
                                append("  ·  ${formatClock(intoSec)} in")
                                fix.altitudeM?.let { append("  ·  ${it.toInt()} m") }
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    ChunkyButton(
                        text = "Open in maps",
                        onClick = { openParkedMaps(context, fix) },
                        style = ChunkyStyle.Outline,
                    )
                }
                TrackPathCard(
                    detail.track,
                    selectedTs = selectedTs,
                    onSelectTs = { selectedTs = it },
                    speeds = detail.speeds,
                )
                ParkedRow(detail.parked)
            }
        }
    }
}

@Composable
private fun DetailFuel(detail: RideDetail) {
    val low = detail.fuelBarsEnd != null && detail.fuelBarsEnd <= 1
    SectionCard(header = { SectionLabel(text = "Fuel") }) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            detail.fuelBarsEnd?.let {
                FuelFact("Bars", "$it/6", if (low) Warning else TextPrimary)
            }
            detail.fuelBarsStart?.let { FuelFact("Started", "$it/6", TextPrimary) }
            detail.tripAKm?.let { FuelFact("Trip A", String.format("%.1f km", it), TextPrimary) }
            detail.tripBKm?.let { FuelFact("Trip B", String.format("%.1f km", it), TextPrimary) }
            (detail.tankKmPerL ?: detail.bikeEconKml)?.let {
                val label = if (detail.tankKmPerL != null) "est. tank km/L" else "Cluster km/L"
                FuelFact(label, String.format("%.1f", it), TextPrimary)
            }
        }
    }
    detail.litresEst?.let {
        Text(
            String.format(
                "est. %.1f L used this ride — %s. Not a pump measurement.",
                it,
                if (detail.litresFromFills) "from your fill km/L" else "from the cluster km/L",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
    detail.rupeesPerKm?.let {
        Text(
            "Running cost about ₹${"%.2f".format(it)}/km from priced fills.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun FuelFact(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor)
    }
}

@Composable
private fun DetailShare(detail: RideDetail, context: android.content.Context) {
    if (detail.shareText.isNotBlank()) {
        ChunkyButton(
            text = "Share recap",
            onClick = { shareRide(context, detail.shareText, null, detail.row.id) },
            style = ChunkyStyle.Outline,
        )
        if (detail.row.endTs != null) ShareCardButton(detail)
        if (detail.gpx != null) {
            ChunkyButton(
                text = "Share GPX",
                onClick = { shareRide(context, detail.shareText, detail.gpx, detail.row.id) },
                style = ChunkyStyle.Outline,
            )
        }
        if (detail.csv != null) {
            ChunkyButton(
                text = "Share CSV",
                onClick = { shareCsv(context, detail.csv, detail.row.id) },
                style = ChunkyStyle.Outline,
            )
        }
    } else if (detail.row.endTs != null) {
        ShareCardButton(detail)
    }
}

@Composable
internal fun Histogram(bands: List<BandShare>, onSelect: ((BandShare) -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().clip(ShapeLg).background(Surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        bands.forEach { band ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onSelect != null) Modifier.clickable { onSelect(band) } else Modifier),
            ) {
                Text(band.label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.width(56.dp))
                Box(
                    Modifier.weight(1f).height(10.dp).clip(ShapePill).background(SurfaceRaised),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(band.fraction.coerceIn(0f, 1f))
                            .background(bandFill(band.band)),
                    )
                }
                Text(
                    "${(band.fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    modifier = Modifier.width(36.dp),
                )
            }
        }
    }
}

private fun bandFill(band: SpeedBand): Color = when (band) {
    SpeedBand.IDLE -> TextDisabled
    SpeedBand.B1_30 -> BlockMint
    SpeedBand.B30_50 -> BlockLavender
    SpeedBand.B50_70 -> BlockYellow
    SpeedBand.B70_90 -> BlockCoral
    SpeedBand.B90_PLUS -> BlockPink
}

internal fun formatTimeRange(startTs: Long, endTs: Long?): String {
    val zone = ZoneId.systemDefault()
    val fmt = DateTimeFormatter.ofPattern("h:mm a")
    val start = Instant.ofEpochMilli(startTs).atZone(zone)
    if (endTs == null) return fmt.format(start)
    val end = Instant.ofEpochMilli(endTs).atZone(zone)
    return "${fmt.format(start)} – ${fmt.format(end)}"
}

fun formatClock(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatKm(km: Double): String = if (km <= 0) "—" else String.format("%.1f km", km)

internal fun formatWhen(ts: Long): String {
    val dt = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern("EEE d MMM · h:mm a").format(dt)
}

@Composable
fun MixBar(title: String, labels: List<String>, values: List<Double>, modifier: Modifier = Modifier) {
    val peak = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    Column(
        modifier
            .fillMaxWidth()
            .clip(ShapeLg)
            .background(Surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel(text = title)
        labels.zip(values).forEach { (label, km) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.width(40.dp))
                Box(Modifier.weight(1f).height(10.dp).clip(ShapePill).background(SurfaceRaised)) {
                    if (km > 0) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(((km / peak).toFloat()).coerceIn(0.04f, 1f))
                                .background(BlockLavender),
                        )
                    }
                }
                Text(
                    if (km <= 0) "—" else String.format("%.0f", km),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    modifier = Modifier.width(36.dp),
                )
            }
        }
    }
}

fun shareRide(context: Context, recap: String, gpx: String?, rideId: Long) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Bumblebee ride")
        putExtra(Intent.EXTRA_TEXT, recap)
    }
    if (gpx != null) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "ride-$rideId.gpx")
        file.writeText(gpx)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        send.type = "application/gpx+xml"
        send.putExtra(Intent.EXTRA_STREAM, uri)
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri("ride.gpx", uri)
    }
    context.startActivity(Intent.createChooser(send, "Share ride"))
}

fun shareCsv(context: Context, csv: String, rideId: Long) {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "ride-$rideId.csv")
    file.writeText(csv)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, "Bumblebee ride $rideId")
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("ride.csv", uri)
    }
    context.startActivity(Intent.createChooser(send, "Share CSV"))
}
