package dev.gdk.bumblebee.ride

import dev.gdk.bumblebee.protocol.TelemetryFrame

/** One archived a537 frame with the phone timestamp it arrived at. */
data class TelemetrySample(
    val ts: Long,
    val speedKmh: Int,
    val odometerKm: Int,
    val tripAKm: Double,
    val tripBKm: Double,
    val fuelBars: Int?,
    val fuelEconKml: Double?,
) {
    companion object {
        fun fromFrame(frame: TelemetryFrame, ts: Long): TelemetrySample = TelemetrySample(
            ts = ts,
            speedKmh = frame.speedKmh,
            odometerKm = frame.odometerKm,
            tripAKm = frame.tripAKm,
            tripBKm = frame.tripBKm,
            fuelBars = frame.fuelBars,
            // V2 (byte25/2, Gixxer-family validated) when raw bytes exist; legacy otherwise.
            fuelEconKml = frame.fuelEconKmlV2 ?: frame.fuelEconKml,
        )
    }
}

/** One GPS fix from the phone. Units: degrees, meters, m/s. */
data class GpsFix(
    val ts: Long,
    val lat: Double,
    val lng: Double,
    val altitudeM: Double? = null,
    val bearingDeg: Float? = null,
    val speedMps: Float? = null,
    val accuracyM: Float? = null,
)
