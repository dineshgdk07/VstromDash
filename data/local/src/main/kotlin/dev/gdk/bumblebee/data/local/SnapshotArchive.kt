package dev.gdk.bumblebee.data.local

import androidx.room.withTransaction
import dev.gdk.bumblebee.snapshot.ApplyPlan
import dev.gdk.bumblebee.snapshot.ParentDecision
import dev.gdk.bumblebee.snapshot.RowAction
import dev.gdk.bumblebee.snapshot.SnapshotBundle
import dev.gdk.bumblebee.snapshot.SnapshotCalibration
import dev.gdk.bumblebee.snapshot.SnapshotDocument
import dev.gdk.bumblebee.snapshot.SnapshotFile
import dev.gdk.bumblebee.snapshot.SnapshotFormat
import dev.gdk.bumblebee.snapshot.SnapshotFuel
import dev.gdk.bumblebee.snapshot.SnapshotPlan
import dev.gdk.bumblebee.snapshot.SnapshotPrefs
import dev.gdk.bumblebee.snapshot.SnapshotRide
import dev.gdk.bumblebee.snapshot.SnapshotRollup
import dev.gdk.bumblebee.snapshot.SnapshotSegment
import dev.gdk.bumblebee.snapshot.SnapshotService
import dev.gdk.bumblebee.snapshot.SnapshotTelemetry
import dev.gdk.bumblebee.snapshot.SnapshotTrack
import dev.gdk.bumblebee.snapshot.SnapshotWidget
import dev.gdk.bumblebee.snapshot.planApply
import dev.gdk.bumblebee.snapshot.rideKey
import java.io.File
import java.io.IOException
import java.util.UUID

data class Capture(val bundle: SnapshotBundle, val missingFiles: List<String>)

