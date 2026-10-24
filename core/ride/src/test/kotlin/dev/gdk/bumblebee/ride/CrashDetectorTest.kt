package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashDetectorTest {

    @Test
    fun `detector stays disarmed below 20 kmh in the last 10 seconds`() {
        val now = 20_000L
        val slow = listOf(10_000L to 18.0, 19_000L to 12.0)
        assertFalse(CrashDetector.armed(slow, now))
        val fast = listOf(12_000L to 45.0)
        assertTrue(CrashDetector.armed(fast, now))
    }

    @Test
    fun `impact then stop then no recovery is a crash`() {
        val samples = riding(untilMs = 10_000, speed = 62.0) +
            impact(atMs = 10_000) +
            stopped(fromMs = 11_000, untilMs = 35_000)
        val verdict = CrashDetector.evaluate(samples, CrashSensitivity.NORMAL)
        assertEquals(CrashVerdictKind.CRASH, verdict.kind)
        assertEquals(62, verdict.speedBeforeKmh)
    }

    @Test
    fun `hard brake that starts moving again is not a crash`() {
        val samples = riding(untilMs = 10_000, speed = 50.0) +
            impact(atMs = 10_000, g = 4.2) +
            stopped(fromMs = 11_000, untilMs = 18_000) +
            riding(untilMs = 25_000, speed = 40.0, fromMs = 19_000)
        assertEquals(CrashVerdictKind.NONE, CrashDetector.evaluate(samples, CrashSensitivity.NORMAL).kind)
    }

    @Test
    fun `pothole without a stop is not a crash`() {
        val samples = riding(untilMs = 20_000, speed = 40.0) + impact(atMs = 8_000, g = 5.0)
        assertEquals(CrashVerdictKind.NONE, CrashDetector.evaluate(samples, CrashSensitivity.NORMAL).kind)
    }

    @Test
    fun `free fall then a stop is a crash`() {
        val samples = riding(untilMs = 5_000, speed = 30.0) +
            freeFall(atMs = 5_000) +
            stopped(fromMs = 6_000, untilMs = 30_000)
        assertEquals(CrashVerdictKind.CRASH, CrashDetector.evaluate(samples, CrashSensitivity.NORMAL).kind)
    }

    @Test
    fun `replay of simulated crashes all trigger`() {
        val cases = listOf(
            riding(10_000, 80.0) + impact(10_000, 7.0) + stopped(11_000, 40_000),
            riding(8_000, 25.0) + freeFall(8_000) + stopped(9_000, 35_000),
            riding(12_000, 55.0) + impact(12_000, 4.5) + stopped(14_000, 40_000),
        )
        assertEquals(3, cases.count { CrashDetector.evaluate(it, CrashSensitivity.NORMAL).kind == CrashVerdictKind.CRASH })
    }

    private fun riding(untilMs: Long, speed: Double, fromMs: Long = 0L): List<CrashSample> =
        (fromMs..untilMs step 1_000L).map { t -> CrashSample(t, accelG = 1.0, speedKmh = speed) }

    private fun impact(atMs: Long, g: Double = 6.0) = listOf(CrashSample(atMs, accelG = g, speedKmh = 40.0))

    private fun freeFall(atMs: Long) = (0L..300L step 50L).map { d ->
        CrashSample(atMs + d, accelG = 0.1, speedKmh = 30.0)
    }

    private fun stopped(fromMs: Long, untilMs: Long) =
        (fromMs..untilMs step 1_000L).map { t -> CrashSample(t, accelG = 1.0, speedKmh = 0.0) }
}
