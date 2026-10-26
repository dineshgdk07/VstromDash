package dev.gdk.bumblebee.snapshot

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

sealed class SnapshotRead {
    data class Ready(val bundle: SnapshotBundle, val manifest: SnapshotManifest) : SnapshotRead()
    data class Rejected(val message: String, val log: List<String>) : SnapshotRead()
}

fun writeSnapshot(out: OutputStream, bundle: SnapshotBundle, buildType: String, exportedAt: Long) {
    val digest = MessageDigest.getInstance("SHA-256")
    val seen = mutableSetOf<String>()
    ZipOutputStream(out).use { zip ->
        fun put(name: String, bytes: ByteArray) {
            if (!seen.add(name)) error("duplicate scan $name")
            digest.update(bytes)
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
        for (section in SnapshotSections.ORDER) {
            put("$section.json", sectionJson(section, bundle).toByteArray(Charsets.UTF_8))
        }
        put("prefs.json", bundle.prefs.toJson().toString().toByteArray(Charsets.UTF_8))
        bundle.files.sortedBy { it.relativePath }.forEach { file ->
            put("files/${file.relativePath}", file.bytes)
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        val counts = sectionCounts(bundle) + (SnapshotSections.PREFS to 1)
        val manifest = JSONObject()
            .put("magic", SnapshotFormat.MAGIC)
            .put("format", SnapshotFormat.FORMAT)
            .put("schema", SnapshotFormat.APP_SCHEMA)
            .put("exportedAt", exportedAt)
            .put("buildType", buildType)
            .put("counts", JSONObject(counts))
            .put("sha256", sha)
        zip.putNextEntry(ZipEntry("manifest.json"))
        zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}

/**
 * Reads a snapshot. With [spoolDir], document scans are written there as they're read rather than
 * kept in memory, so a pack with many scans can't run the phone out of memory; the caller clears
 * the folder once the import is done or abandoned.
 */
fun readSnapshot(input: InputStream, maxUncompressedBytes: Long = MAX_SNAPSHOT_BYTES, spoolDir: File? = null): SnapshotRead {
    val entries = linkedMapOf<String, ByteArray>()
    val spooled = linkedMapOf<String, File>()
    var used = 0L
    try {
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!allowedZipName(name)) {
                    return SnapshotRead.Rejected("This file is not a Bumblebee snapshot.", listOf(name))
                }
                if (spoolDir != null && name.startsWith("files/")) {
                    spoolDir.mkdirs()
                    val file = File(spoolDir, "scan-${spooled.size}")
                    val size = file.outputStream().use { copyAtMost(zip, it, maxUncompressedBytes - used) }
                        ?: return SnapshotRead.Rejected("This snapshot is too large to import.", listOf(name))
                    used += size
                    spooled[name] = file
                } else {
                    val capped = readAtMost(zip, maxUncompressedBytes - used)
                        ?: return SnapshotRead.Rejected("This snapshot is too large to import.", listOf(name))
                    used += capped.size
                    entries[name] = capped
                }
                entry = zip.nextEntry
            }
        }
    } catch (error: Exception) {
        return SnapshotRead.Rejected("This file is not a Bumblebee snapshot.", listOf(error.message ?: "zip"))
    }
    val fileNames = (entries.keys.filter { it.startsWith("files/") } + spooled.keys).toSortedSet()
    val required = SnapshotSections.ORDER.map { "$it.json" } + "prefs.json" + "manifest.json"
    val missing = required.filter { it !in entries }
    if (missing.isNotEmpty()) {
        return SnapshotRead.Rejected("This file is not a Bumblebee snapshot.", missing)
    }
    val digest = MessageDigest.getInstance("SHA-256")
    SnapshotSections.ORDER.forEach { digest.update(entries.getValue("$it.json")) }
    digest.update(entries.getValue("prefs.json"))
    for (name in fileNames) {
        val inMemory = entries[name]
        if (inMemory != null) digest.update(inMemory) else spooled.getValue(name).inputStream().use { s ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
    }
    val sha = digest.digest().joinToString("") { "%02x".format(it) }
    val manifestJson = JSONObject(String(entries.getValue("manifest.json"), Charsets.UTF_8))
    val counts = manifestJson.getJSONObject("counts")
    val countMap = counts.keys().asSequence().associateWith { counts.getInt(it) }
    val manifest = SnapshotManifest(
        magic = manifestJson.getString("magic"),
        format = manifestJson.getInt("format"),
        schema = manifestJson.getInt("schema"),
        exportedAt = manifestJson.getLong("exportedAt"),
        buildType = manifestJson.getString("buildType"),
        counts = countMap,
        sha256 = manifestJson.getString("sha256"),
    )
    if (manifest.sha256 != sha) {
        return SnapshotRead.Rejected("The file is damaged. The checksum does not match.", emptyList())
    }
    val actual = sectionCountsFrom(entries) + (SnapshotSections.PREFS to 1)
    when (val verdict = inspectManifest(manifest, sha, actual)) {
        is SnapshotVerdict.Rejected -> return SnapshotRead.Rejected(verdict.message, verdict.log)
        SnapshotVerdict.Ok -> Unit
    }
    val bundle = parseBundle(entries).let { b ->
        b.copy(files = b.files + spooled.map { (name, file) -> SnapshotFile.lazy(name.removePrefix("files/")) { file.readBytes() } })
    }
    val named = fileNames
    val wanted = (bundle.documents.mapNotNull { it.filePath } + bundle.services.mapNotNull { it.filePath })
        .filter { it.isNotBlank() }
    val absent = wanted.filter { it !in named }
    if (absent.isNotEmpty()) {
        return SnapshotRead.Rejected("A paper is missing its scan.", absent)
    }
    return SnapshotRead.Ready(bundle, manifest)
}

const val MAX_SNAPSHOT_BYTES = 512L * 1024 * 1024

/** Copies at most [max] bytes; returns how many, or null when there were more. */
private fun copyAtMost(input: InputStream, out: OutputStream, max: Long): Long? {
    if (max < 0) return null
    val chunk = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val read = input.read(chunk)
        if (read < 0) break
        total += read
        if (total > max) return null
        out.write(chunk, 0, read)
    }
    return total
}

