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
    fun score(
        fuelBars: Int?,
        bleReady: Boolean?,
        phoneBatteryPercent: Int? = null,
    ): ReadinessScore {
        val fuel = when {
            fuelBars == null -> ReadinessPillar("Fuel", PillarStatus.UNKNOWN, "no reading")
            fuelBars >= 3 -> ReadinessPillar("Fuel", PillarStatus.READY, "$fuelBars bars")
            fuelBars == 2 -> ReadinessPillar("Fuel", PillarStatus.CAUTION, "2 bars")
            else -> ReadinessPillar("Fuel", PillarStatus.NOT_READY, "${fuelBars} bar")
        }
        val link = when {
            bleReady == null -> ReadinessPillar("Link", PillarStatus.UNKNOWN, "no link")
            !bleReady -> ReadinessPillar("Link", PillarStatus.NOT_READY, "disconnected")
            phoneBatteryPercent != null && phoneBatteryPercent < 15 ->
                ReadinessPillar("Link", PillarStatus.CAUTION, "phone battery $phoneBatteryPercent%")
            else -> ReadinessPillar("Link", PillarStatus.READY, "connected")
        }
        val weather = ReadinessPillar("Weather", PillarStatus.UNKNOWN, "no forecast")
        val route = ReadinessPillar("Route", PillarStatus.UNKNOWN, "no GPS")
        val service = ReadinessPillar("Service", PillarStatus.UNKNOWN, "not set")
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
