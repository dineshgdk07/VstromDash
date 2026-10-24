package dev.gdk.bumblebee.ui.garage

import dev.gdk.bumblebee.ride.CareBand
import dev.gdk.bumblebee.ui.garage.documents.documentCountdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DocumentStatusTest {
    private val today = LocalDate.of(2026, 9, 21)

    @Test
    fun `blank has no expiry`() {
        val count = documentCountdown("", today)
        assertNull(count.band)
        assertNull(count.days)
        assertEquals("No expiry", count.line)
    }

    @Test
    fun `past date is overdue`() {
        val count = documentCountdown("2020-01-01", today)
        assertEquals(CareBand.OVERDUE, count.band)
        assertTrue(count.days!! < 0)
        assertEquals("expired ${-count.days} days ago", count.line)
    }

    @Test
    fun `yesterday uses the singular`() {
        val count = documentCountdown(today.minusDays(1).toString(), today)
        assertEquals(-1, count.days)
        assertEquals("expired 1 day ago", count.line)
    }

    @Test
    fun `today expires today and is overdue`() {
        val count = documentCountdown(today.toString(), today)
        assertEquals(CareBand.OVERDUE, count.band)
        assertEquals(0, count.days)
        assertEquals("expires today", count.line)
    }

    @Test
    fun `inside the reminder window is due soon`() {
        val count = documentCountdown(today.plusDays(9).toString(), today, remindDays = 14)
        assertEquals(CareBand.DUE_SOON, count.band)
        assertEquals(9, count.days)
        assertEquals("expires in 9 days", count.line)
    }

    @Test
    fun `one day uses the singular`() {
        val count = documentCountdown(today.plusDays(1).toString(), today)
        assertEquals("expires in 1 day", count.line)
        assertEquals(CareBand.DUE_SOON, count.band)
    }

    @Test
    fun `beyond the reminder window is ok`() {
        val count = documentCountdown("2026-12-01", today, remindDays = 14)
        assertEquals(CareBand.OK, count.band)
        assertEquals(71, count.days)
        assertEquals("expires in 71 days", count.line)
    }

    @Test
    fun `a wider reminder window pulls the same date into due soon`() {
        val iso = today.plusDays(20).toString()
        assertEquals(CareBand.OK, documentCountdown(iso, today, remindDays = 14).band)
        assertEquals(CareBand.DUE_SOON, documentCountdown(iso, today, remindDays = 30).band)
    }

    @Test
    fun `a bad date is not a countdown`() {
        val count = documentCountdown("nope", today)
        assertEquals(CareBand.UNKNOWN, count.band)
        assertNull(count.days)
        assertEquals("Check the date", count.line)
    }
}
