package dev.gdk.bumblebee.ride

import kotlin.math.acos
import kotlin.math.sqrt

/**
 * One accelerometer reading with the speed at that moment. [x], [y], [z] are the raw reading in
 * g, gravity included, so their average over a second or two is which way is down for the phone.
 * Left at zero, the sample has no direction and orientation checks are skipped.
 */
data class CrashSample(
    val ts: Long,
    val accelG: Double,
    val speedKmh: Double,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
)

enum class CrashSensitivity(
    /** g that counts as a hit. */
    val impactG: Double,
    /** Hits needed inside [hitWindowMs]. A pothole is one sharp sample; a crash keeps hitting. */
    val minHits: Int,
    val hitWindowMs: Long,
    /** A single reading this hard counts on its own, tilt or not. */
    val severeG: Double,
    val freeFallG: Double,
    val freeFallMs: Long,
    val stopKmh: Double,
    val stopWithinMs: Long,
    val stillMs: Long,
    /** How far "down" must turn for the phone between riding and lying still. */
    val tiltDeg: Double,
) {
    LOW(5.0, 3, 150, Double.MAX_VALUE, 0.2, 400, 5.0, 4_000, 30_000, 50.0),
    NORMAL(4.0, 3, 150, 10.0, 0.3, 300, 5.0, 5_000, 25_000, 40.0),
    HIGH(3.5, 2, 150, 7.0, 0.35, 250, 8.0, 6_000, 20_000, 30.0),
}

enum class CrashVerdictKind { NONE, CRASH }

data class CrashVerdict(
    val kind: CrashVerdictKind,
    val speedBeforeKmh: Int = 0,
    val impactTs: Long? = null,
    val peakG: Double = 0.0,
    /** Degrees the phone turned, when it could be measured. */
    val tiltDeg: Int? = null,
)

object CrashDetector {
    private const val ARM_KMH = 20.0
    private const val ARM_WINDOW_MS = 10_000L

    fun armed(recentSpeedKmh: List<Pair<Long, Double>>, nowMs: Long): Boolean =
        recentSpeedKmh.any { (ts, kmh) -> nowMs - ts in 0..ARM_WINDOW_MS && kmh >= ARM_KMH }

    /**
     * Samples must cover the impact and the still period after it. A crash needs, in order:
     * - riding at 20 km/h or more in the 10 s before;
     * - an impact: several hard hits close together, one severe hit, or a free fall;
     * - a stop soon after, and no riding again for the still period;
     * - the phone ending up turned well away from how it rode (the bike or rider is down),
     *   unless the hit was severe.
     * A hard bump followed by waiting at a signal fails the last test: nothing tipped over.
     */
    fun evaluate(samples: List<CrashSample>, sensitivity: CrashSensitivity): CrashVerdict {
        if (samples.size < 3) return CrashVerdict(CrashVerdictKind.NONE)
        val ordered = samples.sortedBy { it.ts }
        for (impact in impacts(ordered, sensitivity)) {
            verdictAt(ordered, impact, sensitivity)?.let { return it }
        }
        return CrashVerdict(CrashVerdictKind.NONE)
    }

    private data class Impact(val index: Int, val peakG: Double, val severe: Boolean)

    private fun verdictAt(ordered: List<CrashSample>, impact: Impact, s: CrashSensitivity): CrashVerdict? {
        val at = ordered[impact.index]
        if (!armedBefore(ordered, at.ts)) return null
        val stop = ordered.firstOrNull {
            it.ts >= at.ts && it.ts - at.ts <= s.stopWithinMs && it.speedKmh < s.stopKmh
        } ?: return null
        val recovered = ordered.any { it.ts > stop.ts && it.ts - stop.ts <= s.stillMs && it.speedKmh >= ARM_KMH }
        if (recovered) return null
        if (ordered.last().ts - stop.ts < s.stillMs) return null
        val tilt = tilt(ordered, at.ts, stop.ts, s.stillMs)
        val tipped = tilt != null && tilt >= s.tiltDeg
        if (!tipped && !impact.severe) return null
        val before = ordered.filter { it.ts < at.ts }.maxByOrNull { it.ts }?.speedKmh ?: at.speedKmh
        return CrashVerdict(CrashVerdictKind.CRASH, before.toInt(), at.ts, impact.peakG, tilt?.toInt())
    }

    private fun armedBefore(samples: List<CrashSample>, impactTs: Long): Boolean =
        samples.any { it.ts < impactTs && impactTs - it.ts <= ARM_WINDOW_MS && it.speedKmh >= ARM_KMH }

    /** Every place an impact may have started, oldest first. */
    private fun impacts(samples: List<CrashSample>, s: CrashSensitivity): List<Impact> {
        val out = mutableListOf<Impact>()
        var lastHitTs: Long? = null
        samples.forEachIndexed { i, sample ->
            if (sample.accelG < s.impactG) return@forEachIndexed
            // One candidate per burst.
            val last = lastHitTs
            if (last != null && sample.ts - last <= s.hitWindowMs) return@forEachIndexed
            val window = samples.subList(i, samples.size).takeWhile { it.ts - sample.ts <= s.hitWindowMs }
            val hits = window.count { it.accelG >= s.impactG }
            val peak = window.maxOf { it.accelG }
            if (hits >= s.minHits || peak >= s.severeG) {
                out += Impact(i, peak, severe = peak >= s.severeG)
                lastHitTs = window.last().ts
            }
        }
        var runStart = -1
        var reported = false
        for (i in samples.indices) {
            if (samples[i].accelG < s.freeFallG) {
                if (runStart < 0) {
                    runStart = i
                    reported = false
                }
                if (!reported && samples[i].ts - samples[runStart].ts >= s.freeFallMs) {
                    out += Impact(runStart, samples[runStart].accelG, severe = false)
                    reported = true
                }
            } else {
                runStart = -1
            }
        }
        return out.sortedBy { samples[it.index].ts }
    }

    /**
     * Angle between which way was down while riding (the 3 s before the impact) and while lying
     * still (from 2 s after the stop to the end of the still period). Null without direction data.
     */
    private fun tilt(samples: List<CrashSample>, impactTs: Long, stopTs: Long, stillMs: Long): Double? {
        val before = mean(samples.filter { it.ts in (impactTs - 3_000)..(impactTs - 300) })
        val after = mean(samples.filter { it.ts in (stopTs + 2_000)..(stopTs + stillMs) })
        if (before == null || after == null) return null
        val dot = before[0] * after[0] + before[1] * after[1] + before[2] * after[2]
        return Math.toDegrees(acos(dot.coerceIn(-1.0, 1.0)))
    }

    /** Unit vector of the average reading, or null if there is none to average. */
    private fun mean(samples: List<CrashSample>): DoubleArray? {
        if (samples.size < 3) return null
        val x = samples.sumOf { it.x }
        val y = samples.sumOf { it.y }
        val z = samples.sumOf { it.z }
        val n = sqrt(x * x + y * y + z * z)
        if (n < 1e-6) return null
        return doubleArrayOf(x / n, y / n, z / n)
    }
}
