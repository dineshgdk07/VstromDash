package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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
    fun `map with a drawable track needs tiles, and without one becomes ink on black`() {
        val drawn = ShareCard.of(
            ride(km = 2.0),
            track = atMeters(0.0, 100.0, 500.0, 1200.0, 1600.0, 2000.0),
            switches = ShareSwitches(style = ShareStyle.MAP, background = ShareBackground.PHOTO),
            zone = zone,
        )
        assertEquals(ShareStyle.MAP, drawn.style)
        assertEquals(ShareBackground.BLACK, drawn.background)
        assertTrue(drawn.needsTiles)

        val short = ShareCard.of(
            ride(km = 0.4),
            track = atMeters(0.0, 200.0, 400.0),
            switches = ShareSwitches(style = ShareStyle.MAP),
            zone = zone,
        )
        assertEquals(ShareStyle.INK, short.style)
        assertEquals(ShareBackground.BLACK, short.background)
        assertFalse(short.needsTiles)
    }

    @Test
    fun `map cannot use transparent`() {
        val card = ShareCard.of(
            ride(km = 2.0),
            track = atMeters(0.0, 100.0, 500.0, 1200.0, 1600.0, 2000.0),
            switches = ShareSwitches(style = ShareStyle.MAP, background = ShareBackground.TRANSPARENT),
            zone = zone,
        )
        assertEquals(ShareBackground.BLACK, card.background)
    }

    @Test
    fun `missing tiles become corridor on black`() {
        val map = ShareCard.of(
            ride(km = 2.0),
            track = atMeters(0.0, 100.0, 500.0, 1200.0, 1600.0, 2000.0),
            switches = ShareSwitches(style = ShareStyle.MAP),
            zone = zone,
        )
        val painted = map.withTiles(complete = false)
        assertEquals(ShareStyle.CORRIDOR, painted.style)
        assertEquals(ShareBackground.BLACK, painted.background)
        assertFalse(painted.needsTiles)
    }

    @Test
    fun `a demo ride is flagged`() {
        val card = ShareCard.of(ride(), demo = true, zone = zone)
        assertTrue(card.demo)
        assertFalse(ShareCard.of(ride(), zone = zone).demo)
    }

    @Test
    fun `default style is ink without a stats bar`() {
        val card = ShareCard.of(ride(), zone = zone)
        assertEquals(ShareStyle.INK, card.style)
        assertEquals(ShareBackground.BLACK, card.background)
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
