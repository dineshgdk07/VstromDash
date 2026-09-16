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

    @Test(expected = IllegalStateException::class)
    fun `starting a ride while one is active throws`() = runTest {
        repo.startRide(0L)
        repo.startRide(1L)
    }
}
