package dev.gdk.bumblebee.video

import dev.gdk.bumblebee.ride.GpsFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class PersonalTest {
    private val t0 = 1_774_060_000_000L

    /** About 30 km north, one fix a second at ~11 m/s. */
    private val track = (0..2700).map { i ->
        val f = i / 2700.0
        GpsFix(t0 + i * 1000L, 10.0 + 0.27 * f, 76.9 + 0.002 * sin(f * 30), speedMps = 11f, accuracyM = 5f)
    }

    @Test fun `photos are placed by location, then time, then by hand`() {
        val mid = track[1350]
        val photos = listOf(
            RidePhoto("gps", takenTs = t0 - 86_400_000L, lat = mid.lat + 0.001, lng = mid.lng), // wrong clock, right place
            RidePhoto("time", takenTs = t0 + 900_000L), // 15 min in, no location
            RidePhoto("late", takenTs = t0 + 5 * 3_600_000L), // hours after
            RidePhoto("far", lat = 12.0, lng = 78.0),
            RidePhoto("nothing"),
            RidePhoto("hand", takenTs = t0 + 5 * 3_600_000L, manualM = 20_000.0),
            RidePhoto("home", takenTs = t0 + 10_000L), // 110 m in: inside the hidden start
        )
        val spots = PhotoPlacer.place(photos, track).associateBy { it.photo.id }
        val gps = spots["gps"] as PhotoSpot.Placed
        assertEquals(PhotoPlacing.LOCATION, gps.how)
        assertTrue(gps.rideM in 14_000.0..16_500.0)
        val time = spots["time"] as PhotoSpot.Placed
        assertEquals(PhotoPlacing.TIME, time.how)
        assertTrue(time.rideM in 9_000.0..11_000.0)
        assertTrue((spots["late"] as PhotoSpot.Unplaced).reason.contains("after the ride"))
        assertTrue(spots["far"] is PhotoSpot.Unplaced)
        assertTrue((spots["nothing"] as PhotoSpot.Unplaced).reason.contains("no time or location"))
        assertEquals(PhotoPlacing.MANUAL, (spots["hand"] as PhotoSpot.Placed).how)
        assertTrue(spots["home"] is PhotoSpot.Hidden)
    }

    @Test fun `photos become postcards with tags and fuel`() {
        val input = RideVideoInput(
            track = track,
            title = "Test",
            startTs = t0,
            movingSec = 2700,
            rideNumber = 42,
            weather = "24°C · Partly cloudy",
            sunTag = "Sunrise ride",
            fuelBars = listOf(t0 to 6, t0 + 1_500_000L to 5),
            photos = listOf(RidePhoto("a", takenTs = t0 + 900_000L), RidePhoto("b", manualM = 22_000.0, caption = "Chai stop")),
        )
        val plan = RideVideoPlan.build(input, VideoConfig(lengthS = 45), { _, _, _ -> null })!!
        val photos = plan.timeline.moments.filter { it.kind == MomentKind.PHOTO }
        assertEquals(setOf("a", "b"), photos.map { it.photoId }.toSet())
        assertEquals("Chai stop", photos.single { it.photoId == "b" }.title)
        assertTrue(plan.photoShown("a"))
        assertEquals("Ride #42  ·  24°C · Partly cloudy  ·  Sunrise ride", plan.tagsLine)
        assertEquals(6, plan.fuelAt(1_000.0))
        assertEquals(5, plan.fuelAt(plan.path.length - 100))
        // Photos lead the cold open.
        assertEquals(MomentKind.PHOTO, plan.timeline.teaser?.kind)
        // Off switches.
        val off = RideVideoPlan.build(input, VideoConfig(lengthS = 45, photos = false, tags = false), { _, _, _ -> null })!!
        assertTrue(off.timeline.moments.none { it.kind == MomentKind.PHOTO })
        assertEquals(null, off.tagsLine)
    }

    @Test fun `personal settings round trip`() {
        val c = VideoConfig(marker = MarkerStyle.DOT, markerSize = 1.7, hudStyle = HudStyle.CLUSTER, tags = false, photos = false)
        assertEquals(c, VideoConfig.decode(c.encode()))
    }
}
