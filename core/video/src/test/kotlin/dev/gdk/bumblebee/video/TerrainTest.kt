package dev.gdk.bumblebee.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime
import kotlin.math.abs

class TerrainTest {
    @Test fun `terrarium round trips heights`() {
        for (h in listOf(-430.0, 0.0, 12.5, 2140.25, 8848.0)) {
            assertEquals(h, Terrarium.decode(Terrarium.encode(h)), 0.01)
        }
        // The documented example: RGB (128, 0, 0) is sea level.
        assertEquals(0.0, Terrarium.decode((0xFF shl 24) or (128 shl 16)), 0.0)
    }

    @Test fun `tile math inverts`() {
        val z = 11
        assertEquals(76.5, Terrarium.xToLng(Terrarium.lngToX(76.5, z), z), 1e-9)
        assertEquals(10.2, Terrarium.yToLat(Terrarium.latToY(10.2, z), z), 1e-9)
    }

    @Test fun `zoom keeps tile count bounded`() {
        val small = LatLngBounds(10.0, 76.0, 10.05, 76.05)
        val big = LatLngBounds(8.0, 74.0, 13.0, 78.0)
        assertTrue(Terrarium.zoomFor(small) > Terrarium.zoomFor(big))
        assertTrue(Terrarium.tilesFor(big, Terrarium.zoomFor(big)).size <= 16)
    }

    /** A cone-shaped hill, 1000 m high at the bounds' centre, sea level 250 px away. */
    private fun hillTile(z: Int, x: Int, y: Int, cx: Double, cy: Double): IntArray = IntArray(256 * 256) { i ->
        val px = x * 256 + i % 256 + 0.5
        val py = y * 256 + i / 256 + 0.5
        val d = kotlin.math.hypot(px - cx, py - cy)
        Terrarium.encode((1000.0 - d * 4).coerceAtLeast(0.0))
    }

    @Test fun `grid samples tiles and fills missing ones`() {
        val bounds = LatLngBounds(10.0, 76.0, 10.1, 76.1)
        val z = 12
        val cx = Terrarium.lngToX(bounds.centerLng, z) * 256
        val cy = Terrarium.latToY(bounds.centerLat, z) * 256
        val grid = ElevationGrid.fromTiles(bounds, z, maxCells = 128) { tz, tx, ty -> hillTile(tz, tx, ty, cx, cy) }
        assertTrue(grid.cols in 100..128)
        assertEquals(1000.0, grid.heightAtLatLng(bounds.centerLat, bounds.centerLng), 15.0)
        assertTrue(grid.heightAtLatLng(bounds.south, bounds.west) < 500)
        assertEquals(0, grid.missing)

        val holes = ElevationGrid.fromTiles(bounds, z, maxCells = 64) { _, _, _ -> null }
        assertEquals(holes.cols * holes.rows, holes.missing)
    }

    @Test fun `mesh normals face up on flat ground and tilt on slopes`() {
        val flat = TerrainMesh.build(ElevationGrid.flat(LatLngBounds(10.0, 76.0, 10.01, 76.01), cells = 8), 2.0)
        assertEquals(1f, flat.normals[2], 1e-6f)
        assertEquals(flat.vertexCount, flat.cols * flat.rows)
        assertEquals((flat.cols - 1) * (flat.rows - 1) * 6, flat.indices.size)

        val frame = LocalFrame(10.0, 76.0)
        // Rises to the east: normals lean west (negative x).
        val slope = ElevationGrid(frame, 0.0, 0.0, 10.0, 5, 5, FloatArray(25) { (it % 5) * 10f })
        val mesh = TerrainMesh.build(slope, 1.0)
        val i = (2 * 5 + 2) * 3
        assertTrue(mesh.normals[i] < -0.5f)
        assertEquals(0f, mesh.normals[i + 1], 1e-6f)
    }

    @Test fun `exaggeration grows on flat country`() {
        assertEquals(Exaggeration.MIN, Exaggeration.choose(2_000.0, 20_000.0), 1e-9)
        assertEquals(Exaggeration.MAX, Exaggeration.choose(50.0, 20_000.0), 1e-9)
        val mid = Exaggeration.choose(900.0, 20_000.0)
        assertTrue(mid > Exaggeration.MIN && mid < Exaggeration.MAX)
    }

