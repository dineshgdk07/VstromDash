package dev.gdk.bumblebee.ui.garage

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DocumentStatusTest {
    @Test
    fun `blank is not set`() {
        assertEquals("not set", documentStatus(""))
    }

    @Test
    fun `expired when before today`() {
        assertEquals("expired", documentStatus("2020-01-01", LocalDate.of(2026, 9, 21)))
    }

    @Test
    fun `ok when more than 30 days out`() {
        assertEquals("ok", documentStatus("2026-12-01", LocalDate.of(2026, 9, 21)))
    }
}
