package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashDetectorTest {
    /** Phone upright in a handlebar mount: gravity along y. */
    private val upright = Triple(0.0, 1.0, 0.0)

    /** Bike on its side: gravity along x. */
    private val onSide = Triple(1.0, 0.0, 0.0)

    private fun riding(fromMs: Long, untilMs: Long, speed: Double, down: Triple<Double, Double, Double> = upright) =
        (fromMs..untilMs step 250L).map { t -> CrashSample(t, 1.0, speed, down.first, down.second, down.third) }

    private fun still(fromMs: Long, untilMs: Long, down: Triple<Double, Double, Double>) =
        (fromMs..untilMs step 250L).map { t -> CrashSample(t, 1.0, 0.0, down.first, down.second, down.third) }

    /** [hits] readings of [g] 40 ms apart. */
    private fun hit(atMs: Long, g: Double, hits: Int = 1, speed: Double = 40.0) =
        (0 until hits).map { i -> CrashSample(atMs + i * 40L, g, speed, 0.0, g, 0.0) }

    private fun freeFall(atMs: Long) = (0L..350L step 50L).map { d -> CrashSample(atMs + d, 0.1, 30.0, 0.0, 0.1, 0.0) }

    private fun verdict(samples: List<CrashSample>, s: CrashSensitivity = CrashSensitivity.NORMAL) =
        CrashDetector.evaluate(samples, s)

    @Test fun `detector stays disarmed below 20 kmh in the last 10 seconds`() {
        val now = 20_000L
        assertFalse(CrashDetector.armed(listOf(10_000L to 18.0, 19_000L to 12.0), now))
        assertTrue(CrashDetector.armed(listOf(12_000L to 45.0), now))
    }

    @Test fun `a fall that leaves the bike on its side is a crash`() {
        val v = verdict(riding(0, 10_000, 62.0) + hit(10_000, 5.5, hits = 4) + still(11_000, 40_000, onSide))
        assertEquals(CrashVerdictKind.CRASH, v.kind)
        assertEquals(62, v.speedBeforeKmh)
        assertEquals(90, v.tiltDeg)
    }

    @Test fun `a pothole then waiting at a signal is not a crash`() {
        // The old detector alarmed on exactly this.
        val samples = riding(0, 10_000, 45.0) + hit(10_000, 5.0) + still(12_000, 45_000, upright)
        assertEquals(CrashVerdictKind.NONE, verdict(samples).kind)
    }

    @Test fun `a rough patch then a signal is not a crash`() {
        val samples = riding(0, 10_000, 45.0) + hit(10_000, 5.0, hits = 5) + still(12_000, 45_000, upright)
        assertEquals(CrashVerdictKind.NONE, verdict(samples).kind)
        assertEquals(CrashVerdictKind.NONE, verdict(samples, CrashSensitivity.HIGH).kind)
    }

    @Test fun `a severe hit counts even upright, except on low`() {
        val samples = riding(0, 10_000, 70.0) + hit(10_000, 11.0) + still(11_000, 45_000, upright)
        assertEquals(CrashVerdictKind.CRASH, verdict(samples).kind)
        assertEquals(CrashVerdictKind.NONE, verdict(samples, CrashSensitivity.LOW).kind)
    }

    @Test fun `riding on after a hard stop is not a crash`() {
        val samples = riding(0, 10_000, 50.0) + hit(10_000, 5.0, hits = 4) + still(11_000, 18_000, onSide) +
            riding(19_000, 45_000, 40.0)
        assertEquals(CrashVerdictKind.NONE, verdict(samples).kind)
    }

    @Test fun `free fall needs the phone to end up turned`() {
        val down = riding(0, 5_000, 30.0) + freeFall(5_000) + still(6_000, 35_000, onSide)
        assertEquals(CrashVerdictKind.CRASH, verdict(down).kind)
        val caught = riding(0, 5_000, 30.0) + freeFall(5_000) + still(6_000, 35_000, upright)
        assertEquals(CrashVerdictKind.NONE, verdict(caught).kind)
    }

    @Test fun `slow riding never arms`() {
        val samples = riding(0, 10_000, 15.0) + hit(10_000, 6.0, hits = 4, speed = 15.0) + still(11_000, 40_000, onSide)
        assertEquals(CrashVerdictKind.NONE, verdict(samples).kind)
    }

    @Test fun `an earlier pothole doesn't hide a later crash`() {
        val samples = riding(0, 30_000, 50.0) + hit(8_000, 5.0) + hit(30_000, 6.0, hits = 4) + still(31_000, 60_000, onSide)
        val v = verdict(samples)
        assertEquals(CrashVerdictKind.CRASH, v.kind)
        assertEquals(30_000L, v.impactTs)
    }

    @Test fun `without direction data only a severe hit counts`() {
        val flat = { from: Long, to: Long, speed: Double -> (from..to step 250L).map { CrashSample(it, 1.0, speed) } }
        val hard = flat(0, 10_000, 60.0) + (0 until 4).map { CrashSample(10_000L + it * 40, 5.0, 40.0) } + flat(11_000, 40_000, 0.0)
        assertEquals(CrashVerdictKind.NONE, verdict(hard).kind)
        val severe = flat(0, 10_000, 60.0) + CrashSample(10_000, 12.0, 40.0) + flat(11_000, 40_000, 0.0)
        assertEquals(CrashVerdictKind.CRASH, verdict(severe).kind)
    }
}
