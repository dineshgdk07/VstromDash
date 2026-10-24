package dev.gdk.bumblebee.ui.ride.replay

import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.RideTimeline
import dev.gdk.bumblebee.ride.TelemetrySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayFramesTest {

    @Test
    fun `a two hour ride draws from one downsampled plot and scrubs with at`() {
        val count = 7_200
        val track = List(count) { i ->
            GpsFix(
                ts = i * 1_000L,
                lat = 12.0 + i * 0.0001,
                lng = 77.0,
                altitudeM = 100.0,
                speedMps = 10f,
            )
        }
        val telemetry = List(count / 5) { i ->
            TelemetrySample(
                ts = i * 5_000L,
                speedKmh = 30 + (i % 50),
                odometerKm = 1_000,
                tripAKm = 0.0,
                tripBKm = 0.0,
                fuelBars = 4,
                fuelEconKml = null,
            )
        }
        val timeline = RideTimeline.build(
            track = track,
            telemetry = telemetry,
            startTs = 0L,
            endTs = (count - 1) * 1_000L,
        )
        val plot = replayPlot(timeline, track)
        assertEquals(240, plot.frames.size)
        assertEquals(track.first().ts, plot.frames.first().ts)
        assertEquals(track.last().ts, plot.frames.last().ts)
        plot.frames.forEach { frame ->
            assertEquals(timeline.at(frame.ts).speedKmh, frame.speedKmh)
            assertEquals(timeline.at(frame.ts).altitudeM, frame.altitudeM)
        }
        val drawn = plot
        repeat(300) { i -> timeline.at(i * 1_000L) }
        assertSame(drawn, plot)
        val ticks = kmTickFrames(plot.frames)
        assertTrue(ticks.size >= 10)
        for (i in 1 until ticks.size) {
            val step = ticks[i].distanceKm - ticks[i - 1].distanceKm
            assertTrue("tick step $step", step in 4.0..6.5)
        }
    }
}
