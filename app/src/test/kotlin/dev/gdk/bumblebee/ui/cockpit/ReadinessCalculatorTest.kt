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
    fun `waiting for a paired bike does not tank the score`() {
        val s = ReadinessCalculator.score(fuelBars = 6, bleReady = false, bikePaired = true)
        assertEquals(ReadinessBand.READY, s.band)
        assertEquals(100, s.percent)
        assertEquals(PillarStatus.UNKNOWN, s.pillars.first { it.name == "Link" }.status)
    }

    @Test
    fun `unpaired bike is onboarding not a failure`() {
        val s = ReadinessCalculator.score(fuelBars = null, bleReady = false, bikePaired = false)
        assertEquals(PillarStatus.UNKNOWN, s.pillars.first { it.name == "Link" }.status)
        assertEquals("pair your bike", s.pillars.first { it.name == "Link" }.detail)
    }

    @Test
    fun `failed handshake is not ready`() {
        val s = ReadinessCalculator.score(fuelBars = 6, bleReady = false, bikePaired = true, linkFailed = true)
        assertEquals(PillarStatus.NOT_READY, s.pillars.first { it.name == "Link" }.status)
        assertTrue(s.band != ReadinessBand.READY)
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

    @Test
    fun `service overdue is a not-ready pillar`() {
        val s = ReadinessCalculator.score(fuelBars = 6, bleReady = true, serviceDueKm = -10)
        assertEquals(ReadinessBand.CAUTION, s.band)
        assertEquals(PillarStatus.NOT_READY, s.pillars.first { it.name == "Service" }.status)
    }
}
