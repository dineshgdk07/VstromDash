package dev.gdk.bumblebee.ride

import kotlin.math.abs

/** Speed-colored polyline. Bands match the Ride speed mix (50 / 90). */
object SpeedTrack {

    const val COOL_ARGB: Int = 0xFF2EE86A.toInt()
    const val MID_ARGB: Int = 0xFFFAC816.toInt()
    const val HOT_ARGB: Int = 0xFFF25006.toInt()

    data class Segment(
        val start: GpsFix,
        val end: GpsFix,
        val speedKmh: Int,
        val colorArgb: Int,
    )

    fun colorFor(speedKmh: Int): Int = when {
        speedKmh < 50 -> COOL_ARGB
        speedKmh < 90 -> MID_ARGB
        else -> HOT_ARGB
    }

    /**
     * Join GPS (any rate) to speed samples by timestamp. Both lists should be
     * oldest-first. Two-pointer, O(L+S).
     */
    fun build(track: List<GpsFix>, speeds: List<Pair<Long, Int>>): List<Segment> {
        if (track.size < 2) return emptyList()
        var si = 0
        return (1 until track.size).map { i ->
            val end = track[i]
            while (si + 1 < speeds.size &&
                abs(speeds[si + 1].first - end.ts) <= abs(speeds[si].first - end.ts)
            ) {
                si++
            }
            val kmh = if (speeds.isEmpty()) {
                TrackInsights.kmh(end).toInt()
            } else {
                speeds[si].second
            }
            Segment(track[i - 1], end, kmh, colorFor(kmh))
        }
    }
}
