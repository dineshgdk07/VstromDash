package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GarageCareTest {

    private val today = LocalDate.of(2026, 9, 21)

    @Test
    fun `document without expiry is on file`() {
        val d = GarageCare.documentInsight(expiryIso = null, today = today, title = "RC")
        assertEquals(CareBand.OK, d.band)
        assertTrue(d.label.contains("on file"))
    }

    @Test
    fun `document expired uses days ago`() {
        val d = GarageCare.documentInsight("2026-09-01", today, "Insurance")
        assertEquals(CareBand.OVERDUE, d.band)
        assertTrue(d.label.contains("expired"))
    }

    @Test
    fun `document inside remind window is due soon`() {
        val d = GarageCare.documentInsight("2026-10-01", today, "PUC", remindDays = 14)
        assertEquals(CareBand.DUE_SOON, d.band)
    }

    @Test
    fun `km-only plan overdue`() {
        val p = GarageCare.planInsight(
            title = "Brake pads",
            currentOdoKm = 12_000,
            lastDoneOdoKm = 4_000,
            intervalKm = 5_000,
            lastDoneOn = null,
            intervalDays = null,
            dueOn = null,
            today = today,
            kmPerDay = 20.0,
        )
        assertEquals(CareBand.OVERDUE, p.band)
        assertEquals(-3_000, p.kmRemaining)
    }

    @Test
    fun `calendar due date for tyres`() {
        val p = GarageCare.planInsight(
            title = "Tyres",
            currentOdoKm = 8_000,
            lastDoneOdoKm = null,
            intervalKm = null,
            lastDoneOn = null,
            intervalDays = null,
            dueOn = LocalDate.of(2026, 10, 1),
            today = today,
            kmPerDay = 20.0,
        )
        assertEquals(CareBand.DUE_SOON, p.band)
        assertEquals(10, p.daysRemaining)
        assertTrue(p.label.contains("Tyres"))
    }

    @Test
    fun `history story stacks shop spend and gap`() {
        val s = GarageCare.historyStory("oil", 12_400, 1800.0, "Suzuki", 3_900)
        assertTrue(s.contains("Suzuki"))
        assertTrue(s.contains("3900 km since last"))
        assertTrue(s.contains("₹1800"))
    }

    @Test
    fun `whichever gate hits first wins`() {
        val p = GarageCare.planInsight(
            title = "Oil",
            currentOdoKm = 10_400,
            lastDoneOdoKm = 10_000,
            intervalKm = 1_000,
            lastDoneOn = today.minusDays(80),
            intervalDays = 90,
            dueOn = null,
            today = today,
            kmPerDay = 20.0,
        )
        assertEquals(CareBand.DUE_SOON, p.band)
        assertEquals(10, p.daysRemaining)
    }

    @Test
    fun `reminder fires inside km and day windows`() {
        assertTrue(GarageCare.shouldRemind(CareBand.OVERDUE, kmRemaining = null, daysRemaining = null, remindKm = 200, remindDays = 14))
        assertTrue(GarageCare.shouldRemind(CareBand.DUE_SOON, kmRemaining = 150, daysRemaining = 20, remindKm = 200, remindDays = 14))
        assertEquals(false, GarageCare.shouldRemind(CareBand.OK, kmRemaining = 800, daysRemaining = 40, remindKm = 200, remindDays = 14))
    }
}
