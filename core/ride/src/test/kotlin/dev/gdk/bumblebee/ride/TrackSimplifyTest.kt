package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSimplifyTest {

    @Test
    fun `straight line collapses to endpoints`() {
        val pts = (0..20).map { i ->
            GpsFix(ts = i * 1000L, lat = 12.0 + i * 0.00001, lng = 77.0)
        }
        val simple = TrackSimplify.douglasPeucker(pts, epsilonM = 5.0)
        assertEquals(2, simple.size)
        assertEquals(pts.first().lat, simple.first().lat, 1e-9)
        assertEquals(pts.last().lat, simple.last().lat, 1e-9)
    }

    @Test
    fun `a corner is kept`() {
        val pts = listOf(
            GpsFix(0, 12.0, 77.0),
            GpsFix(1, 12.01, 77.0),
            GpsFix(2, 12.01, 77.02),
        )
        val simple = TrackSimplify.douglasPeucker(pts, epsilonM = 5.0)
        assertTrue(simple.size >= 3)
    }
}