class SnapshotArchive(
    private val db: BumblebeeDb,
    private val garageRoot: File,
) {
    fun schemaVersion(): Int = db.openHelper.readableDatabase.version

    suspend fun capture(): Capture {
        val missing = mutableListOf<String>()
        val files = mutableListOf<SnapshotFile>()
        val documents = db.documentDao().all().map { doc ->
            val relative = packFile(doc.filePath, files, missing)
            doc.toSnapshot(relative)
        }
        val services = db.serviceDao().allNewestFirst().map { log ->
            val relative = packFile(log.filePath, files, missing)
            log.toSnapshot(relative)
        }
        val rides = db.rideDao().allNewestFirst().map { it.toSnapshot() }
        val segments = rides.flatMap { ride -> db.segmentDao().forRide(ride.id).map { it.toSnapshot() } }
        val telemetry = rides.flatMap { ride -> db.telemetryDao().forRide(ride.id).map { it.toSnapshot() } }
        val tracks = rides.flatMap { ride -> db.trackDao().forRide(ride.id).map { it.toSnapshot() } }
        return Capture(
            bundle = SnapshotBundle(
                rides = rides,
                segments = segments,
                telemetry = telemetry,
                tracks = tracks,
                fuel = db.fuelDao().allChronological().map { it.toSnapshot() },
                rollups = db.rollupDao().all().map { it.toSnapshot() },
                calibration = db.calibrationDao().all().map { it.toSnapshot() },
                services = services,
                documents = documents,
                plans = db.planDao().all().map { it.toSnapshot() },
                prefs = defaultPrefs(),
                files = files,
            ),
            missingFiles = missing,
        )
    }

    suspend fun apply(bundle: SnapshotBundle, overwriteClashes: Boolean) {
        if (!garageRoot.isDirectory) throw IOException("garage root is not a directory")
        val here = capture().bundle
        val plan = planApply(bundle, here, overwriteClashes)
        val session = File(garageRoot.parentFile, "snapshot-stage/${UUID.randomUUID()}")
        session.mkdirs()
        try {
            val staged = mutableListOf<Pair<File, File>>()
            val retire = mutableListOf<File>()
            db.withTransaction {
                val rideIds = applyRides(bundle, here, plan)
                applyChildren(bundle, plan, rideIds.sourceToLocal)
                rewriteKeptFuel(rideIds.replacedLocal)
                applyFuel(bundle, plan, rideIds.sourceToLocal)
                applyDocuments(bundle, plan, session, staged, retire)
                applyServices(bundle, plan, session, staged, retire)
                applyPlans(bundle, plan)
                applyRollups(bundle, plan)
                applyCalibration(bundle, plan)
            }
            staged.forEach { (stage, final) ->
                final.parentFile?.mkdirs()
                stage.copyTo(final, overwrite = true)
            }
            retire.forEach { previous ->
                if (previous.exists() && staged.none { it.second.absolutePath == previous.absolutePath }) {
                    previous.delete()
                }
            }
            session.deleteRecursively()
        } catch (error: Exception) {
            session.deleteRecursively()
            throw error
        }
    }

    private data class RideIds(val sourceToLocal: Map<Long, Long>, val replacedLocal: Map<Long, Long>)

    private suspend fun applyRides(
        bundle: SnapshotBundle,
        here: SnapshotBundle,
        plan: ApplyPlan,
    ): RideIds {
        val ids = mutableMapOf<Long, Long>()
        val replaced = mutableMapOf<Long, Long>()
        for (decision in plan.rides) {
            val incoming = bundle.rides.first { it.id == decision.sourceId }
            val local = here.rides.firstOrNull { rideKey(it.remoteId, it.startTs) == decision.key }
            if (decision.action == RowAction.KEEP) {
                if (local != null) ids[incoming.id] = local.id
                continue
            }
            if (decision.action == RowAction.REPLACE && local != null) {
                db.trackDao().deleteForRide(local.id)
                db.telemetryDao().deleteForRide(local.id)
                db.segmentDao().deleteForRide(local.id)
                db.rideDao().delete(local.id)
            }
            val newId = db.rideDao().insert(incoming.toEntity())
            ids[incoming.id] = newId
            if (local != null) replaced[local.id] = newId
        }
        return RideIds(ids, replaced)
    }

    private suspend fun applyChildren(bundle: SnapshotBundle, plan: ApplyPlan, rideIds: Map<Long, Long>) {
        val actions = plan.rides.associateBy { it.sourceId }
        for (segment in bundle.segments) {
            if (actions[segment.rideId]?.action == RowAction.KEEP) continue
            val rideId = rideIds[segment.rideId] ?: continue
            db.segmentDao().insert(segment.toEntity(rideId))
        }
        for (sample in bundle.telemetry) {
            if (actions[sample.rideId]?.action == RowAction.KEEP) continue
            val rideId = rideIds[sample.rideId] ?: continue
            db.telemetryDao().insert(sample.toEntity(rideId))
        }
        for (point in bundle.tracks) {
            if (actions[point.rideId]?.action == RowAction.KEEP) continue
            val rideId = rideIds[point.rideId] ?: continue
            db.trackDao().insert(point.toEntity(rideId))
        }
    }

    private suspend fun rewriteKeptFuel(replacedLocal: Map<Long, Long>) {
        for (fuel in db.fuelDao().allChronological()) {
            val old = fuel.rideId ?: continue
            val replacement = replacedLocal[old] ?: continue
            if (replacement != old) db.fuelDao().update(fuel.copy(rideId = replacement))
        }
    }

    private suspend fun applyFuel(bundle: SnapshotBundle, plan: ApplyPlan, rideIds: Map<Long, Long>) {
        val here = db.fuelDao().allChronological()
        for (decision in plan.fuels) {
            if (decision.action == RowAction.KEEP) continue
            val incoming = bundle.fuel.first { it.id == decision.sourceId }
            if (decision.action == RowAction.REPLACE) {
                here.firstOrNull { fuelKey(it) == decision.key }?.let { db.fuelDao().delete(it.id) }
            }
            val rideId = incoming.rideId?.let { rideIds[it] }
            db.fuelDao().insert(incoming.toEntity(rideId))
        }
    }

    private suspend fun applyDocuments(
        bundle: SnapshotBundle,
        plan: ApplyPlan,
        session: File,
        staged: MutableList<Pair<File, File>>,
        retire: MutableList<File>,
    ) = applyFiles(
        decisions = plan.documents,
        rows = bundle.documents,
        id = { it.id },
        path = { it.filePath },
        folder = "documents",
        session = session,
        staged = staged,
        retire = retire,
        bytesOf = { path -> bytes(bundle, path) },
        deleteLocal = { key ->
            db.documentDao().all().firstOrNull { documentKey(it) == key }?.let { local ->
                local.filePath?.let { retire += File(it) }
                db.documentDao().delete(local.id)
            }
        },
        insert = { row, path -> db.documentDao().insert(row.toEntity(path)) },
    )

    private suspend fun applyServices(
        bundle: SnapshotBundle,
        plan: ApplyPlan,
        session: File,
        staged: MutableList<Pair<File, File>>,
        retire: MutableList<File>,
    ) = applyFiles(
        decisions = plan.services,
        rows = bundle.services,
        id = { it.id },
        path = { it.filePath },
        folder = "service",
        session = session,
        staged = staged,
        retire = retire,
        bytesOf = { path -> bytes(bundle, path) },
        deleteLocal = { key ->
            db.serviceDao().allNewestFirst().firstOrNull { serviceKey(it) == key }?.let { local ->
                local.filePath?.let { retire += File(it) }
                db.serviceDao().delete(local.id)
            }
        },
        insert = { row, path -> db.serviceDao().insert(row.toEntity(path)) },
    )

    private suspend fun <T> applyFiles(
        decisions: List<ParentDecision>,
        rows: List<T>,
        id: (T) -> Long,
        path: (T) -> String?,
        folder: String,
        session: File,
        staged: MutableList<Pair<File, File>>,
        retire: MutableList<File>,
        bytesOf: (String?) -> ByteArray?,
        deleteLocal: suspend (String) -> Unit,
        insert: suspend (T, String?) -> Long,
    ) {
        for (decision in decisions) {
            if (decision.action == RowAction.KEEP) continue
            val row = rows.first { id(it) == decision.sourceId }
            if (decision.action == RowAction.REPLACE) deleteLocal(decision.key)
            val payload = bytesOf(path(row))
            val newId = insert(row, null)
            if (payload == null) continue
            val safe = File(path(row) ?: "file").name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(48)
            val stage = File(session, "$folder-$newId-$safe").apply { writeBytes(payload) }
            val final = File(garageRoot, "$folder/${newId}_$safe")
            staged += stage to final
            when (folder) {
                "documents" -> db.documentDao().byId(newId)?.let { db.documentDao().update(it.copy(filePath = final.absolutePath)) }
                else -> db.serviceDao().byId(newId)?.let { db.serviceDao().update(it.copy(filePath = final.absolutePath)) }
            }
        }
    }

    private suspend fun applyPlans(bundle: SnapshotBundle, plan: ApplyPlan) {
        val here = db.planDao().all()
        for (decision in plan.plans) {
            if (decision.action == RowAction.KEEP) continue
            val incoming = bundle.plans.first { it.id == decision.sourceId }
            if (decision.action == RowAction.REPLACE) {
                here.firstOrNull { planKey(it) == decision.key }?.let { db.planDao().delete(it.id) }
            }
            db.planDao().insert(incoming.toEntity())
        }
    }

    private suspend fun applyRollups(bundle: SnapshotBundle, plan: ApplyPlan) {
        for ((date, action) in plan.rollups) {
            if (action == RowAction.KEEP) continue
            val row = bundle.rollups.first { it.date == date }
            db.rollupDao().upsert(row.toEntity())
        }
    }

    private suspend fun applyCalibration(bundle: SnapshotBundle, plan: ApplyPlan) {
        for ((key, action) in plan.calibration) {
            if (action == RowAction.KEEP) continue
            val row = bundle.calibration.first { it.key == key }
            db.calibrationDao().put(row.toEntity())
        }
    }
}

