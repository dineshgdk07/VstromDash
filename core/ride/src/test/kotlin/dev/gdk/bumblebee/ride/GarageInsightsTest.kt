package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.YearMonth
import java.time.ZoneOffset

class GarageInsightsTest {
    private val zone = ZoneOffset.UTC
    private fun ts(m: Int, d: Int, y: Int = 2026, h: Int = 10) = LocalDateTime.of(y, m, d, h, 0).toInstant(zone).toEpochMilli()
    private val today = LocalDate.of(2026, 10, 24)

    private fun fill(id: Long, m: Int, d: Int, odo: Int, litres: Double?, rupees: Double?, y: Int = 2026) =
        FuelFill(id, ts(m, d, y), odo, litres, rupees)

    private fun service(id: Long, m: Int, d: Int, odo: Int, item: String, rupees: Double?, y: Int = 2026) =
        ServiceSpend(id, ts(m, d, y), odo, item, rupees)

    private fun plan(title: String, kind: String, band: CareBand = CareBand.OK, kmLeft: Int? = null, daysLeft: Int? = null) =
        PlanState(title, kind, band, kmLeft, daysLeft, null, null, null)

    @Test fun `monthly spend fills empty months`() {
        val m = GarageInsights.monthlySpend(
            listOf(fill(1, 9, 3, 1000, 10.0, 1000.0), fill(2, 10, 1, 1300, 9.0, 950.0)),
            listOf(service(1, 9, 20, 1200, "Engine oil", 1800.0)),
            YearMonth.of(2026, 10), 3, zone,
        )
        assertEquals(listOf(0.0, 2800.0, 950.0), m.map { it.total })
        assertEquals(1800.0, m[1].service, 1e-9)
    }

    @Test fun `ownership projects a year from short history`() {
        val fills = listOf(fill(1, 7, 26, 1000, 10.0, 1000.0), fill(2, 8, 25, 1400, 10.0, 1000.0))
        val o = GarageInsights.ownership(fills, emptyList(), null, today, zone)!!
        assertEquals(2000.0, o.thisYear, 1e-9)
        assertEquals(5.0, o.rupeesPerKm!!, 1e-9)
        // 2,000 over 91 days.
        assertEquals(2000.0 / 91 * 365, o.projectedYear!!, 1e-6)
        assertNull(GarageInsights.ownership(listOf(fill(1, 10, 20, 1000, 10.0, 1000.0)), emptyList(), null, today, zone)!!.projectedYear)
        assertNull(GarageInsights.ownership(emptyList(), emptyList(), null, today, zone))
    }

    @Test fun `care dates prefer the nearer of pace and calendar`() {
        val dates = GarageInsights.careDates(
            listOf(
                plan("Engine oil", "OIL", kmLeft = 400, daysLeft = 60),
                plan("PUC", "OTHER", daysLeft = 10),
                plan("Tyres", "TYRES", kmLeft = -20),
                plan("Chain", "CHAIN"),
            ),
            kmPerDay = 20.0, today = today,
        )
        assertEquals(listOf("Tyres", "PUC", "Engine oil"), dates.map { it.title })
        assertTrue(dates[0].overdue)
        assertEquals(today.plusDays(20), dates[2].date)
        assertTrue(dates[2].fromPace)
        assertFalse(dates[1].fromPace)
        assertEquals("around 13 Nov", GarageInsights.forecastLabel(dates[2].date, true, today))
        assertEquals("on 3 Nov", GarageInsights.forecastLabel(dates[1].date, false, today))
    }

    @Test fun `parts wear from the last replacement or the first reading`() {
        val parts = GarageInsights.partsLife(
            listOf(
                service(1, 3, 1, 10_000, "Rear tyre replaced", 4200.0),
                service(2, 5, 1, 12_000, "Chain lube", null),
                service(3, 6, 1, 13_000, "Brake pads front", 900.0),
                service(4, 1, 10, 9_000, "New battery", 2400.0),
            ),
            currentOdoKm = 20_000, firstOdoKm = 5_000, firstTs = ts(1, 1, 2025), today = today, zone = zone,
        ).associateBy { it.title }
        assertEquals(10_000, parts.getValue("Rear tyre").used)
        assertTrue(parts.getValue("Rear tyre").logged)
        // Lube is not a chain replacement.
        assertEquals(15_000, parts.getValue("Chain & sprockets").used)
        assertFalse(parts.getValue("Chain & sprockets").logged)
        assertEquals(15_000, parts.getValue("Front tyre").used)
        assertEquals(7_000, parts.getValue("Brake pads").used)
        assertTrue(parts.getValue("Battery").days)
        assertEquals(287, parts.getValue("Battery").used)
    }

