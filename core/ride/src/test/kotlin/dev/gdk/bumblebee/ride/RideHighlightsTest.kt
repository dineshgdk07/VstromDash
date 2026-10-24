package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RideHighlightsTest {
    private fun s(km: Double = 10.0, max: Int = 60, climb: Double? = null, moving: Int = 1200) =
        HighlightStats(km, max, climb, moving)

    @Test fun `first ever ride gets nothing`() = assertNull(RideHighlights.of(s(km = 150.0), emptyList()))

    @Test fun `first time over 100 km is a club badge, not longest`() =
        assertEquals(HighlightKind.CLUB_100, RideHighlights.of(s(km = 104.2), listOf(s(km = 80.0)))?.kind)

    @Test fun `club badge only the first time`() =
        assertEquals(HighlightKind.LONGEST, RideHighlights.of(s(km = 140.0), listOf(s(km = 120.0)))?.kind)

    @Test fun `crossing two thresholds at once shows the higher`() =
        assertEquals(HighlightKind.CLUB_200, RideHighlights.of(s(km = 210.0), listOf(s(km = 90.0)))?.kind)

    @Test fun `top speed when not longest`() {
        val h = RideHighlights.of(s(km = 10.0, max = 112), listOf(s(km = 50.0, max = 104)))!!
        assertEquals(HighlightKind.TOP_SPEED, h.kind)
        assertEquals("New top speed: 112 km/h", h.detail)
    }

    @Test fun `climb under 40 m never counts`() =
        assertNull(RideHighlights.of(s(km = 5.0, max = 40, climb = 39.0, moving = 100), listOf(s(climb = 10.0))))

    @Test fun `ties are not records`() = assertNull(RideHighlights.of(s(), listOf(s())))
}
