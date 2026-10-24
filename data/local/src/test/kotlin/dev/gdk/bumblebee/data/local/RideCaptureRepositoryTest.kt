package dev.gdk.bumblebee.data.local

import androidx.test.core.app.ApplicationProvider
import dev.gdk.bumblebee.ride.DemoRideSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class RideCaptureRepositoryTest {

    private lateinit var db: BumblebeeDb
    private lateinit var repo: RideCaptureRepository

    @Before
    fun setUp() {
        db = BumblebeeDb.inMemory(ApplicationProvider.getApplicationContext())
        repo = RideCaptureRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `captures a full demo ride end to end`() = runTest {
        val demo = DemoRideSource(startOdoKm = 15_000, startBars = 5, startTs = 0L)

        val rideId = repo.startRide(ts = 0L)
        assertEquals(rideId, repo.activeRideId)
        for ((ts, frame) in demo.telemetry()) repo.onTelemetry(frame, ts)
        for (fix in demo.gps()) repo.onGps(fix)
        val ride = repo.finishRide(ts = 600_000L)
        assertNull(repo.activeRideId)

        // Raw archive: every frame and every fix persisted (the golden rule).
        assertEquals(120, db.telemetryDao().countForRide(rideId))
        assertEquals(600, db.trackDao().countForRide(rideId))

        // Aggregates on the ride row.
        assertEquals(600_000L, ride.endTs)
        assertEquals(15_000, ride.odoStartKm)
        assertEquals(60, ride.maxSpeedKmh)
        assertEquals(5, ride.fuelBarsStart)
        assertEquals(4, ride.fuelBarsEnd)
        assertTrue("moving time should dominate", ride.movingSec > ride.idleSec)
        assertTrue("gps distance > 5km, was ${ride.gpsDistanceM}", ride.gpsDistanceM > 5_000)
        assertNotNull(ride.speedHistogramJson)

        // The demo ride's one bar drop became a persisted fuel event.
        val fuelEvents = db.fuelDao().allChronological()
        assertEquals(1, fuelEvents.size)
        assertEquals("BAR_DROP", fuelEvents[0].type)
        assertEquals("AUTO", fuelEvents[0].source)
        assertEquals(5, fuelEvents[0].barsBefore)
        assertEquals(4, fuelEvents[0].barsAfter)
        assertEquals(rideId, fuelEvents[0].rideId)

        // Daily rollup updated for the ride's start date.
        val date = Instant.ofEpochMilli(0L).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val rollup = db.rollupDao().byDate(date)!!
        assertEquals(1, rollup.rides)
        assertEquals(60, rollup.maxSpeedKmh)
        assertEquals(1, rollup.fuelBarsUsed)
        assertTrue(rollup.km > 5.0)
    }

    @Test
    fun `two rides on the same day accumulate in one rollup`() = runTest {
        val demo1 = DemoRideSource(startTs = 0L)
        repo.startRide(0L)
        for ((ts, frame) in demo1.telemetry()) repo.onTelemetry(frame, ts)
        repo.finishRide(600_000L)

        val demo2 = DemoRideSource(startTs = 1_000_000L, startBars = 4)
        repo.startRide(1_000_000L)
        for ((ts, frame) in demo2.telemetry()) repo.onTelemetry(frame, ts)
        repo.finishRide(1_600_000L)

        val date = Instant.ofEpochMilli(0L).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        assertEquals(2, db.rollupDao().byDate(date)!!.rides)
    }

    @Test
    fun `live snapshot and last finished ride`() = runTest {
        assertNull(repo.liveSnapshot())
        assertNull(repo.lastFinished())
        repo.startRide(0L)
        repo.onTelemetry(frame(odo = 1000), 0L)
        val snap = repo.liveSnapshot(1_000L)
        assertNotNull(snap)
        assertEquals(0L, snap!!.startTs)
        val finished = repo.finishRide(2_000L)
        assertNull(repo.liveSnapshot())
        assertEquals(finished.id, repo.lastFinished()!!.id)
    }

    @Test
    fun `phone gps feed records distance and moving time`() = runTest {
        repo.startRide(0L, gpsOnly = true)
        repo.feedPhoneGps(dev.gdk.bumblebee.ride.GpsFix(ts = 0L, lat = 0.0, lng = 0.0, speedMps = 0f, accuracyM = 5f))
        repo.feedPhoneGps(dev.gdk.bumblebee.ride.GpsFix(ts = 1_000L, lat = 0.001, lng = 0.0, speedMps = 11.1f, accuracyM = 5f))
        repo.feedPhoneGps(dev.gdk.bumblebee.ride.GpsFix(ts = 2_000L, lat = 0.002, lng = 0.0, speedMps = 11.1f, accuracyM = 5f))
        val ride = repo.finishRide(2_000L)
        assertTrue(ride.gpsDistanceM > 200)
        assertEquals(2, ride.movingSec)
        assertEquals(39, ride.maxSpeedKmh)
    }

    @Test(expected = IllegalStateException::class)
    fun `starting a ride while one is active throws`() = runTest {
        repo.startRide(0L)
        repo.startRide(1L)
    }

    @Test
    fun `refuel with rupees feeds tank km per litre and cost`() = runTest {
        repo.logRefuel(odometerKm = 1000, barsAfter = 6, litres = 8.0, cost = null, ts = 1L)
        repo.logRefuel(odometerKm = 1400, barsAfter = 6, litres = 8.0, cost = 400.0, ts = 2L)
        repo.logRefuel(odometerKm = 1800, barsAfter = 6, litres = 8.0, cost = null, ts = 3L)
        val eco = repo.bikeEconomics(bars = 4)
        assertEquals(50.0, eco.tankKmPerL!!, 0.001)
        assertEquals(1.0, eco.cost!!.lifetimeRupeesPerKm, 0.001)
        assertTrue(eco.cost!!.isPartial)
        val priced = db.fuelDao().allChronological().first { it.cost != null }
        assertEquals(400.0, priced.cost!!, 0.001)
    }

    @Test
    fun `refuel stores a rider comment`() = runTest {
        repo.logRefuel(odometerKm = 1000, barsAfter = 6, litres = 8.0, cost = 400.0, note = "HP Whitefield", ts = 1L)
        val ev = db.fuelDao().allChronological().single()
        assertEquals("HP Whitefield", ev.note)
        assertEquals(400.0, ev.cost!!, 0.001)
        assertEquals("RIDER", ev.source)
    }

    @Test
    fun `rider fill and cluster fill at the same odo stay one row`() = runTest {
        repo.logRefuel(odometerKm = 700, barsAfter = 6, litres = 8.0, cost = 400.0, note = "HP", ts = 1_000L)
        repo.startRide(100_000L)
        feedBars(startTs = 100_000L, odo = 700, bars = 2)
        feedBars(startTs = 115_000L, odo = 700, bars = 6)
        val events = db.fuelDao().allChronological()
        assertEquals(1, events.size)
        assertEquals("REFUEL", events[0].type)
        assertEquals("RIDER", events[0].source)
        assertEquals(8.0, events[0].litres!!, 0.0)
        assertEquals(2, events[0].barsBefore)
        assertEquals(6, events[0].barsAfter)
    }

    @Test
    fun `cluster fill then rider log merges pump data`() = runTest {
        repo.startRide(0L)
        feedBars(startTs = 0L, odo = 700, bars = 2)
        feedBars(startTs = 15_000L, odo = 700, bars = 6)
        repo.finishRide(30_000L)
        repo.logRefuel(odometerKm = 702, barsAfter = 6, litres = 9.0, cost = 450.0, ts = 40_000L)
        val fills = db.fuelDao().allChronological().filter { it.type == "REFUEL" }
        assertEquals(1, fills.size)
        assertEquals("RIDER", fills[0].source)
        assertEquals(9.0, fills[0].litres!!, 0.0)
        assertEquals(450.0, fills[0].cost!!, 0.0)
        assertEquals(2, fills[0].barsBefore)
    }

    @Test
    fun `rider can edit an auto detected fill`() = runTest {
        repo.startRide(0L)
        feedBars(startTs = 0L, odo = 700, bars = 2)
        feedBars(startTs = 15_000L, odo = 700, bars = 6)
        repo.finishRide(30_000L)
        val auto = db.fuelDao().allChronological().single { it.type == "REFUEL" }
        assertEquals("AUTO", auto.source)
        repo.updateFuel(auto.id, odometerKm = 700, barsAfter = 6, litres = 8.2, cost = 410.0, note = "IOC", ts = auto.ts)
        val edited = db.fuelDao().byId(auto.id)!!
        assertEquals("RIDER", edited.source)
        assertEquals(8.2, edited.litres!!, 0.0)
        assertEquals("IOC", edited.note)
    }

    @Test
    fun `rider can edit and delete a fill`() = runTest {
        repo.logRefuel(odometerKm = 1000, barsAfter = 6, litres = 8.0, cost = 400.0, note = "HP", ts = 1L)
        val id = db.fuelDao().allChronological().single().id
        repo.updateFuel(id, odometerKm = 1004, barsAfter = 5, litres = 7.5, cost = 380.0, note = "IOC", ts = 2L)
        val edited = db.fuelDao().byId(id)!!
        assertEquals(1004, edited.odometerKm)
        assertEquals(7.5, edited.litres!!, 0.0)
        assertEquals("IOC", edited.note)
        repo.deleteFuel(id)
        assertTrue(db.fuelDao().allChronological().isEmpty())
    }

    @Test
    fun `merge stitches chained sessions into one listed ride`() = runTest {
        repo.startRide(0L)
        repo.onTelemetry(frame(odo = 1000), 0L)
        repo.finishRide(10_000L)
        repo.startRide(20_000L)
        repo.onTelemetry(frame(odo = 1030), 20_000L)
        val second = repo.finishRide(30_000L)
        val first = db.rideDao().allNewestFirst().minBy { it.startTs }
        repo.mergeRides(listOf(first.id, second.id))
        val listed = repo.finishedRides()
        assertEquals(1, listed.size)
        assertEquals(first.id, listed[0].id)
        assertEquals(first.id, db.rideDao().byId(second.id)!!.mergedIntoId)
        assertNotNull(listed[0].name)
        assertTrue(repo.csvForRide(first.id).contains("1030"))
    }

    @Test
    fun `service log feeds running cost`() = runTest {
        repo.logRefuel(1000, 6, 8.0, 400.0, ts = 1L)
        repo.logRefuel(1400, 6, 8.0, 400.0, ts = 2L)
        repo.logService(1200, "oil", 800.0, "first service", 3L)
        val cost = repo.runningCost()!!
        assertEquals(1600.0, cost.totalRupees, 0.001)
        assertEquals(1, repo.allServices().size)
        repo.deleteService(repo.allServices().single().id)
        assertTrue(repo.allServices().isEmpty())
    }

    @Test
    fun `garage defaults seed papers and mixed km-date plans`() = runTest {
        repo.ensureGarageDefaults("2027-01-01", "", "2026-10-01", intervalKm = 1000, lastServiceOdoKm = 9000, now = 1L)
        val docs = repo.allDocuments()
        assertEquals(3, docs.size)
        assertEquals("2027-01-01", docs.first { it.kind == "RC" }.expiryIso)
        val plans = repo.allPlans()
        assertEquals(5, plans.size)
        repo.logService(9500, "oil", 1200.0, null, 2L, shop = "Suzuki")
        val oil = repo.allPlans().first { it.kind == "OIL" }
        assertEquals(9500, oil.lastDoneOdoKm)
        assertEquals("Suzuki", repo.allServices().single().shop)
    }

    @Test
    fun `demo rides stay out of history and calibration`() = runTest {
        RideSession(repo).run(DemoRideSource(startTs = 0L, startBars = 5).asTicks(), isDemo = true)
        assertTrue(repo.finishedRides().isEmpty())
        assertEquals(0, repo.allTimeMaxSpeed())
        val real = repo.startRide(2_000_000L)
        feedBars(2_000_000L, 15_000, 5)
        repo.finishRide(2_030_000L)
        assertEquals(1, repo.finishedRides().size)
        assertEquals(real, repo.finishedRides().single().id)
    }

    @Test
    fun `merge averages speed by moving time`() = runTest {
        repo.startRide(0L)
        repo.onTelemetry(frame(odo = 1000), 0L)
        val first = repo.finishRide(10_000L)
        repo.startRide(20_000L)
        repo.onTelemetry(frame(odo = 1030), 20_000L)
        val second = repo.finishRide(30_000L)
        db.rideDao().update(first.copy(movingSec = 3_600, avgMovingSpeedKmh = 1.0, endTs = 10_000L, odoStartKm = 0, odoEndKm = 40))
        db.rideDao().update(second.copy(movingSec = 10_800, avgMovingSpeedKmh = 1.0, endTs = 30_000L, odoStartKm = 40, odoEndKm = 280))
        val merged = repo.mergeRides(listOf(first.id, second.id))
        assertEquals(70.0, merged.avgMovingSpeedKmh, 0.01)
        repo.splitRide(first.id)
        assertEquals(2, repo.finishedRides().size)
        assertNull(db.rideDao().byId(second.id)!!.mergedIntoId)
    }

    @Test
    fun `unfinished ride is rebuilt after a simulated process death`() = runTest {
        repo.startRide(0L)
        repo.onTelemetry(frame(odo = 1000), 0L)
        repo.onTelemetry(frame(odo = 1001), 5_000L)
        val id = repo.activeRideId!!
        val revived = RideCaptureRepository(db)
        revived.recoverUnfinishedRides(now = 6_000L)
        assertEquals(id, revived.activeRideId)
        assertTrue(revived.isRecording)
        val later = RideCaptureRepository(db)
        later.recoverUnfinishedRides(now = 6_000L + 6 * 60 * 1000L)
        assertNull(later.activeRideId)
        assertNotNull(db.rideDao().byId(id)!!.endTs)
    }

    @Test
    fun `demo ride does not leave a phantom refuel`() = runTest {
        RideSession(repo).run(DemoRideSource(startTs = 0L, startBars = 2).asTicks(), isDemo = true)
        repo.startRide(2_000_000L)
        feedBars(2_000_000L, 15_000, 5)
        val fills = db.fuelDao().allChronological().filter { it.type == "REFUEL" }
        assertTrue(fills.isEmpty())
        assertTrue(db.fuelDao().allChronological().none { it.rideId != null && db.rideDao().byId(it.rideId!!)?.isDemo == true })
    }

    @Test
    fun `two unfinished rides keep only the newest`() = runTest {
        repo.startRide(0L)
        repo.onTelemetry(frame(odo = 1000), 0L)
        val first = repo.activeRideId!!
        val second = db.rideDao().insert(RideEntity(startTs = 10_000L, rideState = RIDE_ACTIVE))
        db.telemetryDao().insert(
            TelemetrySampleEntity(
                rideId = second, ts = 10_000L, speedKmh = 20, odometerKm = 1100,
                tripAKm = 0.0, tripBKm = 0.0, fuelBars = 4, fuelEconKml = null,
            ),
        )
        val revived = RideCaptureRepository(db)
        revived.recoverUnfinishedRides(now = 11_000L)
        assertEquals(second, revived.activeRideId)
        assertNotNull(db.rideDao().byId(first)!!.endTs)
        assertNull(db.rideDao().byId(second)!!.endTs)
    }

    @Test
    fun `recovered bike ride ignores walking GPS`() = runTest {
        repo.startRide(0L)
        repo.onTelemetry(frame(odo = 1000), 0L)
        val id = repo.activeRideId!!
        val revived = RideCaptureRepository(db)
        revived.recoverUnfinishedRides(now = 1_000L)
        revived.feedPhoneGps(
            dev.gdk.bumblebee.ride.GpsFix(ts = 2_000L, lat = 0.0, lng = 0.0, speedMps = 11f, accuracyM = 5f),
        )
        revived.feedPhoneGps(
            dev.gdk.bumblebee.ride.GpsFix(ts = 3_000L, lat = 0.001, lng = 0.0, speedMps = 11f, accuracyM = 5f),
        )
        assertEquals(0, db.trackDao().countForRide(id))
    }

    @Test
    fun `empty rides are deleted`() = runTest {
        repo.startRide(0L)
        repo.finishRide(1_000L)
        assertTrue(repo.finishedRides().isEmpty())
        assertNull(repo.lastFinished())
        assertTrue(db.rideDao().allNewestFirst().isEmpty())
    }

    @Test
    fun `fuel detector last bars survive a new repository`() = runTest {
        repo.startRide(0L)
        feedBars(0L, 700, 2)
        repo.finishRide(20_000L)
        assertEquals("2", db.calibrationDao().get(RideCaptureRepository.FUEL_STABLE_BARS_KEY)?.value)
        val next = RideCaptureRepository(db)
        next.restoreFuelDetector()
        next.startRide(100_000L)
        feedBarsOn(next, 100_000L, 700, 6)
        val fills = db.fuelDao().allChronological().filter { it.type == "REFUEL" }
        assertEquals(1, fills.size)
        assertEquals(2, fills[0].barsBefore)
        assertEquals(6, fills[0].barsAfter)
    }

    private fun frame(odo: Int) = dev.gdk.bumblebee.protocol.TelemetryFrame(
        speedKmh = 20, odometerKm = odo, tripAKm = 0.0, tripBKm = 0.0, fuelBars = 4, fuelEconKml = null,
    )

    private suspend fun feedBars(startTs: Long, odo: Int, bars: Int) = feedBarsOn(repo, startTs, odo, bars)

    private suspend fun feedBarsOn(target: RideCaptureRepository, startTs: Long, odo: Int, bars: Int) {
        for (i in 0 until 3) {
            target.onTelemetry(
                dev.gdk.bumblebee.protocol.TelemetryFrame(
                    speedKmh = 0,
                    odometerKm = odo,
                    tripAKm = 0.0,
                    tripBKm = 0.0,
                    fuelBars = bars,
                    fuelEconKml = null,
                ),
                startTs + i * 5_000L,
            )
        }
    }
}
