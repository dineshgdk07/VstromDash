package dev.gdk.bumblebee.video

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class CameraDirectorTest {
    private val bounds = LatLngBounds(10.0, 76.0, 10.2, 76.2)
    private val frame = LocalFrame(bounds.centerLat, bounds.centerLng)

    /** A ridge rising to 1500 m in the middle of the area. */
    private val grid = run {
        val x0 = frame.x(bounds.west)
        val y0 = frame.y(bounds.south)
        val cols = 101
        val cell = (frame.x(bounds.east) - x0) / (cols - 1)
        val rows = ((frame.y(bounds.north) - y0) / cell).toInt() + 1
        ElevationGrid(frame, x0, y0, cell, cols, rows, FloatArray(cols * rows) { i ->
            val c = i % cols
            val r = i / cols
            val dx = (c - cols / 2.0) / cols
            val dy = (r - rows / 2.0) / rows
            (1500 * kotlin.math.exp(-(dx * dx + dy * dy) * 12)).toFloat()
        })
    }

    /** A winding road over the ridge. */
    private val path = RoutePath.build(
        (0..200).map { i ->
            val t = i / 200.0
            (10.02 + 0.16 * t + 0.01 * sin(t * 40)) to (76.02 + 0.16 * t + 0.01 * cos(t * 25))
        },
        grid,
        exaggeration = 1.5,
    )

    private val director = CameraDirector(path, grid, 1.5, VideoShape.VERTICAL.aspect)
    private val timeline = VideoTimeline(30.0, path.length, listOf(VideoMoment(MomentKind.HIGHEST, path.length / 2, "Highest point", "1,500 m")))

    @Test fun `camera never goes underground and moves smoothly`() {
        var prev: CameraPose? = null
        var t = 0.0
        var maxTurn = 0.0
        var maxStep = 0.0
        while (t <= 30.0) {
            val pose = director.shot(timeline, t)
            val ground = grid.heightAt(pose.eye.x, pose.eye.y) * 1.5
            assertTrue("underground at t=$t", pose.eye.z > ground + 20)
            // The cut out of the cold open is meant to be a jump.
            if (timeline.cue(t).shot != timeline.cue(t - 1.0 / 30).shot && timeline.cue(t - 1.0 / 30).shot == Shot.COLD) prev = null
            prev?.let {
                val a = (it.target - it.eye).normalized()
                val b = (pose.target - pose.eye).normalized()
                maxTurn = maxOf(maxTurn, Math.toDegrees(kotlin.math.acos(a.dot(b).coerceIn(-1.0, 1.0))))
                // Movement as a share of how far away the camera is looking: what the eye sees as a jump.
                maxStep = maxOf(maxStep, (pose.eye - it.eye).length / (it.target - it.eye).length)
            }
            prev = pose
            t += 1.0 / 30
        }
        // At 30 fps: the view turns under 2 degrees a frame (60 a second) and never lurches.
        assertTrue("max turn $maxTurn", maxTurn < 2.0)
        assertTrue("max step $maxStep", maxStep < 0.15)
    }

    @Test fun `chase looks at the road ahead from behind`() {
        val d = path.length * 0.3
        val pose = director.chase(d)
        val marker = path.at(d)
        val heading = path.heading(d, 200.0, 400.0)
        assertTrue((pose.eye - marker).flat().dot(heading) < 0)
        assertTrue((pose.target - marker).flat().dot(heading) >= 0)
        assertTrue(pose.eye.z > marker.z)
    }

    @Test fun `hold returns to the chase pose`() {
        val start = timeline.cue(timeline.introS + timeline.holdStarts[0] + 1e-6)
        val end = timeline.cue(timeline.introS + timeline.holdStarts[0] + VideoTimeline.HOLD_S - 1e-6)
        val chase = director.chase(path.length / 2)
        assertTrue((director.pose(start).eye - chase.eye).length < 5)
        assertTrue((director.pose(end).eye - chase.eye).length < 5)
    }

    @Test fun `overview sees the whole route`() {
        val o = director.overview()
        val forward = (o.target - o.eye).normalized()
        val halfFov = Math.toRadians(o.fovYDeg / 2) * VideoShape.VERTICAL.aspect
        for (p in path.points) {
            val to = (p - o.eye).normalized()
            val angle = kotlin.math.acos(to.dot(forward).coerceIn(-1.0, 1.0))
            assertTrue(angle < Math.toRadians(o.fovYDeg))
        }
        assertTrue(halfFov > 0)
    }
}

