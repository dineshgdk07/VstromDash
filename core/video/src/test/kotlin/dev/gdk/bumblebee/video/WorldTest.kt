package dev.gdk.bumblebee.video

import dev.gdk.bumblebee.ride.GpsFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.sin

class WorldTest {
    private val frame = LocalFrame(10.0, 76.0)

    @Test fun `level reservoirs and the sea are water, gentle plains are not`() {
        val cols = 100
        val rows = 100
        // A plain rising 1 m every 3 cells, a perfectly level lake, and sea along the bottom rows.
        val h = FloatArray(cols * rows) { i ->
            val c = i % cols
            val r = i / cols
            when {
                r < 5 -> -2f
                c in 40..70 && r in 40..70 -> 812f
                else -> 800f + (c + r) / 3f
            }
        }
        val grid = ElevationGrid(frame, 0.0, 0.0, 30.0, cols, rows, h)
        val mask = WaterMask.detect(grid)
        assertEquals(1f, mask[55 * cols + 55], 0f)
        assertEquals(1f, mask[2 * cols + 50], 0f)
        assertEquals(0f, mask[20 * cols + 20], 0f)
        assertEquals(0f, mask[90 * cols + 10], 0f)
        // A small level patch is too small to be a lake.
        val small = FloatArray(cols * rows) { i -> if (i % cols in 10..12 && i / cols in 10..12) 900f else 800f + (i % cols + i / cols) / 3f }
        assertTrue(WaterMask.detect(ElevationGrid(frame, 0.0, 0.0, 30.0, cols, rows, small)).none { it > 0f })
    }

    @Test fun `the palette moves smoothly from day to night`() {
        val accent = 0xF7C948
        val looks = (60 downTo -20).map { e -> VideoLook(Sun(250.0, e.toDouble()), accent) }
        assertEquals(0.0, looks.first().nightness, 1e-9)
        assertEquals(1.0, looks.last().nightness, 1e-9)
        for ((a, b) in looks.zipWithNext()) {
            // A degree of sun is about four minutes of riding; no colour jumps within that.
            assertTrue(abs(a.zenith.r - b.zenith.r) < 0.08 && abs(a.zenith.b - b.zenith.b) < 0.08)
            assertTrue((a.lightDir - b.lightDir).length < 0.2)
            assertTrue(b.nightness >= a.nightness)
        }
        assertTrue(looks.last().pathGlow > looks.first().pathGlow)
    }

    @Test fun `light follows the ride's clock`() {
        // An evening ride in Kochi that runs past sunset.
        val start = ZonedDateTime.parse("2026-03-21T17:30:00+05:30").toInstant().toEpochMilli()
        val fixes = (0..4800).map { i ->
            val f = i / 4800.0
            GpsFix(start + i * 1000L, 9.95 + 0.25 * f, 76.25 + 0.2 * f + 0.003 * sin(f * 40), speedMps = 10f, accuracyM = 5f)
        }
        val input = RideVideoInput(fixes, title = "Evening", startTs = start, movingSec = 4800)
        val flat = { _: Int, _: Int, _: Int -> null as IntArray? }
        val plan = RideVideoPlan.build(input, VideoConfig(lengthS = 40), flat)!!
        val early = plan.sunAt(plan.timeline.rideStartS + 0.1)
        val late = plan.sunAt(plan.totalS - 0.5)
        assertTrue("sun ${early.elevationDeg} -> ${late.elevationDeg}", late.elevationDeg < early.elevationDeg - 10)
        assertTrue(plan.lookAt(plan.totalS - 0.5, 0xFFFFFF).nightness > plan.lookAt(plan.timeline.rideStartS + 0.1, 0xFFFFFF).nightness)
        // Fixed light when time of day is off.
        val fixed = RideVideoPlan.build(input, VideoConfig(lengthS = 40, timeOfDay = false), flat)!!
        assertEquals(fixed.sunAt(1.0), fixed.sunAt(fixed.totalS - 0.5))
    }

    @Test fun `towns are pinned where the route passes them`() {
        val bounds = LatLngBounds(10.0, 76.0, 10.3, 76.3)
        val land = ElevationGrid.flat(bounds, heightM = 100.0)
        val path = RoutePath.build((0..300).map { i -> (10.02 + 0.26 * i / 300) to 76.15 }, land, 1.0)
        val places = listOf(
            PlaceMark(10.10, 76.152, "Alpha"),
            PlaceMark(10.101, 76.151, "Alpha"),
            PlaceMark(10.102, 76.150, "Close by"), // too near Alpha along the route
            PlaceMark(10.20, 76.15, "Beta"),
            PlaceMark(10.15, 76.29, "Far away"), // 15 km off the route
        )
        val labels = RideVideoPlan.labels(places, path, land)
        assertEquals(listOf("Alpha", "Beta"), labels.map { it.name })
        assertTrue(labels.zipWithNext().all { (a, b) -> b.distanceM > a.distanceM })
    }

    @Test fun `config keeps world settings`() {
        val c = VideoConfig(timeOfDay = false, clouds = 0.8, water = false, placeLabels = false)
        assertEquals(c, VideoConfig.decode(c.encode()))
        assertEquals(1.0, VideoConfig.decode("clouds=7").clouds, 0.0)
    }
}

class OsmWaterTest {
    @Test fun `map blue becomes water in the grid`() {
        val water = (0xFF shl 24) or (0xAA shl 16) or (0xD3 shl 8) or 0xDF
        val land = (0xFF shl 24) or (0xF2 shl 16) or (0xEF shl 8) or 0xE9
        assertTrue(OsmWater.isWater(water))
        assertTrue(OsmWater.isWater((0xFF shl 24) or (0xAD shl 16) or (0xD0 shl 8) or 0xE2))
        assertTrue(!OsmWater.isWater(land))
        val bounds = LatLngBounds(10.0, 76.0, 10.05, 76.05)
        val grid = ElevationGrid.flat(bounds, cells = 64, heightM = 500.0)
        // The western half of every tile is water.
        val mask = OsmWater.mask(grid) { _, _, _ -> IntArray(256 * 256) { i -> if (i % 256 < 128) water else land } }!!
        assertTrue(mask.any { it > 0f } && mask.any { it == 0f })
        assertEquals(null, OsmWater.mask(grid) { _, _, _ -> null })
        // The layer takes map water even where the ground isn't level.
        val layer = WaterLayer(LayeredElevation(grid, null)) { _, _, _ -> IntArray(256 * 256) { water } }
        assertTrue(layer.any)
        assertEquals(1.0, layer.at(grid.x0 + grid.width / 2, grid.y0 + grid.height / 2), 1e-9)
    }
}
