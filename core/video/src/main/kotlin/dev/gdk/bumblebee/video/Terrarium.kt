package dev.gdk.bumblebee.video

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * AWS Terrain Tiles in Mapzen's Terrarium encoding: 256×256 PNGs where a pixel's height in metres
 * is (R × 256 + G + B / 256) − 32768.
 */
object Terrarium {
    const val TILE = 256
    const val URL = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/%d/%d/%d.png"
    const val ATTRIBUTION = "Terrain: Mapzen / AWS Terrain Tiles (SRTM, GMTED2010, ETOPO1 and others)"
    const val MAX_ZOOM = 15

    fun url(z: Int, x: Int, y: Int) = URL.format(z, x, y)

    /** Height in metres of one ARGB pixel. */
    fun decode(argb: Int): Double {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return r * 256.0 + g + b / 256.0 - 32768.0
    }

    fun encode(metres: Double): Int {
        val v = metres + 32768.0
        val r = floor(v / 256.0).toInt().coerceIn(0, 255)
        val g = floor(v - r * 256.0).toInt().coerceIn(0, 255)
        val b = ((v - r * 256.0 - g) * 256.0).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun lngToX(lng: Double, z: Int): Double = (lng + 180.0) / 360.0 * (1 shl z)

    fun latToY(lat: Double, z: Int): Double {
        val r = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        return (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * (1 shl z)
    }

    fun xToLng(x: Double, z: Int): Double = x / (1 shl z) * 360.0 - 180.0

    fun yToLat(y: Double, z: Int): Double {
        val n = PI - 2.0 * PI * y / (1 shl z)
        return Math.toDegrees(atan(0.5 * (exp(n) - exp(-n))))
    }

    /** The finest zoom at which [bounds] needs at most [maxTiles] tiles. */
    fun zoomFor(bounds: LatLngBounds, maxTiles: Int = 16, minZoom: Int = 6, maxZoom: Int = 13): Int {
        for (z in maxZoom downTo minZoom) {
            if (tilesFor(bounds, z).size <= maxTiles) return z
        }
        return minZoom
    }

    fun tilesFor(bounds: LatLngBounds, z: Int): List<Triple<Int, Int, Int>> {
        val x0 = floor(lngToX(bounds.west, z)).toInt()
        val x1 = floor(lngToX(bounds.east, z)).toInt()
        val y0 = floor(latToY(bounds.north, z)).toInt()
        val y1 = floor(latToY(bounds.south, z)).toInt()
        val last = (1 shl z) - 1
        return buildList {
            for (y in max(0, y0)..min(last, y1)) for (x in max(0, x0)..min(last, x1)) add(Triple(z, x, y))
        }
    }
}

data class LatLngBounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    val centerLat get() = (south + north) / 2
    val centerLng get() = (west + east) / 2

    /** Grown by [frac] of its size on each side, and to at least [minSpanDeg] across. */
    fun padded(frac: Double, minSpanDeg: Double = 0.01): LatLngBounds {
        val dLat = max((north - south) * frac, (minSpanDeg - (north - south)) / 2)
        val dLng = max((east - west) * frac, (minSpanDeg - (east - west)) / 2)
        return LatLngBounds(south - dLat, west - dLng, north + dLat, east + dLng)
    }

    companion object {
        /**
         * A square area around a route, wide enough that the terrain reaches the horizon in the
         * intro and finish shots: the route's larger side times (1 + 2 × [margin]), and at least
         * [minSideM] across.
         */
        fun aroundRoute(points: List<Pair<Double, Double>>, margin: Double = 0.6, minSideM: Double = 8_000.0): LatLngBounds? {
            val b = of(points) ?: return null
            val frame = LocalFrame(b.centerLat, b.centerLng)
            val w = frame.x(b.east) - frame.x(b.west)
            val h = frame.y(b.north) - frame.y(b.south)
            val half = max(max(w, h) * (1 + 2 * margin), minSideM) / 2
            return LatLngBounds(frame.lat(-half), frame.lng(-half), frame.lat(half), frame.lng(half))
        }

        fun of(points: List<Pair<Double, Double>>): LatLngBounds? {
            if (points.isEmpty()) return null
            return LatLngBounds(points.minOf { it.first }, points.minOf { it.second }, points.maxOf { it.first }, points.maxOf { it.second })
        }
    }
}
