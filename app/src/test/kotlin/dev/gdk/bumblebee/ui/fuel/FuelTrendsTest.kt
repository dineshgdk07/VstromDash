package dev.gdk.bumblebee.ui.fuel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

class FuelTrendsTest {
    private val zone = ZoneOffset.UTC

    private fun at(day: Int) = LocalDateTime.of(2026, 10, day, 9, 0).toInstant(zone).toEpochMilli()

    private fun fill(id: Long, day: Int, note: String?, litres: Double? = null, rupees: Double? = null, km: Int? = null) = FuelLogEntry(
        id = id,
        ts = at(day),
        whenLabel = "",
        type = "REFUEL",
        isFill = true,
        odometerKm = 0,
        barsBefore = 2,
        barsAfter = 6,
        litres = litres,
        rupees = rupees,
        note = note,
        kmSincePrevFill = km,
        tankKmPerL = if (km != null && litres != null) km / litres else null,
        rupeesPerLitre = if (rupees != null && litres != null) rupees / litres else null,
        rupeesPerKm = null,
        riderOwned = true,
        kmSincePrevEvent = null,
        story = "",
    )

    @Test fun `station names come from the comment with one brand spelling`() {
        assertEquals("HP Whitefield", FuelTrends.station("hpcl whitefield, full tank"))
        assertEquals("HP Whitefield", FuelTrends.station("HP Whitefield"))
        assertEquals("Indian Oil Hosur Road", FuelTrends.station("IOCL hosur road. paid by UPI"))
        assertEquals("Shell", FuelTrends.station("shell"))
        assertEquals("Jio-bp Sarjapur", FuelTrends.station("jio bp sarjapur"))
        assertEquals("Highway Fuels", FuelTrends.station("highway fuels"))
        assertNull(FuelTrends.station("full tank"))
        assertNull(FuelTrends.station("slightly short-changed again"))
        assertNull(FuelTrends.station(null))
        assertNull(FuelTrends.station("  "))
    }

    @Test fun `stations group fills and judge fuel by the tank after it`() {
        val log = listOf(
            fill(1, 1, "HP Whitefield", litres = 10.0, rupees = 1_000.0),
            fill(2, 8, "Shell", litres = 8.0, rupees = 880.0, km = 300),
            fill(3, 15, "hpcl whitefield, full", litres = 9.0, rupees = 918.0, km = 240),
            fill(4, 22, "full tank", litres = 8.0, rupees = 800.0, km = 288),
        )
        val stations = FuelTrends.stations(log, zone)
        assertEquals(listOf("HP Whitefield", "Shell"), stations.map { it.name })
        val hp = stations.first()
        assertEquals(2, hp.fills)
        assertEquals(1_918.0 / 19.0, hp.rupeesPerLitre!!, 1e-9)
        // HP fuel went into the tanks closed by fills 2 (300 km / 8 L) and 4 (288 km / 8 L).
        assertEquals((37.5 + 36.0) / 2, hp.kmPerL!!, 1e-9)
        assertEquals("15 Oct", hp.lastLabel)
        // Shell fuel went into the tank closed by fill 3.
        assertEquals(240.0 / 9.0, stations[1].kmPerL!!, 1e-9)
    }

    @Test fun `prices run oldest first and skip unpriced fills`() {
        val log = listOf(
            fill(3, 20, null, litres = 10.0, rupees = 1_050.0),
            fill(2, 10, null, litres = 8.0),
            fill(1, 2, null, litres = 8.0, rupees = 800.0),
        )
        assertEquals(listOf(100.0, 105.0), FuelTrends.prices(log, zone).map { it.rupeesPerLitre })
    }

    @Test fun `streak counts complete fills from the newest back`() {
        val log = listOf(
            fill(4, 20, null, litres = 10.0, rupees = 1_050.0),
            fill(3, 14, null, litres = 8.0, rupees = 800.0),
            fill(2, 8, null, litres = 8.0),
            fill(1, 2, null, litres = 8.0, rupees = 800.0),
        )
        assertEquals(2, FuelTrends.streak(log))
        assertNull(FuelTrends.incomplete(log))
        val missing = listOf(fill(5, 25, null, rupees = 500.0)) + log
        assertEquals(0, FuelTrends.streak(missing))
        assertEquals(5L, FuelTrends.incomplete(missing)?.id)
    }

    @Test fun `budget projects the current month at its pace`() {
        val october = YearMonth.of(2026, 10)
        val view = FuelTrends.budget(1_000.0, 3_000.0, october, LocalDate.of(2026, 10, 10))!!
        assertEquals(3_100.0, view.projected!!, 1e-9)
        assertTrue(view.heading)
        assertFalse(view.over)
        assertEquals(1_000f / 3_000f, view.fraction, 1e-6f)
        assertNull(FuelTrends.budget(1_000.0, 3_000.0, october, LocalDate.of(2026, 11, 2))!!.projected)
        assertNull(FuelTrends.budget(1_000.0, null, october, LocalDate.of(2026, 10, 10)))
        assertTrue(FuelTrends.budget(3_200.0, 3_000.0, october, LocalDate.of(2026, 10, 30))!!.over)
    }
}
