package dev.gdk.bumblebee.ui.cockpit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessCalculatorTest {

    @Test
    fun `connected with fuel 4 is Good to Go`() {
        val s = ReadinessCalculator.score(fuelBars = 4, bleReady = true)
        assertEquals(100, s.percent)
        assertEquals(ReadinessBand.READY, s.band)
        assertEquals("Good to Go", s.label)
    }

    @Test
    fun `two fuel bars is Check`() {
        val s = ReadinessCalculator.score(fuelBars = 2, bleReady = true)
        assertEquals(ReadinessBand.CAUTION, s.band)
        assertEquals("Check", s.label)
    }

    @Test
    fun `disconnected never Good to Go`() {
        val s = ReadinessCalculator.score(fuelBars = 6, bleReady = false)
        assertTrue(s.band != ReadinessBand.READY)
        assertEquals("Check", s.label)
        assertEquals(50, s.percent)
    }

    @Test
    fun `null fuel is excluded from average`() {
        val s = ReadinessCalculator.score(fuelBars = null, bleReady = true)
        assertEquals(100, s.percent)
        assertEquals(ReadinessBand.READY, s.band)
        assertEquals(PillarStatus.UNKNOWN, s.pillars.first { it.name == "Fuel" }.status)
    }

    @Test
    fun `no data is computing`() {
        val s = ReadinessCalculator.score(fuelBars = null, bleReady = null)
        assertEquals(0, s.percent)
        assertEquals(ReadinessBand.COMPUTING, s.band)
        assertEquals("…", s.label)
    }
}
