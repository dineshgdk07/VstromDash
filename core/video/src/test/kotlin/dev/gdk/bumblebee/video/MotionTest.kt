package dev.gdk.bumblebee.video

import dev.gdk.bumblebee.ride.GpsFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

class MotionTest {
    private val hop = 512 / 44_100.0

    /**
     * Envelopes for a synthetic track: a kick every [beatS] from [firstS], every fourth one
     * louder (the bar), quiet until [dropS] then loud.
     */
    private fun envelopes(seconds: Double, beatS: Double, firstS: Double, dropS: Double): Pair<FloatArray, FloatArray> {
        val n = (seconds / hop).toInt()
        val e = FloatArray(n)
        val low = FloatArray(n)
        for (i in 0 until n) {
            val t = i * hop
            val level = if (t < dropS) 0.15 else 1.0
            var kick = 0.0
            var bass = 0.0
            if (t >= firstS) {
                val k = ((t - firstS) / beatS).toInt()
                val since = t - firstS - k * beatS
                val accent = if (k % 4 == 0) 1.0 else 0.45
                kick = exp(-since / 0.05)
                bass = kick * accent
            }
            // A little hiss so it isn't perfectly clean.
            val hiss = 0.02 * (1 + sin(i * 12.9898) * 0.5)
            e[i] = ((0.05 + kick) * level + hiss).toFloat()
            low[i] = ((0.02 + bass) * level + hiss * 0.5).toFloat()
        }
        return e to low
    }

    private fun synthetic(): MusicTrack {
        val (e, low) = envelopes(90.0, 0.5, 0.25, 30.25)
        return BeatTracker.analyze("Test", e, low, hop)
    }

    @Test fun `finds the tempo, the beats and the bar`() {
        val t = synthetic()
        assertEquals(120.0, t.bpm, 2.0)
        assertTrue(abs(t.durationS - 90.0) < 0.1)
        // Every beat within 30 ms of a real one.
        for (b in t.beats.drop(2).dropLast(2)) {
            val off = ((b - 0.25) / 0.5)
            assertTrue("beat at $b", abs(off - Math.round(off)) * 0.5 < 0.03)
        }
        // Downbeats fall on the accented kicks: 0.25 + 2k seconds.
        val downs = t.beats.indices.filter { t.isDownbeat(it) }.map { t.beats[it] }.drop(1).take(10)
        for (d in downs) {
            val bars = (d - 0.25) / 2.0
            assertTrue("downbeat at $d", abs(bars - Math.round(bars)) * 2.0 < 0.05)
        }
    }

    @Test fun `music track survives storing`() {
        val t = synthetic()
        val back = MusicTrack.decode(t.encode())!!
        assertEquals(t.name, back.name)
        assertEquals(t.bpm, back.bpm, 1e-9)
        assertEquals(t.beats.size, back.beats.size)
        assertEquals(t.downbeat, back.downbeat)
        assertEquals(t.loudness.size, back.loudness.size)
        assertNull(MusicTrack.decode("nonsense"))
    }

    @Test fun `the drop lands on the dive-in, on a bar`() {
        val t = synthetic()
        val cue = MusicCue.place(t, totalS = 45.0, rideStartS = 6.0, startS = null, volume = 1.0, fadeS = 1.5, sync = true)
        val atCut = cue.offsetS + 6.0
        assertEquals(30.25, atCut, 0.6)
        val i = t.beats.indices.minBy { abs(t.beats[it] - atCut) }
        assertEquals(0.0, t.beats[i] - atCut, 1e-9)
        assertTrue(t.isDownbeat(i))
        // Pulses: strong on the cut, gone a beat later, none without sync.
        assertTrue(cue.pulse(6.0) > 0.9)
        assertTrue(cue.pulse(6.4) < 0.2)
        val loose = MusicCue.place(t, 45.0, 6.0, null, 1.0, 1.5, sync = false)
        assertEquals(0.0, loose.pulse(6.0), 0.0)
    }

    @Test fun `by hand start is kept, snapped to the beat when synced`() {
        val t = synthetic()
        val free = MusicCue.place(t, 30.0, 5.0, startS = 12.3, volume = 1.0, fadeS = 1.0, sync = false)
        assertEquals(12.3, free.offsetS, 1e-9)
        val synced = MusicCue.place(t, 30.0, 5.0, startS = 12.3, volume = 1.0, fadeS = 1.0, sync = true)
        assertTrue(abs(synced.offsetS - 12.3) <= 1.05)
        assertTrue(t.beats.any { abs(it - (synced.offsetS + 5.0)) < 1e-9 })
    }

    @Test fun `volume fades in and out and stops with the track`() {
        val t = synthetic()
        val cue = MusicCue(t, offsetS = 10.0, totalS = 40.0, volume = 0.8, fadeS = 2.0, synced = true)
        assertEquals(0.0, cue.gain(0.0), 1e-9)
        assertEquals(0.4, cue.gain(1.0), 1e-9)
        assertEquals(0.8, cue.gain(20.0), 1e-9)
        assertEquals(0.0, cue.gain(40.0), 1e-9)
        // A track that runs out before the video does.
        val short = MusicCue(t, offsetS = 70.0, totalS = 40.0, volume = 1.0, fadeS = 2.0, synced = true)
        assertEquals(20.0, short.endS, 0.05)
        assertEquals(0.0, short.gain(25.0), 1e-9)
    }

