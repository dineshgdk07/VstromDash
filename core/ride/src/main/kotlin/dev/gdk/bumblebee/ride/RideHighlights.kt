package dev.gdk.bumblebee.ride

import java.util.Locale

/** Badge kinds. Clubs outrank records, because the first 100 km ride is always also the longest. */
enum class HighlightKind { CLUB_300, CLUB_200, CLUB_100, LONGEST, TOP_SPEED, BIGGEST_CLIMB, LONGEST_SADDLE }

data class Highlight(val kind: HighlightKind, val title: String, val detail: String)

data class HighlightStats(val km: Double, val maxSpeedKmh: Int, val climbM: Double?, val movingSec: Int)

object RideHighlights {
    private const val CLIMB_MIN_M = 40.0

    /** One badge, or null. A record is strictly greater than every earlier ride. The first ride gets nothing. */
    fun of(ride: HighlightStats, earlier: List<HighlightStats>): Highlight? {
        if (earlier.isEmpty()) return null
        val maxKm = earlier.maxOf { it.km }
        club(ride.km, maxKm)?.let { return it }
        if (ride.km > maxKm) {
            val km = fmtKm(ride.km)
            return Highlight(HighlightKind.LONGEST, "Longest ride yet", "Longest ride yet: $km km")
        }
        val maxSpeed = earlier.maxOf { it.maxSpeedKmh }
        if (ride.maxSpeedKmh > maxSpeed) {
            return Highlight(HighlightKind.TOP_SPEED, "New top speed", "New top speed: ${ride.maxSpeedKmh} km/h")
        }
        val climb = ride.climbM
        if (climb != null && climb >= CLIMB_MIN_M) {
            val maxClimb = earlier.maxOf { it.climbM ?: 0.0 }
            if (climb > maxClimb) {
                return Highlight(HighlightKind.BIGGEST_CLIMB, "Biggest climb yet", "Biggest climb yet: ${climb.toInt()} m")
            }
        }
        val maxMoving = earlier.maxOf { it.movingSec }
        if (ride.movingSec > maxMoving) {
            val clock = ShareMetrics.clock(ride.movingSec)
            return Highlight(HighlightKind.LONGEST_SADDLE, "Longest time in the saddle", "Longest time in the saddle: $clock")
        }
        return null
    }

    private fun club(km: Double, earlierMaxKm: Double): Highlight? {
        val kind = when {
            km >= 300.0 && earlierMaxKm < 300.0 -> HighlightKind.CLUB_300
            km >= 200.0 && earlierMaxKm < 200.0 -> HighlightKind.CLUB_200
            km >= 100.0 && earlierMaxKm < 100.0 -> HighlightKind.CLUB_100
            else -> return null
        }
        val title = when (kind) {
            HighlightKind.CLUB_300 -> "300 km club"
            HighlightKind.CLUB_200 -> "200 km club"
            else -> "100 km club"
        }
        return Highlight(kind, title, "$title: ${fmtKm(km)} km")
    }

    private fun fmtKm(km: Double): String = String.format(Locale.US, "%.1f", km)
}
