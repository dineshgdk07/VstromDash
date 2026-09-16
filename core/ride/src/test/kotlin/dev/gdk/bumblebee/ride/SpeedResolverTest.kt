package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedResolverTest {

    private fun fix(
        ts: Long = 10_000L,
        speedMps: Float? = 11.11f, // ~40 km/h
        accuracyM: Float? = 8f,
    ) = GpsFix(ts = ts, lat = 1.0, lng = 2.0, speedMps = speedMps, accuracyM = accuracyM)

    @Test
    fun `fresh GPS wins over BLE`() {
        val r = SpeedResolver.resolve(gps = fix(), nowMs = 11_000L, bleSpeedKmh = 90)
        assertEquals(SpeedSource.GPS, r.source)
        assertEquals(40, r.speedKmh)
    }

    @Test
    fun `GPS under 3 kmh clamps to zero`() {
        val r = SpeedResolver.resolve(
            gps = fix(speedMps = 0.5f),
            nowMs = 10_000L,
            bleSpeedKmh = 40,
        )
        assertEquals(SpeedSource.GPS, r.source)
        assertEquals(0, r.speedKmh)
    }

    @Test
    fun `stale GPS falls back to BLE`() {
        val r = SpeedResolver.resolve(gps = fix(ts = 0L), nowMs = 10_000L, bleSpeedKmh = 55)
        assertEquals(SpeedSource.BLE, r.source)
        assertEquals(55, r.speedKmh)
    }

    @Test
    fun `inaccurate GPS falls back to BLE`() {
        val r = SpeedResolver.resolve(gps = fix(accuracyM = 40f), nowMs = 10_000L, bleSpeedKmh = 12)
        assertEquals(SpeedSource.BLE, r.source)
        assertEquals(12, r.speedKmh)
    }

    @Test
    fun `no GPS no BLE is none`() {
        val r = SpeedResolver.resolve(gps = null, nowMs = 0L, bleSpeedKmh = null)
        assertEquals(SpeedSource.NONE, r.source)
        assertEquals(0, r.speedKmh)
    }

    @Test
    fun `BLE under 3 kmh clamps to zero`() {
        val r = SpeedResolver.resolve(gps = null, nowMs = 0L, bleSpeedKmh = 2)
        assertEquals(SpeedSource.BLE, r.source)
        assertEquals(0, r.speedKmh)
    }

    @Test
    fun `compass cardinals`() {
        assertNull(CompassCardinal.label(null))
        assertEquals("N", CompassCardinal.label(0f))
        assertEquals("NE", CompassCardinal.label(45f))
        assertEquals("E", CompassCardinal.label(90f))
        assertEquals("S", CompassCardinal.label(180f))
        assertEquals("NW", CompassCardinal.label(337f))
        assertEquals("N", CompassCardinal.label(359f))
    }
}
