package dev.gdk.bumblebee.ride

data class ServiceSpend(
    val id: Long,
    val ts: Long,
    val odometerKm: Int,
    val item: String,
    val rupees: Double? = null,
    val note: String? = null,
    val shop: String? = null,
    val planId: Long? = null,
    val filePath: String? = null,
)

data class RunningCost(
    val distanceKm: Int,
    val fuelRupees: Double,
    val serviceRupees: Double,
    val totalRupees: Double,
    val rupeesPerKm: Double,
    val rupeesPer100Km: Double,
    val fuelFraction: Double,
    val serviceFraction: Double,
    val fuelFillsPriced: Int,
    val fuelFillsTotal: Int,
    val servicesPriced: Int,
    val servicesTotal: Int,
)

object RunningCostAnalytics {
    fun cost(
        fills: List<FuelFill>,
        services: List<ServiceSpend>,
        fallbackDistanceKm: Int? = null,
    ): RunningCost? {
        val odos = fills.map { it.odometerKm }
        val fillDelta = if (odos.size >= 2) (odos.max() - odos.min()) else 0
        val distanceKm = (if (fillDelta > 0) fillDelta else fallbackDistanceKm ?: 0).coerceAtLeast(0)
        if (distanceKm <= 0) return null
        val fuelPriced = fills.mapNotNull { it.rupees }.filter { it >= 0.0 }
        val svcPriced = services.mapNotNull { it.rupees }.filter { it >= 0.0 }
        val fuelRupees = fuelPriced.sum()
        val serviceRupees = svcPriced.sum()
        val total = fuelRupees + serviceRupees
        if (total <= 0.0) return null
        return RunningCost(
            distanceKm = distanceKm,
            fuelRupees = fuelRupees,
            serviceRupees = serviceRupees,
            totalRupees = total,
            rupeesPerKm = total / distanceKm,
            rupeesPer100Km = total / distanceKm * 100.0,
            fuelFraction = fuelRupees / total,
            serviceFraction = serviceRupees / total,
            fuelFillsPriced = fuelPriced.size,
            fuelFillsTotal = fills.size,
            servicesPriced = svcPriced.size,
            servicesTotal = services.size,
        )
    }
}
