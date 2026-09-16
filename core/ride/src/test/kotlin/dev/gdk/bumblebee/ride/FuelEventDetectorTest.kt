package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelEventDetectorTest {

    private fun sample(ts: Long, bars: Int?, odo: Int = 500) =
        TelemetrySample(ts, 40, odo, 0.0, 0.0, bars, null)

    @Test
    fun `emits BAR_DROP only after the new value is stable for 3 frames`() {
        val d = FuelEventDetector()
        // Establish stable baseline of 4 bars.
        assertNull(d.onSample(sample(0L, 4)))
        assertNull(d.onSample(sample(5_000L, 4)))
        assertNull(d.onSample(sample(10_000L, 4)))
        // Drop to 3 — needs 3 consecutive frames before the event fires.
        assertNull(d.onSample(sample(15_000L, 3, odo = 548)))
        assertNull(d.onSample(sample(20_000L, 3, odo = 548)))
        val e = d.onSample(sample(25_000L, 3, odo = 549))
        assertEquals(FuelEventType.BAR_DROP, e!!.type)
        assertEquals(4, e.barsBefore)
        assertEquals(3, e.barsAfter)
        assertEquals(549, e.odometerKm)
        assertEquals(25_000L, e.ts)
        // No duplicate event afterwards.
        assertNull(d.onSample(sample(30_000L, 3)))
    }

    @Test
    fun `flicker shorter than 3 frames is ignored`() {
        val d = FuelEventDetector()
        d.onSample(sample(0L, 4)); d.onSample(sample(5_000L, 4)); d.onSample(sample(10_000L, 4))
        // One-frame flicker to 3 (slope/braking slosh) then back to 4.
        assertNull(d.onSample(sample(15_000L, 3)))
        assertNull(d.onSample(sample(20_000L, 4)))
        assertNull(d.onSample(sample(25_000L, 4)))
        assertNull(d.onSample(sample(30_000L, 4)))
    }

    @Test
    fun `emits REFUEL when bars increase, even across a reset`() {
        val d = FuelEventDetector()
        d.onSample(sample(0L, 2)); d.onSample(sample(5_000L, 2)); d.onSample(sample(10_000L, 2))
        d.reset() // ride ended; rider refuels with engine off
        // Next ride starts with 6 bars: first stable value after reset with a known
        // previous stable value of 2 -> REFUEL.
        assertNull(d.onSample(sample(100_000L, 6)))
        assertNull(d.onSample(sample(105_000L, 6)))
        val e = d.onSample(sample(110_000L, 6, odo = 700))
        assertEquals(FuelEventType.REFUEL, e!!.type)
        assertEquals(2, e.barsBefore)
        assertEquals(6, e.barsAfter)
    }

    @Test
    fun `null bars are skipped without breaking state`() {
        val d = FuelEventDetector()
        d.onSample(sample(0L, 4)); d.onSample(sample(5_000L, 4)); d.onSample(sample(10_000L, 4))
        assertNull(d.onSample(sample(15_000L, null)))
        assertNull(d.onSample(sample(20_000L, 3)))
        assertNull(d.onSample(sample(25_000L, 3)))
        val e = d.onSample(sample(30_000L, 3))
        assertEquals(FuelEventType.BAR_DROP, e!!.type)
    }

    @Test
    fun `very first stable value emits no event`() {
        val d = FuelEventDetector()
        assertNull(d.onSample(sample(0L, 5)))
        assertNull(d.onSample(sample(5_000L, 5)))
        assertNull(d.onSample(sample(10_000L, 5)))
        assertNull(d.onSample(sample(15_000L, 5)))
    }
}
