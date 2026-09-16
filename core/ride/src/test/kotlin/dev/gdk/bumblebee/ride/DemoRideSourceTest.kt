package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoRideSourceTest {

    @Test
    fun `produces a 10 minute ride at the right cadences`() {
        val demo = DemoRideSource()
        val frames = demo.telemetry()
        val fixes = demo.gps()
        assertEquals(120, frames.size)      // 600s / 5s
        assertEquals(600, fixes.size)       // 600s / 1s
        assertEquals(0L, frames.first().first)
        assertEquals(595_000L, frames.last().first)
    }

    @Test
    fun `ride shape has idle bookends, movement, and one bar drop`() {
        val demo = DemoRideSource(startOdoKm = 15_000, startBars = 5)
        val frames = demo.telemetry().map { it.second }
        assertEquals(0, frames.first().speedKmh)
        assertEquals(0, frames.last().speedKmh)
        assertTrue(frames.any { it.speedKmh in 30..60 })
        assertTrue(frames.none { it.speedKmh > 60 })
        assertEquals(5, frames.first().fuelBars)
        assertEquals(4, frames.last().fuelBars)
        // Odometer never decreases.
        frames.zipWithNext().forEach { (a, b) -> assertTrue(b.odometerKm >= a.odometerKm) }
    }

    @Test
    fun `gps track moves while telemetry says moving`() {
        val demo = DemoRideSource()
        val fixes = demo.gps()
        val d = Geo.haversineMeters(fixes.first().lat, fixes.first().lng, fixes.last().lat, fixes.last().lng)
        assertTrue("expected net movement > 500m, was $d", d > 500)
    }

    @Test
    fun `is deterministic`() {
        val a = DemoRideSource().telemetry().map { it.second }
        val b = DemoRideSource().telemetry().map { it.second }
        assertEquals(a, b)
    }
}
