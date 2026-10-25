package dev.gdk.bumblebee.data.local

import dev.gdk.bumblebee.protocol.TelemetryFrame
import dev.gdk.bumblebee.ride.BarCalibrator
import dev.gdk.bumblebee.ride.CostStats
import dev.gdk.bumblebee.ride.FuelEconomics
import dev.gdk.bumblebee.ride.FuelEvent
import dev.gdk.bumblebee.ride.FuelEventDetector
import dev.gdk.bumblebee.ride.FuelEventSource
import dev.gdk.bumblebee.ride.FuelEventType
import kotlin.math.abs
import dev.gdk.bumblebee.ride.CsvExport
import dev.gdk.bumblebee.ride.CsvSample
import dev.gdk.bumblebee.ride.ContinuityDecision
import dev.gdk.bumblebee.ride.FuelFill
import dev.gdk.bumblebee.ride.RideContinuity
import dev.gdk.bumblebee.ride.RideNames
import dev.gdk.bumblebee.ride.RideHighlights
import dev.gdk.bumblebee.ride.RideMergeMath
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.Highlight
import dev.gdk.bumblebee.ride.HighlightStats
import dev.gdk.bumblebee.ride.JourneyDetector
import dev.gdk.bumblebee.ride.JourneyRide
import dev.gdk.bumblebee.ride.JourneySuggestion
import dev.gdk.bumblebee.ride.RideAggregates
import dev.gdk.bumblebee.ride.RideRecord
import dev.gdk.bumblebee.ride.RideRecorder
import dev.gdk.bumblebee.ride.RouteClustering
import dev.gdk.bumblebee.ride.RouteLeaderboard
import dev.gdk.bumblebee.ride.RouteRide
import dev.gdk.bumblebee.ride.RouteStats
import dev.gdk.bumblebee.ride.RunningCostAnalytics
import dev.gdk.bumblebee.ride.ServiceSpend
import dev.gdk.bumblebee.ride.TelemetrySample
import dev.gdk.bumblebee.ride.TrackInsights
import dev.gdk.bumblebee.ride.TrackSimplify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.format.TextStyle
import java.util.Locale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Single entry point for ride capture. The BLE foreground service (later plan) calls
 * [startRide] on session start, [onTelemetry]/[onGps] as data streams in, and
 * [finishRide] on disconnect. Raw data is archived immediately (golden rule: capture
 * everything from ride one); aggregates land on the ride row at finish.
 */
class RideCaptureRepository(private val db: BumblebeeDb) {

    private val recorder = RideRecorder()
    private val fuelDetector = FuelEventDetector()
    private val calibrator = BarCalibrator()
    private val gate = Mutex()
    private var lastGpsTs = Long.MIN_VALUE
    private var activeIsDemo = false
    private var activeGpsOnly = false
    private var lastFuelFingerprint: String? = null
    private var lastPersistedBars: Int? = null
    private var recovered = false

    var activeRideId: Long? = null
        private set

    private val finishedSink = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    val finishedEvents: SharedFlow<Long> = finishedSink

    val isRecording: Boolean get() = recorder.isActive
    val isGpsOnly: Boolean get() = activeGpsOnly

    fun observeFuel(): kotlinx.coroutines.flow.Flow<List<FuelEventEntity>> = db.fuelDao().observeAll()
    fun observeFinishedRides(): kotlinx.coroutines.flow.Flow<List<RideEntity>> = db.rideDao().observeFinishedNewestFirst()
    fun observeRollup(date: String): kotlinx.coroutines.flow.Flow<DailyRollupEntity?> = db.rollupDao().observeByDate(date)

    suspend fun liveSnapshot(now: Long = System.currentTimeMillis()): RideAggregates? = serial {
        if (!recorder.isActive) null else recorder.snapshot(now)
    }

    suspend fun isRidePaused(): Boolean = serial {
        val id = activeRideId ?: return@serial false
        db.rideDao().byId(id)?.rideState == RIDE_PAUSED
    }

    suspend fun activePausedAt(): Long? = serial {
        val id = activeRideId ?: return@serial null
        val ride = db.rideDao().byId(id) ?: return@serial null
        if (ride.rideState != RIDE_PAUSED) null else ride.pausedAt
    }

    /**
     * Ends [rideId] when its pause window is due. A newer ride or a resumed ride is
     * left alone, so a leftover alarm cannot finish the wrong outing. A connected bike
     * still finishes a paused ride whose window has already elapsed.
     */
    suspend fun finishIfPauseElapsed(
        rideId: Long,
        now: Long,
        windowMin: Int,
        bikeReady: Boolean,
    ): Boolean = serial {
        val id = activeRideId ?: return@serial false
        if (id != rideId) return@serial false
        val ride = db.rideDao().byId(id) ?: return@serial false
        if (ride.rideState == RIDE_PAUSED) {
            val pausedAt = ride.pausedAt ?: return@serial false
            if (windowMin <= 0 || now < RideContinuity.pauseDeadlineMs(pausedAt, windowMin)) return@serial false
            finishRideUnlocked(RideContinuity.finishTimestamp(lastSampleTs(id, ride.startTs), ride.startTs))
            true
        } else if (!bikeReady && windowMin <= 0 && ride.endTs == null) {
            finishRideUnlocked(now)
            true
        } else {
            false
        }
    }

    suspend fun pausedForMin(now: Long = System.currentTimeMillis()): Int = serial {
        val id = activeRideId ?: return@serial 0
        val ride = db.rideDao().byId(id) ?: return@serial 0
        if (ride.rideState != RIDE_PAUSED) return@serial 0
        val at = ride.pausedAt ?: return@serial 0
        ((now - at) / 60_000L).toInt().coerceAtLeast(0)
    }

    suspend fun lastFinished(): RideEntity? = db.rideDao().lastFinished()

    suspend fun segmentsFor(rideId: Long): List<RideSegmentEntity> = db.segmentDao().forRide(rideId)

    suspend fun finishedRides(): List<RideEntity> = db.rideDao().finishedNewestFirst()

    suspend fun rideById(id: Long): RideEntity? = db.rideDao().byId(id)

    suspend fun lastParked(): GpsFix? {
        val p = db.trackDao().latest() ?: return null
        return GpsFix(p.ts, p.lat, p.lng, p.altitudeM, p.bearingDeg, p.speedMps, p.accuracyM)
    }

    suspend fun lastPointFor(rideId: Long): GpsFix? {
        val p = db.trackDao().lastForRide(rideId) ?: return null
        return GpsFix(p.ts, p.lat, p.lng, p.altitudeM, p.bearingDeg, p.speedMps, p.accuracyM)
    }