private fun readAtMost(input: InputStream, max: Long): ByteArray? {
    if (max < 0) return null
    val out = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    var total = 0L
    while (true) {
        val read = input.read(chunk)
        if (read < 0) break
        total += read
        if (total > max) return null
        out.write(chunk, 0, read)
    }
    return out.toByteArray()
}

private fun allowedZipName(name: String): Boolean {
    if (name.startsWith("/") || name.contains('\\') || name.split('/').any { it == ".." }) return false
    if (name == "manifest.json" || name == "prefs.json") return true
    if (name.endsWith(".json") && name.removeSuffix(".json") in SnapshotSections.ORDER) return true
    return name.startsWith("files/") && name.removePrefix("files/").isNotEmpty()
}

private fun sectionCounts(bundle: SnapshotBundle): Map<String, Int> = mapOf(
    SnapshotSections.RIDES to bundle.rides.size,
    SnapshotSections.SEGMENTS to bundle.segments.size,
    SnapshotSections.TELEMETRY to bundle.telemetry.size,
    SnapshotSections.TRACKS to bundle.tracks.size,
    SnapshotSections.FUEL to bundle.fuel.size,
    SnapshotSections.ROLLUPS to bundle.rollups.size,
    SnapshotSections.CALIBRATION to bundle.calibration.size,
    SnapshotSections.SERVICE to bundle.services.size,
    SnapshotSections.DOCUMENTS to bundle.documents.size,
    SnapshotSections.PLANS to bundle.plans.size,
)

private fun sectionCountsFrom(entries: Map<String, ByteArray>): Map<String, Int> =
    SnapshotSections.ORDER.associateWith { section ->
        JSONArray(String(entries.getValue("$section.json"), Charsets.UTF_8)).length()
    }

