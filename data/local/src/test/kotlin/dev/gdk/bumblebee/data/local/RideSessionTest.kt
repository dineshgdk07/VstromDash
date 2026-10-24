package dev.gdk.bumblebee.data.local

import androidx.test.core.app.ApplicationProvider
import dev.gdk.bumblebee.ride.DemoRideSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RideSessionTest {

    private lateinit var db: BumblebeeDb
    private lateinit var session: RideSession

    @Before
    fun setUp() {
        db = BumblebeeDb.inMemory(ApplicationProvider.getApplicationContext())
        session = RideSession(RideCaptureRepository(db))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `demo ticks capture the full archive`() = runTest {
        val demo = DemoRideSource(startOdoKm = 15_000, startBars = 5, startTs = 0L)
        val ticks = demo.asTicks()
        assertEquals(720, ticks.size) // 120 telemetry + 600 gps
        val ride = session.run(ticks)
        assertEquals(120, db.telemetryDao().countForRide(ride.id))
        assertEquals(600, db.trackDao().countForRide(ride.id))
        assertEquals(1, db.fuelDao().allChronological().size)
        assertTrue(ride.movingSec > ride.idleSec)
    }
}
