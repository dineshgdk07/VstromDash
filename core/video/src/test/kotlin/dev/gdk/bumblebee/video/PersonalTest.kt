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

class BikeModelTest {
    @Test fun `the bike is bike-sized, sits on the ground and faces outward`() {
        for (rider in listOf(false, true)) {
            val m = BikeModel.build(rider)
            assertTrue(m.vertexCount > 1000)
            assertEquals(0, m.vertexCount % 3)
            val xs = (0 until m.vertexCount).map { m.positions[it * 3] }
            val zs = (0 until m.vertexCount).map { m.positions[it * 3 + 2] }
            assertTrue("length ${xs.max() - xs.min()}", (xs.max() - xs.min()) in 1.9f..2.3f)
            assertEquals(0f, zs.min(), 0.01f)
            assertTrue(zs.max() < if (rider) 1.95f else 1.5f)
            // Every normal is a unit vector.
            for (i in 0 until m.vertexCount) {
                val l = kotlin.math.sqrt(m.normals[i * 3] * m.normals[i * 3] + m.normals[i * 3 + 1] * m.normals[i * 3 + 1] + m.normals[i * 3 + 2] * m.normals[i * 3 + 2])
                assertEquals(1f, l, 1e-3f)
            }
            // Painted parts exist; the rider adds a helmet.
            assertTrue(m.materials.any { it.toInt() == BikeMaterial.PAINT.ordinal })
            assertEquals(rider, m.materials.any { it.toInt() == BikeMaterial.HELMET.ordinal })
        }
        // The tank's top faces up and its left side faces left.
        val m = BikeModel.build(false)
        var up = 0
        var left = 0
        for (i in 0 until m.vertexCount) {
            if (m.materials[i].toInt() != BikeMaterial.PAINT.ordinal) continue
            if (m.normals[i * 3 + 2] > 0.7f && m.positions[i * 3 + 2] > 1.0f) up++
            if (m.normals[i * 3 + 1] > 0.7f && m.positions[i * 3 + 1] > 0.15f) left++
        }
        assertTrue(up > 0 && left > 0)
    }
}

class BikeFrameTest {
    private val t0 = 1_774_060_000_000L

    @Test fun `the bike leans into bends and sits level on straights`() {
        // North for 8 km, a long left curve, then west.
        val pts = ArrayList<Pair<Double, Double>>()
        for (i in 0..800) pts += (10.0 + i * 0.0001) to 76.5
        for (i in 1..90) {
            val a = Math.toRadians(i.toDouble())
            pts += (10.08 + 0.0005 * kotlin.math.sin(a)) to (76.5 - 0.0005 * (1 - kotlin.math.cos(a)))
        }
        for (i in 1..800) pts += 10.0805 to (76.4995 - i * 0.0001)
        val track = pts.mapIndexed { i, (lat, lng) -> GpsFix(t0 + i * 1000L, lat, lng, speedMps = 14f, accuracyM = 5f) }
        val plan = RideVideoPlan.build(RideVideoInput(track, title = "t", startTs = t0, movingSec = 1600), VideoConfig(lengthS = 30), { _, _, _ -> null })!!
        val straight = plan.bikeFrame(3_000.0)
        assertEquals(0.0, straight.leanDeg, 1.0)
        assertTrue(straight.forward.y > 0.99)
        // In the tight (about 55 m) bend at 50 km/h it leans left by roughly 20 degrees.
        val curveLean = (0 until 200).map { plan.bikeFrame(plan.path.length * 0.4 + it * 20.0).leanDeg }.max()
        assertTrue("lean $curveLean", curveLean in 12.0..32.0)
        // Leaning left tips the bike's up toward its left.
        val (f, l, u) = BikeFrame(Vec3.ZERO, Vec3(0.0, 1.0, 0.0), 30.0).axes()
        assertTrue(u.dot(Vec3(-1.0, 0.0, 0.0)) > 0.4)
        assertEquals(0.0, l.dot(f), 1e-9)
        assertEquals(1.0, u.length, 1e-9)
    }
}
