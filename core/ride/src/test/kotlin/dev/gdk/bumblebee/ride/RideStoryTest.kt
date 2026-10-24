package dev.gdk.bumblebee.ride

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class RideStoryTest {

    @Test
    fun `morning ride mentions the time of day and distance`() {
        val ride = RideInsightInput(
            startTs = 8L * 3600_000,
            endTs = 9L * 3600_000,
            km = 18.4,
            movingSec = 42 * 60,
            idleSec = 8 * 60,
            maxSpeedKmh = 72,
            avgMovingSpeedKmh = 38.0,
            histogram = SpeedHistogram().apply { add(55, 2000); add(0, 200) },
        )
        val text = RideStory.of(ride, zone = ZoneOffset.UTC)
        assertTrue(text.contains("morning"))
        assertTrue(text.contains("18.4"))
        assertTrue(text.contains("42"))
    }

    @Test
    fun `day story names how many rides and total km`() {
        val a = RideInsightInput(1, 2, km = 12.0, movingSec = 1200, idleSec = 120, maxSpeedKmh = 60, avgMovingSpeedKmh = 35.0)
        val b = RideInsightInput(3, 4, km = 8.0, movingSec = 800, idleSec = 60, maxSpeedKmh = 55, avgMovingSpeedKmh = 32.0)
        val text = RideStory.ofDay("Saturday 21 Sep", listOf(a, b))
        assertTrue(text.contains("two rides") || text.contains("2 rides"))
        assertTrue(text.contains("20.0") || text.contains("20 km"))
    }

    @Test
    fun `month story names ride count and km`() {
        val a = RideInsightInput(1, 2, km = 12.0, movingSec = 1200, idleSec = 120, maxSpeedKmh = 60, avgMovingSpeedKmh = 35.0)
        val b = RideInsightInput(3, 4, km = 8.0, movingSec = 800, idleSec = 60, maxSpeedKmh = 55, avgMovingSpeedKmh = 32.0)
        val text = RideStory.ofMonth("September 2026", listOf(a, b))
        assertTrue(text.contains("2 rides") || text.contains("two rides"))
        assertTrue(text.contains("20.0") || text.contains("20 km"))
        assertTrue(text.contains("September"))
    }
}
