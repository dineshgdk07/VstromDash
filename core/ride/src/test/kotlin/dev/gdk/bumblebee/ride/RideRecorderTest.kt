package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideRecorderTest {

    private fun sample(ts: Long, speed: Int, odo: Int = 1000, bars: Int? = 4) =
        TelemetrySample(ts, speed, odo, 0.0, 0.0, bars, null)

    @Test
    fun `aggregates a simple ride`() {
        val r = RideRecorder()
        r.start(startTs = 0L)
        assertTrue(r.isActive)

        // 5s-spaced frames: idle, then moving, then idle again.
        r.onTelemetry(sample(ts = 0L, speed = 0, odo = 1000, bars = 4))
        r.onTelemetry(sample(ts = 5_000L, speed = 40, odo = 1000, bars = 4))
        r.onTelemetry(sample(ts = 10_000L, speed = 60, odo = 1001, bars = 4))
        r.onTelemetry(sample(ts = 15_000L, speed = 84, odo = 1001, bars = 3))
        r.onTelemetry(sample(ts = 20_000L, speed = 0, odo = 1002, bars = 3))

        val agg = r.stop(endTs = 25_000L)
        assertFalse(r.isActive)

        assertEquals(0L, agg.startTs)
        assertEquals(25_000L, agg.endTs)
        assertEquals(1000, agg.odoStartKm!!)
        assertEquals(1002, agg.odoEndKm!!)
        assertEquals(84, agg.maxSpeedKmh)
        assertEquals(4, agg.fuelBarsStart!!)
        assertEquals(3, agg.fuelBarsEnd!!)
        // First frame has no predecessor (dt unknown -> skipped for time buckets);
        // frames 2..5 each contribute dt=5s: moving 40,60,84 = 15s; idle speed 0 = 5s.
        assertEquals(15, agg.movingSec)
        assertEquals(5, agg.idleSec)
        // Time-weighted moving average: (40+60+84)*5 / 15 = 61.33
        assertEquals(61.33, agg.avgMovingSpeedKmh, 0.01)
        assertEquals(10, agg.histogram.secondsIn(SpeedBand.B30_50) + agg.histogram.secondsIn(SpeedBand.B50_70))
    }

    @Test
    fun `caps dt at 10 seconds across telemetry gaps`() {
        val r = RideRecorder()
        r.start(0L)
        r.onTelemetry(sample(0L, 50))
        r.onTelemetry(sample(60_000L, 50)) // 60s gap -> counts as 10s only
        val agg = r.stop(60_000L)
        assertEquals(10, agg.movingSec)
    }

    @Test
    fun `accumulates gps distance and skips bad fixes`() {
        val r = RideRecorder()
        r.start(0L)
        // ~111m per 0.001 deg latitude at the equator.
        r.onGps(GpsFix(ts = 0L, lat = 0.0, lng = 0.0, accuracyM = 5f))
        r.onGps(GpsFix(ts = 1_000L, lat = 0.001, lng = 0.0, accuracyM = 5f))
        r.onGps(GpsFix(ts = 2_000L, lat = 0.002, lng = 0.0, accuracyM = 90f))  // rejected: accuracy > 25
        r.onGps(GpsFix(ts = 3_000L, lat = 0.001000001, lng = 0.0, accuracyM = 5f)) // < 3m -> ignored
        r.onGps(GpsFix(ts = 4_000L, lat = 0.002, lng = 0.0, accuracyM = 5f))
        val agg = r.stop(5_000L)
        assertEquals(222.4, agg.gpsDistanceM, 1.0) // two ~111.2m segments
    }

    @Test(expected = IllegalStateException::class)
    fun `stop before start throws`() {
        RideRecorder().stop(0L)
    }
}
