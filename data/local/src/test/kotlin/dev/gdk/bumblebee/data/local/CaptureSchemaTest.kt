package dev.gdk.bumblebee.data.local

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CaptureSchemaTest {

    private lateinit var db: BumblebeeDb

    @Before
    fun setUp() {
        db = BumblebeeDb.inMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `ride lifecycle - insert open ride, finalize, read back`() = runTest {
        val rideId = db.rideDao().insert(RideEntity(startTs = 1_000L))
        val open = db.rideDao().byId(rideId)!!
        assertNull(open.endTs)

        db.rideDao().update(
            open.copy(
                endTs = 601_000L, odoStartKm = 15_000, odoEndKm = 15_007,
                gpsDistanceM = 7_150.0, movingSec = 500, idleSec = 95,
                maxSpeedKmh = 60, avgMovingSpeedKmh = 51.4,
                fuelBarsStart = 5, fuelBarsEnd = 4,
                speedHistogramJson = "{\"IDLE\":95}",
            )
        )
        val closed = db.rideDao().byId(rideId)!!
        assertEquals(601_000L, closed.endTs)
        assertEquals(15_007, closed.odoEndKm)
        assertNull(closed.remoteId) // sync fields exist and default to null (D-016)
    }

    @Test
    fun `telemetry and track points are archived per ride and counted`() = runTest {
        val rideId = db.rideDao().insert(RideEntity(startTs = 0L))
        db.telemetryDao().insertAll(
            (0 until 10).map {
                TelemetrySampleEntity(
                    rideId = rideId, ts = it * 5_000L, speedKmh = 40,
                    odometerKm = 15_000, tripAKm = 0.0, tripBKm = 0.0,
                    fuelBars = 5, fuelEconKml = 42.0,
                )
            }
        )
        db.trackDao().insertAll(
            (0 until 50).map {
                TrackPointEntity(
                    rideId = rideId, ts = it * 1_000L, lat = 12.97, lng = 77.59,
                    altitudeM = 900.0, bearingDeg = 0f, speedMps = 11f, accuracyM = 5f,
                )
            }
        )
        assertEquals(10, db.telemetryDao().countForRide(rideId))
        assertEquals(50, db.trackDao().countForRide(rideId))
    }

    @Test
    fun `fuel events store both types and read back chronologically`() = runTest {
        db.fuelDao().insert(FuelEventEntity(ts = 2_000L, rideId = null, odometerKm = 15_100, barsBefore = 4, barsAfter = 3, type = "BAR_DROP"))
        db.fuelDao().insert(FuelEventEntity(ts = 1_000L, rideId = null, odometerKm = 15_050, barsBefore = 5, barsAfter = 4, type = "BAR_DROP"))
        val events = db.fuelDao().allChronological()
        assertEquals(listOf(1_000L, 2_000L), events.map { it.ts })
        assertNull(events[0].litres) // rider inputs are optional
    }

    @Test
    fun `daily rollup upserts by date`() = runTest {
        db.rollupDao().upsert(DailyRollupEntity(date = "2026-07-28", km = 7.0, rides = 1, movingSec = 500, maxSpeedKmh = 60, fuelBarsUsed = 1))
        db.rollupDao().upsert(DailyRollupEntity(date = "2026-07-28", km = 19.0, rides = 2, movingSec = 1400, maxSpeedKmh = 84, fuelBarsUsed = 1))
        val day = db.rollupDao().byDate("2026-07-28")!!
        assertEquals(19.0, day.km, 0.0)
        assertEquals(2, day.rides)
    }

    @Test
    fun `calibration is a string key-value store`() = runTest {
        db.calibrationDao().put(CalibrationEntity(key = "km_per_bar", value = "{\"4\":48.0}"))
        db.calibrationDao().put(CalibrationEntity(key = "km_per_bar", value = "{\"4\":44.0}"))
        assertEquals("{\"4\":44.0}", db.calibrationDao().get("km_per_bar")!!.value)
        assertNull(db.calibrationDao().get("missing"))
    }
}
