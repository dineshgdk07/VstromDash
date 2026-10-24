package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

class RideHistoryTest {
    private val zone = ZoneOffset.UTC

    private fun ride(
        id: Long,
        date: LocalDate,
        km: Double,
        hour: Int = 10,
        max: Int = 80,
        climb: Double? = null,
        moving: Int = (km * 90).toInt(),
    ) = RideFact(id, LocalDateTime.of(date, java.time.LocalTime.of(hour, 0)).toInstant(zone).toEpochMilli(), km, moving, max, climb, "Ride $id")

    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)

    private val rides = listOf(
        ride(1, d(8, 2), 40.0, hour = 6),
        ride(2, d(8, 3), 25.0, hour = 6),
        ride(3, d(9, 10), 120.0, max = 104, climb = 600.0),
        ride(4, d(9, 11), 30.0, hour = 21),
        ride(5, d(9, 12), 15.0),
        ride(6, d(10, 5), 60.0),
        ride(7, d(10, 5), 20.0, hour = 18),
    )

    @Test fun `month totals run oldest first and fill empty months`() {
        val totals = RideHistory.monthTotals(rides, YearMonth.of(2026, 10), 4, zone)
        assertEquals(listOf(YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)), totals.map { it.month })
        assertEquals(listOf(0.0, 65.0, 165.0, 80.0), totals.map { it.km })
    }

    @Test fun `projection only for the current month`() {
        assertEquals(310.0, RideHistory.projectedKm(100.0, YearMonth.of(2026, 10), d(10, 10))!!, 1e-9)
        assertNull(RideHistory.projectedKm(100.0, YearMonth.of(2026, 9), d(10, 10)))
        assertNull(RideHistory.projectedKm(100.0, YearMonth.of(2026, 10), d(10, 2)))
    }

    @Test fun `heat adds up each day of the year`() {
        val heat = RideHistory.yearHeat(rides, 2026, zone)
        assertEquals(80.0, heat[d(10, 5)]!!, 1e-9)
        assertNull(RideHistory.yearHeat(rides, 2025, zone)[d(10, 5, 2025)])
    }

    @Test fun `goal counts the days left including today`() {
        val g = RideHistory.goal(500.0, 200.0, YearMonth.of(2026, 10), d(10, 22))!!
        assertEquals(10, g.daysLeft)
        assertEquals(30.0, g.perDayKm!!, 1e-9)
        assertEquals(0.4f, g.fraction, 1e-6f)
        assertNull(RideHistory.goal(null, 200.0, YearMonth.of(2026, 10), d(10, 22)))
        assertNull(RideHistory.goal(500.0, 600.0, YearMonth.of(2026, 10), d(10, 22))!!.perDayKm)
    }

    @Test fun `records pick the bests`() {
        val records = RideHistory.records(rides, zone).associateBy { it.title }
        assertEquals("120 km", records.getValue("Longest ride").value)
        assertEquals(3L, records.getValue("Longest ride").rideId)
        assertEquals("104 km/h", records.getValue("Top speed").value)
        assertEquals("600 m", records.getValue("Biggest climb").value)
        assertEquals("120 km", records.getValue("Biggest day").value)
        assertEquals("165 km", records.getValue("Best month").value)
        assertEquals("3 days", records.getValue("Longest streak").value)
        assertEquals(d(9, 10), records.getValue("Longest streak").date)
    }

    @Test fun `streak finds the longest run`() {
        assertEquals(d(1, 5) to 3, RideHistory.longestStreak(listOf(d(1, 1), d(1, 5), d(1, 6), d(1, 7), d(1, 9))))
        assertEquals(null to 0, RideHistory.longestStreak(emptyList()))
    }

    private fun line(fromLat: Double, toLat: Double, lng: Double, steps: Int = 40) =
        List(steps + 1) { i -> GpsFix(i * 1_000L, fromLat + (toLat - fromLat) * i / steps, lng) }

    @Test fun `territory counts cells, home and new ground`() {
        val tracks = mapOf(
            1L to line(12.90, 12.95, 77.60),
            2L to line(12.90, 12.95, 77.60),
            6L to line(12.90, 13.00, 77.70),
        )
        val facts = rides.filter { it.id in tracks.keys }
        val t = RideHistory.territory(facts, tracks)
        assertTrue(t.areaKm2 > 0)
        assertEquals(0, t.newCells[2L])
        assertTrue(t.newCells.getValue(6L) > 10)
        assertEquals(RideHistory.cellOf(12.90, 77.60), t.home)
        assertNotNull(t.farthestKm)
        assertFalse(RideHistory.foundNewGround(facts[1], t, tracks))
        assertTrue(RideHistory.foundNewGround(facts[2], t, tracks))
    }

    @Test fun `badges earn on the day and show progress otherwise`() {
        val badges = RideHistory.badges(rides, null, mostRunsOnOneRoute = 4, zone).associateBy { it.title }
        assertEquals(d(9, 10), badges.getValue("Century").earnedOn)
        assertEquals(d(9, 10), badges.getValue("Ton up").earnedOn)
        assertEquals(d(9, 10), badges.getValue("Hill climber").earnedOn)
        assertFalse(badges.getValue("Double century").earned)
        assertEquals(0.6f, badges.getValue("Double century").progress, 1e-6f)
        assertEquals(0.4f, badges.getValue("Dawn patrol").progress, 1e-6f)
        assertEquals(0.4f, badges.getValue("Regular").progress, 1e-6f)
        assertTrue(RideHistory.badges(rides, null, 0, zone).first().earned)
    }

    @Test fun `challenges stretch from the rider's own history`() {
        val c = RideHistory.challenges(rides, YearMonth.of(2026, 10), { it.id == 6L }, zone).associateBy { it.title }
        val longest = c.getValue("Beat your longest ride")
        assertEquals(121.0, longest.target, 1e-9)
        assertEquals(60.0, longest.progress, 1e-9)
        // September had 3 riding days, so October asks for 5.
        assertEquals(1.0, c.getValue("Ride on 5 days").progress, 1e-9)
        assertEquals(0.0, c.getValue("Two sunrise starts").progress, 1e-9)
        assertEquals(1.0, c.getValue("Find new roads").progress, 1e-9)
        assertTrue(RideHistory.challenges(rides.take(2), YearMonth.of(2026, 10), { false }, zone).isEmpty())
    }

    @Test fun `dna reads a persona from habits`() {
        val early = (1..6).map { ride(it.toLong(), d(10, it), 20.0, hour = 6) }
        assertEquals("Dawn Patrol", RideHistory.dna(early, { false }, zone)!!.persona)
        val long = (1..6).map { ride(it.toLong(), d(10, it), 90.0, hour = 11) }
        val dna = RideHistory.dna(long, { false }, zone)!!
        assertEquals("Mile Muncher", dna.persona)
        assertEquals("540 km", dna.signature.first().second)
        assertNull(RideHistory.dna(long.take(4), { false }, zone))
    }
}
