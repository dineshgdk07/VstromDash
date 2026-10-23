package dev.gdk.bumblebee.data.local

import androidx.test.core.app.ApplicationProvider
import dev.gdk.bumblebee.snapshot.SnapshotDocument
import dev.gdk.bumblebee.snapshot.SnapshotFile
import dev.gdk.bumblebee.snapshot.SnapshotFormat
import dev.gdk.bumblebee.snapshot.SnapshotPrefs
import dev.gdk.bumblebee.snapshot.SnapshotRead
import dev.gdk.bumblebee.snapshot.SnapshotRide
import dev.gdk.bumblebee.snapshot.SnapshotTrack
import dev.gdk.bumblebee.snapshot.SnapshotWidget
import dev.gdk.bumblebee.snapshot.readSnapshot
import dev.gdk.bumblebee.snapshot.writeSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class SnapshotArchiveTest {

    private lateinit var db: BumblebeeDb
    private lateinit var garage: File

    @Before
    fun setUp() {
        db = BumblebeeDb.inMemory(ApplicationProvider.getApplicationContext())
        garage = File(createTempDir(), "garage").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `schema constant matches the open database`() {
        val archive = SnapshotArchive(db, garage)
        assertEquals(SnapshotFormat.APP_SCHEMA, archive.schemaVersion())
        assertEquals(archive.schemaVersion(), db.openHelper.readableDatabase.version)
    }

    @Test
    fun `round trip remaps the segment onto the new ride`() = runTest {
        val scan = File(garage, "rc.pdf").apply { writeBytes("scan".toByteArray()) }
        val rideId = db.rideDao().insert(ride(startTs = 42L))
        db.segmentDao().insert(RideSegmentEntity(rideId = rideId, startTs = 42L))
        db.trackDao().insert(track(rideId, lat = 3.0))
        db.documentDao().insert(
            BikeDocumentEntity(
                title = "RC",
                kind = "RC",
                filePath = scan.absolutePath,
                createdTs = 1L,
            ),
        )
        val captured = SnapshotArchive(db, garage).capture()
        val bytes = ByteArrayOutputStream().also { writeSnapshot(it, captured.bundle, "debug", 1L) }.toByteArray()
        val ready = readSnapshot(ByteArrayInputStream(bytes)) as SnapshotRead.Ready
        val destDb = BumblebeeDb.inMemory(ApplicationProvider.getApplicationContext())
        val destGarage = File(createTempDir(), "garage").apply { mkdirs() }
        try {
            SnapshotArchive(destDb, destGarage).apply(ready.bundle, overwriteClashes = false)
            val ride = destDb.rideDao().allNewestFirst().single()
            assertEquals(1, destDb.trackDao().countForRide(ride.id))
            assertEquals(ride.id, destDb.segmentDao().forRide(ride.id).single().rideId)
            val doc = destDb.documentDao().all().single()
            assertArrayEquals("scan".toByteArray(), File(doc.filePath!!).readBytes())
        } finally {
            destDb.close()
        }
    }

    @Test
    fun `overwrite replaces tracks and keep does not`() = runTest {
        val replaced = pair()
        replaced.second.apply(incomingTracks(), overwriteClashes = true)
        val keptRide = replaced.first.rideDao().allNewestFirst().first { it.startTs == 10L }
        assertEquals(2.0, replaced.first.trackDao().forRide(keptRide.id).single().lat, 0.0)
        assertEquals(2, replaced.first.rideDao().allNewestFirst().size)
        replaced.first.close()

        val kept = pair()
        kept.second.apply(incomingTracks(), overwriteClashes = false)
        val ride = kept.first.rideDao().allNewestFirst().first { it.startTs == 10L }
        assertEquals(1.0, kept.first.trackDao().forRide(ride.id).single().lat, 0.0)
        val rides = kept.first.rideDao().allNewestFirst()
        assertEquals(2, rides.size)
        val lats = rides.flatMap { kept.first.trackDao().forRide(it.id) }.map { it.lat }
        assertTrue(2.0 !in lats)
        kept.first.close()
    }

    @Test
    fun `rejected snapshot does not write`() = runTest {
        db.rideDao().insert(ride(startTs = 1L))
        val bytes = ByteArrayOutputStream().also {
            writeSnapshot(it, SnapshotArchive(db, garage).capture().bundle, "debug", 1L)
        }.toByteArray()
        val read = readSnapshot(ByteArrayInputStream(flip(bytes)))
        assertTrue(read is SnapshotRead.Rejected)
        assertEquals(1, db.rideDao().allNewestFirst().size)

        val parent = createTempDir()
        val sibling = File(parent, "keep.pdf").apply { writeBytes("original".toByteArray()) }
        val blocked = File(parent, "garage").apply { writeBytes("not-a-directory".toByteArray()) }
        val archive = SnapshotArchive(db, blocked)
        var threw = false
        try {
            archive.apply(bundleWithScan(), overwriteClashes = true)
        } catch (_: Exception) {
            threw = true
        }
        assertTrue(threw)
        assertArrayEquals("original".toByteArray(), sibling.readBytes())
    }

    private suspend fun pair(): Pair<BumblebeeDb, SnapshotArchive> {
        val database = BumblebeeDb.inMemory(ApplicationProvider.getApplicationContext())
        val root = File(createTempDir(), "garage").apply { mkdirs() }
        val id = database.rideDao().insert(ride(startTs = 10L))
        database.trackDao().insert(track(id, lat = 1.0))
        return database to SnapshotArchive(database, root)
    }

    private fun incomingTracks() = prefsBundle().copy(
        rides = listOf(SnapshotRide(id = 50, startTs = 10L), SnapshotRide(id = 51, startTs = 20L)),
        tracks = listOf(
            SnapshotTrack(id = 1, rideId = 50, ts = 10L, lat = 2.0, lng = 0.0, altitudeM = null, bearingDeg = null, speedMps = null, accuracyM = null),
        ),
    )

    private fun bundleWithScan() = prefsBundle().copy(
        documents = listOf(SnapshotDocument(id = 1, title = "RC", kind = "RC", filePath = "files/rc.pdf", createdTs = 1L)),
        files = listOf(SnapshotFile("rc.pdf", "new".toByteArray())),
    )

    private fun prefsBundle() = dev.gdk.bumblebee.snapshot.SnapshotBundle(
        prefs = SnapshotPrefs(
            bikeMac = null,
            riderName = "RIDER",
            serviceIntervalKm = 0,
            lastServiceOdoKm = null,
            rcExpiry = "",
            insuranceExpiry = "",
            pucExpiry = "",
            mapsNavToCluster = true,
            tankCapacityL = 12f,
            remindersEnabled = true,
            remindDays = 14,
            remindKm = 200,
            pauseWindowMin = 45,
            autoConnect = true,
            skipAutoConnectDate = "",
            crashSensitivity = "NORMAL",
            emergencyContacts = "",
            bloodGroup = "",
            medicalNote = "",
            lastReminderStamp = "",
            widget = SnapshotWidget(null, null, null, "", 0.0, 0.0, "", false, "garage", 0L),
        ),
    )

    private fun ride(startTs: Long) = RideEntity(startTs = startTs, endTs = startTs + 1)

    private fun track(rideId: Long, lat: Double) = TrackPointEntity(
        rideId = rideId,
        ts = 1L,
        lat = lat,
        lng = 0.0,
        altitudeM = null,
        bearingDeg = null,
        speedMps = null,
        accuracyM = null,
    )

    private fun flip(zipBytes: ByteArray): ByteArray {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        entries.getValue("rides.json")[0] = (entries.getValue("rides.json")[0] + 1).toByte()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
