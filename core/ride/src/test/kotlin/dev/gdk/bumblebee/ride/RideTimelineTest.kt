package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

class RideTimelineTest {
    private val zone = ZoneId.of("UTC")

    @Test
    fun `interpolates position altitude speed and distance between close fixes`() {
        val a = fix(0, lat = 12.0, lng = 77.0, alt = 100.0, speedMps = 10f)
        val b = fix(2_000, lat = 12.01, lng = 77.0, alt = 200.0, speedMps = 20f)
        val spanM = Geo.haversineMeters(a.lat, a.lng, b.lat, b.lng)
        val moment = timeline(track = listOf(a, b), endTs = 2_000).at(1_000)

        assertEquals(1_000L, moment.ts)
        assertEquals(12.005, moment.position!!.lat, 1e-9)
        assertEquals(77.0, moment.position.lng, 1e-9)
        assertEquals(150.0, moment.altitudeM!!, 1e-6)
        assertEquals((15.0 * 3.6).roundToInt(), moment.speedKmh)
        assertEquals(SpeedSource.GPS, moment.speedSource)
        assertEquals(spanM / 2.0 / 1000.0, moment.distanceKm, 1e-6)
        assertEquals(spanM / 2.0 / 1000.0, moment.remainingKm, 1e-6)
        assertEquals(
            Instant.ofEpochMilli(1_000).atZone(zone).toLocalTime(),
            moment.clock,
        )
        assertEquals(1, moment.sinceStartSec)
    }

    @Test
    fun `a gps gap holds the last fix and does not invent the in-between`() {
        val a = fix(0, lat = 12.0, lng = 77.0, alt = 10.0, speedMps = 5f)
        val b = fix(10_000, lat = 12.05, lng = 77.05, alt = 90.0, speedMps = 25f)
        val built = timeline(track = listOf(a, b), endTs = 10_000)
        val held = built.at(5_000)

        assertEquals(a.lat, held.position!!.lat, 0.0)
        assertEquals(a.lng, held.position.lng, 0.0)
        assertEquals(10.0, held.altitudeM!!, 0.0)
        assertEquals(0.0, held.distanceKm, 0.0)
        assertEquals((5.0 * 3.6).roundToInt(), held.speedKmh)

        val arrived = built.at(10_000)
        val jumpKm = Geo.haversineMeters(a.lat, a.lng, b.lat, b.lng) / 1000.0
        assertEquals(b.lat, arrived.position!!.lat, 0.0)
        assertEquals(90.0, arrived.altitudeM!!, 0.0)
        assertEquals(jumpKm, arrived.distanceKm, 1e-6)
        assertEquals(0.0, arrived.remainingKm, 1e-6)
    }

    @Test
    fun `gps only rides have no odo estimate`() {
        val built = timeline(
            track = listOf(fix(0, 12.0, 77.0), fix(1_000, 12.01, 77.0)),
            odoStartKm = 12_345,
            odoGpsFactor = 1.2,
            gpsOnly = true,
            endTs = 1_000,
        )
        assertNull(built.at(0).odoEstKm)
        assertNull(built.at(1_000).odoEstKm)
    }

    @Test
    fun `queries before the start and after the end clamp to the ride`() {
        val built = timeline(
            track = listOf(
                fix(0, 12.0, 77.0, speedMps = 10f),
                fix(20_000, 12.02, 77.0, speedMps = 10f),
            ),
            endTs = 20_000,
        )
        val before = built.at(-5_000)
        val after = built.at(25_000)
        assertEquals(0L, before.ts)
        assertEquals(0, before.sinceStartSec)
        assertEquals(0.0, before.distanceKm, 0.0)
        assertEquals(20_000L, after.ts)
        assertEquals(20, after.sinceStartSec)
        assertEquals(built.at(20_000).distanceKm, after.distanceKm, 0.0)
        assertEquals(0.0, after.remainingKm, 1e-9)
    }

