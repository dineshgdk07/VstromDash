package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class FuelEconomicsTest {

    private fun fill(id: Long, odo: Int, litres: Double?, rupees: Double? = null, ts: Long = id * 1_000) =
        FuelFill(id = id, ts = ts, odometerKm = odo, litres = litres, rupees = rupees)

    @Test
    fun `tank km per litre uses the closing fill`() {
        val pairs = FuelEconomics.perTankKmPerL(
            listOf(
                fill(3, 1620, 6.0),
                fill(1, 1000, 8.0),
                fill(2, 1320, 8.0),
            ),
        )
        assertEquals(2, pairs.size)
        assertEquals(40.0, pairs[0].second, 0.001)
        assertEquals(50.0, pairs[1].second, 0.001)
    }

    @Test
    fun `missing litres and bad odo are skipped not zero-filled`() {
        assertTrue(FuelEconomics.perTankKmPerL(listOf(fill(1, 1000, 8.0))).isEmpty())
        assertTrue(
            FuelEconomics.perTankKmPerL(
                listOf(fill(1, 1000, 8.0), fill(2, 1320, 0.0)),
            ).isEmpty(),
        )
        assertTrue(
            FuelEconomics.perTankKmPerL(
                listOf(fill(1, 1320, 8.0), fill(2, 1000, 8.0)),
            ).isEmpty(),
        )
    }

    @Test
    fun `cost ignores unpriced tanks and flags partial coverage`() {
        val fills = listOf(
            fill(1, 1000, 8.0, rupees = null),
            fill(2, 1320, 8.0, rupees = 320.0),
            fill(3, 1620, 6.0, rupees = null),
        )
        val stats = FuelEconomics.costStats(fills)!!
        assertEquals(1.0, stats.rollingRupeesPerKm, 0.001)
        assertEquals(1, stats.pricedIntervals)
        assertEquals(2, stats.totalIntervals)
        assertTrue(stats.isPartial)
        assertNull(FuelEconomics.costStats(listOf(fill(1, 1000, 8.0), fill(2, 1320, 8.0))))
    }

    @Test
    fun `litres estimate prefers fills and says so`() {
        val fromFills = FuelEconomics.litresBurnt(100.0, 50.0, 30.0)!!
        assertEquals(2.0, fromFills.first, 0.001)
        assertTrue(fromFills.second)
        val fromBike = FuelEconomics.litresBurnt(90.0, null, 30.0)!!
        assertEquals(3.0, fromBike.first, 0.001)
        assertFalse(fromBike.second)
        assertNull(FuelEconomics.litresBurnt(10.0, null, null))
    }
}

class RideRhythmTest {

    private val zone = ZoneOffset.UTC
    private val day = 86_400_000L
    private val monday = 1_704_067_200_000L // 2024-01-01 00:00 UTC was Monday

    private fun ride(dayOffset: Long, km: Double, hour: Int = 8, max: Int = 80, moving: Int = 600) = RideRecord(
        id = dayOffset,
        startTs = monday + dayOffset * day + hour * 3_600_000L,
        km = km,
        movingSec = moving,
        maxSpeedKmh = max,
    )

    @Test
    fun `weekday and time-of-day buckets`() {
        val rides = listOf(
            ride(0, 10.0, hour = 8),
            ride(2, 20.0, hour = 18),
            ride(6, 5.0, hour = 23),
        )
        val wd = RideRhythm.weekdayKm(rides, zone)
        assertEquals(10.0, wd[0], 0.001)
        assertEquals(20.0, wd[2], 0.001)
        assertEquals(5.0, wd[6], 0.001)
        val tod = RideRhythm.timeOfDayKm(rides, zone)
        assertEquals(10.0, tod[0], 0.001)
        assertEquals(20.0, tod[2], 0.001)
        assertEquals(5.0, tod[3], 0.001)
    }

    @Test
    fun `streak survives a rideless today`() {
        val today = LocalDate.of(2024, 1, 5)
        val rides = listOf(ride(0, 5.0), ride(1, 5.0), ride(2, 5.0), ride(3, 5.0))
        val s = RideRhythm.streak(rides, zone, today)
        assertEquals(4, s.current)
        assertEquals(4, s.longest)
    }

    @Test
    fun `service eta from pace never invents a date without km`() {
        assertNull(RideRhythm.serviceEta(null, 20.0))
        assertNull(RideRhythm.serviceEta(100, 0.0))
        val due = RideRhythm.serviceEta(-10, 20.0)!!
        assertTrue(due.overdue)
        assertEquals("due now", due.label())
        val eta = RideRhythm.serviceEta(100, 20.0)!!
        assertEquals(5, eta.daysAway)
        assertEquals("~5 days", eta.label())
    }

    @Test
    fun `wrapped is honest about estimated litres`() {
        val rides = listOf(ride(0, 100.0), ride(2, 50.0, max = 110))
        val fills = listOf(
            FuelFill(1, monday, 1000, 10.0, 500.0),
            FuelFill(2, monday + 2 * day, 1150, 5.0, null),
        )
        val wrap = RideRhythm.wrapped(2024, rides, fills, zone)!!
        assertEquals(150.0, wrap.totalKm, 0.001)
        assertEquals(110, wrap.topSpeedKmh)
        assertEquals(30.0, wrap.bestTankKmPerL!!, 0.001)
        assertTrue(wrap.litresFromFills)
        assertEquals(500.0, wrap.spendRupees!!, 0.001)
        assertTrue(wrap.spendPartial)
    }
}

class SpeedTrackTest {

    @Test
    fun `colors follow 50 and 90 bands`() {
        assertEquals(SpeedTrack.COOL_ARGB, SpeedTrack.colorFor(40))
        assertEquals(SpeedTrack.MID_ARGB, SpeedTrack.colorFor(70))
        assertEquals(SpeedTrack.HOT_ARGB, SpeedTrack.colorFor(90))
    }

    @Test
    fun `joins nearest telemetry without quadratic scan mistakes`() {
        val track = listOf(
            GpsFix(0, 12.0, 77.0),
            GpsFix(1_000, 12.01, 77.0),
            GpsFix(2_000, 12.02, 77.0),
        )
        val speeds = listOf(0L to 30, 900L to 80, 2_100L to 100)
        val segs = SpeedTrack.build(track, speeds)
        assertEquals(2, segs.size)
        assertEquals(80, segs[0].speedKmh)
        assertEquals(100, segs[1].speedKmh)
        assertEquals(SpeedTrack.HOT_ARGB, segs[1].colorArgb)
    }
}

class GpxExportTest {

    @Test
    fun `gpx includes points and escaped name`() {
        val xml = GpxExport.toGpx(
            "Ride <1>",
            0L,
            listOf(GpsFix(0, 12.9, 77.6, altitudeM = 900.0), GpsFix(1_000, 12.91, 77.61)),
        )
        assertTrue(xml.contains("<name>Ride &lt;1&gt;</name>"))
        assertTrue(xml.contains("lat=\"12.9\""))
        assertTrue(xml.contains("<ele>900.0</ele>"))
        assertTrue(xml.contains("creator=\"Bumblebee Dash\""))
    }
}
