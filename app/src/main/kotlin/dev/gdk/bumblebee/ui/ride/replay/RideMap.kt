package dev.gdk.bumblebee.ui.ride.replay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.map.OsmTileStore
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.TileCover
import dev.gdk.bumblebee.ride.TileId
import dev.gdk.bumblebee.ride.WebMercator
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.Scrim
import dev.gdk.bumblebee.ui.theme.TextSecondary
import dev.gdk.bumblebee.ui.theme.Warning
import kotlin.math.hypot
import kotlin.math.min

internal data class MapLeg(val start: GpsFix, val end: GpsFix, val color: Color)

internal enum class MapPinKind { START, END, END_FLAG, STOP, FUEL, TICK }

internal data class MapPin(val lat: Double, val lng: Double, val kind: MapPinKind)

private data class MapFrame(
    val originX: Float,
    val originY: Float,
    val tileScale: Float,
    val cover: TileCover,
) {
    fun project(lat: Double, lng: Double): Offset {
        val x = originX + (WebMercator.lngToX(lng, cover.zoom) - cover.x0).toFloat() * 256f * tileScale
        val y = originY + (WebMercator.latToY(lat, cover.zoom) - cover.y0).toFloat() * 256f * tileScale
        return Offset(x, y)
    }
}

private fun mapFrame(width: Float, height: Float, cover: TileCover): MapFrame {
    val tileScale = min(width / (cover.cols * 256f), height / (cover.rows * 256f))
    val originX = (width - cover.cols * 256f * tileScale) / 2f
    val originY = (height - cover.rows * 256f * tileScale) / 2f
    return MapFrame(originX, originY, tileScale, cover)
}

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
    val context = LocalContext.current
    val cover = remember(fixes) { WebMercator.bounds(fixes)?.let { WebMercator.cover(it) } }
    val tiles by produceState(initialValue = emptyMap<TileId, ImageBitmap>(), cover) {
        val ready = cover
        value = if (ready == null) emptyMap()
        else OsmTileStore(context).load(ready).mapValues { it.value.asImageBitmap() }
    }
    val zoom = remember { mutableFloatStateOf(1f) }
    val pan = remember { mutableStateOf(Offset.Zero) }
    val dim = Scrim.copy(alpha = 0.35f)
    if (cover == null) return
    Canvas(
        modifier.clipToBounds()
            .then(
                if (!zoomable) Modifier
                else Modifier.pointerInput(fixes, cover) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        val next = (zoom.floatValue * zoomChange).coerceIn(1f, 12f)
                        if (next > 1.02f || zoom.floatValue > 1.02f) {
                            zoom.floatValue = next
                            pan.value += panChange
                        }
                    }
                },
            )
            .then(
                if (onSelectTs == null) Modifier
                else Modifier.pointerInput(fixes, cover) {
                    detectTapGestures { tap ->
                        val z = if (zoomable) zoom.floatValue else 1f
                        val p = if (zoomable) pan.value else Offset.Zero
                        val world = Offset((tap.x - p.x) / z, (tap.y - p.y) / z)
                        val frame = mapFrame(size.width.toFloat(), size.height.toFloat(), cover)
                        var best = 0
                        var bestD = Float.MAX_VALUE
                        fixes.forEachIndexed { i, fix ->
                            val at = frame.project(fix.lat, fix.lng)
                            val d = hypot(world.x - at.x, world.y - at.y)
                            if (d < bestD) {
                                bestD = d
                                best = i
                            }
                        }
                        onSelectTs(fixes[best].ts)
                    }
                },
            ),
    ) {
        withTransform({
            translate(pan.value.x, pan.value.y)
            scale(zoom.floatValue, zoom.floatValue)
        }) {
            val frame = mapFrame(size.width, size.height, cover)
            drawTiles(frame, tiles)
            drawRect(dim)
            overlay?.invoke(this) { lat, lng -> frame.project(lat, lng) }
            drawLegs(frame, legs)
            pins.forEach { drawPin(frame.project(it.lat, it.lng), it.kind) }
            marker?.let { at ->
                val p = frame.project(at.lat, at.lng)
                drawCircle(OnBlock, 7.dp.toPx(), p)
                drawCircle(Lime, 4.dp.toPx(), p)
            }
        }
    }
}

private fun DrawScope.drawTiles(frame: MapFrame, tiles: Map<TileId, ImageBitmap>) {
    val cover = frame.cover
    tiles.forEach { (id, bmp) ->
        val left = frame.originX + (id.x - cover.x0) * 256f * frame.tileScale
        val top = frame.originY + (id.y - cover.y0) * 256f * frame.tileScale
        val side = (256f * frame.tileScale).toInt().coerceAtLeast(1)
        drawImage(bmp, dstOffset = IntOffset(left.toInt(), top.toInt()), dstSize = IntSize(side, side))
    }
}

private fun DrawScope.drawLegs(frame: MapFrame, legs: List<MapLeg>) {
    val stroke = 3.dp.toPx()
    for (leg in legs) {
        drawLine(
            leg.color,
            frame.project(leg.start.lat, leg.start.lng),
            frame.project(leg.end.lat, leg.end.lng),
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
