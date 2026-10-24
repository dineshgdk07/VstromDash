package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackPlotTest {

    @Test
    fun `downsample keeps ends and requested count`() {
        val pts = (0..99).map { GpsFix(ts = it.toLong(), lat = it.toDouble(), lng = 0.0) }
        val out = TrackPlot.downsample(pts, 10)
        assertEquals(10, out.size)
        assertEquals(0.0, out.first().lat, 0.0)
        assertEquals(99.0, out.last().lat, 0.0)
    }

    @Test
    fun `normalize puts north at the top`() {
        val pts = listOf(
            GpsFix(0, lat = 12.0, lng = 77.0),
            GpsFix(1, lat = 13.0, lng = 77.0),
        )
        val n = TrackPlot.normalize(pts)
        assertTrue(n.last().y < n.first().y)
    }

    @Test
    fun `speed series starts at left and peak is top`() {
        val s = TrackPlot.speedSeries(listOf(0L to 0, 10L to 80, 20L to 40))
        assertEquals(0f, s.first().x, 0.001f)
        assertEquals(1f, s.last().x, 0.001f)
        assertEquals(0f, s[1].y, 0.001f)
    }
}
