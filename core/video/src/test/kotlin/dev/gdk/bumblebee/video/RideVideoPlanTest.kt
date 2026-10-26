package dev.gdk.bumblebee.video

import dev.gdk.bumblebee.ride.GpsFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin

class RideVideoPlanTest {
    private val t0 = 1_774_060_000_000L // a March 2026 morning

    /** About 30 km heading north-east at ~40 km/h, one fix a second, with a 20 min stop half way. */
    private fun ride(): RideVideoInput {
        val fixes = ArrayList<GpsFix>()
        var ts = t0
        for (i in 0..2700) {
            val f = i / 2700.0
            val lat = 10.0 + 0.19 * f + 0.004 * sin(f * 30)
            val lng = 76.9 + 0.19 * f + 0.004 * cos(f * 22)
            if (i == 1350) ts += 20 * 60_000
            fixes += GpsFix(ts, lat, lng, speedMps = 11f, accuracyM = 5f)
            ts += 1000
        }
        return RideVideoInput(
            track = fixes,
            stops = listOf(t0 + 1350_000L to 1200),
            refuelTs = listOf(t0 + 2200_000L + 20 * 60_000),
            title = "Test ride",
            fromPlace = "Alpha",
            toPlace = "Beta",
            startTs = t0,
            movingSec = 2700,
            zone = ZoneId.of("Asia/Kolkata"),
        )
    }

    /** A hill in the middle of everything, 500 m to 1500 m. */
    private fun hills(z: Int, x: Int, y: Int): IntArray = IntArray(256 * 256) { i ->
        val px = (x * 256 + i % 256).toDouble() / (256 shl z)
        val py = (y * 256 + i / 256).toDouble() / (256 shl z)
        Terrarium.encode(1000 + 500 * sin(px * 4000) * cos(py * 3000))
    }

    @Test fun `plan trims, places moments and keeps to the length rules`() {
        val plan = RideVideoPlan.build(ride(), VideoConfig(), ::hills)!!
        assertTrue(!plan.flat)
        // About 30 km, less 500 m each end.
        assertTrue(plan.routeKm in 27.0..32.0)
        assertEquals(500.0, plan.trimStartM, 30.0)
        assertTrue(plan.totalS >= plan.length.min && plan.totalS <= plan.length.max)
        assertEquals(plan.length.recommended.toDouble(), plan.totalS, 0.0)
        assertEquals((plan.totalS * 30).toInt(), plan.frameCount)
        val kinds = plan.timeline.moments.map { it.kind }
        assertTrue("moments $kinds", MomentKind.STOP in kinds || MomentKind.REFUEL in kinds)
        assertTrue(plan.speedAt(plan.path.length / 3) in 35..45)
        assertTrue(plan.clockAt(0.0).endsWith("AM"))
        assertEquals("Alpha  —  Beta", plan.placesLine)
        assertTrue(plan.dateLine.endsWith("morning"))
        assertEquals(4, plan.stats().size)
        // Camera works at every frame boundary.
        for (f in listOf(0, plan.frameCount / 2, plan.frameCount - 1)) plan.camera(plan.timeOf(f))
    }

    @Test fun `chosen length is clamped to the band`() {
        val plan = RideVideoPlan.build(ride(), VideoConfig(lengthS = 500), ::hills)!!
        assertEquals(plan.length.max.toDouble(), plan.totalS, 0.0)
    }

    @Test fun `no terrain falls back to flat`() {
        val plan = RideVideoPlan.build(ride(), VideoConfig(), { _, _, _ -> null })!!
        assertTrue(plan.flat)
        assertEquals(1.0, plan.exaggeration, 0.0)
    }

    @Test fun `too short a ride gives no plan`() {
        val short = ride().let { r -> r.copy(track = r.track.take(100)) } // about 1.1 km
        assertNull(RideVideoPlan.build(short, VideoConfig(), ::hills))
    }
}