private fun packFile(path: String?, files: MutableList<SnapshotFile>, missing: MutableList<String>): String? {
    if (path.isNullOrBlank()) return null
    val file = File(path)
    if (!file.exists()) {
        missing += path
        return null
    }
    val relative = "${file.name}"
    files += SnapshotFile(relative, file.readBytes())
    return "files/$relative"
}

private fun bytes(bundle: SnapshotBundle, path: String?): ByteArray? {
    if (path.isNullOrBlank()) return null
    val relative = path.removePrefix("files/")
    return bundle.files.firstOrNull { it.relativePath == relative }?.bytes
}

private fun defaultPrefs() = SnapshotPrefs(
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

private fun fuelKey(row: FuelEventEntity) =
    dev.gdk.bumblebee.snapshot.fuelKey(row.remoteId, row.ts, row.odometerKm, row.type)

private fun documentKey(row: BikeDocumentEntity) =
    dev.gdk.bumblebee.snapshot.documentKey(row.kind, row.title, row.number)

private fun serviceKey(row: ServiceLogEntity) =
    dev.gdk.bumblebee.snapshot.serviceKey(row.ts, row.item, row.odometerKm)

private fun planKey(row: MaintenancePlanEntity) =
    dev.gdk.bumblebee.snapshot.planKey(row.kind, row.title)

private fun RideEntity.toSnapshot() = SnapshotRide(
    id, startTs, endTs, odoStartKm, odoEndKm, gpsDistanceM, movingSec, idleSec, maxSpeedKmh,
    avgMovingSpeedKmh, fuelBarsStart, fuelBarsEnd, speedHistogramJson, name, tags, mergedIntoId,
    remoteId, syncedAt, isDemo, gpsOnly, rideState, pausedAt,
)

private fun SnapshotRide.toEntity() = RideEntity(
    id = 0, startTs, endTs, odoStartKm, odoEndKm, gpsDistanceM, movingSec, idleSec, maxSpeedKmh,
    avgMovingSpeedKmh, fuelBarsStart, fuelBarsEnd, speedHistogramJson, name, tags, mergedIntoId,
    remoteId, syncedAt, isDemo, gpsOnly, rideState, pausedAt,
)

private fun RideSegmentEntity.toSnapshot() = SnapshotSegment(id, rideId, startTs, endTs, odoStartKm, odoEndKm)

private fun SnapshotSegment.toEntity(rideId: Long) = RideSegmentEntity(0, rideId, startTs, endTs, odoStartKm, odoEndKm)

private fun TelemetrySampleEntity.toSnapshot() = SnapshotTelemetry(
    id, rideId, ts, speedKmh, odometerKm, tripAKm, tripBKm, fuelBars, fuelEconKml,
)

private fun SnapshotTelemetry.toEntity(rideId: Long) = TelemetrySampleEntity(
    0, rideId, ts, speedKmh, odometerKm, tripAKm, tripBKm, fuelBars, fuelEconKml,
)

private fun TrackPointEntity.toSnapshot() = SnapshotTrack(
    id, rideId, ts, lat, lng, altitudeM, bearingDeg, speedMps, accuracyM,
)

private fun SnapshotTrack.toEntity(rideId: Long) = TrackPointEntity(
    0, rideId, ts, lat, lng, altitudeM, bearingDeg, speedMps, accuracyM,
)

private fun FuelEventEntity.toSnapshot() = SnapshotFuel(
    id, ts, rideId, odometerKm, barsBefore, barsAfter, type, source, litres, cost, note, lat, lng,
    remoteId, syncedAt, isFullTank, pricePerLitre, outlierConfirmed,
)

private fun SnapshotFuel.toEntity(rideId: Long?) = FuelEventEntity(
    0, ts, rideId, odometerKm, barsBefore, barsAfter, type, source, litres, cost, note, lat, lng,
    remoteId, syncedAt, isFullTank, pricePerLitre, outlierConfirmed,
)

private fun DailyRollupEntity.toSnapshot() = SnapshotRollup(date, km, rides, movingSec, maxSpeedKmh, fuelBarsUsed)

private fun SnapshotRollup.toEntity() = DailyRollupEntity(date, km, rides, movingSec, maxSpeedKmh, fuelBarsUsed)

private fun CalibrationEntity.toSnapshot() = SnapshotCalibration(key, value)

private fun SnapshotCalibration.toEntity() = CalibrationEntity(key, value)

private fun ServiceLogEntity.toSnapshot(path: String?) = SnapshotService(
    id, ts, odometerKm, item, rupees, note, shop, planId, path,
)

private fun SnapshotService.toEntity(path: String?) = ServiceLogEntity(
    0, ts, odometerKm, item, rupees, note, shop, planId, path,
)

private fun BikeDocumentEntity.toSnapshot(path: String?) = SnapshotDocument(
    id, title, kind, expiryIso, number, path, mime, note, createdTs,
)

private fun SnapshotDocument.toEntity(path: String?) = BikeDocumentEntity(
    0, title, kind, expiryIso, number, path, mime, note, createdTs,
)

private fun MaintenancePlanEntity.toSnapshot() = SnapshotPlan(
    id, title, kind, intervalKm, lastDoneOdoKm, intervalDays, lastDoneOnIso, dueOnIso, active, createdTs,
)

private fun SnapshotPlan.toEntity() = MaintenancePlanEntity(
    0, title, kind, intervalKm, lastDoneOdoKm, intervalDays, lastDoneOnIso, dueOnIso, active, createdTs,
)
