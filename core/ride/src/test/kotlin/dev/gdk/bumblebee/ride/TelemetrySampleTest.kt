package dev.gdk.bumblebee.ride

import dev.gdk.bumblebee.protocol.TelemetryFrame
import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetrySampleTest {

    @Test
    fun `fromFrame copies fields and timestamps`() {
        val frame = TelemetryFrame(
            speedKmh = 62, odometerKm = 15234, tripAKm = 412.5, tripBKm = 88.0,
            fuelBars = 4, fuelEconKml = 41.2,
        )
        val s = TelemetrySample.fromFrame(frame, ts = 1_000L)
        assertEquals(1_000L, s.ts)
        assertEquals(62, s.speedKmh)
        assertEquals(15234, s.odometerKm)
        assertEquals(412.5, s.tripAKm, 0.0)
        assertEquals(88.0, s.tripBKm, 0.0)
        assertEquals(4, s.fuelBars)
        // No raw bytes -> fuelEconKmlV2 is null -> falls back to legacy field.
        assertEquals(41.2, s.fuelEconKml!!, 0.0)
    }

    @Test
    fun `fromFrame prefers the V2 fuel economy decode when raw bytes exist`() {
        // Build a frame whose raw byte 25 = 96 -> V2 economy 48.0 km/L.
        val encoded = TelemetryFrame(
            speedKmh = 40, odometerKm = 100, tripAKm = 1.0, tripBKm = 2.0,
            fuelBars = 5, fuelEconKml = null,
        ).encode()
        encoded[25] = 96
        val decoded = TelemetryFrame(
            speedKmh = 40, odometerKm = 100, tripAKm = 1.0, tripBKm = 2.0,
            fuelBars = 5, fuelEconKml = null, raw = encoded,
        )
        val s = TelemetrySample.fromFrame(decoded, ts = 5L)
        assertEquals(48.0, s.fuelEconKml!!, 0.0)
    }
}
