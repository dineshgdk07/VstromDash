package dev.gdk.bumblebee.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class VideoPlanTest {
    @Test fun `length follows distance and moments`() {
        assertEquals(LengthRange(10, 15, 30), VideoLength.range(8.0, 0))
        val city = VideoLength.range(40.0, 1)
        assertTrue(city.recommended in 20..30)
        val tripFew = VideoLength.range(200.0, 0)
        val tripMany = VideoLength.range(200.0, 8)
        assertTrue(tripMany.recommended > tripFew.recommended)
        assertEquals(VideoLength.MAX_S, VideoLength.range(800.0, 10).max)
        assertEquals(75, VideoLength.range(800.0, 10).recommended)
        for (km in listOf(1.0, 30.0, 100.0, 250.0, 500.0)) {
            val r = VideoLength.range(km, 3)
            assertTrue(r.min <= r.recommended && r.recommended <= r.max && r.max <= VideoLength.MAX_S)
            assertEquals(0, r.recommended % 5)
        }
    }

    private fun hillRide(): List<TrackSample> = (0..2000).map { i ->
        val d = i * 25.0 // 50 km
        // One big climb peaking at 30 km, with a GPS spike at 10 km that must not count.
        val alt = 200 + 1200 * kotlin.math.exp(-((d - 30_000) / 6_000).let { it * it }) + if (i == 400) 3000.0 else 0.0
        val speed = 40 + 20 * sin(d / 3000) + if (i == 1500) 140.0 else 0.0 + if (i == 900) 35.0 else 0.0
        TrackSample(d, alt, speed)
    }

    @Test fun `candidates ignore spikes`() {
        val c = Moments.candidates(hillRide(), stops = listOf(StopMark(12_000.0, 900), StopMark(20_000.0, 120)), refuels = listOf(40_000.0))
        val high = c.single { it.kind == MomentKind.HIGHEST }
        assertTrue(abs(high.distanceM - 30_000) < 500)
        assertTrue(high.value.endsWith(" m"))
        val climb = c.single { it.kind == MomentKind.CLIMB }
        assertTrue(climb.value.startsWith("+1,"))
        // The one-sample 180 km/h spike at sample 1500 loses to the real fast stretch.
        val top = c.single { it.kind == MomentKind.TOP_SPEED }
        assertTrue(top.value != "180 km/h")
        assertEquals(1, c.count { it.kind == MomentKind.STOP })
        assertEquals(1, c.count { it.kind == MomentKind.REFUEL })
    }

    @Test fun `flat rides get no height moments`() {
        val flat = (0..400).map { TrackSample(it * 50.0, 30.0 + (it % 3), 50.0) }
        assertTrue(Moments.candidates(flat).none { it.kind == MomentKind.HIGHEST || it.kind == MomentKind.CLIMB })
    }

    @Test fun `pick respects privacy, spacing and priority`() {
        val l = 50_000.0
        val c = listOf(
            VideoMoment(MomentKind.STOP, 300.0, "Break", "15 min"), // inside the start trim
            VideoMoment(MomentKind.HIGHEST, 30_000.0, "Highest point", "1,400 m"),
            VideoMoment(MomentKind.CLIMB, 29_000.0, "Biggest climb", "+1,200 m"), // merges into highest
            VideoMoment(MomentKind.TOP_SPEED, 10_000.0, "Top speed", "92 km/h"),
            VideoMoment(MomentKind.REFUEL, 40_000.0, "Refuelled", ""),
            VideoMoment(MomentKind.STOP, 49_800.0, "Break", "20 min"), // inside the end trim
        )
        val picked = Moments.pick(c, l, rideSceneS = 30.0, trimStartM = 500.0, trimEndM = 500.0)
        assertEquals(listOf(MomentKind.TOP_SPEED, MomentKind.HIGHEST, MomentKind.REFUEL), picked.map { it.kind })
        // A 6 s ride scene fits one moment: the most important.
        assertEquals(listOf(MomentKind.HIGHEST), Moments.pick(c, l, rideSceneS = 6.0, trimStartM = 500.0, trimEndM = 500.0).map { it.kind })
    }

    @Test fun `timeline covers the whole video in order`() {
        val l = 50_000.0
        val moments = listOf(
            VideoMoment(MomentKind.TOP_SPEED, 10_000.0, "Top speed", "92 km/h"),
            VideoMoment(MomentKind.HIGHEST, 30_000.0, "Highest point", "1,400 m"),
        )
        val tl = VideoTimeline(30.0, l, moments)
        assertEquals(30.0, tl.introS + tl.rideS + tl.finishS, 1e-9)
        assertEquals(Shot.INTRO, tl.cue(0.0).shot)
        assertEquals(Shot.FINISH, tl.cue(29.99).shot)
        assertEquals(l, tl.cue(30.0).distanceM, 1e-9)

        // Distance never goes backwards and holds stop at their moment.
        var last = -1.0
        val holds = mutableSetOf<MomentKind>()
        var t = 0.0
        while (t <= 30.0) {
            val cue = tl.cue(t)
            assertTrue("t=$t", cue.distanceM >= last - 1e-6)
            last = cue.distanceM
            if (cue.shot == Shot.HOLD) {
                holds += cue.moment!!.kind
                assertEquals(cue.moment!!.distanceM, cue.distanceM, 1e-9)
            } else {
                assertNull(cue.moment)
            }
            t += 1.0 / 30
        }
        assertEquals(setOf(MomentKind.TOP_SPEED, MomentKind.HIGHEST), holds)

        // No jump into or out of a hold.
        val start = tl.introS + tl.holdStarts[1]
        assertEquals(30_000.0, tl.cue(start - 1e-4).distanceM, 5.0)
        assertEquals(30_000.0, tl.cue(start + VideoTimeline.HOLD_S + 1e-4).distanceM, 5.0)
    }

    @Test fun `ride clock slows near moments`() {
        val l = 100_000.0
        val tl = VideoTimeline(40.0, l, listOf(VideoMoment(MomentKind.HIGHEST, 50_000.0, "Highest point", "")))
        fun speedAround(d: Double): Double {
            // Metres per second of video, measured over a short span of ride time.
            var t = tl.introS
            while (tl.cue(t).distanceM < d) t += 0.01
            return (tl.cue(t + 0.2).distanceM - tl.cue(t).distanceM) / 0.2
        }
        assertTrue(speedAround(48_000.0) < speedAround(25_000.0) * 0.7)
    }

    @Test fun `interest slows twisty parts`() {
        val l = 100_000.0
        val twisty = VideoTimeline(40.0, l, emptyList()) { d -> if (d in 60_000.0..70_000.0) 1.0 else 0.0 }
        val plain = VideoTimeline(40.0, l, emptyList())
        fun timeTo(tl: VideoTimeline, d: Double): Double {
            var t = tl.introS
            while (tl.cue(t).distanceM < d) t += 0.01
            return t
        }
        val twistySpan = timeTo(twisty, 70_000.0) - timeTo(twisty, 60_000.0)
        val plainSpan = timeTo(plain, 70_000.0) - timeTo(plain, 60_000.0)
        assertTrue(twistySpan > plainSpan * 1.5)
    }
}
