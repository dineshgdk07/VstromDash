package dev.gdk.bumblebee.ride

/**
 * Learns how many km each fuel-bar position lasts from the BAR_DROP history.
 *
 * A bar's consumption is the odometer distance between the drop that *started* it
 * (barsAfter == b) and the next drop (barsBefore == b). Only consecutive drops count;
 * a REFUEL between them invalidates the pair. Bars are rarely linear (spec C2#9), so
 * observations are kept per bar position.
 */
class BarCalibrator {

    private val observations = mutableMapOf<Int, MutableList<Double>>()

    /** Rebuild from the full chronological fuel-event history. */
    fun update(events: List<FuelEvent>) {
        observations.clear()
        var chainStart: FuelEvent? = null // last BAR_DROP, valid only if no refuel since
        for (e in events) {
            when (e.type) {
                FuelEventType.REFUEL -> chainStart = null
                FuelEventType.BAR_DROP -> {
                    val prev = chainStart
                    if (prev != null && prev.barsAfter == e.barsBefore) {
                        val km = (e.odometerKm - prev.odometerKm).toDouble()
                        if (km > 0) observations.getOrPut(e.barsBefore) { mutableListOf() }.add(km)
                    }
                    chainStart = e
                }
            }
        }
    }

    fun kmPerBar(bar: Int): Double? = observations[bar]?.average()

    fun averageKmPerBar(): Double? {
        val all = observations.values.flatten()
        return if (all.isEmpty()) null else all.average()
    }

    /**
     * Remaining km from the current bar count. Uses the learned km for each
     * remaining bar when we have it, otherwise the fleet average. Silent null
     * when nothing has been learned yet.
     */
    fun estimatedRangeKm(currentBars: Int): Double? {
        if (currentBars <= 0) return null
        val fallback = averageKmPerBar() ?: return null
        var sum = 0.0
        for (b in currentBars downTo 1) {
            sum += kmPerBar(b) ?: fallback
        }
        return sum
    }
}
