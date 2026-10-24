package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideAnalyticsTest {
    /** Heads north from [startLat] at [kmh], one fix a second. 0.001° of latitude is ~111 m. */
    private fun north(seconds: Int, kmh: Double, startLat: Double = 12.9, startTs: Long = 0L, lng: Double = 77.6): List<GpsFix> {
        val degPerSec = kmh / 3.6 / 111_195.0
        return List(seconds + 1) { i ->
            GpsFix(startTs + i * 1_000L, startLat + i * degPerSec, lng, speedMps = (kmh / 3.6).toFloat())
        }
    }

    @Test fun `splits close every ten km with their own pace`() {
        val track = north(3_600, 36.0) // 36 km in an hour
        val splits = RideAnalytics.splits(track)
        assertEquals(3, splits.size)
        assertEquals(10.0, splits[0].km, 1e-9)
        assertEquals(1_000.0, splits[0].sec.toDouble(), 2.0)
        assertEquals(36.0, splits[0].avgKmh, 0.2)
    }

    @Test fun `time since the last stop`() {
        val first = north(600, 40.0)
        val stop = List(300) { i -> first.last().copy(ts = first.last().ts + (i + 1) * 1_000L, speedMps = 0f) }
        val second = north(1_200, 40.0, startLat = first.last().lat, startTs = stop.last().ts + 1_000L)
        val track = first + stop + second
        assertEquals(1_200, RideAnalytics.sinceLastStopSec(track, track.last().ts))
        assertEquals(600, RideAnalytics.sinceLastStopSec(first, first.last().ts))
        // A gap in the fixes counts as a stop too.
        val gapped = first + north(60, 40.0, startLat = first.last().lat, startTs = first.last().ts + 600_000L)
        assertEquals(60, RideAnalytics.sinceLastStopSec(gapped, gapped.last().ts))
        assertNull(RideAnalytics.sinceLastStopSec(emptyList(), 0L))
    }

    @Test fun `range lasts at this pace`() {
        assertEquals(210, RideAnalytics.minutesOfRange(140.0, 40.0))
        assertNull(RideAnalytics.minutesOfRange(140.0, 5.0))
        assertNull(RideAnalytics.minutesOfRange(null, 40.0))
    }

    @Test fun `ghost compares at the same distance on the same road`() {
        val today = north(600, 40.0)
        val quicker = north(1_000, 48.0, startTs = 1_000_000L)
        val slower = north(1_000, 30.0, startTs = 2_000_000L)
        val elsewhere = north(1_000, 80.0, lng = 78.6, startTs = 3_000_000L)
        val ghost = RideAnalytics.ghost(today, mapOf(1L to quicker, 2L to slower, 3L to elsewhere))!!
        assertEquals(1L, ghost.pastRideId)
        // 6.67 km took 600 s today and 500 s at 48 km/h.
        assertEquals(100.0, ghost.deltaSec.toDouble(), 2.0)
        assertNull(RideAnalytics.ghost(north(30, 40.0), mapOf(1L to quicker)))
    }

    @Test fun `score takes points off for idling, stops and hard swings`() {
        val smooth = RideAnalytics.score(movingSec = 3_600, idleSec = 120, stops = 1, speeds = emptyList())
        assertEquals(98, smooth.score)
        assertEquals(listOf("Smooth, steady riding"), smooth.reasons)
        val jerky = (0 until 40).flatMap { i -> listOf(i * 10_000L to 30, i * 10_000L + 2_000L to 50) }
        val rough = RideAnalytics.score(movingSec = 3_600, idleSec = 1_800, stops = 6, speeds = jerky)
        assertTrue(rough.score < 60)
        // 40 hard changes an hour costs the most (30), then a third of the time standing still (20).
        assertEquals(listOf("40 hard speed changes", "Stood still 33% of the time", "6 stops along the way"), rough.reasons)
    }

    private fun summary(id: Long, km: Double, avg: Double, moving: Int = 3_600, month: Int = 202610) =
        RideSummary(id, id, km, moving, avg, month)

    @Test fun `usual compares pace, length and route time`() {
        val ride = summary(1, 40.0, 50.0, moving = 2_880)
        val others = listOf(
            summary(2, 20.0, 30.0), summary(3, 30.0, 35.0), summary(4, 50.0, 40.0),
            summary(5, 10.0, 55.0), summary(6, 60.0, 45.0, month = 202609),
        )
        // Their median is 3,180 s, five minutes slower than this ride's 2,880 s.
        val mates = listOf(summary(7, 40.0, 40.0, moving = 3_120), summary(8, 40.0, 40.0, moving = 3_240))
        assertEquals(
            listOf(
                "Faster than 80% of your rides",
                "2nd longest this month",
                "5 min quicker than your usual on this route (3 runs)",
            ),
            RideAnalytics.usual(ride, others, mates),
        )
        assertEquals("Your longest ride ever", RideAnalytics.usual(summary(1, 99.0, 20.0), others, emptyList())[1])
    }

    @Test fun `cost prefers litres and pump price`() {
        val cost = RideAnalytics.cost(km = 86.0, litres = 2.9, rupeesPerLitre = 102.0, rupeesPerKm = 3.0)!!
        assertEquals(295.8, cost.rupees!!, 1e-9)
        assertEquals(3.44, cost.rupeesPerKm!!, 0.01)
        assertEquals(258.0, RideAnalytics.cost(86.0, null, null, 3.0)!!.rupees!!, 1e-9)
        assertNull(RideAnalytics.cost(86.0, null, null, null))
    }

    @Test fun `ghost label reads as a clock`() {
        assertEquals("+2:10", RideAnalytics.ghostLabel(130))
        assertEquals("−0:45", RideAnalytics.ghostLabel(-45))
        assertEquals("±0:00", RideAnalytics.ghostLabel(0))
    }
}
