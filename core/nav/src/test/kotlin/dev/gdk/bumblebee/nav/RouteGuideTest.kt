package dev.gdk.bumblebee.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGuideTest {
    @Test fun `google's sample polyline decodes`() {
        val pts = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, pts.size)
        assertEquals(38.5, pts[0].lat, 1e-6)
        assertEquals(-120.2, pts[0].lng, 1e-6)
        assertEquals(43.252, pts[2].lat, 1e-6)
        assertEquals(-126.453, pts[2].lng, 1e-6)
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", Polyline.encode(pts))
    }

    @Test fun `service turn names become cluster arrows`() {
        assertEquals(ArrowKind.SLIGHT_LEFT, Maneuvers.google("TURN_SLIGHT_LEFT", null))
        assertEquals(ArrowKind.EXIT_RIGHT, Maneuvers.google("RAMP_RIGHT", null))
        assertEquals(ArrowKind.LEFT, Maneuvers.google(null, "Turn left onto MG Road"))
        assertEquals(ArrowKind.KEEP_RIGHT, Maneuvers.ors(13, null))
        assertEquals(ArrowKind.ARRIVE, Maneuvers.ors(10, null))
    }

    /** A straight road north, then a right turn east: about 1.1 km then 1.1 km. */
    private fun route(): NavRoute {
        val north = (0..10).map { LatLng(12.90 + it * 0.001, 77.60) }
        val east = (1..10).map { LatLng(12.91, 77.60 + it * 0.001) }
        val pts = north + east
        return NavRoute(
            points = pts,
            steps = listOf(
                RouteStep(ArrowKind.RIGHT, "Turn right onto 1st Main", 1112.0, 10),
                RouteStep(ArrowKind.ARRIVE, "Arrive", 1085.0, pts.lastIndex),
            ),
            distanceM = 2197.0,
            durationS = 300.0,
            destination = "Office",
        )
    }

    @Test fun `progress counts down to the turn and the end`() {
        val guide = RouteGuide(route())
        val p = guide.progress(LatLng(12.905, 77.6001))
        assertEquals(ArrowKind.RIGHT, p.step!!.arrow)
        assertEquals(556.0, p.toTurnM, 15.0)
        assertTrue(p.remainingM in 1600.0..1700.0)
        assertTrue(p.offRouteM < 20)
        val after = guide.progress(LatLng(12.9101, 77.605))
        assertEquals(ArrowKind.ARRIVE, after.step!!.arrow)
        assertFalse(after.arrived)
        assertTrue(guide.progress(LatLng(12.91, 77.6099)).arrived)
    }

    @Test fun `leaving the route asks for a re-route after a while`() {
        val guide = RouteGuide(route())
        val off = guide.progress(LatLng(12.905, 77.603))
        assertTrue(off.offRouteM > 300)
        assertFalse(guide.shouldReroute(off, 0))
        assertFalse(guide.shouldReroute(off, 5_000))
        assertTrue(guide.shouldReroute(off, 9_000))
        // Back on the road resets it.
        assertFalse(guide.shouldReroute(guide.progress(LatLng(12.906, 77.6)), 10_000))
        assertFalse(guide.shouldReroute(off, 11_000))
    }
}