    @Test
    fun `bike speed within 6s wins otherwise gps`() {
        val track = listOf(fix(10_000, 12.0, 77.0, speedMps = 10f))
        val onTop = timeline(
            track = track,
            telemetry = listOf(sample(10_000, speed = 48)),
            endTs = 10_000,
        ).at(10_000)
        assertEquals(48, onTop.speedKmh)
        assertEquals(SpeedSource.BLE, onTop.speedSource)

        val tooLate = timeline(
            track = track,
            telemetry = listOf(sample(16_001, speed = 48)),
            endTs = 16_001,
        ).at(10_000)
        assertEquals(36, tooLate.speedKmh)
        assertEquals(SpeedSource.GPS, tooLate.speedSource)

        val edge = timeline(
            track = track,
            telemetry = listOf(sample(16_000, speed = 48)),
            endTs = 16_000,
        ).at(10_000)
        assertEquals(48, edge.speedKmh)
        assertEquals(SpeedSource.BLE, edge.speedSource)

        val tie = timeline(
            track = track,
            telemetry = listOf(sample(4_000, speed = 11), sample(16_000, speed = 99)),
            endTs = 16_000,
        ).at(10_000)
        assertEquals(11, tie.speedKmh)
        assertEquals(SpeedSource.BLE, tie.speedSource)

        val neither = timeline(
            track = listOf(fix(10_000, 12.0, 77.0)),
            endTs = 10_000,
        ).at(10_000)
        assertNull(neither.speedKmh)
        assertEquals(SpeedSource.NONE, neither.speedSource)
    }

    @Test
    fun `odo estimate never decreases`() {
        val track = (0..10).map { sec ->
            fix(sec * 1_000L, lat = 12.0 + sec * 0.001, lng = 77.0, speedMps = 8f)
        } + fix(21_000, lat = 12.02, lng = 77.01, speedMps = 8f)
        val built = timeline(
            track = track,
            odoStartKm = 10_000,
            odoGpsFactor = 1.3,
            endTs = 21_000,
        )
        var previous = -1.0
        for (ts in 0L..21_000L step 500L) {
            val odo = built.at(ts).odoEstKm!!
            assertTrue("odo went backwards at $ts: $odo < $previous", odo >= previous - 1e-9)
            previous = odo
        }
        val flat = timeline(
            track = listOf(fix(0, 12.0, 77.0), fix(1_000, 12.01, 77.0)),
            odoStartKm = 50,
            odoGpsFactor = -2.0,
            endTs = 1_000,
        )
        assertEquals(50.0, flat.at(0).odoEstKm!!, 0.0)
        assertEquals(50.0, flat.at(1_000).odoEstKm!!, 0.0)
    }

    @Test
    fun `atDistance hits the cumulative distance and fuel follows the last sample`() {
        val a = fix(0, 12.0, 77.0)
        val b = fix(4_000, 12.02, 77.0)
        val totalKm = Geo.haversineMeters(a.lat, a.lng, b.lat, b.lng) / 1000.0
        val built = timeline(
            track = listOf(a, b),
            telemetry = listOf(sample(0, speed = 30, bars = 6), sample(3_000, speed = 30, bars = null)),
            endTs = 4_000,
            odoStartKm = 100,
        )
        assertEquals(0.0, built.atDistance(0.0).distanceKm, 1e-6)
        assertEquals(totalKm, built.atDistance(totalKm + 5).distanceKm, 1e-6)
        val mid = built.atDistance(totalKm / 2.0)
        assertEquals(totalKm / 2.0, mid.distanceKm, 1e-4)
        assertEquals(2_000L, mid.ts)
        assertEquals(6, built.at(2_000).fuelBars)
        assertNull(built.at(3_000).fuelBars)
        assertEquals(100.0, built.at(0).odoEstKm!!, 0.0)
    }