private fun sectionJson(section: String, bundle: SnapshotBundle): String {
    val array = JSONArray()
    when (section) {
        SnapshotSections.RIDES -> bundle.rides.forEach { array.put(it.toJson()) }
        SnapshotSections.SEGMENTS -> bundle.segments.forEach { array.put(it.toJson()) }
        SnapshotSections.TELEMETRY -> bundle.telemetry.forEach { array.put(it.toJson()) }
        SnapshotSections.TRACKS -> bundle.tracks.forEach { array.put(it.toJson()) }
        SnapshotSections.FUEL -> bundle.fuel.forEach { array.put(it.toJson()) }
        SnapshotSections.ROLLUPS -> bundle.rollups.forEach { array.put(it.toJson()) }
        SnapshotSections.CALIBRATION -> bundle.calibration.forEach { array.put(it.toJson()) }
        SnapshotSections.SERVICE -> bundle.services.forEach { array.put(it.toJson()) }
        SnapshotSections.DOCUMENTS -> bundle.documents.forEach { array.put(it.toJson()) }
        SnapshotSections.PLANS -> bundle.plans.forEach { array.put(it.toJson()) }
    }
    return array.toString()
}

private fun parseBundle(entries: Map<String, ByteArray>): SnapshotBundle {
    fun array(section: String) = JSONArray(String(entries.getValue("$section.json"), Charsets.UTF_8))
    val files = entries.filterKeys { it.startsWith("files/") }.map { (name, bytes) ->
        SnapshotFile(name.removePrefix("files/"), bytes)
    }
    return SnapshotBundle(
        rides = array(SnapshotSections.RIDES).objects().map { it.toRide() },
        segments = array(SnapshotSections.SEGMENTS).objects().map { it.toSegment() },
        telemetry = array(SnapshotSections.TELEMETRY).objects().map { it.toTelemetry() },
        tracks = array(SnapshotSections.TRACKS).objects().map { it.toTrack() },
        fuel = array(SnapshotSections.FUEL).objects().map { it.toFuel() },
        rollups = array(SnapshotSections.ROLLUPS).objects().map { it.toRollup() },
        calibration = array(SnapshotSections.CALIBRATION).objects().map { it.toCalibration() },
        services = array(SnapshotSections.SERVICE).objects().map { it.toService() },
        documents = array(SnapshotSections.DOCUMENTS).objects().map { it.toDocument() },
        plans = array(SnapshotSections.PLANS).objects().map { it.toPlan() },
        prefs = JSONObject(String(entries.getValue("prefs.json"), Charsets.UTF_8)).toPrefs(),
        files = files,
    )
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

private fun JSONObject.putNullable(key: String, value: Any?) = put(key, value ?: JSONObject.NULL)

private fun JSONObject.str(key: String): String? = if (isNull(key)) null else getString(key)

private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)

private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)

private fun JSONObject.dblOrNull(key: String): Double? = if (isNull(key)) null else getDouble(key)

private fun JSONObject.fltOrNull(key: String): Float? = if (isNull(key)) null else getDouble(key).toFloat()

private fun SnapshotRide.toJson() = JSONObject()
    .put("id", id)
    .put("startTs", startTs)
    .putNullable("endTs", endTs)
    .putNullable("odoStartKm", odoStartKm)
    .putNullable("odoEndKm", odoEndKm)
    .put("gpsDistanceM", gpsDistanceM)
    .put("movingSec", movingSec)
    .put("idleSec", idleSec)
    .put("maxSpeedKmh", maxSpeedKmh)
    .put("avgMovingSpeedKmh", avgMovingSpeedKmh)
    .putNullable("fuelBarsStart", fuelBarsStart)
    .putNullable("fuelBarsEnd", fuelBarsEnd)
    .putNullable("speedHistogramJson", speedHistogramJson)
    .putNullable("name", name)
    .putNullable("tags", tags)
    .putNullable("mergedIntoId", mergedIntoId)
    .putNullable("remoteId", remoteId)
    .putNullable("syncedAt", syncedAt)
    .put("isDemo", isDemo)
    .put("gpsOnly", gpsOnly)
    .put("rideState", rideState)
    .putNullable("pausedAt", pausedAt)
    .putNullable("startPlace", startPlace)
    .putNullable("endPlace", endPlace)
    .putNullable("weatherTempC", weatherTempC)
    .putNullable("weatherCode", weatherCode)
    .putNullable("climbM", climbM)

