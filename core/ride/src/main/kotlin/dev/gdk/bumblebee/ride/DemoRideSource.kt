package dev.gdk.bumblebee.ride

import dev.gdk.bumblebee.protocol.TelemetryFrame

/**
 * Deterministic scripted ~10-minute urban demo ride (spec §6 demo mode seed).
 * Speed script (per 5 s frame, 120 frames): warm-up idle, two cruise legs with a
 * mid-ride stop, cool-down idle. One fuel-bar drop at the 60% mark.
 */
class DemoRideSource(
    private val startOdoKm: Int = 15_000,
    private val startBars: Int = 5,
    private val startTs: Long = 0L,
) {
    companion object {
        const val DURATION_SEC = 600
        const val TELEMETRY_PERIOD_SEC = 5
        private const val BAR_DROP_AT_FRAME = 72 // 60% of 120 frames

        /** Speed in km/h for second [t] of the ride. Piecewise trapezoid profile. */
        internal fun speedAt(t: Int): Int = when {
            t < 30 -> 0                                    // warm-up idle
            t < 60 -> (t - 30) * 2                         // ramp 0 -> 60
            t < 240 -> 45 + ((t / 10) % 3) * 5             // urban cruise 45-55
            t < 270 -> maxOf(0, 50 - (t - 240) * 2)        // slow to stop (signal)
            t < 300 -> 0                                   // stopped
            t < 330 -> minOf(60, (t - 300) * 2)            // pull away
            t < 540 -> 50 + ((t / 15) % 3) * 5             // cruise 50-60
            t < 570 -> maxOf(0, 60 - (t - 540) * 2)        // arrive
            else -> 0                                      // cool-down idle
        }
    }

    fun telemetry(): List<Pair<Long, TelemetryFrame>> {
        val frames = mutableListOf<Pair<Long, TelemetryFrame>>()
        var odoMeters = 0.0
        for (i in 0 until DURATION_SEC / TELEMETRY_PERIOD_SEC) {
            val t = i * TELEMETRY_PERIOD_SEC
            val speed = speedAt(t)
            odoMeters += speed / 3.6 * TELEMETRY_PERIOD_SEC
            val bars = if (i >= BAR_DROP_AT_FRAME) startBars - 1 else startBars
            frames += (startTs + t * 1000L) to TelemetryFrame(
                speedKmh = speed,
                odometerKm = startOdoKm + (odoMeters / 1000).toInt(),
                tripAKm = odoMeters / 1000,
                tripBKm = odoMeters / 1000,
                fuelBars = bars,
                fuelEconKml = 42.0,
            )
        }
        return frames
    }

    fun gps(): List<GpsFix> {
        val fixes = mutableListOf<GpsFix>()
        var lat = 12.9716 // Bengaluru-ish start point
        val lng = 77.5946
        for (t in 0 until DURATION_SEC) {
            val speedMps = speedAt(t) / 3.6
            lat += speedMps / 111_320.0 // ~meters per degree latitude; ride heads due north
            fixes += GpsFix(
                ts = startTs + t * 1000L,
                lat = lat,
                lng = lng,
                altitudeM = 900.0,
                bearingDeg = 0f,
                speedMps = speedMps.toFloat(),
                accuracyM = 5f,
            )
        }
        return fixes
    }
}
