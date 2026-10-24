package dev.gdk.bumblebee.ride

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** One finished stretch of a ride: [km] is where it ends, [sec] how long it took. */
data class Split(val index: Int, val km: Double, val sec: Int, val avgKmh: Double)

/** Ahead (negative) or behind (positive) the fastest earlier ride on the same road, at the same distance. */
data class Ghost(val deltaSec: Int, val pastRideId: Long)

/** 0–100 for how smooth a ride was, and what took points off, biggest first. */
data class RideScore(val score: Int, val reasons: List<String>)

/** What a ride cost in fuel. */
data class RideCost(val litres: Double?, val rupees: Double?, val rupeesPerKm: Double?)

/** The few numbers [RideAnalytics.usual] compares a ride on. */
data class RideSummary(
    val id: Long,
    val startTs: Long,
    val km: Double,
    val movingSec: Int,
    val avgKmh: Double,
    val monthKey: Int,
)

object RideAnalytics {
    /** Metres along [track] at each fix. */
    fun cumulative(track: List<GpsFix>): DoubleArray {
        val out = DoubleArray(track.size)
        for (i in 1 until track.size) {
            val a = track[i - 1]
            val b = track[i]
            out[i] = out[i - 1] + Geo.haversineMeters(a.lat, a.lng, b.lat, b.lng)
        }
        return out
    }

    /** Completed splits of [everyKm], each with its own time and average. */
    fun splits(track: List<GpsFix>, everyKm: Double = 10.0): List<Split> {
        if (track.size < 2) return emptyList()
        val cum = cumulative(track)
        val step = everyKm * 1_000
        val out = ArrayList<Split>()
        var lastTs = track.first().ts
        var next = step
        for (i in 1 until track.size) {
            while (cum[i] >= next) {
                val sec = ((track[i].ts - lastTs) / 1_000L).toInt().coerceAtLeast(1)
                out += Split(out.size + 1, next / 1_000, sec, everyKm / (sec / 3_600.0))
                lastTs = track[i].ts
                next += step
            }
        }
        return out
    }

    /**
     * Seconds since the rider last stood still for [stopSec] or more, or since the start.
     * A stop is a time gap between fixes, or a run of fixes slower than [stillMps].
     */
    fun sinceLastStopSec(track: List<GpsFix>, now: Long, stopSec: Int = 180, stillMps: Float = 1.5f): Int? {
        if (track.isEmpty()) return null
        val gap = stopSec * 1_000L
        var resume = track.first().ts
        var stillSince: Long? = null
        for (i in track.indices) {
            val fix = track[i]
            if (i > 0 && fix.ts - track[i - 1].ts >= gap) resume = fix.ts
            val still = (fix.speedMps ?: Float.MAX_VALUE) < stillMps
            if (still) {
                if (stillSince == null) stillSince = fix.ts
            } else {
                if (stillSince != null && fix.ts - stillSince >= gap) resume = fix.ts
                stillSince = null
            }
        }
        if (stillSince != null && now - stillSince >= gap) return 0
        return ((now - resume) / 1_000L).toInt().coerceAtLeast(0)
    }

    /** Minutes until the range runs out at [avgKmh], or null when there is no pace to go on. */
    fun minutesOfRange(rangeKm: Double?, avgKmh: Double): Int? {
        if (rangeKm == null || rangeKm <= 0 || avgKmh < 10) return null
        return (rangeKm / avgKmh * 60).roundToInt()
    }

    /**
     * Compares [current] with earlier rides that started within [startRadiusM] of it: at the
     * distance ridden so far, how long each took, if it was within [matchRadiusM] of where the
     * rider is now. Returns the gap to the quickest.
     */
    fun ghost(
        current: List<GpsFix>,
        past: Map<Long, List<GpsFix>>,
        startRadiusM: Double = 400.0,
        matchRadiusM: Double = 600.0,
        minMeters: Double = 1_000.0,
    ): Ghost? {
        if (current.size < 2) return null
        val here = current.last()
        val start = current.first()
        val ridden = cumulative(current).last()
        if (ridden < minMeters) return null
        val elapsed = ((here.ts - start.ts) / 1_000L).toInt()
        var best: Pair<Long, Int>? = null
        for ((id, track) in past) {
            if (track.size < 2) continue
            val first = track.first()
            if (Geo.haversineMeters(first.lat, first.lng, start.lat, start.lng) > startRadiusM) continue
            val cum = cumulative(track)
            val at = cum.indexOfFirst { it >= ridden }
            if (at < 0) continue
            val there = track[at]
            if (Geo.haversineMeters(there.lat, there.lng, here.lat, here.lng) > matchRadiusM) continue
            val took = ((there.ts - first.ts) / 1_000L).toInt()
            if (best == null || took < best.second) best = id to took
        }
        return best?.let { (id, took) -> Ghost(elapsed - took, id) }
    }

