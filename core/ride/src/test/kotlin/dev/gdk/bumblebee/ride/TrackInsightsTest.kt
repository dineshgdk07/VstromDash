package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackInsightsTest {

    @Test
    fun `ignores the start pause and counts a mid-ride stop`() {
        val track = buildList {
            repeat(30) { add(fix(it * 1000L, 12.9, 77.6, speed = 0f)) }
            repeat(40) { add(fix(30_000L + it * 1000L, 12.9 + it * 0.0002, 77.6, speed = 12f)) }
            repeat(25) { add(fix(70_000L + it * 1000L, 12.908, 77.6, speed = 0f)) }
            repeat(20) { add(fix(95_000L + it * 1000L, 12.908 + it * 0.0002, 77.6, speed = 11f)) }
        }
        assertEquals(1, TrackInsights.stopCount(track))
    }

    @Test
    fun `elevation gain ignores small GPS noise`() {
        val track = listOf(
            fix(0, 12.9, 77.6, alt = 100.0),
            fix(1_000, 12.91, 77.6, alt = 101.5),
            fix(2_000, 12.92, 77.6, alt = 120.0),
            fix(3_000, 12.93, 77.6, alt = 118.0),
        )
        assertEquals(20.0, TrackInsights.elevationGainM(track)!!, 0.2)
    }

    @Test
    fun `debrief is not capped at three lines`() {
        val h = SpeedHistogram().apply {
            add(60, 400)
            add(0, 80)
        }
        val ride = RideInsightInput(
            startTs = 0L,
            endTs = 120_000L,
            km = 48.0,
            movingSec = 1800,
            idleSec = 400,
            maxSpeedKmh = 91,
            avgMovingSpeedKmh = 40.0,
            odoStartKm = 14_990,
            odoEndKm = 15_040,
            histogram = h,
        )
        val track = List(80) { i ->
            fix(i * 1000L, 12.90 + i * 0.0004, 77.60 + i * 0.0001, speed = if (i == 40) 25f else 12f, acc = 6f)
        }
        val lines = RideInsights.debrief(ride, allTimeMaxKmh = 91, monthLongestKm = 48.0, track = track)
        assertTrue(lines.size > 3)
        assertTrue(lines.any { it.title == "Personal best" })
        assertTrue(lines.any { it.title == "Top speed" || it.title.contains("speed") })
    }

    @Test
    fun `nearest index picks the closer timestamp`() {
        val track = listOf(fix(0), fix(1000), fix(2000))
        assertEquals(1, TrackPlot.nearestIndex(track, 1200L))
    }

    private fun fix(
        ts: Long,
        lat: Double = 12.9,
        lng: Double = 77.6,
        speed: Float? = 10f,
        alt: Double? = null,
        acc: Float? = 8f,
    ) = GpsFix(ts, lat, lng, altitudeM = alt, speedMps = speed, accuracyM = acc)
}
