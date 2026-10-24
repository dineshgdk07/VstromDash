package dev.gdk.bumblebee.ride

data class CrashSample(
    val ts: Long,
    val accelG: Double,
    val speedKmh: Double,
)

enum class CrashSensitivity(
    val impactG: Double,
    val freeFallG: Double,
    val freeFallMs: Long,
    val stopKmh: Double,
    val stopWithinMs: Long,
    val stillMs: Long,
) {
    LOW(impactG = 6.0, freeFallG = 0.2, freeFallMs = 400, stopKmh = 5.0, stopWithinMs = 5_000, stillMs = 25_000),
    NORMAL(impactG = 4.0, freeFallG = 0.3, freeFallMs = 300, stopKmh = 5.0, stopWithinMs = 5_000, stillMs = 20_000),
    HIGH(impactG = 3.0, freeFallG = 0.4, freeFallMs = 200, stopKmh = 8.0, stopWithinMs = 6_000, stillMs = 15_000),
}

enum class CrashVerdictKind { NONE, CRASH }

data class CrashVerdict(
    val kind: CrashVerdictKind,
    val speedBeforeKmh: Int = 0,
    val impactTs: Long? = null,
)

object CrashDetector {
    private const val ARM_KMH = 20.0
    private const val ARM_WINDOW_MS = 10_000L

    fun armed(recentSpeedKmh: List<Pair<Long, Double>>, nowMs: Long): Boolean =
        recentSpeedKmh.any { (ts, kmh) -> nowMs - ts in 0..ARM_WINDOW_MS && kmh >= ARM_KMH }

    /**
     * Replay helper. Samples must be time-ordered. A crash needs an impact
     * (or free-fall), a stop within the sensitivity window, then no riding
     * movement for the still period. The detector is armed only when speed
     * was at least 20 km/h in the 10 s before the impact.
     */
    fun evaluate(samples: List<CrashSample>, sensitivity: CrashSensitivity): CrashVerdict {
        if (samples.size < 3) return CrashVerdict(CrashVerdictKind.NONE)
        val ordered = samples.sortedBy { it.ts }
        val impactAt = impactIndex(ordered, sensitivity) ?: return CrashVerdict(CrashVerdictKind.NONE)
        val impact = ordered[impactAt]
        if (!armedBefore(ordered, impact.ts)) return CrashVerdict(CrashVerdictKind.NONE)
        val stop = ordered.firstOrNull {
            it.ts >= impact.ts && it.ts - impact.ts <= sensitivity.stopWithinMs && it.speedKmh < sensitivity.stopKmh
        } ?: return CrashVerdict(CrashVerdictKind.NONE)
        val recovered = ordered.any {
            it.ts > stop.ts && it.ts - stop.ts <= sensitivity.stillMs && it.speedKmh >= ARM_KMH
        }
        if (recovered) return CrashVerdict(CrashVerdictKind.NONE)
        val observedUntil = ordered.last().ts
        if (observedUntil - stop.ts < sensitivity.stillMs) return CrashVerdict(CrashVerdictKind.NONE)
        val before = ordered.filter { it.ts < impact.ts }.maxByOrNull { it.ts }?.speedKmh
            ?: impact.speedKmh
        return CrashVerdict(CrashVerdictKind.CRASH, speedBeforeKmh = before.toInt(), impactTs = impact.ts)
    }

    private fun armedBefore(samples: List<CrashSample>, impactTs: Long): Boolean =
        samples.any { it.ts < impactTs && impactTs - it.ts <= ARM_WINDOW_MS && it.speedKmh >= ARM_KMH }

    private fun impactIndex(samples: List<CrashSample>, sensitivity: CrashSensitivity): Int? {
        samples.forEachIndexed { i, sample ->
            if (sample.accelG >= sensitivity.impactG) return i
        }
        var runStart = -1
        for (i in samples.indices) {
            if (samples[i].accelG < sensitivity.freeFallG) {
                if (runStart < 0) runStart = i
                if (samples[i].ts - samples[runStart].ts >= sensitivity.freeFallMs) return runStart
            } else {
                runStart = -1
            }
        }
        return null
    }
}
