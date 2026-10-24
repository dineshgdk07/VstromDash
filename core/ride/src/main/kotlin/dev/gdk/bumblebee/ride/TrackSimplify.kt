package dev.gdk.bumblebee.ride

import kotlin.math.abs

/**
 * Ramer–Douglas–Peucker simplification in lat/lng space (good enough for clustering
 * and map drawing; not a geodesic equal-area projection).
 */
object TrackSimplify {
    fun douglasPeucker(points: List<GpsFix>, epsilonM: Double = 12.0): List<GpsFix> {
        if (points.size < 3) return points
        return simplify(points, epsilonM)
    }

    private fun simplify(points: List<GpsFix>, epsilonM: Double): List<GpsFix> {
        if (points.size < 3) return points
        var maxDist = 0.0
        var index = 0
        val start = points.first()
        val end = points.last()
        for (i in 1 until points.lastIndex) {
            val d = perpendicularMeters(points[i], start, end)
            if (d > maxDist) {
                maxDist = d
                index = i
            }
        }
        if (maxDist <= epsilonM) return listOf(start, end)
        val left = simplify(points.subList(0, index + 1), epsilonM)
        val right = simplify(points.subList(index, points.size), epsilonM)
        return left.dropLast(1) + right
    }

    private fun perpendicularMeters(p: GpsFix, a: GpsFix, b: GpsFix): Double {
        val ab = Geo.haversineMeters(a.lat, a.lng, b.lat, b.lng)
        if (ab < 1e-6) return Geo.haversineMeters(p.lat, p.lng, a.lat, a.lng)
        val ap = Geo.haversineMeters(a.lat, a.lng, p.lat, p.lng)
        val bp = Geo.haversineMeters(b.lat, b.lng, p.lat, p.lng)
        val s = (ab + ap + bp) / 2.0
        val area2 = (s * (s - ab) * (s - ap) * (s - bp)).coerceAtLeast(0.0)
        val height = (2.0 * kotlin.math.sqrt(area2)) / ab
        return abs(height)
    }
}
