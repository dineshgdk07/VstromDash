package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebMercatorTest {

    @Test
    fun `lng 0 is the middle of the world at zoom 1`() {
        assertEquals(1.0, WebMercator.lngToX(0.0, 1), 0.001)
    }

    @Test
    fun `cover stays within tile budget`() {
        val pts = listOf(
            GpsFix(0, 12.90, 77.55),
            GpsFix(1, 13.05, 77.70),
        )
        val cover = WebMercator.cover(WebMercator.bounds(pts)!!, maxTiles = 12)
        assertTrue(cover.count in 1..12)
        assertTrue(cover.zoom in 11..16)
    }
}
