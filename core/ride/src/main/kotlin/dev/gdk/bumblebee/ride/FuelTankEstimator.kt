package dev.gdk.bumblebee.ride

data class FuelEstimate(
    val litresLeft: Double,
    val percent: Double,
    val rangeKm: Double,
    val kmPerLUsed: Double,
    val isRough: Boolean,
)

/** V-Strom 250 SX tank is 12 L. Used only when the rider has not overridden capacity. */
object FuelTankEstimator {
    const val VSTROM_250_SX_LITRES: Double = 12.0
    const val DEFAULT_FALLBACK_KM_PER_L: Double = 40.0
    private const val BARS_MAX = 6.0

    fun estimate(
        fills: List<FuelFill>,
        currentOdometerKm: Int?,
        avgKmPerL: Double?,
        bikeLiveKmPerL: Double?,
        bikeFuelBars: Int?,
        capacityL: Double = VSTROM_250_SX_LITRES,
        fallbackKmPerL: Double = DEFAULT_FALLBACK_KM_PER_L,
    ): FuelEstimate? {
        if (capacityL <= 0.0) return null
        val kmPerL = listOfNotNull(avgKmPerL, bikeLiveKmPerL).firstOrNull { it > 0.0 } ?: fallbackKmPerL
        if (kmPerL <= 0.0 || kmPerL.isNaN()) return null
        val anchor = fills.maxByOrNull { it.ts }
        if (anchor != null && currentOdometerKm != null) {
            val kmSince = (currentOdometerKm - anchor.odometerKm).coerceAtLeast(0)
            val litres = (capacityL - kmSince / kmPerL).coerceIn(0.0, capacityL)
            return FuelEstimate(litres, litres / capacityL, litres * kmPerL, kmPerL, isRough = false)
        }
        val bars = bikeFuelBars ?: return null
        if (bars < 0) return null
        val litres = ((bars / BARS_MAX) * capacityL).coerceIn(0.0, capacityL)
        return FuelEstimate(litres, litres / capacityL, litres * kmPerL, kmPerL, isRough = true)
    }
}

enum class RefuelBucket { TODAY, ONE_DAY, TWO_THREE_DAYS, THIS_WEEK, OVER_A_WEEK, UNKNOWN }

data class RefuelPrediction(val bucket: RefuelBucket, val fillBeforeService: Boolean) {
    fun label(): String = when (bucket) {
        RefuelBucket.TODAY -> "fill today"
        RefuelBucket.ONE_DAY -> "~1 day of riding left"
        RefuelBucket.TWO_THREE_DAYS -> "~2–3 days of riding left"
        RefuelBucket.THIS_WEEK -> "this week"
        RefuelBucket.OVER_A_WEEK -> "more than a week"
        RefuelBucket.UNKNOWN -> "need range and a recent pace"
    }
}

object RefuelPredictor {
    private const val SERVICE_NEAR_KM = 200

    fun predict(rangeKm: Double?, dailyKmPace: Double?, serviceKmRemaining: Int?): RefuelPrediction {
        if (rangeKm == null || rangeKm < 0.0) return RefuelPrediction(RefuelBucket.UNKNOWN, false)
        if (dailyKmPace == null || dailyKmPace <= 0.0) return RefuelPrediction(RefuelBucket.UNKNOWN, false)
        val days = rangeKm / dailyKmPace
        val bucket = when {
            days < 1.0 -> RefuelBucket.TODAY
            days < 2.0 -> RefuelBucket.ONE_DAY
            days < 4.0 -> RefuelBucket.TWO_THREE_DAYS
            days < 7.0 -> RefuelBucket.THIS_WEEK
            else -> RefuelBucket.OVER_A_WEEK
        }
        val fillBeforeService = serviceKmRemaining != null &&
            serviceKmRemaining <= (rangeKm + SERVICE_NEAR_KM).toInt()
        return RefuelPrediction(bucket, fillBeforeService)
    }
}
