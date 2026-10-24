package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideInsightsTest {

    private fun ride(
        km: Double = 12.0,
        moving: Int = 1800,
        idle: Int = 300,
        max: Int = 84,
        odoStart: Int? = 14_980,
        odoEnd: Int? = 14_992,
        histogram: SpeedHistogram? = null,
    ) = RideInsightInput(
        startTs = 0L,
        endTs = 2_100_000L,
        km = km,
        movingSec = moving,
        idleSec = idle,
        maxSpeedKmh = max,
        avgMovingSpeedKmh = 40.0,
        odoStartKm = odoStart,
        odoEndKm = odoEnd,
        histogram = histogram,
    )

    @Test
    fun `personal best ranks first`() {
        val line = RideInsights.headline(ride(max = 91), allTimeMaxKmh = 91, monthLongestKm = 40.0)!!
        assertEquals(InsightKind.INSIGHT, line.kind)
        assertTrue(line.body.contains("91"))
    }

    @Test
    fun `odometer milestone when a thousand is crossed`() {
        val lines = RideInsights.forRide(ride(km = 20.0, max = 40, odoStart = 14_990, odoEnd = 15_010), allTimeMaxKmh = 80)
        assertTrue(lines.any { it.body.contains("15,000") })
    }

    @Test
    fun `caps at three lines`() {
        val h = SpeedHistogram().apply {
            add(60, 400)
            add(0, 80)
        }
        val lines = RideInsights.forRide(
            ride(km = 48.0, max = 91, idle = 400, histogram = h),
            allTimeMaxKmh = 91,
            monthLongestKm = 48.0,
        )
        assertEquals(3, lines.size)
    }

    @Test
    fun `cruise band uses histogram share`() {
        val h = SpeedHistogram().apply {
            add(60, 720)
            add(40, 80)
            add(0, 100)
        }
        val line = RideInsights.forRide(ride(max = 40, histogram = h), allTimeMaxKmh = 80)
            .first { it.title == "Pace" }
        assertTrue(line.body.contains("50–70"))
        assertTrue(line.body.contains("90%"))
    }
}
