package dev.gdk.bumblebee.video

import java.util.Locale
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class VideoShape(val width: Int, val height: Int, val label: String) {
    VERTICAL(1080, 1920, "9:16"),
    LANDSCAPE(1920, 1080, "16:9"),
    SQUARE(1080, 1080, "1:1"),
    ;

    val aspect: Double get() = width.toDouble() / height
}

/** How long a ride's video should be, in whole seconds. */
data class LengthRange(val min: Int, val recommended: Int, val max: Int)

object VideoLength {
    /** Instagram Reels' limit, and longer than people watch a flyover. */
    const val MAX_S = 90
    const val SHORTS_S = 60

    private data class Band(val upToKm: Double, val recLo: Int, val recHi: Int, val min: Int, val max: Int)

    private val BANDS = listOf(
        Band(20.0, 15, 15, 10, 30),
        Band(60.0, 20, 30, 10, 45),
        Band(150.0, 30, 45, 15, 60),
        Band(300.0, 45, 60, 20, 90),
        Band(Double.MAX_VALUE, 60, 75, 30, 90),
    )

    /**
     * The recommendation sits inside the distance band, nearer its top for longer rides and for
     * rides with more moments to show (six or more counts as full).
     */
    fun range(distanceKm: Double, moments: Int): LengthRange {
        val i = BANDS.indexOfFirst { distanceKm < it.upToKm }.let { if (it < 0) BANDS.lastIndex else it }
        val b = BANDS[i]
        val from = if (i == 0) 0.0 else BANDS[i - 1].upToKm
        val distFrac = if (b.upToKm == Double.MAX_VALUE) min(1.0, (distanceKm - from) / 300.0) else (distanceKm - from) / (b.upToKm - from)
        val frac = (0.5 * distFrac.coerceIn(0.0, 1.0) + 0.5 * min(1.0, moments / 6.0))
        val rec = (b.recLo + (b.recHi - b.recLo) * frac)
        val rounded = (floor(rec / 5.0 + 0.5) * 5).toInt().coerceIn(b.recLo, b.recHi)
        return LengthRange(b.min, rounded, min(b.max, MAX_S))
    }
}

enum class MomentKind(val rank: Int) { HIGHEST(0), CLIMB(1), TOP_SPEED(2), STOP(3), REFUEL(4), LOWEST(5) }

/** Something worth pausing on, at [distanceM] along the trimmed route. */
data class VideoMoment(val kind: MomentKind, val distanceM: Double, val title: String, val value: String)

/** One point of the trimmed ride: distance along it, and what was recorded there. */
data class TrackSample(val distanceM: Double, val altitudeM: Double?, val speedKmh: Double?)

data class StopMark(val distanceM: Double, val durationS: Int)

object Moments {
    const val MIN_RELIEF_M = 150.0
    const val MIN_CLIMB_M = 100.0
    const val MIN_STOP_S = 600
    const val MIN_TOP_SPEED = 20.0

    /**
     * Candidates from the samples. Altitudes are smoothed over about 200 m first, so a GPS spike is
     * never called the highest point; pass terrain heights rather than GPS ones when available.
     */
    fun candidates(samples: List<TrackSample>, stops: List<StopMark> = emptyList(), refuels: List<Double> = emptyList()): List<VideoMoment> {
        val out = mutableListOf<VideoMoment>()
        val alt = smoothAltitude(samples, 200.0)
        if (alt.isNotEmpty()) {
            val hi = alt.indices.maxBy { alt[it]!! }
            val lo = alt.indices.minBy { alt[it]!! }
            if (alt[hi]!! - alt[lo]!! >= MIN_RELIEF_M) {
                out += VideoMoment(MomentKind.HIGHEST, samples[hi].distanceM, "Highest point", "${metres(alt[hi]!!)} m")
                out += VideoMoment(MomentKind.LOWEST, samples[lo].distanceM, "Lowest point", "${metres(alt[lo]!!)} m")
            }
            // Biggest single climb: the largest rise from a low to a later high.
            var runMin = alt[0]!!
            var best = 0.0
            var bestTop = -1
            for (i in alt.indices) {
                val a = alt[i]!!
                if (a < runMin) runMin = a
                if (a - runMin > best) {
                    best = a - runMin
                    bestTop = i
                }
            }
            if (best >= MIN_CLIMB_M && bestTop >= 0) {
                out += VideoMoment(MomentKind.CLIMB, samples[bestTop].distanceM, "Biggest climb", "+${metres(best)} m")
            }
        }
        // Top speed from three-sample medians, so one bad reading can't win.
        val speeds = samples.map { it.speedKmh }
        var top = -1.0
        var topAt = -1
        for (i in 1 until speeds.size - 1) {
            val trio = listOfNotNull(speeds[i - 1], speeds[i], speeds[i + 1])
            if (trio.size < 3) continue
            val med = trio.sorted()[1]
            if (med > top) {
                top = med
                topAt = i
            }
        }
        if (topAt >= 0 && top >= MIN_TOP_SPEED) {
            out += VideoMoment(MomentKind.TOP_SPEED, samples[topAt].distanceM, "Top speed", "${top.roundToInt()} km/h")
        }
        stops.filter { it.durationS >= MIN_STOP_S }.forEach {
            out += VideoMoment(MomentKind.STOP, it.distanceM, "Break", "${it.durationS / 60} min")
        }
        refuels.forEach { out += VideoMoment(MomentKind.REFUEL, it, "Refuelled", "") }
        return out
    }