    suspend fun allTimeMaxSpeed(): Int = db.rideDao().allTimeMaxSpeed() ?: 0

    suspend fun trackForRide(rideId: Long): List<GpsFix> =
        memberIds(rideId).flatMap { id -> db.trackDao().forRide(id) }
            .sortedBy { it.ts }
            .map { GpsFix(it.ts, it.lat, it.lng, it.altitudeM, it.bearingDeg, it.speedMps, it.accuracyM) }

    suspend fun speedsForRide(rideId: Long): List<Pair<Long, Int>> =
        memberIds(rideId).flatMap { id -> db.telemetryDao().forRide(id) }
            .sortedBy { it.ts }
            .map { it.ts to it.speedKmh }

    suspend fun telemetryForRide(rideId: Long): List<TelemetrySample> =
        memberIds(rideId).flatMap { id -> db.telemetryDao().forRide(id) }
            .sortedBy { it.ts }
            .map {
                TelemetrySample(
                    ts = it.ts,
                    speedKmh = it.speedKmh,
                    odometerKm = it.odometerKm,
                    tripAKm = it.tripAKm,
                    tripBKm = it.tripBKm,
                    fuelBars = it.fuelBars,
                    fuelEconKml = it.fuelEconKml,
                )
            }

    suspend fun fuelEventsForRide(rideId: Long): List<FuelEvent> =
        memberIds(rideId).flatMap { id -> db.fuelDao().forRide(id) }
            .sortedBy { it.ts }
            .map {
                FuelEvent(
                    ts = it.ts,
                    odometerKm = it.odometerKm,
                    barsBefore = it.barsBefore,
                    barsAfter = it.barsAfter,
                    type = FuelEventType.valueOf(it.type),
                )
            }

    suspend fun csvForRide(rideId: Long): String {
        val samples = memberIds(rideId).flatMap { id -> db.telemetryDao().forRide(id) }
            .sortedBy { it.ts }
            .map {
                CsvSample(it.ts, it.speedKmh, it.odometerKm, it.tripAKm, it.tripBKm, it.fuelBars, it.fuelEconKml)
            }
        return CsvExport.rideSamples(rideId, samples)
    }

    suspend fun rollups(): List<DailyRollupEntity> = db.rollupDao().all()

    suspend fun recentFuel(limit: Int = 20): List<FuelEventEntity> =
        db.fuelDao().allChronological().takeLast(limit).reversed()

    suspend fun allFuel(): List<FuelEventEntity> = db.fuelDao().allChronological()

    suspend fun kmPerBarJson(): String? = db.calibrationDao().get("km_per_bar")?.value

    suspend fun lastTelemetry(rideId: Long): TelemetrySampleEntity? = db.telemetryDao().lastForRide(rideId)

    suspend fun latestKnownOdometerKm(): Int? = db.telemetryDao().latestOdometerKm()

    suspend fun fuelFills(): List<FuelFill> = db.fuelDao().allChronological()
        .filter { it.type == FuelEventType.REFUEL.name }
        .map { it.toFill() }

    data class BikeEconomics(
        val rangeKm: Double?,
        val tankKmPerL: Double?,
        val cost: CostStats?,
        val kmPerBarAvg: Double?,
    )

    suspend fun bikeEconomics(bars: Int?): BikeEconomics = serial {
        updateCalibrationUnlocked()
        val fills = fuelFillsUnlocked()
        BikeEconomics(
            rangeKm = bars?.let { calibrator.estimatedRangeKm(it) },
            tankKmPerL = FuelEconomics.averageKmPerL(fills),
            cost = FuelEconomics.costStats(fills),
            kmPerBarAvg = calibrator.averageKmPerBar(),
        )
    }

    suspend fun logRefuel(
        odometerKm: Int,
        barsAfter: Int,
        litres: Double?,
        cost: Double? = null,
        note: String? = null,
        ts: Long = System.currentTimeMillis(),
        isFullTank: Boolean = true,
    ) = serial {
        val comment = note?.trim()?.ifBlank { null }
        val nearby = nearbyRefuel(odometerKm, ts)
        if (nearby != null) {
            db.fuelDao().update(
                nearby.copy(
                    ts = ts,
                    odometerKm = odometerKm,
                    barsAfter = barsAfter,
                    litres = litres,
                    cost = cost,
                    note = comment,
                    source = FuelEventSource.RIDER.name,
                    isFullTank = isFullTank,
                    pricePerLitre = pricePerLitre(cost, litres),
                ),
            )
        } else {
            db.fuelDao().insert(
                FuelEventEntity(
                    ts = ts,
                    rideId = activeRideId,
                    odometerKm = odometerKm,
                    barsBefore = 0,
                    barsAfter = barsAfter,
                    type = FuelEventType.REFUEL.name,
                    source = FuelEventSource.RIDER.name,
                    litres = litres,
                    cost = cost,
                    note = comment,
                    isFullTank = isFullTank,
                    pricePerLitre = pricePerLitre(cost, litres),
                ),
            )
        }
        updateCalibrationUnlocked()
    }

    suspend fun updateFuel(
        id: Long,
        odometerKm: Int,
        barsAfter: Int,
        litres: Double?,
        cost: Double?,
        note: String?,
        ts: Long,
        isFullTank: Boolean = true,
        outlierConfirmed: Boolean = false,
    ) = serial {
        val existing = db.fuelDao().byId(id) ?: return@serial
        db.fuelDao().update(
            existing.copy(
                ts = ts,
                odometerKm = odometerKm,
                barsAfter = barsAfter,
                litres = litres,
                cost = cost,
                note = note?.trim()?.ifBlank { null },
                isFullTank = isFullTank,
                outlierConfirmed = outlierConfirmed,
                pricePerLitre = pricePerLitre(cost, litres),
                source = if (existing.type == FuelEventType.REFUEL.name) {
                    FuelEventSource.RIDER.name
                } else {
                    existing.source
                },
            ),
        )
        updateCalibrationUnlocked()
    }

    suspend fun deleteFuel(id: Long) = serial {
        db.fuelDao().delete(id)
        updateCalibrationUnlocked()
    }

    suspend fun allServices(): List<ServiceSpend> = db.serviceDao().allNewestFirst().map {
        ServiceSpend(it.id, it.ts, it.odometerKm, it.item, it.rupees, it.note, it.shop, it.planId, it.filePath)
    }

