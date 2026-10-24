package dev.gdk.bumblebee.ride

import kotlin.math.abs

enum class ContinuityDecision {
    RESUME,
    START_NEW,
    FINISH,
    STAY_PAUSED,
}

/**
 * Pause-window rules for one outing that survives ignition-off.
 * A window of 0 minutes means auto-continue is off.
 */
object RideContinuity {
    const val DEFAULT_WINDOW_MIN = 45
    const val ODO_SLACK_KM = 1
    val WINDOW_CHOICES_MIN = listOf(0, 15, 30, 45, 60, 120)

    fun onReconnect(
        windowMin: Int,
        pausedAtMs: Long,
        nowMs: Long,
        lastOdoKm: Int?,
        nextOdoKm: Int?,
        odoSlackKm: Int = ODO_SLACK_KM,
    ): ContinuityDecision {
        if (windowMin <= 0) return ContinuityDecision.START_NEW
        if (nowMs - pausedAtMs > windowMin * 60_000L) return ContinuityDecision.FINISH
        val jumped = lastOdoKm != null && nextOdoKm != null &&
            abs(nextOdoKm - lastOdoKm) > odoSlackKm.coerceAtLeast(0)
        return if (jumped) ContinuityDecision.START_NEW else ContinuityDecision.RESUME
    }

    fun onWindowElapsed(): ContinuityDecision = ContinuityDecision.FINISH

    fun onEndNow(): ContinuityDecision = ContinuityDecision.FINISH

    fun onStationaryTimeout(): ContinuityDecision = ContinuityDecision.FINISH

    /** After a killed process: stay paused only while the window is still open. */
    fun onRestart(windowMin: Int, pausedAtMs: Long, nowMs: Long): ContinuityDecision {
        if (windowMin <= 0) return ContinuityDecision.FINISH
        return if (nowMs - pausedAtMs > windowMin * 60_000L) {
            ContinuityDecision.FINISH
        } else {
            ContinuityDecision.STAY_PAUSED
        }
    }

    /** The stored end is the last telemetry, never the moment the timer fired. */
    fun finishTimestamp(lastSampleTs: Long, rideStartTs: Long): Long =
        maxOf(lastSampleTs, rideStartTs)
}

data class MergePreview(
    val rideCount: Int,
    val km: Double,
    val movingSec: Int,
    val elapsedSec: Int,
    val stops: Int,
) {
    val avgKmh: Double get() = RideMergeMath.averageSpeedKmh(km, movingSec)
}

object RideMergeMath {
    /** km/h from total distance and moving time. Pauses are not part of [movingSec]. */
    fun averageSpeedKmh(distanceKm: Double, movingSec: Int): Double {
        if (distanceKm <= 0.0 || movingSec <= 0) return 0.0
        return distanceKm / (movingSec / 3600.0)
    }

    fun preview(
        km: List<Double>,
        movingSec: List<Int>,
        startTs: List<Long>,
        endTs: List<Long>,
    ): MergePreview? {
        if (km.size < 2 || km.size != movingSec.size || km.size != startTs.size || km.size != endTs.size) {
            return null
        }
        val first = startTs.minOrNull() ?: return null
        val last = endTs.maxOrNull() ?: return null
        return MergePreview(
            rideCount = km.size,
            km = km.sum(),
            movingSec = movingSec.sum(),
            elapsedSec = ((last - first) / 1000L).toInt().coerceAtLeast(0),
            stops = km.size - 1,
        )
    }
}