private fun JSONObject.toRide() = SnapshotRide(
    id = getLong("id"),
    startTs = getLong("startTs"),
    endTs = longOrNull("endTs"),
    odoStartKm = intOrNull("odoStartKm"),
    odoEndKm = intOrNull("odoEndKm"),
    gpsDistanceM = getDouble("gpsDistanceM"),
    movingSec = getInt("movingSec"),
    idleSec = getInt("idleSec"),
    maxSpeedKmh = getInt("maxSpeedKmh"),
    avgMovingSpeedKmh = getDouble("avgMovingSpeedKmh"),
    fuelBarsStart = intOrNull("fuelBarsStart"),
    fuelBarsEnd = intOrNull("fuelBarsEnd"),
    speedHistogramJson = str("speedHistogramJson"),
    name = str("name"),
    tags = str("tags"),
    mergedIntoId = longOrNull("mergedIntoId"),
    remoteId = str("remoteId"),
    syncedAt = longOrNull("syncedAt"),
    isDemo = getBoolean("isDemo"),
    gpsOnly = getBoolean("gpsOnly"),
    rideState = getString("rideState"),
    pausedAt = longOrNull("pausedAt"),
    startPlace = str("startPlace"),
    endPlace = str("endPlace"),
    weatherTempC = dblOrNull("weatherTempC"),
    weatherCode = intOrNull("weatherCode"),
    climbM = dblOrNull("climbM"),
)

private fun SnapshotSegment.toJson() = JSONObject()
    .put("id", id)
    .put("rideId", rideId)
    .put("startTs", startTs)
    .putNullable("endTs", endTs)
    .putNullable("odoStartKm", odoStartKm)
    .putNullable("odoEndKm", odoEndKm)

private fun JSONObject.toSegment() = SnapshotSegment(
    id = getLong("id"),
    rideId = getLong("rideId"),
    startTs = getLong("startTs"),
    endTs = longOrNull("endTs"),
    odoStartKm = intOrNull("odoStartKm"),
    odoEndKm = intOrNull("odoEndKm"),
)

private fun SnapshotTelemetry.toJson() = JSONObject()
    .put("id", id)
    .put("rideId", rideId)
    .put("ts", ts)
    .put("speedKmh", speedKmh)
    .put("odometerKm", odometerKm)
    .put("tripAKm", tripAKm)
    .put("tripBKm", tripBKm)
    .putNullable("fuelBars", fuelBars)
    .putNullable("fuelEconKml", fuelEconKml)

private fun JSONObject.toTelemetry() = SnapshotTelemetry(
    id = getLong("id"),
    rideId = getLong("rideId"),
    ts = getLong("ts"),
    speedKmh = getInt("speedKmh"),
    odometerKm = getInt("odometerKm"),
    tripAKm = getDouble("tripAKm"),
    tripBKm = getDouble("tripBKm"),
    fuelBars = intOrNull("fuelBars"),
    fuelEconKml = dblOrNull("fuelEconKml"),
)

private fun SnapshotTrack.toJson() = JSONObject()
    .put("id", id)
    .put("rideId", rideId)
    .put("ts", ts)
    .put("lat", lat)
    .put("lng", lng)
    .putNullable("altitudeM", altitudeM)
    .putNullable("bearingDeg", bearingDeg)
    .putNullable("speedMps", speedMps)
    .putNullable("accuracyM", accuracyM)

private fun JSONObject.toTrack() = SnapshotTrack(
    id = getLong("id"),
    rideId = getLong("rideId"),
    ts = getLong("ts"),
    lat = getDouble("lat"),
    lng = getDouble("lng"),
    altitudeM = dblOrNull("altitudeM"),
    bearingDeg = fltOrNull("bearingDeg"),
    speedMps = fltOrNull("speedMps"),
    accuracyM = fltOrNull("accuracyM"),
)

