package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ShareCardTest {

    private val zone = ZoneOffset.UTC

    @Test
    fun `distance and moving time appear when the ride has them`() {
        val card = ShareCard.of(ride(km = 86.4, movingSec = 102 * 60, max = 72, avg = 50.0), zone = zone)
        assertEquals("86.4", card.stats[0].value)
        assertEquals("KM", card.stats[0].unitLabel)
        assertEquals("1h 42m", card.stats[1].value)
        assertEquals("MOVING", card.stats[1].unitLabel)
    }

    @Test
    fun `third stat prefers a fast max, then climb, then tank, then average`() {
        val fast = ShareCard.of(ride(max = 112, avg = 54.0), climbM = 80.0, tankKmPerL = 34.5, zone = zone)
        assertEquals(ShareMetricKind.MAX_SPEED, fast.stats[2].kind)
        assertEquals("112", fast.stats[2].value)

        val climb = ShareCard.of(ride(max = 80, avg = 54.0), climbM = 50.0, tankKmPerL = 34.5, zone = zone)
        assertEquals(ShareMetricKind.ELEVATION, climb.stats[2].kind)
        assertEquals("50", climb.stats[2].value)

        val tank = ShareCard.of(ride(max = 80, avg = 54.0), climbM = 10.0, tankKmPerL = 34.5, zone = zone)
        assertEquals(ShareMetricKind.FUEL_ECONOMY, tank.stats[2].kind)
        assertEquals("34.5", tank.stats[2].value)

        val avg = ShareCard.of(ride(max = 80, avg = 54.2), zone = zone)
        assertEquals(ShareMetricKind.AVG_SPEED, avg.stats[2].kind)
        assertEquals("54", avg.stats[2].value)
    }

    @Test
    fun `big number defaults to distance and can be none or another metric`() {
        val auto = ShareCard.of(ride(km = 58.0, movingSec = 53 * 60, max = 148), zone = zone)
        assertEquals(ShareMetricKind.DISTANCE, auto.hero?.kind)
        assertEquals("58.0", auto.hero?.heroValue)

        val none = ShareCard.of(
            ride(km = 58.0, movingSec = 53 * 60, max = 148),
            switches = ShareSwitches(bigNumber = null),
            zone = zone,
        )
        assertNull(none.hero)

        val speed = ShareCard.of(
            ride(km = 58.0, movingSec = 53 * 60, max = 148),
            switches = ShareSwitches(bigNumber = ShareMetricKind.MAX_SPEED),
            zone = zone,
        )
        assertEquals("148", speed.hero?.heroValue)
    }

    @Test
    fun `an unavailable big number is not shown`() {
        val card = ShareCard.of(
            ride(km = 12.0, movingSec = 600, max = 40),
            switches = ShareSwitches(bigNumber = ShareMetricKind.ELEVATION),
            climbM = 10.0,
            zone = zone,
        )
        assertNull(card.hero)
        assertTrue(card.available.none { it.kind == ShareMetricKind.ELEVATION })
    }

    @Test
    fun `user-selected stats keep order and cap at three`() {
        val card = ShareCard.of(
            ride(km = 58.0, movingSec = 53 * 60, max = 148, avg = 66.0),
            climbM = 262.0,
            switches = ShareSwitches(
                stats = listOf(
                    ShareMetricKind.MAX_SPEED,
                    ShareMetricKind.ELEVATION,
                    ShareMetricKind.DISTANCE,
                    ShareMetricKind.MOVING,
                ),
            ),
            zone = zone,
        )
        assertEquals(
            listOf(ShareMetricKind.MAX_SPEED, ShareMetricKind.ELEVATION, ShareMetricKind.DISTANCE),
            card.stats.map { it.kind },
        )
    }

    @Test
    fun `unavailable stats cannot be selected and user choice is not overridden`() {
        val card = ShareCard.of(
            ride(km = 12.0, movingSec = 400, max = 40, avg = 30.0),
            switches = ShareSwitches(
                stats = listOf(ShareMetricKind.ELEVATION, ShareMetricKind.AVG_SPEED),
            ),
            climbM = 5.0,
            zone = zone,
        )
        assertEquals(listOf(ShareMetricKind.AVG_SPEED), card.stats.map { it.kind })
    }

    @Test
    fun `zeros are not offered as metrics`() {
        val card = ShareCard.of(
            RideInsightInput(
                startTs = 1_000L,
                endTs = 1_000L,
                km = 0.0,
                movingSec = 0,
                idleSec = 0,
                maxSpeedKmh = 0,
                avgMovingSpeedKmh = 0.0,
            ),
            zone = zone,
        )
        assertTrue(card.available.isEmpty())
        assertTrue(card.stats.isEmpty())
        assertNull(card.hero)
    }

    @Test
    fun `sentence keeps the kilometres after the decimal`() {
        val card = ShareCard.of(ride(km = 2.0, movingSec = 3 * 60, max = 40, avg = 30.0), zone = zone)
        assertTrue(card.sentence.contains("2.0 km"))
    }

    @Test
    fun `a ride under 1 km yields no plot points`() {
        val card = ShareCard.of(ride(km = 0.4), track = atMeters(0.0, 200.0, 400.0), zone = zone)
        assertTrue(card.plot.isEmpty())
        assertTrue(card.route.isEmpty())
    }

    @Test
    fun `a longer ride drops the first and last 500 m`() {
        val track = atMeters(0.0, 100.0, 500.0, 1200.0, 1600.0, 2000.0)
        val card = ShareCard.of(ride(km = 2.0), track = track, zone = zone)
        assertEquals(listOf(track[2].lat, track[3].lat), card.route.map { it.lat })
        assertEquals(2, card.plot.size)
    }

    @Test
    fun `map with a drawable track needs tiles, and without one becomes ink on own`() {
        val drawn = ShareCard.of(
            ride(km = 2.0),
            track = atMeters(0.0, 100.0, 500.0, 1200.0, 1600.0, 2000.0),
            switches = ShareSwitches(background = ShareBackground.MAP),
            zone = zone,
        )
        assertEquals(ShareBackground.MAP, drawn.background)
        assertTrue(drawn.needsTiles)

        val short = ShareCard.of(
            ride(km = 0.4),
            track = atMeters(0.0, 200.0, 400.0),
            switches = ShareSwitches(background = ShareBackground.MAP),
            zone = zone,
        )
        assertEquals(ShareBackground.OWN, short.background)
        assertFalse(short.needsTiles)
    }

    @Test
    fun `transparent background stays transparent`() {
        val card = ShareCard.of(
            ride(km = 2.0),
            track = atMeters(0.0, 100.0, 500.0, 1200.0, 1600.0, 2000.0),
            switches = ShareSwitches(background = ShareBackground.TRANSPARENT),
            zone = zone,
        )
        assertEquals(ShareBackground.TRANSPARENT, card.background)
    }

    @Test
    fun `missing tiles become own and flag a fallback`() {
        val map = ShareCard.of(
            ride(km = 2.0),
            track = atMeters(0.0, 100.0, 500.0, 1200.0, 1600.0, 2000.0),
            switches = ShareSwitches(background = ShareBackground.MAP),
            zone = zone,
        )
        val painted = map.withTiles(complete = false)
        assertEquals(ShareBackground.OWN, painted.background)
        assertTrue(painted.mapFallback)
        assertEquals(ShareLook.NIGHT_GLOW, painted.look)
        assertFalse(painted.needsTiles)
    }

    @Test
    fun `a demo ride is flagged`() {
        val card = ShareCard.of(ride(), demo = true, zone = zone)
        assertTrue(card.demo)
        assertFalse(ShareCard.of(ride(), zone = zone).demo)
    }

    @Test
    fun `default look is night glow on own`() {
        val card = ShareCard.of(ride(), zone = zone)
        assertEquals(ShareBackground.OWN, card.background)
        assertEquals(ShareLook.NIGHT_GLOW, card.look)
    }

    @Test fun `headline uses the ride name and never puts it in the bike slot`() {
        val card = ShareCard.of(ride(), extras = ShareExtras(rideName = "Nandi run"), zone = zone)
        assertEquals("Nandi run", card.headline)
    }

    @Test fun `place codes and ride number are filled for ride pass`() {
        val card = ShareCard.of(
            ride(),
            extras = ShareExtras(startPlace = "Bengaluru", endPlace = "Nandi Hills", rideNumber = 147),
            zone = zone,
        )
        assertEquals("BEN", card.fromCode)
        assertEquals("NAN", card.toCode)
        assertEquals("No. 0147", card.rideNumberText)
    }

    @Test fun `places off hides place fields`() {
        val card = ShareCard.of(
            ride(),
            switches = ShareSwitches(showPlaces = false),
            extras = ShareExtras(startPlace = "Bengaluru", endPlace = "Nandi Hills"),
            zone = zone,
        )
        assertNull(card.fromPlace)
        assertNull(card.fromCode)
    }

    @Test fun `gps only ride has no odometer`() {
        val card = ShareCard.of(ride(), extras = ShareExtras(odoEndKm = 24816, gpsOnly = true), zone = zone)
        assertNull(card.odoKm)
    }

    @Test fun `missing tiles flag a map fallback instead of silently switching`() {
        val card = ShareCard.of(
            ride(km = 20.0),
            track = eastbound(20_000.0),
            switches = ShareSwitches(background = ShareBackground.MAP),
            zone = zone,
        ).withTiles(complete = false)
        assertTrue(card.mapFallback)
        assertEquals(ShareBackground.OWN, card.background)
        assertEquals(ShareLook.NIGHT_GLOW, card.look)
    }

    @Test fun `feed shape is carried`() {
        assertEquals(ShareShape.FEED, ShareCard.of(ride(), switches = ShareSwitches(shape = ShareShape.FEED), zone = zone).shape)
    }

    @Test fun `day card sums rides and marks stops between them`() {
        val a = DayRide(ride(km = 40.0, movingSec = 3600, max = 90), eastbound(40_000.0), 50.0)
        val b = DayRide(ride(km = 30.0, movingSec = 2400, max = 104), eastbound(30_000.0, startLng = 1.0), 30.0)
        val card = ShareCard.ofDay(LocalDate.of(2026, 10, 24), listOf(a, b), zone = zone)
        assertEquals("70.0", card.stats.first { it.kind == ShareMetricKind.DISTANCE }.value)
        assertEquals(1, card.stops.size)
        assertEquals("2 rides · 70.0 km", card.sentence)
        assertEquals("SAT, 24 OCT", card.headline)
        assertNull(card.rideNumberText)
    }

    @Test fun `day name overrides date headline`() =
        assertEquals(
            "Coorg loop",
            ShareCard.ofDay(LocalDate.of(2026, 10, 24), listOf(dayRide(), dayRide()), dayName = " Coorg loop ", zone = zone).headline,
        )

    @Test fun `merged ride segment breaks become stops`() {
        val track = eastbound(40_000.0)
        val card = ShareCard.of(ride(km = 40.0), track = track, segmentBreaks = listOf(track[track.size / 2].ts), zone = zone)
        assertEquals(1, card.stops.size)
    }

    private fun ride(
        km: Double = 86.4,
        movingSec: Int = 3600,
        max: Int = 72,
        avg: Double = 40.0,
    ) = RideInsightInput(
        startTs = 18L * 3600_000,
        endTs = 20L * 3600_000,
        km = km,
        movingSec = movingSec,
        idleSec = 0,
        maxSpeedKmh = max,
        avgMovingSpeedKmh = avg,
    )

    /** Roughly [meters] of eastbound travel, copied from ShareCardRenderTest. */
    private fun eastbound(meters: Double, startLng: Double = 77.59): List<GpsFix> {
        val total = meters + 1_200.0
        val steps = 80
        return List(steps) { i ->
            val t = i / (steps - 1).toFloat()
            val eastM = total * t
            val northM = kotlin.math.sin(t * 12.0) * 900.0 + t * 400.0
            GpsFix(
                ts = i * 1_000L,
                lat = 12.97 + northM / 111_320.0,
                lng = startLng + eastM / (111_320.0 * kotlin.math.cos(Math.toRadians(12.97))),
                speedMps = 12f + 8f * t,
            )
        }
    }

    private fun dayRide() = DayRide(ride(), eastbound(20_000.0), climbM = null)

    /** Northbound fixes whose haversine distance from the start is each given metre. */
    private fun atMeters(vararg meters: Double): List<GpsFix> {
        return meters.mapIndexed { i, m ->
            if (m <= 0.0) return@mapIndexed GpsFix(ts = i.toLong(), lat = 0.0, lng = 0.0, speedMps = 10f)
            var lo = 0.0
            var hi = m / 100_000.0 + 0.02
            repeat(48) {
                val mid = (lo + hi) / 2.0
                if (Geo.haversineMeters(0.0, 0.0, mid, 0.0) < m) lo = mid else hi = mid
            }
            GpsFix(ts = i.toLong(), lat = hi, lng = 0.0, speedMps = 10f)
        }
    }
}
