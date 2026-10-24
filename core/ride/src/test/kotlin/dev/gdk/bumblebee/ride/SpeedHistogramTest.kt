package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedHistogramTest {

    @Test
    fun `buckets speeds into the right bands`() {
        val h = SpeedHistogram()
        h.add(speedKmh = 0, durationSec = 60)    // IDLE
        h.add(speedKmh = 2, durationSec = 10)    // IDLE (< 3 km/h is idle)
        h.add(speedKmh = 25, durationSec = 30)   // B1_30
        h.add(speedKmh = 30, durationSec = 20)   // B30_50 (lower bound inclusive)
        h.add(speedKmh = 65, durationSec = 120)  // B50_70
        h.add(speedKmh = 84, durationSec = 15)   // B70_90
        h.add(speedKmh = 120, durationSec = 5)   // B90_PLUS

        assertEquals(70, h.secondsIn(SpeedBand.IDLE))
        assertEquals(30, h.secondsIn(SpeedBand.B1_30))
        assertEquals(20, h.secondsIn(SpeedBand.B30_50))
        assertEquals(120, h.secondsIn(SpeedBand.B50_70))
        assertEquals(15, h.secondsIn(SpeedBand.B70_90))
        assertEquals(5, h.secondsIn(SpeedBand.B90_PLUS))
    }

    @Test
    fun `json round trip preserves all bands`() {
        val h = SpeedHistogram()
        h.add(55, 300)
        h.add(0, 45)
        val restored = SpeedHistogram.fromJson(h.toJson())
        for (band in SpeedBand.entries) {
            assertEquals(h.secondsIn(band), restored.secondsIn(band))
        }
    }

    @Test
    fun `empty histogram serializes and restores to all zeros`() {
        val restored = SpeedHistogram.fromJson(SpeedHistogram().toJson())
        for (band in SpeedBand.entries) {
            assertEquals(0, restored.secondsIn(band))
        }
    }
}
