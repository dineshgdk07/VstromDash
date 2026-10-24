package dev.gdk.bumblebee.ride

import java.util.Locale

/** One rider-logged fill. Litres and rupees are optional — never invent them. */
data class FuelFill(
    val id: Long,
    val ts: Long,
    val odometerKm: Int,
    val litres: Double? = null,
    val rupees: Double? = null,
    val isFullTank: Boolean = true,
    val outlierConfirmed: Boolean = false,
)

data class TankSpan(
    val closingFillId: Long,
    val km: Double,
    val litres: Double,
    val kmPerL: Double,
    val rupeesPerKm: Double?,
    val outlier: Boolean,
)

data class ClusterAccuracy(
    val bikeKmPerL: Double,
    val tankKmPerL: Double,
    val deltaFraction: Double,
    val summary: String,
)

data class CostStats(
    val rollingRupeesPerKm: Double,
    val lifetimeRupeesPerKm: Double,
    val pricedIntervals: Int,
    val totalIntervals: Int,
) {
    val isPartial: Boolean get() = pricedIntervals < totalIntervals
}

object FuelEconomics {

    /**
     * Economy between two full-tank fills. Litres are every fill after the
     * opening full tank, up to and including the closing one. Partials do not
     * close a tank. A result more than 30% off the median is an outlier until
     * the rider confirms it.
     */
    fun tankSpans(fills: List<FuelFill>): List<TankSpan> {
        val asc = fills.sortedBy { it.ts }
        val fullAt = asc.indices.filter { asc[it].isFullTank }
        val drafts = mutableListOf<TankSpan>()
        for (i in 1 until fullAt.size) {
            val open = asc[fullAt[i - 1]]
            val close = asc[fullAt[i]]
            val between = asc.subList(fullAt[i - 1] + 1, fullAt[i] + 1)
            val litres = between.sumOf { it.litres ?: 0.0 }
            val km = (close.odometerKm - open.odometerKm).toDouble()
            if (km <= 0.0 || litres <= 0.0) continue
            val priced = between.all { it.rupees != null && it.rupees > 0.0 }
            val rupees = if (priced) between.sumOf { it.rupees ?: 0.0 } else null
            drafts += TankSpan(
                closingFillId = close.id,
                km = km,
                litres = litres,
                kmPerL = km / litres,
                rupeesPerKm = rupees?.let { it / km },
                outlier = false,
            )
        }
        if (drafts.isEmpty()) return drafts
        val median = median(drafts.map { it.kmPerL })
        return drafts.map { span ->
            val confirmed = asc.any { it.id == span.closingFillId && it.outlierConfirmed }
            val off = median > 0.0 && kotlin.math.abs(span.kmPerL - median) / median > 0.30
            span.copy(outlier = off && !confirmed)
        }
    }

    /** Full-to-full km/L, outliers excluded until the rider confirms them. */
    fun perTankKmPerL(fills: List<FuelFill>): List<Pair<Long, Double>> =
        tankSpans(fills).filter { !it.outlier }.map { it.closingFillId to it.kmPerL }

    fun clusterAccuracy(bikeKmPerL: Double?, spans: List<TankSpan>): ClusterAccuracy? {
        val usable = spans.filter { !it.outlier }
        if (bikeKmPerL == null || bikeKmPerL <= 0.0 || usable.size < 3) return null
        val tank = usable.map { it.kmPerL }.average()
        if (tank <= 0.0) return null
        val delta = (bikeKmPerL - tank) / tank
        val pct = kotlin.math.round(delta * 100).toInt()
        val sign = if (pct > 0) "+" else ""
        return ClusterAccuracy(
            bikeKmPerL = bikeKmPerL,
            tankKmPerL = tank,
            deltaFraction = delta,
            summary = "Bike shows ${root1(bikeKmPerL)} km/L; your tanks say ${root1(tank)} km/L (cluster $sign$pct%)",
        )
    }

    fun missedFill(prevOdoKm: Int, nextOdoKm: Int, tankRangeKm: Double): Boolean =
        tankRangeKm > 0.0 && (nextOdoKm - prevOdoKm) > tankRangeKm

    private fun root1(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    fun averageKmPerL(fills: List<FuelFill>, count: Int = 5): Double? {
        val pairs = perTankKmPerL(fills).takeLast(count)
        return if (pairs.isEmpty()) null else pairs.map { it.second }.average()
    }

    fun totalIntervals(fills: List<FuelFill>): Int {
        val asc = fills.sortedBy { it.ts }
        var n = 0
        for (i in 1 until asc.size) {
            if (asc[i].odometerKm - asc[i - 1].odometerKm > 0) n++
        }
        return n
    }

    fun perTankRupeesPerKm(fills: List<FuelFill>): List<Pair<Long, Double>> {
        val asc = fills.sortedBy { it.ts }
        val out = mutableListOf<Pair<Long, Double>>()
        for (i in 1 until asc.size) {
            val km = (asc[i].odometerKm - asc[i - 1].odometerKm).toDouble()
            val rupees = asc[i].rupees
            if (km > 0 && rupees != null && rupees > 0.0) out += asc[i].id to (rupees / km)
        }
        return out
    }

    fun costStats(fills: List<FuelFill>, count: Int = 5): CostStats? {
        val priced = perTankRupeesPerKm(fills)
        if (priced.isEmpty()) return null
        return CostStats(
            rollingRupeesPerKm = priced.takeLast(count).map { it.second }.average(),
            lifetimeRupeesPerKm = priced.map { it.second }.average(),
            pricedIntervals = priced.size,
            totalIntervals = totalIntervals(fills),
        )
    }

    /** Litres for a distance. Prefers tank km/L; bike cluster km/L is a fallback and must be labelled. */
    fun litresBurnt(distanceKm: Double, tankKmPerL: Double?, bikeKmPerL: Double?): Pair<Double, Boolean>? {
        if (distanceKm <= 0) return null
        val tank = tankKmPerL?.takeIf { it > 0 }
        val bike = bikeKmPerL?.takeIf { it > 0 }
        val kml = tank ?: bike ?: return null
        return (distanceKm / kml) to (tank != null)
    }
}
