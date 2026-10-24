package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RideNamesTest {
    @Test fun `clean trims, collapses and caps at 30`() {
        assertEquals("Nandi Hills sunrise", RideNames.clean("  Nandi   Hills sunrise "))
        assertEquals(30, RideNames.clean("x".repeat(45))!!.length)
        assertNull(RideNames.clean("   "))
    }

    @Test fun `headline prefers name, then places, then time of day`() {
        assertEquals("Nandi run", RideNames.headline("Nandi run", "Bengaluru", "Nandi Hills", true, "Morning"))
        assertEquals("Bengaluru → Nandi Hills", RideNames.headline(null, "Bengaluru", "Nandi Hills", true, "Morning"))
        assertEquals("Morning ride", RideNames.headline(null, "Bengaluru", "Nandi Hills", false, "Morning"))
        assertEquals("Morning ride", RideNames.headline(null, "Bengaluru", null, true, "Morning"))
    }

    @Test fun `code is first three letters uppercased`() {
        assertEquals("BEN", RideNames.code("Bengaluru"))
        assertEquals("STT", RideNames.code("St. Thomas"))
    }
}
