package dev.gdk.bumblebee.ble.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveredBikeTest {

    @Test
    fun `SBM name looks like Suzuki`() {
        val bike = DiscoveredBike(mac = "11:22:33:44:55:66", name = "SBM 123", rssi = -50, bonded = false)
        assertTrue(bike.looksLikeSuzuki)
    }

    @Test
    fun `random earbuds do not look like Suzuki`() {
        val bike = DiscoveredBike(mac = "AA:BB:CC:DD:EE:FF", name = "Pixel Buds", rssi = -40, bonded = false)
        assertFalse(bike.looksLikeSuzuki)
    }

    @Test
    fun `Suzuki OUI looks like Suzuki even without a name`() {
        val bike = DiscoveredBike(mac = "74:B8:39:00:11:22", name = null, rssi = -60, bonded = true)
        assertTrue(bike.looksLikeSuzuki)
    }
}
