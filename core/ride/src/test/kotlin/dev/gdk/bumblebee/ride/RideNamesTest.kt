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

    @Test fun `code is airport style`() {
        assertEquals("BNG", RideNames.code("Bengaluru"))
        assertEquals("NDH", RideNames.code("Nandi Hills"))
        assertEquals("MSR", RideNames.code("Mysuru"))
        assertEquals("STT", RideNames.code("St. Thomas"))
        assertEquals("GOA", RideNames.code("Goa"))
        assertEquals("ADY", RideNames.code("Ady"))
        assertEquals("KU", RideNames.code("Ku"))
        assertEquals("", RideNames.code("—"))
    }
}
