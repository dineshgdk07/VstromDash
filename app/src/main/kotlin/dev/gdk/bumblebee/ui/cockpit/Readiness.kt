package dev.gdk.bumblebee.ui.cockpit

enum class PillarStatus { READY, CAUTION, NOT_READY, UNKNOWN }

enum class ReadinessBand { READY, CAUTION, NOT_READY, COMPUTING }

data class ReadinessPillar(
    val name: String,
    val status: PillarStatus,
    val detail: String,
)

data class ReadinessScore(
    val percent: Int,
    val band: ReadinessBand,
    val label: String,
    val pillars: List<ReadinessPillar>,
)

object ReadinessCalculator {
    /** WMO codes that make riding genuinely unsafe: heavy rain, ice, snow, storms. */
    private val SEVERE = setOf(56, 57, 65, 66, 67, 75, 82, 85, 86, 95, 96, 99)

    /** Codes that warrant caution: fog, light rain, drizzle, showers. */
    private val POOR = setOf(45, 48, 51, 53, 55, 61, 63, 71, 73, 77, 80, 81)

    fun score(
        fuelBars: Int?,
        bleReady: Boolean?,
        phoneBatteryPercent: Int? = null,
        gpsOn: Boolean = false,
        weatherCode: Int? = null,
        weatherLabel: String? = null,
        serviceDueKm: Int? = null,
        bikePaired: Boolean = true,
        linkFailed: Boolean = false,
    ): ReadinessScore {
        val fuel = when {
            fuelBars == null -> ReadinessPillar("Fuel", PillarStatus.UNKNOWN, "no reading")
            fuelBars >= 3 -> ReadinessPillar("Fuel", PillarStatus.READY, "$fuelBars bars")
            fuelBars == 2 -> ReadinessPillar("Fuel", PillarStatus.CAUTION, "2 bars")
            else -> ReadinessPillar("Fuel", PillarStatus.NOT_READY, "${fuelBars} bar")
        }
        val link = when {
            !bikePaired -> ReadinessPillar("Link", PillarStatus.UNKNOWN, "pair your bike")
            linkFailed -> ReadinessPillar("Link", PillarStatus.NOT_READY, "couldn't connect")
            bleReady == null -> ReadinessPillar("Link", PillarStatus.UNKNOWN, "no link")
            !bleReady -> ReadinessPillar("Link", PillarStatus.UNKNOWN, "waiting for bike")
            phoneBatteryPercent != null && phoneBatteryPercent < 15 ->
                ReadinessPillar("Link", PillarStatus.CAUTION, "phone battery $phoneBatteryPercent%")
            else -> ReadinessPillar("Link", PillarStatus.READY, "connected")
        }
        val weather = when {
            weatherCode == null -> ReadinessPillar("Weather", PillarStatus.UNKNOWN, "no forecast")
            weatherCode in SEVERE -> ReadinessPillar("Weather", PillarStatus.NOT_READY, weatherLabel ?: "severe")
            weatherCode in POOR -> ReadinessPillar("Weather", PillarStatus.CAUTION, weatherLabel ?: "poor")
            else -> ReadinessPillar("Weather", PillarStatus.READY, weatherLabel ?: "clear")
        }
        val route = if (gpsOn) {
            ReadinessPillar("Route", PillarStatus.READY, "GPS")
        } else {
            ReadinessPillar("Route", PillarStatus.UNKNOWN, "no GPS")
        }
        val service = when {
            serviceDueKm == null -> ReadinessPillar("Service", PillarStatus.UNKNOWN, "not set")
            serviceDueKm <= 0 -> ReadinessPillar("Service", PillarStatus.NOT_READY, "overdue")
            serviceDueKm <= 200 -> ReadinessPillar("Service", PillarStatus.CAUTION, "$serviceDueKm km")
            else -> ReadinessPillar("Service", PillarStatus.READY, "$serviceDueKm km")
        }
        val pillars = listOf(fuel, weather, route, link, service)

        val scored = pillars.filter { it.status != PillarStatus.UNKNOWN }
        if (scored.isEmpty()) {
            return ReadinessScore(0, ReadinessBand.COMPUTING, "…", pillars)
        }
        val pts = scored.map {
            when (it.status) {
                PillarStatus.READY -> 100
                PillarStatus.CAUTION -> 50
                PillarStatus.NOT_READY -> 0
                PillarStatus.UNKNOWN -> 0
            }
        }
        val percent = pts.average().toInt()
        val hasNotReady = scored.any { it.status == PillarStatus.NOT_READY }
        val band = when {
            hasNotReady && percent >= 85 -> ReadinessBand.CAUTION
            percent >= 85 -> ReadinessBand.READY
            percent >= 50 -> ReadinessBand.CAUTION
            else -> ReadinessBand.NOT_READY
        }
        val label = when (band) {
            ReadinessBand.READY -> "Good to Go"
            ReadinessBand.CAUTION -> "Check"
            ReadinessBand.NOT_READY -> "Not Ready"
            ReadinessBand.COMPUTING -> "…"
        }
        return ReadinessScore(percent, band, label, pillars)
    }
}
