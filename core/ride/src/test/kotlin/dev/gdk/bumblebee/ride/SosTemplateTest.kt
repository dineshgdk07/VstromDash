package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SosTemplateTest {
    @Test fun `the default template is valid`() {
        assertTrue(SosTemplate.problems(SosTemplate.DEFAULT).isEmpty())
    }

    @Test fun `location and time are required`() {
        val p = SosTemplate.problems("Help {name}!")
        val missing = p.filterIsInstance<SosProblem.Missing>().single()
        assertEquals(listOf(SosField.LOCATION, SosField.TIME), missing.fields)
        assertEquals("Must include {location} and {time} so they can find you", missing.message)
    }

    @Test fun `unknown fields are caught`() {
        val p = SosTemplate.problems("{location} {time} {phone}")
        assertEquals(listOf("{phone}"), p.filterIsInstance<SosProblem.Unknown>().single().tokens)
    }

    @Test fun `empty values drop their label`() {
        val text = SosTemplate.render(
            "Crash! {location} at {time}. Blood: {blood}. Bike: {bike} ({reg}). {note} Bye.",
            mapOf(SosField.LOCATION to "L", SosField.TIME to "7pm", SosField.BIKE to "V-Strom"),
        )
        assertEquals("Crash! L at 7pm. Bike: V-Strom. Bye.", text)
    }

    @Test fun `filled values go in place`() {
        val text = SosTemplate.render(SosTemplate.DEFAULT, SosField.entries.associateWith { it.sample })
        assertTrue(text.contains("KA 01 AB 1234"))
        assertTrue(text.contains("Blood: O+."))
        assertFalse(text.contains("{"))
    }

    @Test fun `registration numbers format and validate`() {
        assertEquals("KA 01 AB 1234", RegNo.format("ka01ab1234"))
        assertEquals("KA 01 AB 1234", RegNo.format("KA-01-AB-1234"))
        assertEquals("DL 03 C 456", RegNo.format("dl3c456"))
        assertEquals("22 BH 1234 AA", RegNo.format("22bh1234aa"))
        assertTrue(RegNo.isValid("KA 01 AB 1234"))
        assertTrue(RegNo.isValid("22 BH 1234 AA"))
        assertFalse(RegNo.isValid("KA01AB"))
        assertFalse(RegNo.isValid("hello"))
    }
}
