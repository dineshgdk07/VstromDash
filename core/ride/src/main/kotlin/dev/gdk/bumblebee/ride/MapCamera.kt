package dev.gdk.bumblebee.ride

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * Where a slippy map looks. [x] and [y] are the centre in world units (the whole Web Mercator
 * world is 0..1 on both axes); [zoom] is continuous, so pinching is smooth and tiles are picked by
 * rounding it.
 */
data class MapCamera(val x: Double, val y: Double, val zoom: Double)

/** Pure maths for a pannable, zoomable OpenStreetMap view. Screen units are pixels. */
object SlippyMap {
    const val MIN_ZOOM = 2.0
    const val MAX_ZOOM = 19.0

    fun worldX(lng: Double): Double = (lng + 180.0) / 360.0

    fun worldY(lat: Double): Double {
        val r = lat.coerceIn(-85.0511, 85.0511) * PI / 180.0
        return (1.0 - ln(tan(r) + 1.0 / kotlin.math.cos(r)) / PI) / 2.0
    }

    /** How many pixels the whole world spans at [zoom], with tiles drawn [tilePx] wide at zoom 0. */
    fun worldPx(zoom: Double, tilePx: Double): Double = tilePx * 2.0.pow(zoom)

    fun toScreen(cam: MapCamera, wx: Double, wy: Double, width: Double, height: Double, tilePx: Double): Pair<Double, Double> {
        val s = worldPx(cam.zoom, tilePx)
        return (width / 2 + (wx - cam.x) * s) to (height / 2 + (wy - cam.y) * s)
    }

    fun toWorld(cam: MapCamera, sx: Double, sy: Double, width: Double, height: Double, tilePx: Double): Pair<Double, Double> {
        val s = worldPx(cam.zoom, tilePx)
        return (cam.x + (sx - width / 2) / s) to (cam.y + (sy - height / 2) / s)
    }

    /** The camera that fits every point with [pad] of the view spare around them. */
    fun fit(
        points: List<Pair<Double, Double>>,
        width: Double,
        height: Double,
        tilePx: Double,
        pad: Double = 0.12,
        maxZoom: Double = 17.0,
    ): MapCamera? {
        if (points.isEmpty() || width <= 0 || height <= 0) return null
        val xs = points.map { worldX(it.second) }
        val ys = points.map { worldY(it.first) }
        val minX = xs.min()
        val maxX = xs.max()
        val minY = ys.min()
        val maxY = ys.max()
        val spanX = (maxX - minX).coerceAtLeast(1e-9)
        val spanY = (maxY - minY).coerceAtLeast(1e-9)
        val usable = 1.0 - 2 * pad
        val zoom = log2(min(width * usable / (spanX * tilePx), height * usable / (spanY * tilePx)))
        return MapCamera((minX + maxX) / 2, (minY + maxY) / 2, zoom.coerceIn(MIN_ZOOM, maxZoom))
    }

    /** Zooms by [factor] keeping the world point under ([fx], [fy]) still, as a pinch should. */
    fun zoomAbout(cam: MapCamera, factor: Double, fx: Double, fy: Double, width: Double, height: Double, tilePx: Double): MapCamera {
        val zoom = (cam.zoom + log2(factor)).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val (wx, wy) = toWorld(cam, fx, fy, width, height, tilePx)
        val s = worldPx(zoom, tilePx)
        return MapCamera(wx - (fx - width / 2) / s, wy - (fy - height / 2) / s, zoom)
    }

    /** Moves the view by a finger drag of ([dx], [dy]) pixels. */
    fun pan(cam: MapCamera, dx: Double, dy: Double, tilePx: Double): MapCamera {
        val s = worldPx(cam.zoom, tilePx)
        return cam.copy(x = (cam.x - dx / s).coerceIn(0.0, 1.0), y = (cam.y - dy / s).coerceIn(0.0, 1.0))
    }

    /** The tile zoom level to draw: the nearest whole level, so tiles show between 0.7x and 1.4x. */
    fun tileZoom(zoom: Double): Int = zoom.roundToInt().coerceIn(MIN_ZOOM.toInt(), MAX_ZOOM.toInt())

    /** Tiles covering the view at [z], nearest the centre first so the middle fills in first. */
    fun visibleTiles(cam: MapCamera, width: Double, height: Double, tilePx: Double, z: Int = tileZoom(cam.zoom)): List<TileId> {
        val n = 1 shl z
        val (left, top) = toWorld(cam, 0.0, 0.0, width, height, tilePx)
        val (right, bottom) = toWorld(cam, width, height, width, height, tilePx)
        val x0 = floor(left * n).toInt().coerceIn(0, n - 1)
        val x1 = floor(right * n).toInt().coerceIn(0, n - 1)
        val y0 = floor(top * n).toInt().coerceIn(0, n - 1)
        val y1 = floor(bottom * n).toInt().coerceIn(0, n - 1)
        val cx = cam.x * n
        val cy = cam.y * n
        return (y0..y1).flatMap { y -> (x0..x1).map { x -> TileId(z, x, y) } }
            .sortedBy { (it.x + 0.5 - cx).let { d -> d * d } + (it.y + 0.5 - cy).let { d -> d * d } }
    }

    /** A tile's top-left on screen and its side, in pixels. */
    fun tileRect(cam: MapCamera, id: TileId, width: Double, height: Double, tilePx: Double): Triple<Double, Double, Double> {
        val n = (1 shl id.z).toDouble()
        val (sx, sy) = toScreen(cam, id.x / n, id.y / n, width, height, tilePx)
        return Triple(sx, sy, worldPx(cam.zoom, tilePx) / n)
    }

    /** The coarser tile that contains [id], and which quarter (or smaller part) of it to show. */
    fun parent(id: TileId, levels: Int = 1): Pair<TileId, Triple<Double, Double, Double>>? {
        if (id.z - levels < 0) return null
        val k = 1 shl levels
        val p = TileId(id.z - levels, id.x / k, id.y / k)
        val part = 1.0 / k
        return p to Triple((id.x % k) * part, (id.y % k) * part, part)
    }
}
