package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

class SunTimesTest {
    private val ist = ZoneId.of("Asia/Kolkata")

    @Test fun `bengaluru sunrise and sunset within 3 minutes of published times`() {
        val d = SunTimes.of(LocalDate.of(2026, 10, 24), 12.9716, 77.5946, ist)
        assertNear(LocalTime.of(6, 8), d.sunriseTs!!, ist, 3)   // reference: timeanddate.com, Bengaluru 24 Oct
        assertNear(LocalTime.of(17, 59), d.sunsetTs!!, ist, 3)
    }

    @Test fun `ride overlapping sunrise window is tagged`() {
        val sun = SunDay(sunriseTs = 1_000_000_000L, sunsetTs = null)
        assertEquals("sunrise ride", SunTimes.tag(sun.sunriseTs!! + 25 * 60_000, sun.sunriseTs!! + 90 * 60_000, sun))
        assertNull(SunTimes.tag(sun.sunriseTs!! + 31 * 60_000, sun.sunriseTs!! + 90 * 60_000, sun))
    }

    @Test fun `weather line drops missing parts`() {
        assertEquals("22°C · Clear Sky · sunrise ride", SunTimes.weatherLine(21.6, "Clear Sky", "sunrise ride"))
        assertEquals("Clear Sky", SunTimes.weatherLine(null, "Clear Sky", null))
        assertNull(SunTimes.weatherLine(null, null, null))
    }

    private fun assertNear(expected: LocalTime, ts: Long, zone: ZoneId, minutes: Int) {
        val actual = Instant.ofEpochMilli(ts).atZone(zone).toLocalTime()
        val delta = abs(actual.toSecondOfDay() / 60 - expected.toSecondOfDay() / 60)
        assertTrue("$actual not within $minutes min of $expected", delta <= minutes)
    }
}
