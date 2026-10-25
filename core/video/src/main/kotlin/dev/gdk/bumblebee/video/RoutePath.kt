package dev.gdk.bumblebee.video

import kotlin.math.max
import kotlin.math.min

/**
 * The ride route in local metres, resampled every [step] metres and sitting on the terrain
 * ([lift] metres above it, heights exaggerated like the mesh). Distances along the route are
 * horizontal, as the ride's distance is.
 */
class RoutePath private constructor(
    val points: List<Vec3>,
    /** Distance along the route at each point. */
    val along: DoubleArray,
    /** True for points inside a GPS gap, to be drawn dashed. */
    val gap: BooleanArray,
) {
    val length: Double get() = along.last()

    /** Position at distance [d] along the route, interpolated. */
    fun at(d: Double): Vec3 {
        val i = index(d)
        if (i >= points.lastIndex) return points.last()
        val span = along[i + 1] - along[i]
        val t = if (span <= 0) 0.0 else ((d - along[i]) / span).coerceIn(0.0, 1.0)
        return Vec3.lerp(points[i], points[i + 1], t)
    }

    /**
     * Smoothed horizontal travel direction at [d]: the average of the route's direction from
     * [behind] metres back to [ahead] metres on, so the camera doesn't snap on every bend.
     */
    fun heading(d: Double, behind: Double, ahead: Double): Vec3 {
        val from = at(max(0.0, d - behind))
        val to = at(min(length, d + ahead))
        val v = (to - from).flat()
        if (v.length > 1e-6) return v.normalized()
        val a = at(max(0.0, d - 1))
        val b = at(min(length, d + 1))
        return (b - a).flat().normalized().takeIf { it.length > 0.5 } ?: Vec3(0.0, 1.0, 0.0)
    }

    /** How much the route bends in a window, from 0 (straight) to 1 (hairpins). */
    fun twistiness(d: Double, window: Double): Double {
        val from = max(0.0, d - window / 2)
        val to = min(length, d + window / 2)
        if (to - from < 1) return 0.0
        val chord = (at(to) - at(from)).flatLength
        return (1.0 - chord / (to - from)).coerceIn(0.0, 1.0)
    }

    fun index(d: Double): Int {
        var lo = 0
        var hi = along.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (along[mid] <= d) lo = mid else hi = mid - 1
        }
        return lo
    }

    companion object {
        /**
         * [latLng] is the trimmed track in order; [gapAfter] marks indices followed by a GPS gap.
         */
        fun build(
            latLng: List<Pair<Double, Double>>,
            grid: Elevation,
            exaggeration: Double,
            step: Double = 10.0,
            lift: Double = 4.0,
            gapAfter: Set<Int> = emptySet(),
        ): RoutePath {
            require(latLng.isNotEmpty())
            val raw = latLng.map { (lat, lng) -> Vec3(grid.frame.x(lng), grid.frame.y(lat), 0.0) }
            val pts = ArrayList<Vec3>()
            val along = ArrayList<Double>()
            val gaps = ArrayList<Boolean>()
            var total = 0.0
            pts += raw[0]
            along += 0.0
            gaps += false
            for (i in 1 until raw.size) {
                val a = raw[i - 1]
                val b = raw[i]
                val seg = (b - a).flatLength
                if (seg <= 0) continue
                val n = max(1, (seg / step).toInt())
                for (k in 1..n) {
                    pts += Vec3.lerp(a, b, k.toDouble() / n)
                    along += total + seg * k / n
                    gaps += (i - 1) in gapAfter
                }
                total += seg
            }
            val draped = pts.map { Vec3(it.x, it.y, grid.heightAt(it.x, it.y) * exaggeration + lift) }
            return RoutePath(draped, along.toDoubleArray(), gaps.toBooleanArray())
        }
    }
}
