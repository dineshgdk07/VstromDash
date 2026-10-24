package dev.gdk.bumblebee.ble.android

import androidx.test.core.app.ApplicationProvider
import dev.gdk.bumblebee.data.local.BumblebeeDb
import dev.gdk.bumblebee.data.local.RideCaptureRepository
import dev.gdk.bumblebee.protocol.FRAME_LEN
import dev.gdk.bumblebee.protocol.TelemetryFrame
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TelemetrySinkTest {

    private lateinit var db: BumblebeeDb
    private lateinit var repo: RideCaptureRepository
    private lateinit var sink: TelemetrySink

    private fun frame(speed: Int = 40, odo: Int = 15_000, bars: Int? = 5): ByteArray =
        TelemetryFrame(
            speedKmh = speed,
            odometerKm = odo,
            tripAKm = 1.0,
            tripBKm = 2.0,
            fuelBars = bars,
            fuelEconKml = 42.0,
        ).encode()

    @Before
    fun setUp() {
        db = BumblebeeDb.inMemory(ApplicationProvider.getApplicationContext())
        repo = RideCaptureRepository(db)
        sink = TelemetrySink(repo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `notify before ready still opens a ride`() = runTest {
        sink.onNotify(frame(), ts = 1_000L)
        assertEquals(1_000L, db.rideDao().byId(repo.activeRideId!!)!!.startTs)
        assertEquals(1, db.telemetryDao().countForRide(repo.activeRideId!!))
    }

    @Test
    fun `ready then two telemetry frames are archived`() = runTest {
        sink.onReady(ts = 0L)
        val rideId = repo.activeRideId!!
        sink.onNotify(frame(speed = 40, odo = 15_000), ts = 5_000L)
        sink.onNotify(frame(speed = 50, odo = 15_001), ts = 10_000L)
        assertEquals(2, db.telemetryDao().countForRide(rideId))
    }

    @Test
    fun `disconnect finalizes the ride`() = runTest {
        sink.onReady(ts = 0L)
        sink.onNotify(frame(), ts = 5_000L)
        val rideId = repo.activeRideId!!
        sink.onDisconnected(ts = 20_000L)
        assertNull(repo.activeRideId)
        assertEquals(20_000L, db.rideDao().byId(rideId)!!.endTs)
    }

    @Test
    fun `malformed buffer does not throw`() = runTest {
        sink.onReady(ts = 0L)
        sink.onNotify(ByteArray(FRAME_LEN), ts = 1L)
        sink.onNotify(byteArrayOf(1, 2, 3), ts = 2L)
        assertEquals(0, db.telemetryDao().countForRide(repo.activeRideId!!))
    }
}
