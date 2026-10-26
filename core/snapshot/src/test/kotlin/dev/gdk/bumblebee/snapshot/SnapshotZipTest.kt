package dev.gdk.bumblebee.snapshot

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.MessageDigest
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
    fun `ride places and weather round trip`() {
        val ride = SnapshotRide(
            id = 7,
            startTs = 42L,
            startPlace = "Bengaluru",
            endPlace = "Nandi",
            weatherTempC = 22.0,
            weatherCode = 0,
            climbM = 80.0,
        )
        val out = ByteArrayOutputStream()
        writeSnapshot(out, sample("x".toByteArray()).copy(rides = listOf(ride)), "debug", 1L)
        val ready = readSnapshot(ByteArrayInputStream(out.toByteArray())) as SnapshotRead.Ready
        val back = ready.bundle.rides.single()
        assertEquals("Bengaluru", back.startPlace)
        assertEquals("Nandi", back.endPlace)
        assertEquals(22.0, back.weatherTempC!!, 0.0)
        assertEquals(0, back.weatherCode)
        assertEquals(80.0, back.climbM!!, 0.0)
    }

    @Test
    fun `v8 ride json without new keys reads them as null`() {
        val out = ByteArrayOutputStream()
        writeSnapshot(out, sample("x".toByteArray()), "debug", 1L)
        val ready = readSnapshot(ByteArrayInputStream(stripNewRideKeys(out.toByteArray()))) as SnapshotRead.Ready
        val ride = ready.bundle.rides.single()
        assertNull(ride.startPlace)
        assertNull(ride.endPlace)
        assertNull(ride.weatherTempC)
        assertNull(ride.weatherCode)
        assertNull(ride.climbM)
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
    fun `two scans cannot share one archive path`() {
        val bundle = sample("a".toByteArray()).copy(
            files = listOf(
                SnapshotFile("documents/1_bill.pdf", "a".toByteArray()),
                SnapshotFile("documents/1_bill.pdf", "b".toByteArray()),
            ),
        )
        var threw = false
        try {
            writeSnapshot(ByteArrayOutputStream(), bundle, "debug", 1L)
        } catch (_: IllegalStateException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `an oversized entry is rejected`() {
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { out ->
            out.putNextEntry(ZipEntry("rides.json"))
            out.write(ByteArray(32))
            out.closeEntry()
        }
        val read = readSnapshot(ByteArrayInputStream(zip.toByteArray()), maxUncompressedBytes = 8)
        assertEquals("This snapshot is too large to import.", (read as SnapshotRead.Rejected).message)
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

private fun stripNewRideKeys(zipBytes: ByteArray): ByteArray {
    val entries = linkedMapOf<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            entries[entry.name] = zip.readBytes()
            entry = zip.nextEntry
        }
    }
    val rides = JSONArray(String(entries.getValue("rides.json"), Charsets.UTF_8))
    val ride = rides.getJSONObject(0)
    listOf("startPlace", "endPlace", "weatherTempC", "weatherCode", "climbM").forEach { ride.remove(it) }
    entries["rides.json"] = rides.toString().toByteArray(Charsets.UTF_8)
    // Packs from before settings were added have no settings file.
    entries.remove("settings.json")
    val digest = MessageDigest.getInstance("SHA-256")
    listOf(
        "rides", "ride_segments", "telemetry_samples", "track_points", "fuel_events",
        "daily_rollups", "calibration", "service_logs", "bike_documents", "maintenance_plans",
    ).forEach { digest.update(entries.getValue("$it.json")) }
    digest.update(entries.getValue("prefs.json"))
    entries.keys.filter { it.startsWith("files/") }.sorted().forEach { digest.update(entries.getValue(it)) }
    val sha = digest.digest().joinToString("") { "%02x".format(it) }
    val manifest = JSONObject(String(entries.getValue("manifest.json"), Charsets.UTF_8))
    manifest.put("schema", 8)
    manifest.put("sha256", sha)
    entries["manifest.json"] = manifest.toString().toByteArray(Charsets.UTF_8)
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

class SnapshotSpoolTest {
    @Test fun `scans spool to disk and read back the same`() {
        val dir = java.nio.file.Files.createTempDirectory("spool").toFile()
        try {
            val scan = ByteArray(200_000) { (it % 251).toByte() }
            val bundle = sample(scan)
            val out = java.io.ByteArrayOutputStream()
            writeSnapshot(out, bundle, "debug", 1L)
            val read = readSnapshot(java.io.ByteArrayInputStream(out.toByteArray()), spoolDir = dir) as SnapshotRead.Ready
            val file = read.bundle.files.single()
            org.junit.Assert.assertEquals("rc.pdf", file.relativePath)
            org.junit.Assert.assertArrayEquals(scan, file.bytes)
            // On disk, not in the parsed entries.
            org.junit.Assert.assertEquals(1, dir.listFiles()!!.size)
            org.junit.Assert.assertEquals(scan.size.toLong(), dir.listFiles()!!.single().length())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun `a spooled pack over the cap is refused`() {
        val dir = java.nio.file.Files.createTempDirectory("spool").toFile()
        try {
            val bundle = sample(ByteArray(50_000))
            val out = java.io.ByteArrayOutputStream()
            writeSnapshot(out, bundle, "debug", 1L)
            val read = readSnapshot(java.io.ByteArrayInputStream(out.toByteArray()), maxUncompressedBytes = 10_000, spoolDir = dir)
            org.junit.Assert.assertTrue(read is SnapshotRead.Rejected)
        } finally {
            dir.deleteRecursively()
        }
    }
}

class SnapshotSettingsTest {
    @Test fun `settings round trip with their types, and progress is reported`() {
        val settings = SnapshotSettings(
            mapOf(
                "bumblebee_bike" to mapOf("variant" to "YELLOW", "accent" to 7, "plate" to "KA01", "since" to 1_700_000_000_000L, "tank" to 12.5f, "on" to true),
                "bumblebee_journey" to mapOf("places" to setOf("Home", "Work")),
            ),
        )
        val bundle = sample("x".toByteArray()).copy(settings = settings)
        val steps = mutableListOf<String>()
        val out = ByteArrayOutputStream()
        writeSnapshot(out, bundle, "debug", 1L) { step, _, _ -> steps += step }
        val read = readSnapshot(ByteArrayInputStream(out.toByteArray())) { step, _, _ -> steps += step } as SnapshotRead.Ready
        assertEquals(settings, read.bundle.settings)
        assertTrue(steps.any { it.startsWith("Packing rides") })
        assertTrue(steps.any { it == "Checking the checksum" })
    }

    @Test fun `a NaN average doesn't break the export`() {
        val bundle = sample("x".toByteArray()).let { it.copy(rides = listOf(it.rides.single().copy(avgMovingSpeedKmh = Double.NaN, gpsDistanceM = Double.POSITIVE_INFINITY))) }
        val out = ByteArrayOutputStream()
        writeSnapshot(out, bundle, "debug", 1L)
        val ride = (readSnapshot(ByteArrayInputStream(out.toByteArray())) as SnapshotRead.Ready).bundle.rides.single()
        assertEquals(0.0, ride.avgMovingSpeedKmh, 0.0)
        assertEquals(0.0, ride.gpsDistanceM, 0.0)
    }
}
