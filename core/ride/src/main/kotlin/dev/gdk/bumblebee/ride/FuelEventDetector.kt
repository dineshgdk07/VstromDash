package dev.gdk.bumblebee.ride

enum class FuelEventType { BAR_DROP, REFUEL }

/** AUTO = cluster bar change. RIDER = logged or claimed at the pump. */
enum class FuelEventSource { AUTO, RIDER }

data class FuelEvent(
    val ts: Long,
    val odometerKm: Int,
    val barsBefore: Int,
    val barsAfter: Int,
    val type: FuelEventType,
)

/**
 * Detects fuel-bar transitions from the a537 stream. Bars flicker on slopes and under
 * braking, so a new value must persist for [stabilityFrames] consecutive non-null frames
 * (~15 s at the bike's 5 s cadence) before it is accepted as a transition.
 *
 * [reset] clears the debounce candidate but keeps the last stable value, so a refuel
 * between rides (bars jump up across sessions) is still detected on the next ride.
 */
class FuelEventDetector(private val stabilityFrames: Int = 3) {

    private var stableBars: Int? = null
    private var candidateBars: Int? = null
    private var candidateCount = 0

    fun lastStableBars(): Int? = stableBars

    fun restoreLastStable(bars: Int?) {
        stableBars = bars
        candidateBars = null
        candidateCount = 0
    }

    fun reset() {
        candidateBars = null
        candidateCount = 0
    }

    fun onSample(sample: TelemetrySample): FuelEvent? {
        val bars = sample.fuelBars ?: return null

        if (bars == stableBars) {
            candidateBars = null
            candidateCount = 0
            return null
        }

        if (bars == candidateBars) {
            candidateCount++
        } else {
            candidateBars = bars
            candidateCount = 1
        }
        if (candidateCount < stabilityFrames) return null

        val previous = stableBars
        stableBars = bars
        candidateBars = null
        candidateCount = 0

        if (previous == null) return null // first ever stable reading; nothing to compare
        return FuelEvent(
            ts = sample.ts,
            odometerKm = sample.odometerKm,
            barsBefore = previous,
            barsAfter = bars,
            type = if (bars < previous) FuelEventType.BAR_DROP else FuelEventType.REFUEL,
        )
    }
}