    /**
     * Keeps the most important moments that fit: none within [trimStartM] of the start or
     * [trimEndM] of the end (the privacy zones), at most one per 4 s of the ride scene, and none
     * closer than 8% of the route (or 4 s of travel) to a more important one.
     */
    fun pick(
        candidates: List<VideoMoment>,
        routeLengthM: Double,
        rideSceneS: Double,
        trimStartM: Double = 0.0,
        trimEndM: Double = 0.0,
    ): List<VideoMoment> {
        if (routeLengthM <= 0 || rideSceneS <= 0) return emptyList()
        val maxCount = floor(rideSceneS / 4.0).toInt()
        val minGap = routeLengthM * max(0.08, 4.0 / rideSceneS)
        val chosen = mutableListOf<VideoMoment>()
        for (m in candidates.sortedWith(compareBy<VideoMoment> { it.kind.rank }.thenBy { it.distanceM })) {
            if (chosen.size >= maxCount) break
            if (m.distanceM < trimStartM || m.distanceM > routeLengthM - trimEndM) continue
            // Too close to the very start or finish to pause on.
            if (m.distanceM < routeLengthM * 0.04 || m.distanceM > routeLengthM * 0.96) continue
            if (chosen.any { kotlin.math.abs(it.distanceM - m.distanceM) < minGap }) continue
            chosen += m
        }
        return chosen.sortedBy { it.distanceM }
    }

    private fun smoothAltitude(samples: List<TrackSample>, windowM: Double): List<Double?> {
        val withAlt = samples.count { it.altitudeM != null }
        if (withAlt < samples.size * 0.5 || samples.isEmpty()) return emptyList()
        // Fill gaps from the last known height so indices line up with samples.
        var last = samples.firstNotNullOf { it.altitudeM }
        val filled = samples.map { s -> (s.altitudeM ?: last).also { last = it } }
        val out = ArrayList<Double?>(samples.size)
        var lo = 0
        var hi = 0
        var sum = 0.0
        for (i in samples.indices) {
            val d = samples[i].distanceM
            while (hi < samples.size && samples[hi].distanceM <= d + windowM / 2) sum += filled[hi++]
            while (samples[lo].distanceM < d - windowM / 2) sum -= filled[lo++]
            out += sum / (hi - lo)
        }
        return out
    }

    private fun metres(m: Double) = String.format(Locale.ENGLISH, "%,d", m.roundToInt())
}

enum class Shot { INTRO, RIDE, HOLD, FINISH }

/** What the camera and overlays should show at one instant. [progress] runs 0..1 within the shot. */
data class Cue(val shot: Shot, val progress: Double, val distanceM: Double, val moment: VideoMoment? = null)

/**
 * The video's clock: an intro, the ride with pauses at moments, and a finish. During the ride the
 * marker's distance follows a warped clock that slows near moments, near both ends, and wherever
 * [interest] is high (twisty or steep road), so the time goes on the good parts.
 */
class VideoTimeline(
    val totalS: Double,
    val routeLengthM: Double,
    moments: List<VideoMoment>,
    interest: (distanceM: Double) -> Double = { 0.0 },
) {
    val introS = max(3.0, totalS * 0.15)
    val finishS = max(3.5, totalS * 0.18)
    val rideS = max(1.0, totalS - introS - finishS)
    val moments: List<VideoMoment> = moments.sortedBy { it.distanceM }
    private val holdS: Double = if (moments.isEmpty()) 0.0 else min(HOLD_S, rideS * 0.30 / moments.size)
    private val travelS = rideS - holdS * moments.size

    private val n = 2000
    private val cum = DoubleArray(n + 1)

    init {
        val sigma = routeLengthM * 0.04
        val ends = routeLengthM * 0.05
        for (i in 1..n) {
            val d = routeLengthM * (i - 0.5) / n
            var w = 1.0 + 1.2 * interest(d).coerceIn(0.0, 1.0)
            for (m in this.moments) {
                val z = (d - m.distanceM) / sigma
                w += 1.5 * exp(-z * z)
            }
            w += exp(-(d / ends).let { it * it }) + exp(-((routeLengthM - d) / ends).let { it * it })
            cum[i] = cum[i - 1] + w
        }
    }

    private fun warpAt(d: Double): Double {
        val f = (d / routeLengthM * n).coerceIn(0.0, n.toDouble())
        val i = min(floor(f).toInt(), n - 1)
        return cum[i] + (cum[i + 1] - cum[i]) * (f - i)
    }

    private fun distanceAtWarp(w: Double): Double {
        var lo = 0
        var hi = n
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (cum[mid + 1] < w) lo = mid + 1 else hi = mid
        }
        val i = min(lo, n - 1)
        val span = cum[i + 1] - cum[i]
        val t = if (span <= 0) 0.0 else ((w - cum[i]) / span).coerceIn(0.0, 1.0)
        return routeLengthM * (i + t) / n
    }

    /** Ride-scene times at which each moment's hold starts. */
    val holdStarts: List<Double> = this.moments.mapIndexed { k, m -> warpAt(m.distanceM) / cum[n] * travelS + k * holdS }

    fun cue(t: Double): Cue {
        if (t < introS) return Cue(Shot.INTRO, (t / introS).coerceIn(0.0, 1.0), 0.0)
        val r = t - introS
        if (r >= rideS) return Cue(Shot.FINISH, ((r - rideS) / finishS).coerceIn(0.0, 1.0), routeLengthM)
        var holdsBefore = 0
        for ((k, start) in holdStarts.withIndex()) {
            if (r < start) break
            if (r < start + holdS) return Cue(Shot.HOLD, (r - start) / holdS, moments[k].distanceM, moments[k])
            holdsBefore = k + 1
        }
        val travel = (r - holdsBefore * holdS).coerceIn(0.0, travelS)
        val d = if (travelS <= 0) 0.0 else distanceAtWarp(travel / travelS * cum[n])
        return Cue(Shot.RIDE, r / rideS, d)
    }

    companion object {
        const val HOLD_S = 1.6
    }
}
