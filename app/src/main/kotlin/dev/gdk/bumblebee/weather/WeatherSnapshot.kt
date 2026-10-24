package dev.gdk.bumblebee.weather

data class WeatherSnapshot(
    val tempC: Double,
    val highC: Double?,
    val lowC: Double?,
    val code: Int,
    val fetchedAt: Long,
) {
    val label: String get() = WmoCode.label(code)

    /** Suzuki cluster icon code carried in the a533 heartbeat frame. */
    val suzukiCode: Int get() = WmoCode.suzukiCode(code)
}

/** WMO 4677 present-weather codes, as returned by Open-Meteo. */
object WmoCode {
    fun label(code: Int): String = when (code) {
        0 -> "Clear Sky"
        1 -> "Mainly Clear"
        2 -> "Partly Cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing Drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing Rain"
        71, 73, 75 -> "Snow"
        77 -> "Snow Grains"
        80, 81, 82 -> "Rain Showers"
        85, 86 -> "Snow Showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm, Hail"
        else -> "—"
    }

    /**
     * Collapses a WMO code onto the small icon set the cluster understands.
     * 0x01 sunny, 0x02 cloudy, 0x03 rain, 0x04 snow, 0x05 storm.
     */
    fun suzukiCode(code: Int): Int = when (code) {
        0, 1 -> 0x01
        2, 3, 45, 48 -> 0x02
        in 51..67, in 80..82 -> 0x03
        in 71..77, 85, 86 -> 0x04
        95, 96, 99 -> 0x05
        else -> 0x01
    }
}
