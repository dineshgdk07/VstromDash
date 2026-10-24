package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class CockpitInsightsTest {
    private val zone = ZoneOffset.UTC

    @Test fun `milestone counts to the next thousand`() {
        val m = CockpitInsights.milestone(24_816)
        assertEquals(25_000, m.target)
        assertEquals(184, m.kmToGo)
        assertEquals(0.816f, m.fraction, 1e-4f)
        assertNull(m.justCrossed)
    }

    @Test fun `a fresh round number is celebrated for a few km`() {
        assertEquals(25_000, CockpitInsights.milestone(25_012).justCrossed)
        assertNull(CockpitInsights.milestone(25_060).justCrossed)
        assertNull(CockpitInsights.milestone(30).justCrossed)
    }

    private fun at(h: Int, m: Int = 0, day: Int = 21) = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, zone)

    private val sun = SunDay(at(6, 5).toInstant().toEpochMilli(), at(18, 12).toInstant().toEpochMilli())

    @Test fun `golden hour before sunset`() =
        assertEquals("Sunset at 18:12, 24°C and clear. Golden-hour loop?", CockpitInsights.rideSuggestion(0, 24.4, at(17, 0), sun))

    @Test fun `early morning after sunrise`() =
        assertEquals("19°C and dry at sunrise. Early roads are empty.", CockpitInsights.rideSuggestion(2, 19.0, at(6, 40), sun))

    @Test fun `a hot afternoon waits for sunset`() =
        assertEquals("Hot at 36°C. Cooler after sunset at 18:12.", CockpitInsights.rideSuggestion(1, 36.0, at(13, 0), sun))

    @Test fun `weekend mornings suggest a long ride`() =
        // 24 Oct 2026 is a Saturday.
        assertEquals("27°C and clear. Good day for a long one.", CockpitInsights.rideSuggestion(0, 27.0, at(10, 0, day = 24), sunOn(24)))

    @Test fun `rain or a plain weekday says nothing`() {
        assertNull(CockpitInsights.rideSuggestion(61, 24.0, at(17, 0), sun))
        assertNull(CockpitInsights.rideSuggestion(0, 27.0, at(11, 0), sun))
        assertNull(CockpitInsights.rideSuggestion(null, 27.0, at(17, 0), sun))
        assertNull(CockpitInsights.rideSuggestion(0, 27.0, at(22, 0), sun))
    }

    private fun sunOn(day: Int) = SunDay(at(6, 5, day).toInstant().toEpochMilli(), at(18, 12, day).toInstant().toEpochMilli())

    private fun ride(id: Long, date: LocalDate, km: Double, label: String = "Nandi Hills") =
        PastRide(id, date.atTime(7, 0).toInstant(zone).toEpochMilli(), km, label)

    @Test fun `on this day finds the most recent year`() {
        val today = LocalDate.of(2026, 10, 25)
        val rides = listOf(
            ride(1, LocalDate.of(2024, 10, 25), 40.0, "Hosur"),
            ride(2, LocalDate.of(2025, 10, 25), 86.4),
            ride(3, LocalDate.of(2025, 10, 25), 12.0, "Errand"),
        )
        assertEquals(OnThisDay(2, "A year ago today", "Nandi Hills · 86 km"), CockpitInsights.onThisDay(rides, today, zone))
        assertEquals(
            OnThisDay(1, "2 years ago today", "Hosur · 40 km"),
            CockpitInsights.onThisDay(rides.take(1), today, zone),
        )
    }

    @Test fun `on this day falls back to last month`() {
        val today = LocalDate.of(2026, 10, 25)
        assertEquals(
            OnThisDay(4, "A month ago today", "Nandi Hills · 30 km"),
            CockpitInsights.onThisDay(listOf(ride(4, LocalDate.of(2026, 9, 25), 30.0)), today, zone),
        )
        // 31 March has no 31 February to look back to.
        assertNull(CockpitInsights.onThisDay(listOf(ride(5, LocalDate.of(2026, 2, 28), 30.0)), LocalDate.of(2026, 3, 31), zone))
    }

    @Test fun `distance reads naturally`() {
        assertEquals("Right here", CockpitInsights.distanceLabel(20.0))
        assertEquals("350 m away", CockpitInsights.distanceLabel(347.0))
        assertEquals("1.2 km away", CockpitInsights.distanceLabel(1_234.0))
        assertEquals("1,234 km away", CockpitInsights.distanceLabel(1_234_000.0))
    }
}
