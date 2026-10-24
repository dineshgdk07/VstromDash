package dev.gdk.bumblebee.ride

data class CsvSample(
    val ts: Long,
    val speedKmh: Int,
    val odometerKm: Int,
    val tripAKm: Double,
    val tripBKm: Double,
    val fuelBars: Int?,
    val fuelEconKml: Double?,
)

object CsvExport {
    fun rideSamples(rideId: Long, samples: List<CsvSample>): String = buildString {
        appendLine("# Bumblebee Dash ride #$rideId")
        appendLine("timestamp_ms,speed_kmh,odometer_km,trip_a_km,trip_b_km,fuel_bars,fuel_econ_kml")
        for (s in samples) {
            append(s.ts); append(',')
            append(s.speedKmh); append(',')
            append(s.odometerKm); append(',')
            append(s.tripAKm); append(',')
            append(s.tripBKm); append(',')
            append(s.fuelBars ?: ""); append(',')
            append(s.fuelEconKml ?: "")
            appendLine()
        }
    }
}