    private fun gride(id: Long, m: Int, d: Int, km: Double, code: Int? = null, odo: Int? = null) =
        GarageRide(id, ts(m, d), km, "Ride $id", odo, code)

    @Test fun `wet rides make chain lube due early`() {
        val rides = listOf(gride(1, 10, 20, 30.0, code = 63), gride(2, 10, 22, 40.0, code = 61), gride(3, 10, 10, 50.0, code = 63))
        val wet = GarageInsights.chainLube(12_000, ts(10, 15), 12_150, rides, 500, zone)!!
        assertTrue(wet.due)
        assertEquals(70, wet.wetKm)
        assertEquals("Rode 70 km wet since Thu. Lube the chain", wet.message)
        val dry = GarageInsights.chainLube(12_000, ts(10, 15), 12_150, rides.map { it.copy(weatherCode = 0) }, 500, zone)!!
        assertFalse(dry.due)
        assertTrue(GarageInsights.chainLube(12_000, null, 12_520, emptyList(), 500, zone)!!.due)
        assertNull(GarageInsights.chainLube(null, null, 12_520, emptyList(), 500, zone))
    }

    private fun fact(id: Long, m: Int, d: Int, km: Double, movingSec: Int, climb: Double? = null) =
        RideFact(id, ts(m, d), km, movingSec, 80, climb, "Ride $id")

    @Test fun `efficiency detective names likely causes`() {
        // Five tanks of 300 km on 10 L, then one on 12 L.
        val fills = (0..6).map { i ->
            FuelFill(i.toLong(), ts(4 + i, 1), 1000 + i * 300, if (i == 0) 10.0 else if (i == 6) 12.0 else 10.0, 1000.0)
        }
        val rides = listOf(
            fact(1, 5, 10, 100.0, 3 * 3600),
            fact(2, 6, 10, 100.0, 3 * 3600),
            fact(3, 9, 15, 100.0, 5 * 3600),
        )
        val alert = GarageInsights.efficiency(fills, rides, oilOverdue = true, chainDue = false)!!
        assertEquals(25.0, alert.latestKmPerL, 1e-9)
        assertEquals(30.0, alert.usualKmPerL, 1e-9)
        assertEquals("Engine oil is overdue", alert.causes[0])
        assertEquals("More stop-go riding: 20 km/h average vs your usual 33", alert.causes[1])
        assertEquals("Check tyre pressure", alert.causes.last())
        val steady = fills.map { if (it.id == 6L) it.copy(litres = 10.0) else it }
        assertNull(GarageInsights.efficiency(steady, rides, oilOverdue = false, chainDue = false))
    }

    @Test fun `health score adds up what pulls it down`() {
        val h = GarageInsights.health(
            plans = listOf(plan("Engine oil", "OIL", CareBand.OVERDUE), plan("Brake pads", "BRAKES", CareBand.DUE_SOON)),
            docs = listOf("Insurance" to CareBand.DUE_SOON, "RC" to CareBand.OK),
            parts = listOf(PartWear("Rear tyre", 19_000, 18_000, false, true), PartWear("Chain & sprockets", 30_000, 20_000, false, false)),
            efficiency = null,
            chain = ChainLube(600, 0, true, ""),
        )
        assertEquals(100 - 12 - 10 - 5 - 5 - 4, h.score)
        assertEquals("Engine oil is overdue", h.reasons.first().text)
        assertEquals("Needs attention", h.label)
        assertEquals(100, GarageInsights.health(emptyList(), emptyList(), emptyList(), null, null).score)
    }

    @Test fun `check streak counts riding days that had a check`() {
        val d = { day: Int -> LocalDate.of(2026, 10, day) }
        val rides = setOf(d(18), d(20), d(22), d(23))
        assertEquals(3, GarageInsights.checkStreak(setOf(d(20), d(22), d(23)), rides, d(24)))
        assertEquals(4, GarageInsights.checkStreak(setOf(d(20), d(22), d(23), d(24)), rides, d(24)))
        // A ride today without a check yet doesn't break the run.
        assertEquals(3, GarageInsights.checkStreak(setOf(d(20), d(22), d(23)), rides + d(24), d(24)))
        assertEquals(0, GarageInsights.checkStreak(setOf(d(20), d(22)), rides, d(24)))
    }