class ViewMathTest {
    @Test fun `matrix and projection agree`() {
        val pose = CameraPose(Vec3(100.0, -2000.0, 900.0), Vec3(300.0, 500.0, 200.0), 50.0)
        val vm = ViewMath(pose, 9.0 / 16, 5.0, 100_000.0)
        val p = Vec3(400.0, 300.0, 250.0)
        val c = vm.clip(p - vm.eye)
        val ndcX = c[0] / c[3]
        val ndcY = c[1] / c[3]
        val ndcZ = c[2] / c[3]
        val s = vm.project(p, 1080, 1920)!!
        org.junit.Assert.assertEquals((ndcX + 1) / 2 * 1080, s[0], 1e-3)
        org.junit.Assert.assertEquals((1 - ndcY) / 2 * 1920, s[1], 1e-3)
        assertTrue(ndcZ > -1 && ndcZ < 1)
        // The target is dead centre.
        val t = vm.project(pose.target, 1080, 1920)!!
        org.junit.Assert.assertEquals(540.0, t[0], 1e-6)
        org.junit.Assert.assertEquals(960.0, t[1], 1e-6)
        assertTrue(vm.project(pose.eye - (pose.target - pose.eye), 1080, 1920) == null)
    }
}

class StoryTest {
    private val bounds = LatLngBounds(10.0, 76.0, 10.2, 76.2)
    private val frame = LocalFrame(bounds.centerLat, bounds.centerLng)
    private val flat = ElevationGrid.flat(bounds, heightM = 200.0)

    /** A straight run, then tight zig-zags, then straight again. */
    private val path = RoutePath.build(
        (0..300).map { i ->
            val t = i / 300.0
            val wiggle = if (t in 0.4..0.6) 0.004 * kotlin.math.sin(t * 600) else 0.0
            (10.02 + 0.16 * t) to (76.1 + wiggle)
        },
        flat,
        1.0,
    )
    private val top = VideoMoment(MomentKind.HIGHEST, path.length * 0.7, "Highest point", "1,500 m")
    private val speed = VideoMoment(MomentKind.TOP_SPEED, path.length * 0.3, "Top speed", "92 km/h")

    @Test fun `cold open comes first and cuts cleanly`() {
        val tl = VideoTimeline(30.0, path.length, listOf(speed, top), teaser = top)
        val first = tl.cue(0.0)
        org.junit.Assert.assertEquals(Shot.COLD, first.shot)
        org.junit.Assert.assertEquals(top.distanceM, first.distanceM, 1e-9)
        org.junit.Assert.assertEquals(Shot.INTRO, tl.cue(tl.coldS + 0.01).shot)
        org.junit.Assert.assertEquals(30.0, tl.coldS + tl.introS + tl.rideS + tl.finishS, 1e-9)
        // Smoothing windows stop at the cut.
        assertTrue(tl.segment(tl.coldS - 0.1).endInclusive < tl.coldS)
        assertTrue(tl.segment(tl.coldS + 0.1).start >= tl.coldS)
        // timeAt agrees with cue.
        val at = tl.timeAt(path.length * 0.5)
        org.junit.Assert.assertEquals(path.length * 0.5, tl.cue(at).distanceM, path.length * 0.01)
    }

