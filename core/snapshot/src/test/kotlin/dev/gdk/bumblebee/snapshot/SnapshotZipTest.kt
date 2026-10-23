package dev.gdk.bumblebee.snapshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SnapshotZipTest {

    @Test
    fun `round trip keeps the ride the segment and the scan`() {
        val scan = "scan-bytes".toByteArray()
        val bundle = sample(scan)
        val out = ByteArrayOutputStream()
        writeSnapshot(out, bundle, "debug", 1_700_000_000_000L)
        val read = readSnapshot(ByteArrayInputStream(out.toByteArray()))
        val ready = read as SnapshotRead.Ready
        assertEquals(42L, ready.bundle.rides.single().startTs)
        assertEquals(7L, ready.bundle.segments.single().rideId)
        assertTrue(scan.contentEquals(ready.bundle.files.single().bytes))
    }

    @Test
    fun `flipping rides json fails the checksum`() {
        val out = ByteArrayOutputStream()
        writeSnapshot(out, sample("ok".toByteArray()), "release", 1L)
        val flipped = flipEntry(out.toByteArray(), "rides.json")
        val read = readSnapshot(ByteArrayInputStream(flipped))
        assertEquals(
            "The file is damaged. The checksum does not match.",
            (read as SnapshotRead.Rejected).message,
        )
    }

    @Test
    fun `zip entry that escapes files is rejected`() {
        val dir = Files.createTempDirectory("snapshot-zip")
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { out ->
            out.putNextEntry(ZipEntry("files/../../tmp/pwned"))
            out.write(byteArrayOf(1))
            out.closeEntry()
        }
        val read = readSnapshot(ByteArrayInputStream(zip.toByteArray()))
        val rejected = read as SnapshotRead.Rejected
        assertEquals("This file is not a Bumblebee snapshot.", rejected.message)
        assertTrue(rejected.log.any { it.contains("files/../../tmp/pwned") })
        assertFalse(Files.exists(dir.resolve("pwned")))
        assertFalse(Files.exists(dir.resolve("tmp").resolve("pwned")))
    }

    @Test
    fun `document without its scan is rejected`() {
        val doc = sample("x".toByteArray()).documents.single().copy(filePath = "files/rc.pdf")
        val bundle = sample("x".toByteArray()).copy(documents = listOf(doc), files = emptyList())
        val out = ByteArrayOutputStream()
        writeSnapshot(out, bundle, "debug", 1L)
        val read = readSnapshot(ByteArrayInputStream(out.toByteArray()))
        assertEquals("A paper is missing its scan.", (read as SnapshotRead.Rejected).message)
    }
}

private fun sample(scan: ByteArray): SnapshotBundle {
    val prefs = SnapshotPrefs(
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
    )
    return SnapshotBundle(
        rides = listOf(SnapshotRide(id = 7, startTs = 42L)),
        segments = listOf(SnapshotSegment(id = 1, rideId = 7, startTs = 42L)),
        documents = listOf(
            SnapshotDocument(
                id = 3,
                title = "RC",
                kind = "RC",
                filePath = "files/rc.pdf",
                createdTs = 5L,
            ),
        ),
        prefs = prefs,
        files = listOf(SnapshotFile("rc.pdf", scan)),
    )
}

private fun flipEntry(zipBytes: ByteArray, name: String): ByteArray {
    val entries = linkedMapOf<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            entries[entry.name] = zip.readBytes()
            entry = zip.nextEntry
        }
    }
    val target = entries.getValue(name)
    target[0] = (target[0] + 1).toByte()
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        entries.forEach { (entryName, bytes) ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}