    @Test fun `route path resamples drapes and finds headings`() {
        val bounds = LatLngBounds(10.0, 76.0, 10.02, 76.02)
        val grid = ElevationGrid.flat(bounds, heightM = 100.0)
        // East 1 km-ish, then north.
        val path = RoutePath.build(listOf(10.005 to 76.005, 10.005 to 76.014, 10.014 to 76.014), grid, exaggeration = 2.0, step = 10.0, gapAfter = setOf(1))
        assertTrue(path.length in 1900.0..2000.0)
        assertEquals(204.0, path.at(500.0).z, 1e-6)
        val east = path.heading(300.0, 50.0, 50.0)
        assertEquals(1.0, east.x, 1e-6)
        val north = path.heading(path.length - 200, 50.0, 50.0)
        assertEquals(1.0, north.y, 1e-6)
        assertTrue(path.twistiness(path.length / 2, 600.0) > 0.1)
        assertTrue(path.twistiness(300.0, 400.0) < 0.01)
        assertTrue(path.gap.last())
        assertTrue(!path.gap[1])
    }

    @Test fun `sun is high at tropical noon and down at midnight`() {
        // Kochi, 21 March, 12:20 local (solar noon is about then).
        val noon = ZonedDateTime.parse("2026-03-21T12:20:00+05:30").toInstant().toEpochMilli()
        val sun = SunPosition.at(noon, 9.97, 76.28)
        assertTrue(sun.elevationDeg > 75)
        val night = ZonedDateTime.parse("2026-03-21T00:20:00+05:30").toInstant().toEpochMilli()
        assertTrue(SunPosition.at(night, 9.97, 76.28).isNight)
        // Morning sun is in the east.
        val morning = ZonedDateTime.parse("2026-03-21T07:30:00+05:30").toInstant().toEpochMilli()
        val m = SunPosition.at(morning, 9.97, 76.28)
        assertTrue(abs(m.azimuthDeg - 90) < 15)
        assertTrue(m.isGolden || m.elevationDeg in 12.0..25.0)
        assertTrue(m.direction.x > 0.8)
    }
}

class LayersTest {
    @Test fun `spikes are clamped to the bulk`() {
        val h = FloatArray(1000) { 1000f + (it % 50) }
        h[10] = -3400f
        h[20] = 9000f
        ElevationGrid.despike(h)
        assertTrue(h[10] > 600f)
        assertTrue(h[20] < 1400f)
        assertEquals(1025f, h[25], 0f)
    }

    @Test fun `route area is square and padded`() {
        val b = LatLngBounds.aroundRoute(listOf(10.0 to 76.0, 10.02 to 76.2), margin = 0.5)!!
        val f = LocalFrame(b.centerLat, b.centerLng)
        val w = f.x(b.east) - f.x(b.west)
        val h = f.y(b.north) - f.y(b.south)
        assertEquals(w, h, w * 0.01)
        assertTrue(w > 21_900 * 2 * 0.99)
    }

    @Test fun `detail wins inside and blends at its edge`() {
        val frame = LocalFrame(10.0, 76.0)
        val base = ElevationGrid(frame, -5000.0, -5000.0, 100.0, 101, 101, FloatArray(101 * 101) { 100f })
        val detail = ElevationGrid(frame, -1000.0, -1000.0, 10.0, 201, 201, FloatArray(201 * 201) { 300f })
        val land = LayeredElevation(base, detail, blend = 0.1)
        assertEquals(300.0, land.heightAt(0.0, 0.0), 1e-9)
        assertEquals(100.0, land.heightAt(3000.0, 0.0), 1e-9)
        // Halfway through the blend band (5% in from the edge, i.e. 100 m).
        assertEquals(200.0, land.heightAt(-900.0, 0.0), 1e-6)
        assertEquals(300.0, land.maxHeight, 1e-9)
    }

    @Test fun `camera lifts over a ridge in the way`() {
        val frame = LocalFrame(10.0, 76.0)
        // A 1000 m wall across x = 0.
        val wall = ElevationGrid(frame, -5000.0, -5000.0, 50.0, 201, 201, FloatArray(201 * 201) { i -> if (kotlin.math.abs(i % 201 - 100) <= 2) 1000f else 0f })
        val path = RoutePath.build(listOf(10.0 to 76.03, 10.01 to 76.03), wall, 1.0)
        val director = CameraDirector(path, wall, 1.0, 1.0)
        val eye = Vec3(-3000.0, 0.0, 200.0)
        val target = Vec3(2000.0, 0.0, 0.0)
        val lifted = director.clearView(eye, target)
        assertTrue(lifted.z > 1000)
        // Nothing blocks a clear line.
        assertEquals(eye, director.clearView(eye, Vec3(-1000.0, 0.0, 100.0)))
    }
}
