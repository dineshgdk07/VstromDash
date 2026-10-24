package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsCapturePolicyTest {

    @Test
    fun `location stays off when the bike is parked and no ride is open`() {
        assertFalse(GpsCapturePolicy.shouldCapture(bikeLinked = false, gpsOnlyRide = false))
    }

    @Test
    fun `location runs while the bike is linked`() {
        assertTrue(GpsCapturePolicy.shouldCapture(bikeLinked = true, gpsOnlyRide = false))
    }

    @Test
    fun `location runs for a rider-started GPS ride`() {
        assertTrue(GpsCapturePolicy.shouldCapture(bikeLinked = false, gpsOnlyRide = true))
    }

    @Test
    fun `a recovered bike ride does not keep the GNSS chip on`() {
        assertFalse(GpsCapturePolicy.shouldCapture(bikeLinked = false, gpsOnlyRide = false))
    }

    @Test
    fun `a stop shorter than three minutes keeps one-second tracking`() {
        assertEquals(GpsPace.Tracking, GpsCapturePolicy.pace(stationaryForMs = 2 * 60_000L))
    }

    @Test
    fun `three minutes without movement drops to the parked request`() {
        assertEquals(GpsPace.Parked, GpsCapturePolicy.pace(stationaryForMs = 3 * 60_000L))
    }

    @Test
    fun `phone speed or bike speed counts as moving`() {
        assertTrue(GpsCapturePolicy.isMoving(speedMps = 1.5f, bikeSpeedKmh = 0))
        assertTrue(GpsCapturePolicy.isMoving(speedMps = 0f, bikeSpeedKmh = 5))
        assertFalse(GpsCapturePolicy.isMoving(speedMps = 1.4f, bikeSpeedKmh = 4))
        assertFalse(GpsCapturePolicy.isMoving(speedMps = null, bikeSpeedKmh = null))
    }
}