    @Test fun `year in review totals one year`() {
        val fills = listOf(
            fill(1, 12, 1, 1000, 10.0, 1000.0, y = 2025),
            fill(2, 1, 5, 1300, 10.0, 1000.0),
            fill(3, 2, 5, 1650, 10.0, 1100.0),
        )
        val y = GarageInsights.year(2026, fills, listOf(service(1, 3, 1, 1700, "Service", 2500.0)), listOf(fact(1, 1, 2, 300.0, 3600), fact(2, 2, 2, 350.0, 3600)), zone)
        assertEquals(4600.0, y.spent, 1e-9)
        assertEquals(650.0, y.km, 1e-9)
        assertEquals(35.0, y.bestKmPerL!!, 1e-9)
        assertEquals(32.5, y.avgKmPerL!!, 1e-9)
        assertEquals(2, y.fills)
        assertEquals(-25, GarageInsights.change(75.0, 100.0))
        assertNull(GarageInsights.change(75.0, null))
    }

    @Test fun `cost plan prices care from past bills`() {
        val p = GarageInsights.costPlan(
            plans = listOf(plan("Engine oil", "OIL", kmLeft = 500), plan("Tyres", "TYRES", kmLeft = 9000), plan("Brake pads", "BRAKES", kmLeft = 200)),
            services = listOf(service(1, 1, 1, 1, "Oil change", 1600.0), service(2, 5, 1, 1, "Engine oil", 2000.0), service(3, 6, 1, 1, "Engine oil", 1800.0)),
            kmPerDay = 20.0, kmPerL = 30.0, rupeesPerLitre = 105.0, today = today,
        )
        assertEquals(listOf("Brake pads", "Engine oil"), p.items.map { it.title })
        assertNull(p.items[0].rupees)
        assertEquals(1800.0, p.items[1].rupees!!, 1e-9)
        assertEquals(20.0 * 90 / 30 * 105, p.fuelRupees!!, 1e-9)
        assertEquals(1, p.unpriced)
    }

    @Test fun `odometer story finds the busiest month`() {
        val rides = listOf(
            fact(1, 1, 5, 100.0, 3600), fact(2, 12, 5, 300.0, 3600).copy(startTs = ts(12, 5, 2025)),
            fact(3, 3, 5, 50.0, 3600), fact(4, 10, 5, 80.0, 3600),
        )
        val s = GarageInsights.odoStory(rides, 5000, 6200, today, zone)
        assertEquals(Month.DECEMBER, s.busiestMonth)
        assertEquals(LocalDate.of(2025, 12, 5), s.since)
        assertEquals(530.0, s.loggedKm, 1e-9)
        assertEquals(12, s.months.size)
        assertEquals(530.0 / 11, s.avgPerMonth!!, 1e-9)
    }

    @Test fun `passport merges history newest first`() {
        val events = GarageInsights.passport(
            services = listOf(service(1, 6, 1, 14_800, "Engine oil", 1800.0)),
            fills = listOf(fill(1, 6, 3, 14_900, 10.0, 1000.0), fill(2, 6, 20, 15_200, 9.0, 950.0)),
            docs = listOf("Insurance" to ts(2, 1)),
            rides = listOf(
                gride(1, 5, 1, 40.0, odo = 14_700),
                gride(2, 6, 10, 210.0, odo = 15_050),
            ),
            zone = zone,
        )
        assertEquals(PassportKind.FUEL, events[0].kind)
        assertEquals("2 fills", events[0].title)
        assertEquals("19.0 L · ₹1,950", events[0].detail)
        val kinds = events.map { it.kind }
        assertTrue(PassportKind.MILESTONE in kinds)
        assertNotNull(events.firstOrNull { it.kind == PassportKind.TRIP && it.rideId == 2L })
        assertEquals("Crossed 15,000 km", events.first { it.kind == PassportKind.MILESTONE }.title)
        assertEquals(PassportKind.DOCUMENT, events.last().kind)
    }
}
