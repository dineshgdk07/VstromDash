package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharePrivacyTest {

    @Test
    fun `nothing in the first or last 500 metres is publishable`() {
        assertFalse(SharePrivacy.livePointAllowed(distanceFromStartM = 499.0))
        assertTrue(SharePrivacy.livePointAllowed(distanceFromStartM = 500.0))
        val kept = SharePrivacy.retainAfterRide(
            distancesFromStartM = listOf(0.0, 100.0, 500.0, 1200.0, 1600.0, 2000.0),
            totalM = 2000.0,
        )
        assertEquals(listOf(500.0, 1200.0), kept)
    }

    @Test
    fun `a ride shorter than the two zones publishes nothing`() {
        assertTrue(SharePrivacy.retainAfterRide(listOf(0.0, 200.0, 400.0), totalM = 400.0).isEmpty())
    }
}
