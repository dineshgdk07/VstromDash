package dev.gdk.bumblebee.transfer

import androidx.test.core.app.ApplicationProvider
import dev.gdk.bumblebee.ble.android.BikePrefs
import dev.gdk.bumblebee.data.local.BumblebeeDb
import dev.gdk.bumblebee.data.local.FuelEventEntity
import dev.gdk.bumblebee.data.local.RIDE_ACTIVE
import dev.gdk.bumblebee.data.local.RideEntity
import dev.gdk.bumblebee.data.local.SnapshotArchive
import dev.gdk.bumblebee.data.local.TrackPointEntity
import dev.gdk.bumblebee.snapshot.SnapshotFile
import dev.gdk.bumblebee.snapshot.SnapshotRide
import dev.gdk.bumblebee.snapshot.SnapshotTrack
import dev.gdk.bumblebee.snapshot.SnapshotWidget
import dev.gdk.bumblebee.snapshot.writeSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
class TransferRepositoryTest {

    private val widget = object : WidgetStore {
        override fun read() = SnapshotWidget(null, null, null, "", 0.0, 0.0, "", false, "garage", 0L)
        override fun write(widget: SnapshotWidget) = Unit
    }

    @Test
    fun `preview counts the ride the distance and the fill`() = runTest {
        val repo = repo()
        repo.db.rideDao().insert(RideEntity(startTs = 1L, endTs = 2L, gpsDistanceM = 2000.0))
        repo.db.fuelDao().insert(
            FuelEventEntity(ts = 1L, rideId = null, odometerKm = 10, barsBefore = 1, barsAfter = 5, type = "REFUEL"),
        )
        val preview = repo.repository.preview()
        assertEquals(1, preview.rideCount)
        assertEquals(2, preview.distanceKm)
        assertEquals(1, preview.fillCount)
        assertTrue(preview.issues.isEmpty())
        repo.db.close()
    }

    @Test
    fun `an open ride is left out of the pack, which still saves`() = runTest {
        val repo = repo()
        repo.db.rideDao().insert(RideEntity(startTs = 1L, endTs = 2L, gpsDistanceM = 1000.0))
        repo.db.rideDao().insert(RideEntity(startTs = 5L, rideState = RIDE_ACTIVE))
        val preview = repo.repository.preview()
        assertTrue(preview.issues.single().message.startsWith("The ride being recorded now isn't included"))
        val out = ByteArrayOutputStream()
        val steps = mutableListOf<String>()
        val bytes = repo.repository.writeTo(out) { step, _ -> steps += step }
        assertEquals(out.size().toLong(), bytes)
        assertTrue(bytes > 0)
        assertTrue(steps.any { it.startsWith("Packing rides") })
        val read = dev.gdk.bumblebee.snapshot.readSnapshot(ByteArrayInputStream(out.toByteArray())) as dev.gdk.bumblebee.snapshot.SnapshotRead.Ready
        assertEquals(listOf(1L), read.bundle.rides.map { it.startTs })
        repo.db.close()
    }

    @Test
    fun `inspect holds a clashing ride and commit can keep or overwrite it`() = runTest {
        val here = repo()
        val rideId = here.db.rideDao().insert(RideEntity(startTs = 10L, endTs = 11L))
        here.db.trackDao().insert(point(rideId, 1.0))
        val incoming = here.repository.preview().bundle.copy(
            rides = listOf(
                SnapshotRide(id = 50, startTs = 10L),
                SnapshotRide(id = 51, startTs = 20L, gpsDistanceM = 1000.0),
            ),
            tracks = listOf(pointSnapshot(50, 2.0)),
            prefs = here.repository.preview().bundle.prefs.copy(bikeMac = "AA:BB"),
            files = emptyList<SnapshotFile>(),
        )
        val zip = ByteArrayOutputStream().also { writeSnapshot(it, incoming, "debug", 1L) }.toByteArray()
        val inspected = here.repository.inspect(ByteArrayInputStream(zip))
        assertTrue(inspected is BringIn.Held)
        assertEquals(1.0, here.db.trackDao().forRide(rideId).single().lat, 0.0)

        here.repository.commit(incoming, overwriteClashes = false)
        assertEquals(1.0, here.db.trackDao().forRide(rideId).single().lat, 0.0)
        assertEquals(2, here.db.rideDao().allNewestFirst().size)
        here.db.close()

        val replaced = repo()
        val keptId = replaced.db.rideDao().insert(RideEntity(startTs = 10L, endTs = 11L))
        replaced.db.trackDao().insert(point(keptId, 1.0))
        replaced.repository.commit(incoming, overwriteClashes = true)
        val clashing = replaced.db.rideDao().allNewestFirst().first { it.startTs == 10L }
        assertEquals(2.0, replaced.db.trackDao().forRide(clashing.id).single().lat, 0.0)
        assertEquals("AA:BB", replaced.prefs.bikeMac)
        replaced.db.close()
    }

    private fun point(rideId: Long, lat: Double) = TrackPointEntity(
        rideId = rideId, ts = 1L, lat = lat, lng = 0.0, altitudeM = null, bearingDeg = null, speedMps = null, accuracyM = null,
    )

    private fun pointSnapshot(rideId: Long, lat: Double) = SnapshotTrack(
        id = 1, rideId = rideId, ts = 1L, lat = lat, lng = 0.0, altitudeM = null, bearingDeg = null, speedMps = null, accuracyM = null,
    )

    private fun repo(): Fixture {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("bumblebee_ble", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val db = BumblebeeDb.inMemory(context)
        val garage = File(kotlin.io.path.createTempDirectory("garage").toFile(), "root").apply { mkdirs() }
        val prefs = BikePrefs(context)
        val repository = TransferRepository(SnapshotArchive(db, garage), prefs, widget, "debug")
        return Fixture(db, prefs, repository)
    }

    private data class Fixture(
        val db: BumblebeeDb,
        val prefs: BikePrefs,
        val repository: TransferRepository,
    )
}
