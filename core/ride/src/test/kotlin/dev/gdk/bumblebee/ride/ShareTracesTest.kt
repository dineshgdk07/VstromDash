package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTracesTest {
    @Test fun `flat ride has no elevation profile`() =
        assertTrue(ShareTraces.elevation(climbTrack(gainM = 20.0), climbM = 20.0).isEmpty())

    @Test fun `climb gives 64 normalised samples ending high`() {
        val e = ShareTraces.elevation(climbTrack(gainM = 600.0), climbM = 600.0)
        assertEquals(64, e.size)
        assertTrue(e.all { it in 0f..1f })
        assertTrue(e.last() > 0.9f)
    }

    @Test fun `speed trace is normalised to the peak`() {
        val t = ShareTraces.speed(trackWithSpeeds(listOf(5f, 10f, 20f, 10f)))
        assertEquals(64, t.size)
        assertEquals(1f, t.max(), 0.001f)
    }

    private fun climbTrack(gainM: Double): List<GpsFix> {
        val n = 24
        return List(n) { i ->
            val t = i / (n - 1).toDouble()
            GpsFix(ts = i * 1_000L, lat = 0.0, lng = t * 0.2, altitudeM = gainM * t, speedMps = 8f)
        }
    }

    private fun trackWithSpeeds(speeds: List<Float>): List<GpsFix> =
        speeds.mapIndexed { i, speed ->
            GpsFix(ts = i * 1_000L, lat = 0.0, lng = i * 0.01, speedMps = speed)
        }
}