private fun SnapshotFuel.toJson() = JSONObject()
    .put("id", id)
    .put("ts", ts)
    .putNullable("rideId", rideId)
    .put("odometerKm", odometerKm)
    .put("barsBefore", barsBefore)
    .put("barsAfter", barsAfter)
    .put("type", type)
    .put("source", source)
    .putNullable("litres", litres)
    .putNullable("cost", cost)
    .putNullable("note", note)
    .putNullable("lat", lat)
    .putNullable("lng", lng)
    .putNullable("remoteId", remoteId)
    .putNullable("syncedAt", syncedAt)
    .put("isFullTank", isFullTank)
    .putNullable("pricePerLitre", pricePerLitre)
    .put("outlierConfirmed", outlierConfirmed)

private fun JSONObject.toFuel() = SnapshotFuel(
    id = getLong("id"),
    ts = getLong("ts"),
    rideId = longOrNull("rideId"),
    odometerKm = getInt("odometerKm"),
    barsBefore = getInt("barsBefore"),
    barsAfter = getInt("barsAfter"),
    type = getString("type"),
    source = getString("source"),
    litres = dblOrNull("litres"),
    cost = dblOrNull("cost"),
    note = str("note"),
    lat = dblOrNull("lat"),
    lng = dblOrNull("lng"),
    remoteId = str("remoteId"),
    syncedAt = longOrNull("syncedAt"),
    isFullTank = getBoolean("isFullTank"),
    pricePerLitre = dblOrNull("pricePerLitre"),
    outlierConfirmed = getBoolean("outlierConfirmed"),
)

private fun SnapshotRollup.toJson() = JSONObject()
    .put("date", date)
    .put("km", km)
    .put("rides", rides)
    .put("movingSec", movingSec)
    .put("maxSpeedKmh", maxSpeedKmh)
    .put("fuelBarsUsed", fuelBarsUsed)

private fun JSONObject.toRollup() = SnapshotRollup(
    date = getString("date"),
    km = getDouble("km"),
    rides = getInt("rides"),
    movingSec = getInt("movingSec"),
    maxSpeedKmh = getInt("maxSpeedKmh"),
    fuelBarsUsed = getInt("fuelBarsUsed"),
)

private fun SnapshotCalibration.toJson() = JSONObject().put("key", key).put("value", value)

private fun JSONObject.toCalibration() = SnapshotCalibration(getString("key"), getString("value"))

private fun SnapshotService.toJson() = JSONObject()
    .put("id", id)
    .put("ts", ts)
    .put("odometerKm", odometerKm)
    .put("item", item)
    .putNullable("rupees", rupees)
    .putNullable("note", note)
    .putNullable("shop", shop)
    .putNullable("planId", planId)
    .putNullable("filePath", filePath)

private fun JSONObject.toService() = SnapshotService(
    id = getLong("id"),
    ts = getLong("ts"),
    odometerKm = getInt("odometerKm"),
    item = getString("item"),
    rupees = dblOrNull("rupees"),
    note = str("note"),
    shop = str("shop"),
    planId = longOrNull("planId"),
    filePath = str("filePath"),
)

private fun SnapshotDocument.toJson() = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("kind", kind)
    .putNullable("expiryIso", expiryIso)
    .putNullable("number", number)
    .putNullable("filePath", filePath)
    .putNullable("mime", mime)
    .putNullable("note", note)
    .put("createdTs", createdTs)

private fun JSONObject.toDocument() = SnapshotDocument(
    id = getLong("id"),
    title = getString("title"),
    kind = getString("kind"),
    expiryIso = str("expiryIso"),
    number = str("number"),
    filePath = str("filePath"),
    mime = str("mime"),
    note = str("note"),
    createdTs = getLong("createdTs"),
)

private fun SnapshotPlan.toJson() = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("kind", kind)
    .putNullable("intervalKm", intervalKm)
    .putNullable("lastDoneOdoKm", lastDoneOdoKm)
    .putNullable("intervalDays", intervalDays)
    .putNullable("lastDoneOnIso", lastDoneOnIso)
    .putNullable("dueOnIso", dueOnIso)
    .put("active", active)
    .put("createdTs", createdTs)

