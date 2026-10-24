package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class JourneyDetectorTest {

    private val zone = ZoneOffset.UTC

    private fun ride(id: Long, start: Long, end: Long, odoStart: Int, odoEnd: Int) = JourneyRide(
        id = id,
        startTs = start,
        endTs = end,
        odoStartKm = odoStart,
        odoEndKm = odoEnd,
    )

    @Test
    fun `a tea stop on the same outing is one suggestion`() {
        val t0 = 1_704_067_200_000L
        val sug = JourneyDetector.detect(
            listOf(
                ride(1, t0, t0 + 60 * 60_000L, 1000, 1080),
                ride(2, t0 + 80 * 60_000L, t0 + 3 * 60 * 60_000L, 1080, 1180),
            ),
            zone = zone,
        )
        assertEquals(1, sug.size)
        assertEquals(listOf(1L, 2L), sug[0].rideIds)
        assertEquals(180, sug[0].totalKm)
    }

    @Test
    fun `trips that cross midnight still suggest a merge`() {
        val evening = 1_704_067_200_000L + 23 * 60 * 60_000L
        val afterMidnight = 1_704_067_200_000L + 24 * 60 * 60_000L + 10 * 60_000L
        val sug = JourneyDetector.detect(
            listOf(
                ride(1, evening, evening + 40 * 60_000L, 2000, 2080),
                ride(2, afterMidnight, afterMidnight + 50 * 60_000L, 2080, 2140),
            ),
            zone = zone,
        )
        assertEquals(1, sug.size)
        assertTrue(JourneyDetector.caption(sug[0], zone).contains("continued overnight"))
    }

    @Test
    fun `same calendar day flag still splits at midnight when asked`() {
        val evening = 1_704_067_200_000L + 23 * 60 * 60_000L
        val afterMidnight = 1_704_067_200_000L + 24 * 60 * 60_000L + 10 * 60_000L
        val sug = JourneyDetector.detect(
            listOf(
                ride(1, evening, evening + 40 * 60_000L, 2000, 2080),
                ride(2, afterMidnight, afterMidnight + 50 * 60_000L, 2080, 2140),
            ),
            cfg = JourneyConfig(sameCalendarDay = true),
            zone = zone,
        )
        assertTrue(sug.isEmpty())
    }
}
