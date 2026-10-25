package dev.gdk.bumblebee.ui.ride.replay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.map.MapTiles
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.MapCamera
import dev.gdk.bumblebee.ride.SlippyMap
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.Scrim
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import dev.gdk.bumblebee.ui.theme.Warning
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

internal data class MapLeg(val start: GpsFix, val end: GpsFix, val color: Color)

internal enum class MapPinKind { START, END, END_FLAG, STOP, FUEL, TICK }

internal data class MapPin(val lat: Double, val lng: Double, val kind: MapPinKind)

/** On-screen width of a 256 px OSM tile at a whole zoom level; larger keeps street names readable. */
private const val TILE_DP = 200

/**
 * A slippy OpenStreetMap view of a ride. It opens framed on [fixes]. Pinch zooms around your
 * fingers and loads sharper tiles as you go, double-tap zooms in, a two-finger tap zooms out,
 * and a drag flings. At the framed zoom a one-finger drag scrolls the page instead, so a map inside
 * a scrolling screen doesn't trap the thumb.
 */
@Composable
internal fun RideMapCanvas(
    fixes: List<GpsFix>,
    legs: List<MapLeg>,
    pins: List<MapPin>,
    marker: GpsFix?,
    modifier: Modifier = Modifier,
    onSelectTs: ((Long) -> Unit)? = null,
    zoomable: Boolean = true,
    overlay: (DrawScope.(project: (Double, Double) -> Offset) -> Unit)? = null,
) {
    if (fixes.isEmpty()) return
    val context = LocalContext.current
    val tilePx = with(LocalDensity.current) { TILE_DP.dp.toPx() }.toDouble()
    val slop = LocalViewConfiguration.current.touchSlop
    var size by remember { mutableStateOf(IntSize.Zero) }
    val points = remember(fixes) { fixes.map { it.lat to it.lng } }
    val fitCam = remember(points, size, tilePx) {
        SlippyMap.fit(points, size.width.toDouble(), size.height.toDouble(), tilePx)
    }
    var camera by remember(fitCam) { mutableStateOf(fitCam) }
    val scope = rememberCoroutineScope()
    var motion by remember { mutableStateOf<Job?>(null) }
    val dim = Scrim.copy(alpha = 0.35f)

    fun animateTo(target: MapCamera) {
        val from = camera ?: return
        motion?.cancel()
        motion = scope.launch {
            val t = Animatable(0f)
            t.animateTo(1f, tween(320)) {
                val f = value.toDouble()
                camera = MapCamera(from.x + (target.x - from.x) * f, from.y + (target.y - from.y) * f, from.zoom + (target.zoom - from.zoom) * f)
            }
        }
    }

    // Ask for the tiles on screen whenever the view settles on new ones.
    LaunchedEffect(fitCam) {
        snapshotFlow { camera?.let { SlippyMap.visibleTiles(it, size.width.toDouble(), size.height.toDouble(), tilePx) } }
            .collect { ids -> if (ids != null) MapTiles.request(context, ids) }
    }

    val w = size.width.toDouble()
    val h = size.height.toDouble()
    val gestures = if (!zoomable) Modifier else Modifier.pointerInput(fitCam, tilePx) {
        val tracker = VelocityTracker()
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            motion?.cancel()
            tracker.resetTracking()
            var moving = false
            var maxPointers = 1
            var travel = 0f
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed == 0) break
                maxPointers = maxOf(maxPointers, pressed)
                val cam = camera ?: break
                val fit = fitCam ?: break
                val zoomedIn = cam.zoom > fit.zoom + 0.05
                // One finger at the framed view belongs to the page's scroll.
                if (pressed < 2 && !zoomedIn && !moving) continue
                val zoomChange = event.calculateZoom()
                val pan = event.calculatePan()
                travel += pan.getDistance() + abs(zoomChange - 1f) * 200f
                if (!moving && travel > slop) moving = true
                if (!moving) continue
                val c = event.calculateCentroid(useCurrent = true)
                var next = cam
                if (zoomChange != 1f) next = SlippyMap.zoomAbout(next, zoomChange.toDouble(), c.x.toDouble(), c.y.toDouble(), w, h, tilePx)
                next = SlippyMap.pan(next, pan.x.toDouble(), pan.y.toDouble(), tilePx)
                camera = next
                tracker.addPosition(event.changes.first().uptimeMillis, c)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
            if (!moving && maxPointers >= 2) {
                // Two-finger tap: zoom out a step.
                camera?.let { cam -> animateTo(SlippyMap.zoomAbout(cam, 0.5, w / 2, h / 2, w, h, tilePx)) }
                return@awaitEachGesture
            }
            if (!moving || maxPointers > 1) return@awaitEachGesture
            val v = tracker.calculateVelocity()
            if (hypot(v.x, v.y) < 300f) return@awaitEachGesture
            motion = scope.launch {
                var last = Offset.Zero
                Animatable(Offset.Zero, Offset.VectorConverter).animateDecay(Offset(v.x, v.y), exponentialDecay(frictionMultiplier = 2f)) {
                    val d = value - last
                    last = value
                    camera?.let { camera = SlippyMap.pan(it, d.x.toDouble(), d.y.toDouble(), tilePx) }
                }
            }
        }
    }
    val taps = Modifier.pointerInput(fitCam, onSelectTs, zoomable) {
        detectTapGestures(
            onDoubleTap = if (!zoomable) null else { at: Offset ->
                camera?.let { cam -> animateTo(SlippyMap.zoomAbout(cam, 2.0, at.x.toDouble(), at.y.toDouble(), w, h, tilePx)) }
            },
            onTap = { at ->
                val cam = camera ?: return@detectTapGestures
                val select = onSelectTs ?: return@detectTapGestures
                var best = 0
                var bestD = Double.MAX_VALUE
                fixes.forEachIndexed { i, fix ->
                    val (x, y) = SlippyMap.toScreen(cam, SlippyMap.worldX(fix.lng), SlippyMap.worldY(fix.lat), w, h, tilePx)
                    val d = hypot(x - at.x, y - at.y)
                    if (d < bestD) {
                        bestD = d
                        best = i
                    }
                }
                select(fixes[best].ts)
            },
        )
    }

    Box(modifier.clipToBounds().onSizeChanged { size = it }) {
        Canvas(Modifier.fillMaxSize().then(gestures).then(taps)) {
            val cam = camera ?: return@Canvas
            // Read so the map redraws as tiles arrive.
            MapTiles.version.intValue
            drawTiles(cam, tilePx)
            drawRect(dim)
            val project = { lat: Double, lng: Double ->
                val (x, y) = SlippyMap.toScreen(cam, SlippyMap.worldX(lng), SlippyMap.worldY(lat), size.width.toDouble(), size.height.toDouble(), tilePx)
                Offset(x.toFloat(), y.toFloat())
            }
            overlay?.invoke(this, project)
            drawLegs(legs, project)
            pins.forEach { drawPin(project(it.lat, it.lng), it.kind) }
            marker?.let { at ->
                val p = project(at.lat, at.lng)
                drawCircle(OnBlock, 7.dp.toPx(), p)
                drawCircle(Lime, 4.dp.toPx(), p)
            }
            // OpenStreetMap's tile licence asks for this credit wherever its tiles are shown.
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 9.dp.toPx()
                color = android.graphics.Color.argb(170, 255, 255, 255)
            }
            val credit = "© OpenStreetMap contributors"
            drawContext.canvas.nativeCanvas.drawText(credit, size.width - paint.measureText(credit) - 6.dp.toPx(), size.height - 5.dp.toPx(), paint)
        }
        val fit = fitCam
        val cam = camera
        if (zoomable && fit != null && cam != null && (abs(cam.zoom - fit.zoom) > 0.05 || abs(cam.x - fit.x) + abs(cam.y - fit.y) > 1e-7)) {
            Text(
                "Fit",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(ShapePill)
                    .background(OnBlock.copy(alpha = 0.75f))
                    .clickable { animateTo(fit) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Tiles at the nearest whole zoom. A tile still loading borrows a blown-up piece of a coarser
 * tile already in memory, so zooming shows a soft map that sharpens rather than gaps.
 */
private fun DrawScope.drawTiles(cam: MapCamera, tilePx: Double) {
    val w = size.width.toDouble()
    val h = size.height.toDouble()
    for (id in SlippyMap.visibleTiles(cam, w, h, tilePx)) {
        val (sx, sy, side) = SlippyMap.tileRect(cam, id, w, h, tilePx)
        // Snap both edges so neighbouring tiles meet without hairline seams.
        val left = floor(sx).toInt()
        val top = floor(sy).toInt()
        val dst = IntSize(floor(sx + side).toInt() - left, floor(sy + side).toInt() - top)
        val exact = MapTiles.cached(id)
        if (exact != null) {
            drawImage(exact, dstOffset = IntOffset(left, top), dstSize = dst)
            continue
        }
        for (levels in 1..4) {
            val (parentId, part) = SlippyMap.parent(id, levels) ?: break
            val parent = MapTiles.cached(parentId) ?: continue
            val (fx, fy, fs) = part
            val px = parent.width
            drawImage(
                parent,
                srcOffset = IntOffset((fx * px).toInt(), (fy * px).toInt()),
                srcSize = IntSize((fs * px).toInt().coerceAtLeast(1), (fs * px).toInt().coerceAtLeast(1)),
                dstOffset = IntOffset(left, top),
                dstSize = dst,
            )
            break
        }
    }
}

private fun DrawScope.drawLegs(legs: List<MapLeg>, project: (Double, Double) -> Offset) {
    val stroke = 3.dp.toPx()
    for (leg in legs) {
        drawLine(
            leg.color,
            project(leg.start.lat, leg.start.lng),
            project(leg.end.lat, leg.end.lng),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawPin(at: Offset, kind: MapPinKind) {
    when (kind) {
        MapPinKind.START -> drawCircle(Lime, 5.dp.toPx(), at)
        MapPinKind.END -> drawCircle(Danger, 5.dp.toPx(), at)
        MapPinKind.END_FLAG -> {
            val pole = 10.dp.toPx()
            drawLine(Danger, at, Offset(at.x, at.y - pole), strokeWidth = 1.5.dp.toPx())
            val fly = 7.dp.toPx()
            val path = Path().apply {
                moveTo(at.x, at.y - pole)
                lineTo(at.x + fly, at.y - pole + 3.dp.toPx())
                lineTo(at.x, at.y - pole + 6.dp.toPx())
                close()
            }
            drawPath(path, Danger)
        }
        MapPinKind.STOP -> {
            val side = 8.dp.toPx()
            drawRect(TextSecondary, topLeft = Offset(at.x - side / 2f, at.y - side / 2f), size = androidx.compose.ui.geometry.Size(side, side))
        }
        MapPinKind.FUEL -> drawCircle(Warning, 5.dp.toPx(), at)
        MapPinKind.TICK -> drawLine(
            TextSecondary,
            Offset(at.x, at.y - 6.dp.toPx()),
            Offset(at.x, at.y + 6.dp.toPx()),
            strokeWidth = 1.5.dp.toPx(),
        )
    }
}
