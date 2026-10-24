package dev.gdk.bumblebee.ride

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

data class GeoBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
)

data class TileId(val z: Int, val x: Int, val y: Int)

/** Inclusive tile rectangle at one zoom, origin at northwest tile. */
data class TileCover(
    val zoom: Int,
    val x0: Int,
    val y0: Int,
    val x1: Int,
    val y1: Int,
) {
    val cols: Int get() = x1 - x0 + 1
    val rows: Int get() = y1 - y0 + 1
    val count: Int get() = cols * rows
    fun tiles(): List<TileId> = buildList {
        for (y in y0..y1) for (x in x0..x1) add(TileId(zoom, x, y))
    }
}

object WebMercator {
    fun lngToX(lng: Double, zoom: Int): Double {
        val n = 1 shl zoom
        return (lng + 180.0) / 360.0 * n
    }

    fun latToY(lat: Double, zoom: Int): Double {
        val n = 1 shl zoom
        val latRad = lat.coerceIn(-85.0511, 85.0511) * PI / 180.0
        return (1.0 - ln(tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / PI) / 2.0 * n
    }

    fun bounds(points: List<GpsFix>, padFrac: Double = 0.18): GeoBounds? {
        if (points.isEmpty()) return null
        var minLat = points.minOf { it.lat }
        var maxLat = points.maxOf { it.lat }
        var minLng = points.minOf { it.lng }
        var maxLng = points.maxOf { it.lng }
        val dLat = max(maxLat - minLat, 0.002)
        val dLng = max(maxLng - minLng, 0.002)
        minLat -= dLat * padFrac
        maxLat += dLat * padFrac
        minLng -= dLng * padFrac
        maxLng += dLng * padFrac
        return GeoBounds(minLat, maxLat, minLng, maxLng)
    }

    fun cover(bounds: GeoBounds, maxTiles: Int = 12, minZoom: Int = 11, maxZoom: Int = 16): TileCover {
        var z = maxZoom
        var cover = coverAt(bounds, z)
        while (cover.count > maxTiles && z > minZoom) {
            z--
            cover = coverAt(bounds, z)
        }
        return cover
    }

    private fun coverAt(bounds: GeoBounds, zoom: Int): TileCover {
        val n = 1 shl zoom
        val xA = lngToX(bounds.minLng, zoom)
        val xB = lngToX(bounds.maxLng, zoom)
        val yA = latToY(bounds.maxLat, zoom)
        val yB = latToY(bounds.minLat, zoom)
        val x0 = floor(min(xA, xB)).toInt().coerceIn(0, n - 1)
        val x1 = ceil(max(xA, xB)).toInt().coerceIn(0, n) - 1
        val y0 = floor(min(yA, yB)).toInt().coerceIn(0, n - 1)
        val y1 = ceil(max(yA, yB)).toInt().coerceIn(0, n) - 1
        return TileCover(zoom, x0, y0, x1.coerceAtLeast(x0), y1.coerceAtLeast(y0))
    }
}