    @Test fun `camera banks into turns and rises over hairpins`() {
        val level = CameraDirector(path, flat, 1.0, 0.5625, CameraStyle(bank = 0.0, hairpinView = false))
        val banked = CameraDirector(path, flat, 1.0, 0.5625, CameraStyle(bank = 1.0, hairpinView = false))
        val raised = CameraDirector(path, flat, 1.0, 0.5625, CameraStyle(bank = 0.0, hairpinView = true))
        val straight = 2_000.0
        val zigzag = path.length * 0.5
        org.junit.Assert.assertEquals(0.0, level.chase(zigzag).rollDeg, 1e-9)
        org.junit.Assert.assertEquals(0.0, banked.chase(straight).rollDeg, 0.5)
        // Over hairpins the hairpin view climbs much higher above the road.
        val marker = path.at(zigzag)
        assertTrue(raised.chase(zigzag).eye.z - marker.z > (level.chase(zigzag).eye.z - marker.z) * 1.3)
    }

    @Test fun `every moment shot returns to the chase`() {
        val director = CameraDirector(path, flat, 1.0, 0.5625)
        for (kind in MomentKind.entries) {
            val m = VideoMoment(kind, path.length * 0.3, "x", "y")
            val start = director.pose(Cue(Shot.HOLD, 0.0, m.distanceM, m))
            val end = director.pose(Cue(Shot.HOLD, 1.0, m.distanceM, m))
            val chase = director.chase(m.distanceM)
            assertTrue("$kind start", (start.eye - chase.eye).length < 1)
            assertTrue("$kind end", (end.eye - chase.eye).length < 1)
            val mid = director.pose(Cue(Shot.HOLD, 0.5, m.distanceM, m))
            assertTrue("$kind moves", (mid.eye - chase.eye).length > 50)
        }
    }

    @Test fun `roll turns the view about its axis`() {
        val pose = CameraPose(Vec3(0.0, -1000.0, 500.0), Vec3(0.0, 0.0, 0.0), 50.0)
        val level = ViewMath(pose, 1.0, 5.0, 10_000.0)
        val rolled = ViewMath(pose.copy(rollDeg = 10.0), 1.0, 5.0, 10_000.0)
        // Positive roll lowers the right side.
        assertTrue(rolled.right.z < level.right.z)
        org.junit.Assert.assertEquals(0.0, rolled.right.dot(rolled.forward), 1e-9)
        org.junit.Assert.assertEquals(1.0, rolled.up.length, 1e-9)
    }
}

class ChaptersTest {
    @Test fun `climb, twisties and a break become chapters`() {
        val l = 60_000.0
        // Flat to 20 km, climb 800 m to 35 km, then twisty to the end.
        val alt = { d: Double -> if (d < 20_000) 100.0 else if (d < 35_000) 100 + (d - 20_000) / 15_000 * 800 else 900.0 }
        val twist = { d: Double -> if (d > 38_000) 0.5 else 0.0 }
        val ch = Chapters.plan(l, alt, twist, breaks = listOf(StopMark(50_000.0, 900)))
        val titles = ch.map { it.title }
        assertTrue(titles.toString(), "The climb" in titles)
        assertTrue(titles.toString(), "The twisties" in titles)
        assertTrue(titles.toString(), "After the break" in titles)
        org.junit.Assert.assertEquals("Setting off", ch.first().title)
        org.junit.Assert.assertEquals(0.0, ch.first().startM, 1e-9)
        assertTrue(ch.zipWithNext().all { (a, b) -> b.startM > a.startM })
        assertTrue(ch.single { it.title == "The climb" }.detail!!.startsWith("+"))
        org.junit.Assert.assertEquals((1..ch.size).toList(), ch.map { it.number })
    }

    @Test fun `a uniform ride has no chapters`() {
        assertTrue(Chapters.plan(40_000.0, { 100.0 }, { 0.0 }).isEmpty())
        assertTrue(Chapters.plan(2_000.0, { it / 10 }, { 0.0 }).isEmpty())
    }
}
