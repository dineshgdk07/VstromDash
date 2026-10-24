package dev.gdk.bumblebee.ride

import kotlin.math.cos
import kotlin.math.max

/** Unit square (0–1) for drawing a GPS track or a speed series. Y grows downward. */
data class PlotPoint(val x: Float, val y: Float)

object TrackPlot {
    fun downsample(points: List<GpsFix>, maxPoints: Int = 240): List<GpsFix> = stride(points, maxPoints)

    fun <T> stride(items: List<T>, maxPoints: Int = 240): List<T> {
        if (items.size <= maxPoints) return items
        val step = (items.size - 1).toFloat() / (maxPoints - 1)
        return List(maxPoints) { i -> items[(i * step).toInt().coerceIn(0, items.lastIndex)] }
    }

    fun normalize(points: List<GpsFix>): List<PlotPoint> {
        if (points.isEmpty()) return emptyList()
        val lats = points.map { it.lat }
        val lngs = points.map { it.lng }
        val minLat = lats.min()
        val maxLat = lats.max()
        val minLng = lngs.min()
        val maxLng = lngs.max()
        val midLat = (minLat + maxLat) / 2.0
        val scaleLng = cos(Math.toRadians(midLat)).coerceAtLeast(0.2)
        val dLat = max(maxLat - minLat, 1e-5)
        val dLng = max((maxLng - minLng) * scaleLng, 1e-5)
        val span = max(dLat, dLng)
        val padLat = (span - dLat) / 2.0
        val padLng = (span - dLng) / 2.0
        return points.map { p ->
            val x = (((p.lng - minLng) * scaleLng + padLng) / span).toFloat()
            val y = (1.0 - ((p.lat - minLat + padLat) / span)).toFloat()
            PlotPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        }
    }

    fun speedSeries(samples: List<Pair<Long, Int>>): List<PlotPoint> {
        if (samples.isEmpty()) return emptyList()
        val t0 = samples.first().first
        val t1 = samples.last().first
        val dt = (t1 - t0).coerceAtLeast(1L).toFloat()
        val peak = samples.maxOf { it.second }.coerceAtLeast(1).toFloat()
        return samples.map { (ts, kmh) ->
            PlotPoint((ts - t0) / dt, 1f - (kmh / peak).coerceIn(0f, 1f))
        }
    }

    fun nearestIndex(points: List<GpsFix>, ts: Long): Int {
        if (points.isEmpty()) return -1
        var best = 0
        var bestAbs = kotlin.math.abs(points[0].ts - ts)
        for (i in 1 until points.size) {
            val d = kotlin.math.abs(points[i].ts - ts)
            if (d < bestAbs) {
                bestAbs = d
                best = i
            }
        }
        return best
    }
}