    /**
     * Smoothness: points off for time standing still, for frequent stops, and for hard speed
     * changes (12 km/h or more within 3 s).
     */
    fun score(movingSec: Int, idleSec: Int, stops: Int, speeds: List<Pair<Long, Int>>): RideScore {
        val total = (movingSec + idleSec).coerceAtLeast(1)
        val hours = (movingSec / 3_600.0).coerceAtLeast(0.25)
        val idleShare = idleSec.toDouble() / total
        val idlePenalty = (idleShare * 60).coerceAtMost(30.0)
        val stopPenalty = ((stops / hours - 1.5) * 6).coerceIn(0.0, 20.0)
        val ordered = speeds.sortedBy { it.first }
        var swings = 0
        for (i in 1 until ordered.size) {
            val dt = ordered[i].first - ordered[i - 1].first
            if (dt in 1..3_000 && abs(ordered[i].second - ordered[i - 1].second) >= 12) swings++
        }
        val swingPenalty = (swings / hours).coerceAtMost(30.0)
        val reasons = listOf(
            idlePenalty to "Stood still ${(idleShare * 100).roundToInt()}% of the time",
            stopPenalty to (if (stops == 1) "1 stop" else "$stops stops") + " along the way",
            swingPenalty to (if (swings == 1) "1 hard speed change" else "$swings hard speed changes"),
        ).filter { it.first >= 3 }.sortedByDescending { it.first }.map { it.second }
        val score = (100 - idlePenalty - stopPenalty - swingPenalty).roundToInt().coerceIn(0, 100)
        return RideScore(score, reasons.ifEmpty { listOf("Smooth, steady riding") })
    }

    /**
     * How a ride compares with the rider's others: pace against all rides, length within its
     * month, and time against the same route's usual when it is a repeat.
     */
    fun usual(ride: RideSummary, others: List<RideSummary>, routeMates: List<RideSummary>): List<String> {
        val out = ArrayList<String>()
        val rest = others.filter { it.id != ride.id && it.km >= 1.0 }
        if (rest.size >= 4 && ride.avgKmh > 0) {
            val slower = rest.count { it.avgKmh < ride.avgKmh }
            val pct = (slower * 100.0 / rest.size).roundToInt()
            out += when {
                pct >= 50 -> "Faster than $pct% of your rides"
                else -> "A relaxed pace: slower than ${100 - pct}% of your rides"
            }
        }
        val month = (others + ride).distinctBy { it.id }.filter { it.monthKey == ride.monthKey }
        if (month.size >= 2) {
            val rank = month.sortedByDescending { it.km }.indexOfFirst { it.id == ride.id } + 1
            when {
                rest.isNotEmpty() && ride.km > rest.maxOf { it.km } -> out += "Your longest ride ever"
                rank == 1 -> out += "Longest ride this month"
                rank == 2 -> out += "2nd longest this month"
                rank == 3 -> out += "3rd longest this month"
            }
        }
        val mates = routeMates.filter { it.id != ride.id && it.movingSec > 0 }
        if (mates.size >= 2 && ride.movingSec > 0) {
            val sorted = mates.map { it.movingSec }.sorted()
            val mid = sorted.size / 2
            val usual = if (sorted.size % 2 == 1) sorted[mid].toDouble() else (sorted[mid - 1] + sorted[mid]) / 2.0
            val diffMin = ((usual - ride.movingSec) / 60.0).roundToInt()
            val runs = mates.size + 1
            out += when {
                diffMin > 0 -> "$diffMin min quicker than your usual on this route ($runs runs)"
                diffMin < 0 -> "${-diffMin} min slower than your usual on this route ($runs runs)"
                else -> "Right on your usual time for this route ($runs runs)"
            }
        }
        return out
    }

    /** Fuel this ride used and what it cost, from litres and pump price, else the rolling ₹ a km. */
    fun cost(km: Double, litres: Double?, rupeesPerLitre: Double?, rupeesPerKm: Double?): RideCost? {
        if (km <= 0) return null
        val rupees = when {
            litres != null && rupeesPerLitre != null -> litres * rupeesPerLitre
            rupeesPerKm != null -> rupeesPerKm * km
            else -> null
        }
        if (litres == null && rupees == null) return null
        return RideCost(litres, rupees, rupees?.let { it / km })
    }

    /** "+2:10" behind or "−0:45" ahead. */
    fun ghostLabel(deltaSec: Int): String {
        val sign = if (deltaSec > 0) "+" else if (deltaSec < 0) "−" else "±"
        val s = abs(deltaSec)
        return String.format(Locale.US, "%s%d:%02d", sign, s / 60, s % 60)
    }
}
