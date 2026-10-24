package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarCalibratorTest {

    private fun drop(odo: Int, from: Int) =
        FuelEvent(ts = odo.toLong(), odometerKm = odo, barsBefore = from, barsAfter = from - 1, type = FuelEventType.BAR_DROP)

    private fun refuel(odo: Int, from: Int, to: Int) =
        FuelEvent(ts = odo.toLong(), odometerKm = odo, barsBefore = from, barsAfter = to, type = FuelEventType.REFUEL)

    @Test
    fun `learns km per bar from consecutive drops`() {
        val c = BarCalibrator()
        // Bar 5 lasted 1040-1000=40km; bar 4 lasted 1088-1040=48km.
        c.update(listOf(drop(1000, from = 6), drop(1040, from = 5), drop(1088, from = 4)))
        assertEquals(40.0, c.kmPerBar(5)!!, 0.001)
        assertEquals(48.0, c.kmPerBar(4)!!, 0.001)
        assertNull(c.kmPerBar(3)) // never observed
        assertEquals(44.0, c.averageKmPerBar()!!, 0.001)
    }

    @Test
    fun `a refuel breaks the consecutive-drop chain`() {
        val c = BarCalibrator()
        c.update(listOf(
            drop(1000, from = 3),
            refuel(1010, from = 2, to = 6),
            drop(1100, from = 6), // NOT paired with the pre-refuel drop
            drop(1145, from = 5),
        ))
        assertEquals(45.0, c.kmPerBar(5)!!, 0.001)
        assertNull(c.kmPerBar(2))
        assertNull(c.kmPerBar(6)) // needs the *next* drop after 1100->? chain start
    }

    @Test
    fun `averages multiple observations of the same bar`() {
        val c = BarCalibrator()
        c.update(listOf(
            drop(1000, from = 5), drop(1040, from = 4),   // bar 4: 40 km
            refuel(1050, from = 3, to = 6),
            drop(1200, from = 5), drop(1248, from = 4),   // bar 4: 48 km
        ))
        assertEquals(44.0, c.kmPerBar(4)!!, 0.001)
    }

    @Test
    fun `estimates range from current bars`() {
        val c = BarCalibrator()
        c.update(listOf(drop(1000, from = 6), drop(1040, from = 5), drop(1088, from = 4)))
        // 3 bars remaining x 44.0 average = 132 km.
        assertEquals(132.0, c.estimatedRangeKm(3)!!, 0.001)
        assertNull(BarCalibrator().estimatedRangeKm(3)) // no data yet -> silent (spec principle 1)
    }

    @Test
    fun `remaining range uses the learned bars not a flat average`() {
        val c = BarCalibrator()
        c.update(listOf(drop(1000, from = 3), drop(1040, from = 2), drop(1088, from = 1)))
        // Remaining 2 bars: bar 2 = 40, bar 1 = 48.
        assertEquals(88.0, c.estimatedRangeKm(2)!!, 0.001)
    }
}
