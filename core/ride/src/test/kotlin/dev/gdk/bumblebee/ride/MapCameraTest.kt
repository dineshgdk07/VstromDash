package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapCameraTest {
    private val tile = 256.0
    private val w = 1000.0
    private val h = 600.0

    @Test fun `fit frames all the points`() {
        val pts = listOf(12.90 to 77.55, 13.05 to 77.70)
        val cam = SlippyMap.fit(pts, w, h, tile)!!
        pts.forEach { (lat, lng) ->
            val (x, y) = SlippyMap.toScreen(cam, SlippyMap.worldX(lng), SlippyMap.worldY(lat), w, h, tile)
            assertTrue(x in 0.0..w && y in 0.0..h)
        }
    }

    @Test fun `zooming about a point keeps it under the finger`() {
        val cam = MapCamera(0.7, 0.45, 12.0)
        val before = SlippyMap.toWorld(cam, 300.0, 200.0, w, h, tile)
        val zoomed = SlippyMap.zoomAbout(cam, 2.0, 300.0, 200.0, w, h, tile)
        assertEquals(13.0, zoomed.zoom, 1e-9)
        val after = SlippyMap.toWorld(zoomed, 300.0, 200.0, w, h, tile)
        assertEquals(before.first, after.first, 1e-12)
        assertEquals(before.second, after.second, 1e-12)
    }

    @Test fun `zoom stays within what tiles exist`() {
        val cam = MapCamera(0.5, 0.5, 18.8)
        assertEquals(SlippyMap.MAX_ZOOM, SlippyMap.zoomAbout(cam, 8.0, 0.0, 0.0, w, h, tile).zoom, 1e-9)
    }

    @Test fun `panning moves the map with the finger`() {
        val cam = MapCamera(0.5, 0.5, 10.0)
        val moved = SlippyMap.pan(cam, 100.0, 0.0, tile)
        val (x, _) = SlippyMap.toScreen(moved, cam.x, cam.y, w, h, tile)
        assertEquals(w / 2 + 100.0, x, 1e-6)
    }

    @Test fun `visible tiles cover the view, centre first`() {
        val cam = MapCamera(0.5, 0.5, 10.0)
        val tiles = SlippyMap.visibleTiles(cam, w, h, tile)
        assertEquals(10, tiles.first().z)
        // 1000 x 600 px of 256 px tiles straddling a tile corner: 5 columns, 3 rows at most.
        assertTrue(tiles.size in 12..20)
        val first = tiles.first()
        assertTrue(first.x in 511..512 && first.y in 511..512)
    }

    @Test fun `the parent tile and the part to crop`() {
        val (p, part) = SlippyMap.parent(TileId(10, 513, 342))!!
        assertEquals(TileId(9, 256, 171), p)
        assertEquals(Triple(0.5, 0.0, 0.5), part)
        val (gp, gpart) = SlippyMap.parent(TileId(10, 515, 342), levels = 2)!!
        assertEquals(TileId(8, 128, 85), gp)
        assertEquals(0.75, gpart.first, 1e-9)
        assertEquals(0.5, gpart.second, 1e-9)
    }
}