    suspend fun logService(
        odometerKm: Int,
        item: String,
        rupees: Double?,
        note: String?,
        ts: Long,
        shop: String? = null,
        planId: Long? = null,
        filePath: String? = null,
    ): Long {
        val id = db.serviceDao().insert(
            ServiceLogEntity(
                ts = ts,
                odometerKm = odometerKm,
                item = item.trim().ifBlank { "service" },
                rupees = rupees,
                note = note?.trim()?.ifBlank { null },
                shop = shop?.trim()?.ifBlank { null },
                planId = planId,
                filePath = filePath,
            ),
        )
        rollPlanAfterWork(planId, item, odometerKm, ts)
        return id
    }

    suspend fun updateService(log: ServiceSpend) {
        val existing = db.serviceDao().byId(log.id) ?: return
        db.serviceDao().update(
            existing.copy(
                ts = log.ts,
                odometerKm = log.odometerKm,
                item = log.item.trim().ifBlank { "service" },
                rupees = log.rupees,
                note = log.note?.trim()?.ifBlank { null },
                shop = log.shop?.trim()?.ifBlank { null },
                planId = log.planId,
                filePath = log.filePath,
            ),
        )
        rollPlanAfterWork(log.planId, log.item, log.odometerKm, log.ts)
    }

    suspend fun deleteService(id: Long) {
        db.serviceDao().delete(id)
    }

    suspend fun allDocuments(): List<BikeDocumentEntity> = db.documentDao().all()

    suspend fun upsertDocument(doc: BikeDocumentEntity): Long {
        return if (doc.id == 0L) db.documentDao().insert(doc) else {
            db.documentDao().update(doc)
            doc.id
        }
    }

    suspend fun deleteDocument(id: Long) {
        db.documentDao().delete(id)
    }

    suspend fun allPlans(): List<MaintenancePlanEntity> = db.planDao().all()

    suspend fun upsertPlan(plan: MaintenancePlanEntity): Long {
        return if (plan.id == 0L) db.planDao().insert(plan) else {
            db.planDao().update(plan)
            plan.id
        }
    }

    suspend fun deletePlan(id: Long) {
        db.planDao().delete(id)
    }

    suspend fun ensureGarageDefaults(
        rcExpiry: String,
        insuranceExpiry: String,
        pucExpiry: String,
        intervalKm: Int,
        lastServiceOdoKm: Int?,
        now: Long = System.currentTimeMillis(),
    ) {
        if (db.documentDao().count() == 0) {
            seedDoc("RC", "RC", rcExpiry, now)
            seedDoc("Insurance", "INSURANCE", insuranceExpiry, now)
            seedDoc("PUC", "PUC", pucExpiry, now)
        }
        if (db.planDao().count() == 0) {
            val serviceKm = intervalKm.takeIf { it > 0 } ?: 4_000
            val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString()
            db.planDao().insert(
                MaintenancePlanEntity(
                    title = "Periodic service",
                    kind = "SERVICE",
                    intervalKm = serviceKm,
                    lastDoneOdoKm = lastServiceOdoKm,
                    intervalDays = 180,
                    lastDoneOnIso = lastServiceOdoKm?.let { today },
                    createdTs = now,
                ),
            )
            db.planDao().insert(
                MaintenancePlanEntity(
                    title = "Engine oil",
                    kind = "OIL",
                    intervalKm = 4_000,
                    lastDoneOdoKm = lastServiceOdoKm,
                    intervalDays = 180,
                    lastDoneOnIso = lastServiceOdoKm?.let { today },
                    createdTs = now,
                ),
            )
            db.planDao().insert(
                MaintenancePlanEntity(title = "Drive chain", kind = "CHAIN", intervalKm = 500, createdTs = now),
            )
            db.planDao().insert(
                MaintenancePlanEntity(title = "Brake pads", kind = "BRAKES", intervalKm = 8_000, createdTs = now),
            )
            db.planDao().insert(
                MaintenancePlanEntity(title = "Tyres", kind = "TYRES", intervalKm = 20_000, createdTs = now),
            )
        }
    }

    private suspend fun seedDoc(title: String, kind: String, expiry: String, now: Long) {
        db.documentDao().insert(
            BikeDocumentEntity(
                title = title,
                kind = kind,
                expiryIso = expiry.trim().ifBlank { null },
                createdTs = now,
            ),
        )
    }

    /** Resets every plan the visit covered: the one picked, plus one per listed job. */
    private suspend fun rollPlanAfterWork(planId: Long?, item: String, odometerKm: Int, ts: Long) {
        val plans = db.planDao().all()
        val picked = planId?.let { id -> plans.firstOrNull { it.id == id } }
        val matched = dev.gdk.bumblebee.ride.GarageInsights.workItems(item).mapNotNull { job ->
            plans.firstOrNull { it.title.equals(job, ignoreCase = true) }
                ?: plans.firstOrNull { job.contains(it.title, ignoreCase = true) }
                ?: plans.firstOrNull { kindMatches(it.kind, job) }
        }
        val doneOn = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
        (listOfNotNull(picked) + matched).distinctBy { it.id }.forEach { plan ->
            val nextDue = plan.intervalDays?.takeIf { it > 0 }?.let { doneOn.plusDays(it.toLong()).toString() }
            db.planDao().update(
                plan.copy(
                    lastDoneOdoKm = odometerKm,
                    lastDoneOnIso = doneOn.toString(),
                    dueOnIso = nextDue ?: plan.dueOnIso,
                ),
            )
        }
    }

    private fun kindMatches(kind: String, item: String): Boolean {
        val t = item.lowercase()
        return when (kind) {
            "OIL" -> t.contains("oil")
            "CHAIN" -> t.contains("chain")
            "BRAKES" -> t.contains("brake") || t.contains("pad")
            "TYRES" -> t.contains("tyre") || t.contains("tire")
            // Only a service names a service; an oil change has its own plan.
            "SERVICE" -> t.contains("service")
            else -> false
        }
    }

    suspend fun runningCost(): dev.gdk.bumblebee.ride.RunningCost? {
        val fills = fuelFills()
        val services = allServices()
        val fallback = finishedRides().mapNotNull { it.odoDeltaKm()?.toInt() }.sum().takeIf { it > 0 }
        return RunningCostAnalytics.cost(fills, services, fallback)
    }

