package dev.gdk.bumblebee.ui.garage

import dev.gdk.bumblebee.ui.components.StatusChipTone
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PlanDueTest {

    private val today = LocalDate.of(2026, 9, 21)

    @Test
    fun `km fraction closer than days wins`() {
        val due = planDue(
            currentOdoKm = 12_580,
            lastDoneOdoKm = 10_000,
            intervalKm = 3_000,
            lastDoneOn = today.minusDays(140),
            intervalDays = 180,
            dueOn = null,
            today = today,
        )
        assertEquals("2,580", due.usedLabel)
        assertEquals("3,000 km", due.totalLabel)
        assertEquals(2_580f / 3_000f, due.fraction, 0.001f)
        assertEquals(StatusChipTone.Ok, due.tone)
        assertEquals("due in 420 km", due.caption)
    }

    @Test
    fun `days fraction closer than km wins`() {
        val due = planDue(
            currentOdoKm = 11_000,
            lastDoneOdoKm = 10_000,
            intervalKm = 3_000,
            lastDoneOn = today.minusDays(170),
            intervalDays = 180,
            dueOn = null,
            today = today,
        )
        assertEquals("170", due.usedLabel)
        assertEquals("180 days", due.totalLabel)
        assertEquals(StatusChipTone.DueSoon, due.tone)
        assertEquals("due in 10 days", due.caption)
    }

    @Test
    fun `overdue km clamps the bar`() {
        val due = planDue(
            currentOdoKm = 12_000,
            lastDoneOdoKm = 4_000,
            intervalKm = 5_000,
            lastDoneOn = null,
            intervalDays = null,
            dueOn = null,
            today = today,
        )
        assertEquals("8,000", due.usedLabel)
        assertEquals("5,000 km", due.totalLabel)
        assertEquals(1f, due.fraction, 0.001f)
        assertEquals(StatusChipTone.Overdue, due.tone)
        assertEquals("3,000 km overdue", due.caption)
    }

    @Test
    fun `no interval has an empty meter`() {
        val due = planDue(
            currentOdoKm = 12_000,
            lastDoneOdoKm = null,
            intervalKm = null,
            lastDoneOn = null,
            intervalDays = null,
            dueOn = null,
            today = today,
        )
        assertEquals("—", due.usedLabel)
        assertEquals("—", due.totalLabel)
        assertEquals(0f, due.fraction, 0.001f)
        assertEquals(StatusChipTone.NoExpiry, due.tone)
        assertEquals("Set km or a date", due.caption)
    }

    @Test
    fun `due date without an interval has no bar`() {
        val due = planDue(
            currentOdoKm = 8_000,
            lastDoneOdoKm = null,
            intervalKm = null,
            lastDoneOn = null,
            intervalDays = null,
            dueOn = LocalDate.of(2026, 10, 1),
            today = today,
        )
        assertEquals(0f, due.fraction, 0.001f)
        assertEquals(StatusChipTone.DueSoon, due.tone)
        assertEquals("due in 10 days", due.caption)
    }
}