    @Test fun `cold open and intro are whole beats, the intro whole bars`() {
        val beat = 0.5
        val tl = VideoTimeline(45.0, 50_000.0, emptyList(), teaser = VideoMoment(MomentKind.TOP_SPEED, 20_000.0, "Top speed", "90 km/h"), beatS = beat)
        assertEquals(0.0, tl.coldS / beat - Math.round(tl.coldS / beat), 1e-9)
        assertEquals(0.0, tl.introS / (beat * 4) - Math.round(tl.introS / (beat * 4)), 1e-9)
        assertTrue(tl.coldS in 1.8..3.6)
        assertTrue(tl.introS >= 3.0)
        val free = VideoTimeline(45.0, 50_000.0, emptyList())
        assertEquals(free.totalS, tl.totalS, 0.0)
    }

    @Test fun `milestones at round distances, away from pauses`() {
        val l = 60_000.0
        val tl = VideoTimeline(45.0, l, emptyList())
        val ms = Milestones.plan(l, 500.0, null, tl::timeAt, emptyList())
        assertEquals(listOf("25 KM", "50 KM"), ms.map { it.label })
        assertEquals(24_500.0, ms[0].distanceM, 1e-6)
        // A pause right at 25 km pushes that one out.
        val held = Milestones.plan(l, 500.0, null, tl::timeAt, listOf(tl.timeAt(24_500.0) + 0.5))
        assertEquals(listOf("50 KM"), held.map { it.label })
        // First time above 1,000 m.
        val hill = Milestones.plan(l, 500.0, { d -> 600 + d / 100 }, tl::timeAt, emptyList())
        assertTrue(hill.any { it.label == "1,000 M" && abs(it.distanceM - 40_000) < 200 })
    }

    @Test fun `ramp ends and middle`() {
        assertEquals(TrailRamp.SPEED.first(), TrailRamp.at(TrailRamp.SPEED, 0.0))
        assertEquals(TrailRamp.SPEED.last(), TrailRamp.at(TrailRamp.SPEED, 1.0))
        assertEquals(TrailRamp.SPEED.last(), TrailRamp.at(TrailRamp.SPEED, 7.0))
        assertNull(TrailRamp.stops(TrailColor.ACCENT))
    }

    private val t0 = 1_774_060_000_000L

    /** About 30 km, speeding up from 8 to 25 m/s. */
    private val track = (0..2400).map { i ->
        val f = i / 2400.0
        GpsFix(t0 + i * 1000L, 10.0 + 0.27 * f * f + 0.05 * f, 76.9 + 0.002 * sin(f * 30), speedMps = (8 + 17 * f).toFloat(), accuracyM = 5f)
    }

    @Test fun `plan carries music, milestones and a speed trail`() {
        val input = RideVideoInput(track = track, title = "Test", startTs = t0, movingSec = 2400, music = synthetic())
        val plan = RideVideoPlan.build(input, VideoConfig(lengthS = 40), { _, _, _ -> null })!!
        val music = assertNotNull(plan.music).let { plan.music!! }
        val cut = music.offsetS + plan.timeline.rideStartS
        assertTrue(music.track.beats.any { abs(it - cut) < 1e-9 })
        assertTrue(plan.milestones.isNotEmpty())
        val trail = plan.trail!!
        assertEquals(plan.path.points.size, trail.values.size)
        assertTrue(trail.values.all { it in 0f..1f })
        // Faster at the end than the start.
        assertTrue(trail.values.takeLast(50).average() > trail.values.take(50).average() + 0.4)
        // Off switches.
        val quiet = RideVideoPlan.build(input, VideoConfig(lengthS = 40, music = false, milestones = false, trailColor = TrailColor.ACCENT), { _, _, _ -> null })!!
        assertNull(quiet.music)
        assertTrue(quiet.milestones.isEmpty())
        assertNull(quiet.trail)
        assertEquals(0.0, quiet.beatPulse(10.0), 0.0)
    }

    @Test fun `phase E options round trip and presets keep the music`() {
        val c = VideoConfig(
            trailColor = TrailColor.CLIMB, trailLegend = false, kineticType = false, milestones = false,
            music = false, musicVolume = 0.4, musicStartS = 33.5, musicFadeS = 3.0, beatSync = false, beatPulse = 0.2,
        )
        assertEquals(c, VideoConfig.decode(c.encode()))
        val p = c.withPreset(VideoPreset.ADVENTURE)
        assertEquals(33.5, p.musicStartS!!, 0.0)
        assertEquals(0.4, p.musicVolume, 0.0)
        assertEquals(false, p.beatSync)
        assertEquals(TrailColor.ACCENT, VideoConfig().withPreset(VideoPreset.MINIMAL).trailColor)
        assertEquals(1.0, VideoConfig(beatPulse = 9.0).clamped().beatPulse, 0.0)
    }
}