    suspend fun routeStats(): List<RouteStats> {
        val rides = finishedRides().take(50)
        if (rides.isEmpty()) return emptyList()
        val tracks = rides.associate { it.id to TrackSimplify.douglasPeucker(trackForRide(it.id)) }
        val clusters = RouteClustering.cluster(rides.map { it.id }, tracks)
        val kmPerL = FuelEconomics.averageKmPerL(fuelFills())
        return RouteLeaderboard.leaderboard(
            clusters,
            rides.map { RouteRide(it.id, it.startTs, it.endTs, it.km(), it.movingSec) },
            kmPerL,
        )
    }

    suspend fun journeySuggestions(): List<JourneySuggestion> {
        val rides = db.rideDao().allNewestFirst()
            .filter { it.endTs != null && !it.isDemo }
            .map {
                JourneyRide(it.id, it.startTs, it.endTs!!, it.odoStartKm, it.odoEndKm, it.mergedIntoId)
            }
        return JourneyDetector.detect(rides)
    }

    suspend fun mergeRides(ids: List<Long>): RideEntity = serial {
        require(ids.size >= 2) { "need at least two rides" }
        val rides = ids.map { id -> checkNotNull(db.rideDao().byId(id)) { "missing ride $id" } }
            .sortedBy { it.startTs }
        val parent = rides.first()
        val last = rides.last()
        val distanceKm = when {
            parent.odoStartKm != null && last.odoEndKm != null ->
                (last.odoEndKm - parent.odoStartKm).toDouble().coerceAtLeast(0.0)
            else -> rides.sumOf { it.gpsDistanceM } / 1000.0
        }
        val moving = rides.sumOf { it.movingSec }
        for (ride in rides) {
            db.segmentDao().insert(
                RideSegmentEntity(
                    rideId = parent.id,
                    startTs = ride.startTs,
                    endTs = ride.endTs,
                    odoStartKm = ride.odoStartKm,
                    odoEndKm = ride.odoEndKm,
                ),
            )
        }
        val named = parent.copy(
            endTs = last.endTs,
            odoEndKm = last.odoEndKm ?: parent.odoEndKm,
            gpsDistanceM = rides.sumOf { it.gpsDistanceM },
            movingSec = moving,
            idleSec = rides.sumOf { it.idleSec },
            maxSpeedKmh = rides.maxOf { it.maxSpeedKmh },
            avgMovingSpeedKmh = RideMergeMath.averageSpeedKmh(distanceKm, moving),
            fuelBarsEnd = last.fuelBarsEnd ?: parent.fuelBarsEnd,
            name = parent.name ?: mergedName(parent.startTs, parent.odoStartKm, last.odoEndKm),
            rideState = RIDE_FINISHED,
        )
        db.rideDao().update(named)
        for (child in rides.drop(1)) {
            db.rideDao().update(child.copy(mergedIntoId = parent.id))
        }
        rebuildRollups(rides.map { dateOf(it.startTs) }.toSet())
        return@serial checkNotNull(db.rideDao().byId(parent.id))
    }

    suspend fun splitRide(parentId: Long) = serial { splitRideUnlocked(parentId) }

    suspend fun canSplit(rideId: Long): Boolean = serial {
        db.rideDao().childrenOf(rideId).isNotEmpty() || db.segmentDao().forRide(rideId).size >= 2
    }

    private suspend fun splitRideUnlocked(parentId: Long) {
        val parent = db.rideDao().byId(parentId) ?: return
        val children = db.rideDao().childrenOf(parentId)
        val dates = mutableSetOf(dateOf(parent.startTs))
        if (children.isNotEmpty()) {
            val segments = db.segmentDao().forRide(parentId).sortedBy { it.startTs }
            val first = segments.firstOrNull()
            for (child in children) {
                dates += dateOf(child.startTs)
                db.rideDao().update(child.copy(mergedIntoId = null))
            }
            replayUnlocked(parent)
            val end = first?.endTs ?: parent.startTs
            val agg = recorder.stop(end)
            db.rideDao().update(
                parent.copy(
                    endTs = end,
                    odoEndKm = first?.odoEndKm ?: agg.odoEndKm,
                    gpsDistanceM = agg.gpsDistanceM,
                    movingSec = agg.movingSec,
                    idleSec = agg.idleSec,
                    maxSpeedKmh = agg.maxSpeedKmh,
                    avgMovingSpeedKmh = agg.avgMovingSpeedKmh,
                    rideState = RIDE_FINISHED,
                    pausedAt = null,
                ),
            )
            activeRideId = null
        }
        rebuildRollups(dates)
    }

    /**
     * Deletes a finished ride with its track, telemetry and segments, and anything merged into it,
     * then rebuilds that day's totals. The ride being recorded can't be deleted.
     */
    suspend fun deleteRide(id: Long) = serial {
        if (id == activeRideId) return@serial
        val ride = db.rideDao().byId(id) ?: return@serial
        val ids = listOf(id) + db.rideDao().childrenOf(id).map { it.id }
        val dates = mutableSetOf(dateOf(ride.startTs))
        for (rid in ids) {
            db.rideDao().byId(rid)?.let { dates += dateOf(it.startTs) }
            db.trackDao().deleteForRide(rid)
            db.telemetryDao().deleteForRide(rid)
            db.segmentDao().deleteForRide(rid)
            db.rideDao().delete(rid)
        }
        rebuildRollups(dates)
    }

    /**
     * Deletes demo rides, and rides stamped before 2000 (a phone clock that hadn't synced), with
     * everything recorded for them. Returns how many top-level rides went.
     */
    suspend fun deleteJunkRides(demo: Boolean = true, unsynced: Boolean = true): Int {
        val cutoff = Instant.parse("2000-01-01T00:00:00Z").toEpochMilli()
        val junk = db.rideDao().finishedNewestFirst()
            .filter { it.mergedIntoId == null && (demo && it.isDemo || unsynced && it.startTs < cutoff) }
        junk.forEach { deleteRide(it.id) }
        return junk.size
    }

    /** How many demo and pre-2000 rides there are, for the button label. */
    suspend fun junkRideCount(): Pair<Int, Int> {
        val cutoff = Instant.parse("2000-01-01T00:00:00Z").toEpochMilli()
        val top = db.rideDao().finishedNewestFirst().filter { it.mergedIntoId == null }
        return top.count { it.isDemo } to top.count { it.startTs < cutoff }
    }

    /**
     * Forgets the fuel gauge's remembered steady level and recomputes km-per-bar from the fuel log,
     * for when range or refuel detection has gone odd. Fuel log entries are kept.
     */
    suspend fun relearnFuel() = serial {
        db.calibrationDao().delete(FUEL_STABLE_BARS_KEY)
        db.calibrationDao().delete("km_per_bar")
        lastFuelFingerprint = null
        updateCalibrationUnlocked()
    }

