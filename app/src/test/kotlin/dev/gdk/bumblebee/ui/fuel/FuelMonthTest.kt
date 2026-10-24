package dev.gdk.bumblebee.ui.fuel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

class FuelMonthTest {
    private val zone = ZoneOffset.UTC
    private val october = YearMonth.of(2026, 10)

    private fun at(day: Int, month: Int = 10) = LocalDateTime.of(2026, month, day, 9, 0).toInstant(zone).toEpochMilli()

    private fun fill(
        id: Long,
        ts: Long,
        litres: Double? = null,
        rupees: Double? = null,
        km: Int? = null,
    ) = FuelLogEntry(
        id = id,
        ts = ts,
        whenLabel = "",
        type = "REFUEL",
        isFill = true,
        odometerKm = 0,
        barsBefore = 2,
        barsAfter = 6,
        litres = litres,
        rupees = rupees,
        note = null,
        kmSincePrevFill = km,
        tankKmPerL = if (km != null && litres != null) km / litres else null,
        rupeesPerLitre = if (rupees != null && litres != null) rupees / litres else null,
        rupeesPerKm = null,
        riderOwned = true,
        kmSincePrevEvent = null,
        story = "",
    )

    private fun drop(id: Long, ts: Long) = fill(id, ts).copy(type = "DROP", isFill = false, barsBefore = 5, barsAfter = 4)

    private val log = listOf(
        fill(5, at(20), litres = 10.0, rupees = 1_050.0, km = 300),
        drop(4, at(14)),
        fill(3, at(5), litres = 8.0, rupees = 800.0, km = 240),
        fill(2, at(25, month = 9), litres = 9.0, rupees = 900.0, km = 250),
        fill(1, at(2, month = 9), rupees = 300.0),
    )

    @Test fun `shows only the month, or one day of it`() {
        assertEquals(listOf(5L, 4L, 3L), FuelMonth.shown(log, october, null, zone).map { it.id })
        assertEquals(listOf(4L), FuelMonth.shown(log, october, "2026-10-14", zone).map { it.id })
    }

    @Test fun `calendar marks fills and drops per day`() {
        val marks = FuelMonth.marks(log, october, zone)
        assertEquals(DayMarks(1, 0), marks["2026-10-20"])
        assertEquals(DayMarks(0, 1), marks["2026-10-14"])
        assertNull(marks["2026-09-25"])
    }

    @Test fun `month adds up spend, fuel, price and economy`() {
        val m = FuelMonth.insights(log, october, zone)
        assertEquals(2, m.fills)
        assertEquals(1, m.drops)
        assertEquals(1_850.0, m.spend!!, 1e-9)
        assertEquals(18.0, m.litres!!, 1e-9)
        assertEquals(1_850.0 / 18.0, m.rupeesPerLitre!!, 1e-9)
        assertEquals(540, m.km)
        assertEquals(30.0, m.kmPerL!!, 1e-9)
        assertEquals(1_850.0 / 540.0, m.rupeesPerKm!!, 1e-9)
        assertEquals(30.0, m.bestKmPerL!!, 1e-9)
        assertEquals("20 Oct", m.bestLabel)
    }

    @Test fun `spend and price compare with what came before`() {
        val m = FuelMonth.insights(log, october, zone)
        // ₹1,850 against September's ₹1,200.
        assertEquals(54, m.spendVsPrevPct)
        // ₹105/L on the 20th against ₹100/L on the 5th.
        assertEquals(5, m.priceChangePct)
    }

    @Test fun `a fill with no price marks spend as partial`() {
        val m = FuelMonth.insights(log + fill(6, at(28), litres = 5.0), october, zone)
        assertTrue(m.spendPartial)
    }

    @Test fun `an empty month has nothing to say`() {
        val m = FuelMonth.insights(log, YearMonth.of(2026, 8), zone)
        assertEquals(0, m.fills)
        assertNull(m.spend)
        assertNull(m.kmPerL)
    }
}