private fun JSONObject.toPlan() = SnapshotPlan(
    id = getLong("id"),
    title = getString("title"),
    kind = getString("kind"),
    intervalKm = intOrNull("intervalKm"),
    lastDoneOdoKm = intOrNull("lastDoneOdoKm"),
    intervalDays = intOrNull("intervalDays"),
    lastDoneOnIso = str("lastDoneOnIso"),
    dueOnIso = str("dueOnIso"),
    active = getBoolean("active"),
    createdTs = getLong("createdTs"),
)

private fun SnapshotPrefs.toJson() = JSONObject()
    .putNullable("bikeMac", bikeMac)
    .put("riderName", riderName)
    .put("serviceIntervalKm", serviceIntervalKm)
    .putNullable("lastServiceOdoKm", lastServiceOdoKm)
    .put("rcExpiry", rcExpiry)
    .put("insuranceExpiry", insuranceExpiry)
    .put("pucExpiry", pucExpiry)
    .put("mapsNavToCluster", mapsNavToCluster)
    .put("tankCapacityL", tankCapacityL.toDouble())
    .put("remindersEnabled", remindersEnabled)
    .put("remindDays", remindDays)
    .put("remindKm", remindKm)
    .put("pauseWindowMin", pauseWindowMin)
    .put("autoConnect", autoConnect)
    .put("skipAutoConnectDate", skipAutoConnectDate)
    .put("crashSensitivity", crashSensitivity)
    .put("emergencyContacts", emergencyContacts)
    .put("bloodGroup", bloodGroup)
    .put("medicalNote", medicalNote)
    .put("lastReminderStamp", lastReminderStamp)
    .put("widget", widget.toJson())

private fun SnapshotWidget.toJson() = JSONObject()
    .putNullable("fuelBars", fuelBars)
    .putNullable("rangeKm", rangeKm)
    .putNullable("serviceDueKm", serviceDueKm)
    .put("alert", alert)
    .put("todayKm", todayKm)
    .put("lastRideKm", lastRideKm)
    .put("lastRideLabel", lastRideLabel)
    .put("linked", linked)
    .put("open", open)
    .put("atMs", atMs)

private fun JSONObject.toPrefs() = SnapshotPrefs(
    bikeMac = str("bikeMac"),
    riderName = getString("riderName"),
    serviceIntervalKm = getInt("serviceIntervalKm"),
    lastServiceOdoKm = intOrNull("lastServiceOdoKm"),
    rcExpiry = getString("rcExpiry"),
    insuranceExpiry = getString("insuranceExpiry"),
    pucExpiry = getString("pucExpiry"),
    mapsNavToCluster = getBoolean("mapsNavToCluster"),
    tankCapacityL = getDouble("tankCapacityL").toFloat(),
    remindersEnabled = getBoolean("remindersEnabled"),
    remindDays = getInt("remindDays"),
    remindKm = getInt("remindKm"),
    pauseWindowMin = getInt("pauseWindowMin"),
    autoConnect = getBoolean("autoConnect"),
    skipAutoConnectDate = getString("skipAutoConnectDate"),
    crashSensitivity = getString("crashSensitivity"),
    emergencyContacts = getString("emergencyContacts"),
    bloodGroup = getString("bloodGroup"),
    medicalNote = getString("medicalNote"),
    lastReminderStamp = getString("lastReminderStamp"),
    widget = getJSONObject("widget").toWidget(),
)

private fun JSONObject.toWidget() = SnapshotWidget(
    fuelBars = intOrNull("fuelBars"),
    rangeKm = intOrNull("rangeKm"),
    serviceDueKm = intOrNull("serviceDueKm"),
    alert = getString("alert"),
    todayKm = getDouble("todayKm"),
    lastRideKm = getDouble("lastRideKm"),
    lastRideLabel = getString("lastRideLabel"),
    linked = getBoolean("linked"),
    open = getString("open"),
    atMs = getLong("atMs"),
)