    /** Row counts per table, for the developer page. */
    suspend fun tableCounts(): List<Pair<String, Long>> = withContext(Dispatchers.IO) {
        val sql = db.openHelper.readableDatabase
        TABLES.map { t ->
            t to runCatching { sql.query("SELECT COUNT(*) FROM `$t`").use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } }.getOrDefault(-1L)
        }
    }

    /** Size of the database file in bytes, write-ahead log included. */
    suspend fun databaseBytes(): Long = withContext(Dispatchers.IO) {
        val path = db.openHelper.readableDatabase.path ?: return@withContext 0L
        listOf("", "-wal", "-shm").sumOf { java.io.File(path + it).length() }
    }

    /** Reclaims space left by deletes. */
    suspend fun vacuum() = serial {
        withContext(Dispatchers.IO) { db.openHelper.writableDatabase.execSQL("VACUUM") }
    }

    /** Recomputes every day's totals from the rides, for when they look wrong. */
    suspend fun rebuildAllRollups() = serial {
        rebuildRollups(db.rideDao().finishedNewestFirst().map { dateOf(it.startTs) }.toSet())
    }

    suspend fun setRideMeta(id: Long, name: String?, tags: String?) {
        val ride = db.rideDao().byId(id) ?: return
        db.rideDao().update(
            ride.copy(
                name = name?.trim()?.ifBlank { null },
                tags = tags?.trim()?.ifBlank { null },
            ),
        )
    }

    suspend fun renameRide(id: Long, name: String?) {
        val ride = db.rideDao().byId(id) ?: return
        setRideMeta(id, RideNames.clean(name), ride.tags)
    }

    suspend fun setPlaces(id: Long, start: String?, end: String?) {
        val ride = db.rideDao().byId(id) ?: return
        db.rideDao().update(ride.copy(startPlace = start, endPlace = end))
    }

    suspend fun recordWeatherIfMissing(tempC: Double, code: Int) = serial {
        val id = activeRideId ?: return@serial
        val ride = db.rideDao().byId(id) ?: return@serial
        if (ride.weatherTempC != null) return@serial
        db.rideDao().update(ride.copy(weatherTempC = tempC, weatherCode = code))
    }

    suspend fun rideNumber(id: Long): Int? {
        val ride = db.rideDao().byId(id) ?: return null
        if (ride.endTs == null || ride.isDemo || ride.mergedIntoId != null) return null
        return db.rideDao().rideNumber(ride.startTs)
    }

    suspend fun highlightFor(id: Long): Highlight? {
        val ride = db.rideDao().byId(id) ?: return null
        if (ride.endTs == null || ride.isDemo || ride.mergedIntoId != null) return null
        val earlier = db.rideDao().finishedBefore(ride.startTs)
        return RideHighlights.of(highlightStats(ride), earlier.map { highlightStats(it) })
    }

    suspend fun ridesOnDay(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<RideEntity> {
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return db.rideDao().finishedBetween(from, to)
    }

    private suspend fun highlightStats(ride: RideEntity): HighlightStats {
        val climb = ride.climbM ?: TrackInsights.elevationGainM(trackFixes(ride.id))?.also { gain ->
            db.rideDao().update(ride.copy(climbM = gain))
        }
        val km = when {
            ride.odoStartKm != null && ride.odoEndKm != null && ride.odoEndKm >= ride.odoStartKm ->
                (ride.odoEndKm - ride.odoStartKm).toDouble()
            else -> ride.gpsDistanceM / 1000.0
        }
        return HighlightStats(km, ride.maxSpeedKmh, climb, ride.movingSec)
    }

    private suspend fun trackFixes(rideId: Long): List<GpsFix> =
        db.trackDao().forRide(rideId).map { point ->
            GpsFix(point.ts, point.lat, point.lng, point.altitudeM, point.bearingDeg, point.speedMps, point.accuracyM)
        }

    suspend fun ensureRide(ts: Long): Long = serial { ensureRideUnlocked(ts) }

    suspend fun ingestTelemetry(frame: TelemetryFrame, ts: Long) = serial {
        val rideId = ensureRideUnlocked(ts)
        onTelemetryUnlocked(frame, ts, rideId)
    }

    suspend fun startRide(ts: Long, isDemo: Boolean = false, gpsOnly: Boolean = false): Long = serial {
        recoverUnlocked(ts)
        startRideUnlocked(ts, isDemo, gpsOnly)
    }

    suspend fun restoreFuelDetector() = serial { restoreFuelDetectorUnlocked() }

    suspend fun recoverUnfinishedRides(
        now: Long = System.currentTimeMillis(),
        pauseWindowMin: Int = RideContinuity.DEFAULT_WINDOW_MIN,
    ) = serial {
        recoverUnlocked(now, pauseWindowMin)
    }

    private suspend fun recoverUnlocked(
        now: Long,
        pauseWindowMin: Int = RideContinuity.DEFAULT_WINDOW_MIN,
    ) {
        if (recovered) return
        recovered = true
        restoreFuelDetectorUnlocked()
        val open = db.rideDao().unfinished()
        if (open.isEmpty()) return
        val ranked = open
            .map { it to lastSampleTs(it.id, it.startTs) }
            .sortedByDescending { it.second }
            .map { it.first }
        for (older in ranked.drop(1)) {
            replayUnlocked(older)
            finishRideUnlocked(lastSampleTs(older.id, older.startTs), skipIfEmpty = true)
        }
        val ride = ranked.first()
        if (activeRideId == ride.id && recorder.isActive) return
        replayUnlocked(ride)
        val last = lastSampleTs(ride.id, ride.startTs)
        if (ride.rideState == RIDE_PAUSED) {
            val pausedAt = ride.pausedAt ?: last
            val decision = RideContinuity.onRestart(pauseWindowMin, pausedAt, now)
            if (decision == ContinuityDecision.FINISH) {
                finishRideUnlocked(RideContinuity.finishTimestamp(last, ride.startTs), skipIfEmpty = false)
            } else {
                activeRideId = ride.id
                activeIsDemo = ride.isDemo
                activeGpsOnly = ride.gpsOnly
            }
        } else if (now - last > STALE_RIDE_MS) {
            finishRideUnlocked(maxOf(last, ride.startTs), skipIfEmpty = false)
        } else {
            activeRideId = ride.id
            activeIsDemo = ride.isDemo
            activeGpsOnly = ride.gpsOnly
        }
    }

    suspend fun pauseActiveRide(atTs: Long) = serial {
        val id = activeRideId ?: return@serial
        val ride = db.rideDao().byId(id) ?: return@serial
        if (ride.endTs != null || ride.rideState == RIDE_PAUSED) return@serial
        val odo = db.telemetryDao().lastForRide(id)?.odometerKm
        closeOpenSegment(id, atTs, odo)
        db.rideDao().update(ride.copy(rideState = RIDE_PAUSED, pausedAt = atTs))
    }

    suspend fun onBikeBack(now: Long, odoKm: Int?, windowMin: Int): ContinuityDecision = serial {
        val id = activeRideId
        if (id == null) {
            startRideUnlocked(now)
            return@serial ContinuityDecision.START_NEW
        }
        val ride = db.rideDao().byId(id) ?: return@serial ContinuityDecision.START_NEW
        if (ride.rideState != RIDE_PAUSED) return@serial ContinuityDecision.RESUME
        val lastOdo = db.telemetryDao().lastForRide(id)?.odometerKm
        val decision = RideContinuity.onReconnect(
            windowMin, ride.pausedAt ?: ride.startTs, now, lastOdo, odoKm,
        )
        when (decision) {
            ContinuityDecision.RESUME -> {
                db.rideDao().update(ride.copy(rideState = RIDE_ACTIVE, pausedAt = null))
                openSegment(id, now, odoKm ?: lastOdo)
                ContinuityDecision.RESUME
            }
            ContinuityDecision.START_NEW, ContinuityDecision.FINISH -> {
                val last = lastSampleTs(id, ride.startTs)
                finishRideUnlocked(RideContinuity.finishTimestamp(last, ride.startTs))
                if (decision == ContinuityDecision.START_NEW) startRideUnlocked(now)
                decision
            }
            ContinuityDecision.STAY_PAUSED -> ContinuityDecision.STAY_PAUSED
        }
    }

    suspend fun endRideNow(now: Long): RideEntity? = serial {
        val id = activeRideId ?: return@serial null
        val ride = db.rideDao().byId(id) ?: return@serial null
        val end = if (ride.rideState == RIDE_PAUSED) {
            RideContinuity.finishTimestamp(lastSampleTs(id, ride.startTs), ride.startTs)
        } else {
            now
        }
        finishRideUnlocked(end)
    }

    suspend fun finishAtLastSample(): RideEntity? = serial {
        val id = activeRideId ?: return@serial null
        val ride = db.rideDao().byId(id) ?: return@serial null
        finishRideUnlocked(lastSampleTs(id, ride.startTs))
    }

    suspend fun onTelemetry(frame: TelemetryFrame, ts: Long) = serial {
        val rideId = checkNotNull(activeRideId) { "no active ride" }
        onTelemetryUnlocked(frame, ts, rideId)
    }

    suspend fun onGps(fix: GpsFix) = serial {
        val rideId = checkNotNull(activeRideId) { "no active ride" }
        onGpsUnlocked(fix, rideId)
    }

    /**
     * GPS-only capture path: archive the fix and count moving time from phone speed.
     * Skip when the bike is already feeding [onTelemetry], or the same fix will double-count.
     */
    suspend fun feedPhoneGps(fix: GpsFix) = serial {
        if (!activeGpsOnly) return@serial
        val rideId = activeRideId ?: return@serial
        onGpsUnlocked(fix, rideId)
        val kmh = ((fix.speedMps ?: 0f) * 3.6f).toInt()
        if (recorder.isActive) recorder.onSpeedTick(fix.ts, kmh)
    }

    data class TodayStats(
        val km: Double,
        val movingSec: Int,
        val rides: Int,
        val maxSpeedKmh: Int,
    )

    suspend fun todayStats(now: Long = System.currentTimeMillis()): TodayStats = serial {
        val date = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val rollup = db.rollupDao().byDate(date)
        var km = rollup?.km ?: 0.0
        var moving = rollup?.movingSec ?: 0
        var rides = rollup?.rides ?: 0
        var maxS = rollup?.maxSpeedKmh ?: 0
        if (recorder.isActive) {
            val live = recorder.snapshot(now)
            val odo = live.odoEndKm
            val start = live.odoStartKm
            if (odo != null && start != null) km += (odo - start).toDouble()
            else km += live.gpsDistanceM / 1000.0
            moving += live.movingSec
            maxS = maxOf(maxS, live.maxSpeedKmh)
        }
        TodayStats(km = km, movingSec = moving, rides = rides, maxSpeedKmh = maxS)
    }

    suspend fun finishRide(ts: Long): RideEntity = serial { finishRideUnlocked(ts) }

    private suspend fun persistFuelEvent(event: FuelEvent, rideId: Long) {
        if (activeIsDemo) return
        if (event.type == FuelEventType.REFUEL) {
            val nearby = nearbyRefuel(event.odometerKm, event.ts)
            if (nearby != null) {
                db.fuelDao().update(
                    nearby.copy(
                        barsBefore = event.barsBefore,
                        barsAfter = event.barsAfter,
                        rideId = nearby.rideId ?: rideId,
                    ),
                )
                persistStableBars()
                return
            }
        }
        db.fuelDao().insert(
            FuelEventEntity(
                ts = event.ts, rideId = rideId, odometerKm = event.odometerKm,
                barsBefore = event.barsBefore, barsAfter = event.barsAfter,
                type = event.type.name,
                source = FuelEventSource.AUTO.name,
            ),
        )
        persistStableBars()
    }

    private suspend fun nearbyRefuel(odometerKm: Int, ts: Long): FuelEventEntity? =
        db.fuelDao().allChronological()
            .filter { it.type == FuelEventType.REFUEL.name }
            .filter { abs(it.odometerKm - odometerKm) <= MERGE_ODO_KM }
            .filter { abs(it.ts - ts) <= MERGE_WINDOW_MS }
            .minByOrNull { abs(it.odometerKm - odometerKm) }

    companion object {
        private const val MERGE_ODO_KM = 5
        private const val MERGE_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
        private const val STALE_RIDE_MS = 5L * 60 * 1000
        const val FUEL_STABLE_BARS_KEY = "fuel_stable_bars"
        private const val MAX_GPS_ACCURACY_M = 25f
        private val TABLES = listOf(
            "rides", "ride_segments", "telemetry_samples", "track_points", "fuel_events",
            "service_logs", "maintenance_plans", "bike_documents", "daily_rollups", "calibration",
        )
    }

    private suspend fun updateRollup(ride: RideEntity) {
        if (ride.isDemo) return
        val date = Instant.ofEpochMilli(ride.startTs)
            .atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val km = ride.odoDeltaKm() ?: (ride.gpsDistanceM / 1000.0)
        val barsUsed = maxOf(0, (ride.fuelBarsStart ?: 0) - (ride.fuelBarsEnd ?: 0))
        val prev = db.rollupDao().byDate(date)
        db.rollupDao().upsert(
            DailyRollupEntity(
                date = date,
                km = (prev?.km ?: 0.0) + km,
                rides = (prev?.rides ?: 0) + 1,
                movingSec = (prev?.movingSec ?: 0) + ride.movingSec,
                maxSpeedKmh = maxOf(prev?.maxSpeedKmh ?: 0, ride.maxSpeedKmh),
                fuelBarsUsed = (prev?.fuelBarsUsed ?: 0) + barsUsed,
            )
        )
    }

    private suspend fun fuelFillsUnlocked(): List<FuelFill> = db.fuelDao().allChronological()
        .filter { it.type == FuelEventType.REFUEL.name }
        .map { it.toFill() }

    private suspend fun updateCalibrationUnlocked() {
        val rows = db.fuelDao().allChronological()
        val fingerprint = rows.joinToString("|") { "${it.id}:${it.ts}:${it.barsBefore}:${it.barsAfter}:${it.type}" }
        if (fingerprint == lastFuelFingerprint) return
        lastFuelFingerprint = fingerprint
        val demoIds = db.rideDao().allNewestFirst().filter { it.isDemo }.map { it.id }.toSet()
        val events = rows
            .filter { it.rideId == null || it.rideId !in demoIds }
            .map {
                FuelEvent(
                    ts = it.ts, odometerKm = it.odometerKm, barsBefore = it.barsBefore,
                    barsAfter = it.barsAfter, type = FuelEventType.valueOf(it.type),
                )
            }
        calibrator.update(events)
        val json = (1..6).mapNotNull { bar ->
            calibrator.kmPerBar(bar)?.let { "\"$bar\":$it" }
        }.joinToString(",", "{", "}")
        db.calibrationDao().put(CalibrationEntity(key = "km_per_bar", value = json))
    }

    private suspend fun <T> serial(block: suspend () -> T): T = gate.withLock { block() }

    private suspend fun ensureRideUnlocked(ts: Long): Long {
        recoverUnlocked(ts)
        return activeRideId ?: startRideUnlocked(ts)
    }

    private suspend fun startRideUnlocked(ts: Long, isDemo: Boolean = false, gpsOnly: Boolean = false): Long {
        check(activeRideId == null) { "a ride is already active" }
        restoreFuelDetectorUnlocked()
        val id = db.rideDao().insert(
            RideEntity(startTs = ts, isDemo = isDemo, gpsOnly = gpsOnly, rideState = RIDE_ACTIVE),
        )
        db.segmentDao().insert(RideSegmentEntity(rideId = id, startTs = ts))
        if (recorder.isActive) recorder.stop(ts)
        recorder.start(ts)
        fuelDetector.reset()
        activeRideId = id
        activeIsDemo = isDemo
        activeGpsOnly = gpsOnly
        lastGpsTs = Long.MIN_VALUE
        return id
    }

    private suspend fun onTelemetryUnlocked(frame: TelemetryFrame, ts: Long, rideId: Long) {
        val sample = TelemetrySample.fromFrame(frame, ts)
        db.telemetryDao().insert(
            TelemetrySampleEntity(
                rideId = rideId, ts = sample.ts, speedKmh = sample.speedKmh,
                odometerKm = sample.odometerKm, tripAKm = sample.tripAKm,
                tripBKm = sample.tripBKm, fuelBars = sample.fuelBars,
                fuelEconKml = sample.fuelEconKml,
            )
        )
        recorder.onTelemetry(sample)
        if (!activeIsDemo) {
            fuelDetector.onSample(sample)?.let { persistFuelEvent(it, rideId) }
            persistStableBars()
        }
    }

    private suspend fun onGpsUnlocked(fix: GpsFix, rideId: Long) {
        val acc = fix.accuracyM
        if (acc != null && acc > MAX_GPS_ACCURACY_M) return
        if (fix.ts <= lastGpsTs) return
        lastGpsTs = fix.ts
        db.trackDao().insert(
            TrackPointEntity(
                rideId = rideId, ts = fix.ts, lat = fix.lat, lng = fix.lng,
                altitudeM = fix.altitudeM, bearingDeg = fix.bearingDeg,
                speedMps = fix.speedMps, accuracyM = fix.accuracyM,
            )
        )
        recorder.onGps(fix)
    }

    private suspend fun finishRideUnlocked(ts: Long, skipIfEmpty: Boolean = false): RideEntity {
        val rideId = checkNotNull(activeRideId) { "no active ride" }
        if (!recorder.isActive) {
            replayUnlocked(checkNotNull(db.rideDao().byId(rideId)))
        }
        val agg = recorder.stop(ts)
        val existing = checkNotNull(db.rideDao().byId(rideId))
        closeOpenSegment(rideId, agg.endTs, agg.odoEndKm)
        val climb = TrackInsights.elevationGainM(trackFixes(rideId))
        val ride = existing.copy(
            endTs = agg.endTs,
            odoStartKm = agg.odoStartKm,
            odoEndKm = agg.odoEndKm,
            gpsDistanceM = agg.gpsDistanceM,
            movingSec = agg.movingSec,
            idleSec = agg.idleSec,
            maxSpeedKmh = agg.maxSpeedKmh,
            avgMovingSpeedKmh = agg.avgMovingSpeedKmh,
            fuelBarsStart = agg.fuelBarsStart,
            fuelBarsEnd = agg.fuelBarsEnd,
            speedHistogramJson = agg.histogram.toJson(),
            rideState = RIDE_FINISHED,
            pausedAt = null,
            climbM = climb,
        )
        val empty = ride.movingSec == 0 &&
            ride.gpsDistanceM == 0.0 &&
            (ride.odoEndKm == null || ride.odoEndKm == ride.odoStartKm) &&
            db.telemetryDao().countForRide(rideId) == 0 &&
            db.trackDao().countForRide(rideId) == 0
        if (empty) {
            db.segmentDao().deleteForRide(rideId)
            db.telemetryDao().deleteForRide(rideId)
            db.trackDao().deleteForRide(rideId)
            db.rideDao().delete(rideId)
        } else {
            db.rideDao().update(ride)
            if (!existing.isDemo) finishedSink.tryEmit(ride.id)
            if (!skipIfEmpty) updateRollup(ride)
        }
        updateCalibrationUnlocked()
        if (existing.isDemo) restoreFuelDetectorUnlocked() else persistStableBars()
        activeRideId = null
        activeIsDemo = false
        activeGpsOnly = false
        lastGpsTs = Long.MIN_VALUE
        return ride
    }

    private suspend fun replayUnlocked(ride: RideEntity) {
        if (recorder.isActive) recorder.stop(ride.startTs)
        recorder.start(ride.startTs)
        activeRideId = ride.id
        activeIsDemo = ride.isDemo
        activeGpsOnly = ride.gpsOnly
        lastGpsTs = Long.MIN_VALUE
        val samples = db.telemetryDao().forRide(ride.id)
        val points = db.trackDao().forRide(ride.id)
        val ticks = ArrayList<Pair<Long, Any>>(samples.size + points.size)
        samples.forEach { ticks.add(it.ts to it) }
        points.forEach { ticks.add(it.ts to it) }
        ticks.sortBy { it.first }
        for ((_, item) in ticks) {
            when (item) {
                is TelemetrySampleEntity -> recorder.onTelemetry(
                    TelemetrySample(
                        ts = item.ts,
                        speedKmh = item.speedKmh,
                        odometerKm = item.odometerKm,
                        tripAKm = item.tripAKm,
                        tripBKm = item.tripBKm,
                        fuelBars = item.fuelBars,
                        fuelEconKml = item.fuelEconKml,
                    ),
                )
                is TrackPointEntity -> {
                    val fix = GpsFix(
                        item.ts, item.lat, item.lng, item.altitudeM,
                        item.bearingDeg, item.speedMps, item.accuracyM,
                    )
                    if (fix.ts > lastGpsTs) lastGpsTs = fix.ts
                    recorder.onGps(fix)
                }
            }
        }
    }

    private suspend fun restoreFuelDetectorUnlocked() {
        val raw = db.calibrationDao().get(FUEL_STABLE_BARS_KEY)?.value?.toIntOrNull()
        if (raw != null) {
            fuelDetector.restoreLastStable(raw)
            lastPersistedBars = raw
        }
    }

    private suspend fun persistStableBars() {
        if (activeIsDemo) return
        val bars = fuelDetector.lastStableBars() ?: return
        if (bars == lastPersistedBars) return
        lastPersistedBars = bars
        db.calibrationDao().put(CalibrationEntity(key = FUEL_STABLE_BARS_KEY, value = bars.toString()))
    }

    private suspend fun openSegment(rideId: Long, ts: Long, odo: Int?) {
        db.segmentDao().insert(
            RideSegmentEntity(rideId = rideId, startTs = ts, odoStartKm = odo),
        )
    }

    private suspend fun closeOpenSegment(rideId: Long, ts: Long, odo: Int?) {
        val open = db.segmentDao().openForRide(rideId) ?: return
        db.segmentDao().update(open.copy(endTs = ts, odoEndKm = odo ?: open.odoEndKm))
    }

    private suspend fun lastSampleTs(rideId: Long, fallback: Long): Long {
        val lastTel = db.telemetryDao().lastForRide(rideId)?.ts
        val lastGps = db.trackDao().lastForRide(rideId)?.ts
        return listOfNotNull(lastTel, lastGps, fallback).maxOrNull() ?: fallback
    }

    private suspend fun rebuildRollups(dates: Set<String>) {
        if (dates.isEmpty()) return
        // Demo rides never count toward the day, matching updateRollup.
        val finished = db.rideDao().finishedNewestFirst().filter { !it.isDemo }
        for (date in dates) {
            val day = finished.filter { dateOf(it.startTs) == date }
            val km = day.sumOf { it.odoDeltaKm() ?: (it.gpsDistanceM / 1000.0) }
            db.rollupDao().upsert(
                DailyRollupEntity(
                    date = date,
                    km = km,
                    rides = day.size,
                    movingSec = day.sumOf { it.movingSec },
                    maxSpeedKmh = day.maxOfOrNull { it.maxSpeedKmh } ?: 0,
                    fuelBarsUsed = day.sumOf { maxOf(0, (it.fuelBarsStart ?: 0) - (it.fuelBarsEnd ?: 0)) },
                ),
            )
        }
    }

    private fun dateOf(ts: Long): String =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    private fun pricePerLitre(cost: Double?, litres: Double?): Double? {
        if (cost == null || litres == null || litres <= 0.0) return null
        return cost / litres
    }

    private fun weightedAvgSpeed(rides: List<RideEntity>): Double {
        val moving = rides.sumOf { it.movingSec }
        if (moving <= 0) return 0.0
        return rides.sumOf { it.avgMovingSpeedKmh * it.movingSec } / moving
    }

    private suspend fun memberIds(rideId: Long): List<Long> {
        val kids = db.rideDao().childrenOf(rideId)
        return if (kids.isEmpty()) listOf(rideId) else listOf(rideId) + kids.map { it.id }
    }

    private fun RideEntity.km(): Double = odoDeltaKm() ?: (gpsDistanceM / 1000.0)

    private fun RideEntity.odoDeltaKm(): Double? {
        val s = odoStartKm ?: return null
        val e = odoEndKm ?: return null
        return (e - s).toDouble()
    }

    private fun mergedName(startTs: Long, odoStart: Int?, odoEnd: Int?): String {
        val day = Instant.ofEpochMilli(startTs).atZone(ZoneId.systemDefault()).dayOfWeek
            .getDisplayName(TextStyle.FULL, Locale.getDefault())
        val km = if (odoStart != null && odoEnd != null) (odoEnd - odoStart).coerceAtLeast(0) else null
        return if (km != null) "$day ride · $km km" else "$day ride"
    }
}

private fun FuelEventEntity.toFill() = FuelFill(
    id = id,
    ts = ts,
    odometerKm = odometerKm,
    litres = litres,
    rupees = cost,
    isFullTank = isFullTank,
    outlierConfirmed = outlierConfirmed,
)

fun RideEntity.toRecord(): RideRecord {
    val s = odoStartKm
    val e = odoEndKm
    val km = if (s != null && e != null) (e - s).toDouble() else gpsDistanceM / 1000.0
    return RideRecord(id = id, startTs = startTs, endTs = endTs, km = km, movingSec = movingSec, maxSpeedKmh = maxSpeedKmh)
}
