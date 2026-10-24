package dev.gdk.bumblebee.ui.journey

import org.junit.Assert.assertEquals
import org.junit.Test

class JourneyCopyTest {
    @Test
    fun `strips padded cluster distances`() {
        assertEquals("200 m", prettyDistance("0200", "M"))
        assertEquals("1.2 km", prettyDistance("01.2", "K"))
    }

    @Test
    fun `formats twelve-hour eta`() {
        assertEquals("05:00 AM", prettyEta("0500AM"))
    }
}