    @Test
    fun `nearest event is the closest within 30s and includes max speed`() {
        val track = listOf(
            fix(0, 12.0, 77.0, speedMps = 5f),
            fix(10_000, 12.01, 77.0, speedMps = 5f),
        )
        val built = timeline(
            track = track,
            telemetry = listOf(sample(3_000, speed = 80, bars = 4)),
            fuelEvents = listOf(
                FuelEvent(10_000, 100, 4, 6, FuelEventType.REFUEL),
            ),
            stops = listOf(RideStop(ts = 0, durationSec = 120)),
            endTs = 80_000,
        )
        assertEquals(RideEventKind.STOP, built.at(0).nearestEvent!!.kind)
        assertEquals(RideEventKind.MAX_SPEED, built.at(3_000).nearestEvent!!.kind)
        assertEquals(3_000L, built.events.first { it.kind == RideEventKind.MAX_SPEED }.ts)
        assertEquals(RideEventKind.REFUEL, built.at(12_000).nearestEvent!!.kind)
        assertNull(built.at(50_000).nearestEvent)
        assertTrue(built.events.any { it.kind == RideEventKind.STOP && it.durationSec == 120 })
    }

    @Test
    fun `missing odo start hides the estimate`() {
        val built = timeline(
            track = listOf(fix(0, 12.0, 77.0), fix(1_000, 12.01, 77.0)),
            odoStartKm = null,
            endTs = 1_000,
        )
        assertNull(built.at(1_000).odoEstKm)
    }

    @Test
    fun `odo factor uses this ride and refuses a non-positive ratio`() {
        assertEquals(1.0, RideTimeline.odoGpsFactor(null, 10, 1_000.0), 0.0)
        assertEquals(1.0, RideTimeline.odoGpsFactor(10, null, 1_000.0), 0.0)
        assertEquals(1.0, RideTimeline.odoGpsFactor(10, 10, 5_000.0), 0.0)
        assertEquals(1.0, RideTimeline.odoGpsFactor(12, 10, 5_000.0), 0.0)
        assertEquals(1.0, RideTimeline.odoGpsFactor(10, 20, 0.0), 0.0)
        assertEquals(47.0 / 46.2, RideTimeline.odoGpsFactor(12_340, 12_387, 46_200.0), 1e-9)
    }

    @Test
    fun `stops are the gaps of at least a minute`() {
        assertTrue(RideTimeline.stopsBetween(listOf(SegmentSpan(0, 1_000))).isEmpty())
        val short = listOf(SegmentSpan(0, 10_000), SegmentSpan(69_000, 80_000))
        assertTrue(RideTimeline.stopsBetween(short).isEmpty())
        val stops = RideTimeline.stopsBetween(
            listOf(
                SegmentSpan(120_000, 200_000),
                SegmentSpan(0, 30_000),
                SegmentSpan(50_000, null),
            ),
        )
        assertEquals(1, stops.size)
        assertEquals(30_000L, stops[0].ts)
        assertEquals(90, stops[0].durationSec)
    }

    private fun timeline(
        track: List<GpsFix>,
        telemetry: List<TelemetrySample> = emptyList(),
        fuelEvents: List<FuelEvent> = emptyList(),
        stops: List<RideStop> = emptyList(),
        odoStartKm: Int? = null,
        odoGpsFactor: Double = 1.0,
        gpsOnly: Boolean = false,
        startTs: Long = 0L,
        endTs: Long = track.maxOfOrNull { it.ts } ?: startTs,
    ) = RideTimeline.build(
        track = track,
        telemetry = telemetry,
        fuelEvents = fuelEvents,
        stops = stops,
        odoStartKm = odoStartKm,
        odoGpsFactor = odoGpsFactor,
        gpsOnly = gpsOnly,
        startTs = startTs,
        endTs = endTs,
        zone = zone,
    )

    private fun fix(
        ts: Long,
        lat: Double,
        lng: Double,
        alt: Double? = null,
        speedMps: Float? = null,
    ) = GpsFix(ts = ts, lat = lat, lng = lng, altitudeM = alt, speedMps = speedMps)

    private fun sample(ts: Long, speed: Int, bars: Int? = null) = TelemetrySample(
        ts = ts,
        speedKmh = speed,
        odometerKm = 1_000,
        tripAKm = 0.0,
        tripBKm = 0.0,
        fuelBars = bars,
        fuelEconKml = null,
    )
}
