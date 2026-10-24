package dev.gdk.bumblebee.ui.explorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RecapPeriodTest {
    @Test fun `a year covers every month of it`() {
        val year = RecapPeriod.Year(2026)
        assertTrue(year.contains(LocalDate.of(2026, 1, 1)))
        assertTrue(year.contains(LocalDate.of(2026, 12, 31)))
        assertFalse(year.contains(LocalDate.of(2025, 12, 31)))
        assertEquals(2026L, year.id)
        assertEquals("2026", year.label)
    }

    @Test fun `a month covers only its own days`() {
        val october = RecapPeriod.Month(YearMonth.of(2026, 10))
        assertTrue(october.contains(LocalDate.of(2026, 10, 1)))
        assertTrue(october.contains(LocalDate.of(2026, 10, 31)))
        assertFalse(october.contains(LocalDate.of(2026, 11, 1)))
        assertFalse(october.contains(LocalDate.of(2025, 10, 15)))
        assertEquals(202610L, october.id)
        assertTrue(october.label.endsWith("2026"))
    }

    @Test fun `short distances read as a share of the trip`() {
        assertEquals("That's 4% of Chennai → Delhi", chennaiDelhiLine(86.4))
        assertEquals("That's 1% of Chennai → Delhi", chennaiDelhiLine(3.0))
        assertEquals("That's Chennai → Delhi ×2.0", chennaiDelhiLine(4_400.0))
    }
}
