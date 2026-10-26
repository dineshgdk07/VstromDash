package dev.gdk.bumblebee.ui.ride

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ble.BikeBridgeService
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.data.local.RideEntity
import dev.gdk.bumblebee.data.local.RideSession
import dev.gdk.bumblebee.data.local.asTicks
import dev.gdk.bumblebee.ride.CompassCardinal
import dev.gdk.bumblebee.ride.DemoRideSource
import dev.gdk.bumblebee.ride.FuelEconomics
import dev.gdk.bumblebee.ride.Ghost
import dev.gdk.bumblebee.ride.RideAnalytics
import dev.gdk.bumblebee.ride.RideCost
import dev.gdk.bumblebee.ride.RideScore
import dev.gdk.bumblebee.ride.RouteClustering
import dev.gdk.bumblebee.ride.Split
import dev.gdk.bumblebee.ride.TrackSimplify
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.GpxExport
import dev.gdk.bumblebee.ride.InsightLine
import dev.gdk.bumblebee.ride.ResolvedSpeed
import dev.gdk.bumblebee.ride.RideAggregates
import dev.gdk.bumblebee.ride.RideInsightInput
import dev.gdk.bumblebee.ride.RideInsights
import dev.gdk.bumblebee.ride.RideEvent
import dev.gdk.bumblebee.ride.RideStory
import dev.gdk.bumblebee.ride.RideTimeline
import dev.gdk.bumblebee.ride.SegmentSpan
import dev.gdk.bumblebee.ride.SpeedBand
import dev.gdk.bumblebee.ride.SpeedHistogram
import dev.gdk.bumblebee.ride.SpeedResolver
import dev.gdk.bumblebee.ride.SpeedSource
import dev.gdk.bumblebee.ride.TrackPlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class RidePhase { IDLE, LIVE, PAUSED, SUMMARY }

data class RideRow(
    val id: Long,
    val startTs: Long,
    val endTs: Long?,
    val km: Double,
    val movingSec: Int,
    val idleSec: Int,
    val maxSpeedKmh: Int,
    val avgMovingSpeedKmh: Double,
    val headline: String,
    val fuelBarsEnd: Int? = null,
    val name: String? = null,
    val odoStartKm: Int? = null,
    val odoEndKm: Int? = null,
)

data class BandShare(val label: String, val fraction: Float, val band: SpeedBand)

data class RideDetail(
    val row: RideRow,
    val insights: List<InsightLine>,
    val bands: List<BandShare>,
    val fuelBarsStart: Int?,
    val fuelBarsEnd: Int?,
    val odoEndKm: Int?,
    val odoStartKm: Int? = null,
    val timeline: RideTimeline = RideTimeline.empty(),
    val events: List<RideEvent> = emptyList(),
    val parked: GpsFix?,
    val track: List<GpsFix> = emptyList(),
    val speeds: List<Pair<Long, Int>> = emptyList(),
    val story: String = "",
    val tripAKm: Double? = null,
    val tripAStartKm: Double? = null,
    val gpsDistanceM: Double = 0.0,
    val gpsOnly: Boolean = false,
    val tripBKm: Double? = null,
    val bikeEconKml: Double? = null,
    val tankKmPerL: Double? = null,
    val rupeesPerKm: Double? = null,
    val litresEst: Double? = null,
    val litresFromFills: Boolean = false,
    /** The track without the first and last 500 m; null when the ride is too short for that. */
    val gpx: String? = null,
    /** The whole track, start and finish included. */
    val gpxFull: String? = null,
    val csv: String? = null,
    val shareText: String = "",
    val canSplit: Boolean = false,
    val overnight: Boolean = false,
    val elapsedSec: Int = 0,
    val stopLines: List<String> = emptyList(),
    val demo: Boolean = false,
    val score: RideScore? = null,
    /** How the ride compares with the rider's others. */
    val usual: List<String> = emptyList(),
    val cost: RideCost? = null,
)

data class RideUiState(
    val phase: RidePhase = RidePhase.IDLE,
    val pausedMin: Int = 0,
    val linked: Boolean = false,
    val gpsOn: Boolean = false,
    val speed: ResolvedSpeed = ResolvedSpeed(0, SpeedSource.NONE),
    val heading: String? = null,
    val elapsedSec: Int = 0,
    val movingSec: Int = 0,
    val idleSec: Int = 0,
    val distanceKm: Double = 0.0,
    val maxSpeedKmh: Int = 0,
    val avgMovingSpeedKmh: Double = 0.0,
    val fuelBarsStart: Int? = null,
    val fuelBarsEnd: Int? = null,
    val demoRunning: Boolean = false,
    val status: String = "Start a ride to record GPS speed, distance, and moving time.",
    val todayKm: Double = 0.0,
    val weekKm: Double = 0.0,
    val monthKm: Double = 0.0,
    val pbKmh: Int = 0,
    val todayMaxKmh: Int = 0,
    val periodInsights: List<InsightLine> = emptyList(),
    val headline: InsightLine? = null,
    val parked: GpsFix? = null,
    val recent: List<RideRow> = emptyList(),
    val lastTrack: List<GpsFix> = emptyList(),
    val lastSpeeds: List<Pair<Long, Int>> = emptyList(),
    val lastTimeline: RideTimeline = RideTimeline.empty(),
    val recentThumbs: Map<Long, List<GpsFix>> = emptyMap(),
    val detail: RideDetail? = null,
    val openShareId: Long? = null,
    val tripAKm: Double? = null,
    val tripBKm: Double? = null,
    val economyKml: Double? = null,
    val rangeKm: Double? = null,
    /** Completed 10 km splits of the live ride. */
    val splits: List<Split> = emptyList(),
    /** Seconds since the last stop of three minutes or more. */
    val sinceStopSec: Int? = null,
    /** Minutes until the range runs out at the live average. */
    val fuelMinutes: Int? = null,
    /** Against the quickest earlier ride from the same start, at the same distance. */
    val ghost: Ghost? = null,
)

class RideViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DashApp
    private val session = RideSession(app.capture)
    @Volatile private var demoRunning = false
    private var cachedRides: List<RideEntity> = emptyList()
    private var cachedParked: GpsFix? = null
    private var cachedPb = 0
    private var cachedLastTrack: List<GpsFix> = emptyList()
    private var cachedLastSpeeds: List<Pair<Long, Int>> = emptyList()
    private var cachedLastTimeline: RideTimeline = RideTimeline.empty()
    private var cachedThumbs: Map<Long, List<GpsFix>> = emptyMap()
    private var cachedLastTrackRideId: Long? = null
    private var cachedRange: Double? = null
    private var cachedTankKmPerL: Double? = null
    private var cachedRupeesPerKm: Double? = null
    private var openRideId: Long? = null
    private var pendingShareId: Long? = null
    private var cachedDetail: RideDetail? = null
    private var cachedDetailId: Long? = null
    /** Route cluster of each finished ride; rebuilt when the archive changes. */
    private var cachedClusters: Map<Long, Int>? = null
    private var liveExtras = LiveExtras()
    private var liveExtrasAt = 0L
    private var ghostRideId: Long? = null
    private var ghostTracks: Map<Long, List<GpsFix>> = emptyMap()

    private data class LiveExtras(
        val splits: List<Split> = emptyList(),
        val sinceStopSec: Int? = null,
        val ghost: Ghost? = null,
    )

    private val _ui = MutableStateFlow(RideUiState())
    val ui: StateFlow<RideUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            refreshArchive()
            tick()
        }
        viewModelScope.launch {
            app.capture.observeFinishedRides().collect {
                refreshArchive()
                tick()
            }
        }
        viewModelScope.launch {
            app.capture.observeFuel().collect {
                withContext(Dispatchers.IO) {
                    val eco = app.capture.bikeEconomics(app.latestTelemetry.value?.fuelBars)
                    cachedRange = eco.rangeKm
                    cachedTankKmPerL = eco.tankKmPerL
                    cachedRupeesPerKm = eco.cost?.rollingRupeesPerKm
                }
                tick()
            }
        }
        viewModelScope.launch {
            combine(app.latestTelemetry, app.latestGps, app.connection) { _, _, _ -> }
                .collect { tick() }
        }
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                if (app.capture.isRecording || demoRunning || _ui.value.phase == RidePhase.PAUSED) tick()
            }
        }
    }

    fun startGpsRide() {
        if (demoRunning || _ui.value.phase == RidePhase.LIVE) return
        BikeBridgeService.startGpsRide(getApplication())
        openRideId = null
    }

    fun endPausedRide() {
        BikeBridgeService.endRide(getApplication())
        viewModelScope.launch { refreshArchive() }
    }

    fun stopRide() {
        if (_ui.value.linked || demoRunning) return
        BikeBridgeService.stopGpsRide(getApplication())
        viewModelScope.launch { refreshArchive() }
    }

    fun startDemo() {
        if (demoRunning || _ui.value.phase == RidePhase.LIVE) return
        if (app.connection.value is ConnectionState.Ready) {
            _ui.value = _ui.value.copy(status = "Stop the live bike link before running a demo.")
            return
        }
        viewModelScope.launch {
            demoRunning = true
            _ui.value = _ui.value.copy(demoRunning = true, status = "Capturing demo ride…")
            try {
                withContext(Dispatchers.IO) {
                    session.run(DemoRideSource(startTs = System.currentTimeMillis()).asTicks(), isDemo = true)
                }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(status = e.message ?: "Demo ride failed")
            } finally {
                demoRunning = false
                refreshArchive()
            }
        }
    }

    fun openRide(id: Long) {
        openRideId = id
        cachedDetailId = null
        viewModelScope.launch { tick() }
    }

    fun openShare(rideId: Long) {
        pendingShareId = rideId
        openRide(rideId)
    }

    fun consumeOpenShare() {
        pendingShareId = null
        _ui.value = _ui.value.copy(openShareId = null)
    }

    fun renameRide(id: Long, name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { app.capture.renameRide(id, name) }
            if (cachedDetailId == id) cachedDetailId = null
            refreshArchive()
            tick()
        }
    }

    fun closeDetail() {
        openRideId = null
        cachedDetail = null
        cachedDetailId = null
        _ui.value = _ui.value.copy(detail = null)
    }

    private suspend fun refreshArchive() {
        withContext(Dispatchers.IO) {
            cachedRides = app.capture.finishedRides()
            cachedParked = app.capture.lastParked()
            cachedPb = app.capture.allTimeMaxSpeed()
            val recentIds = cachedRides.take(3).map { it.id }
            val lastId = recentIds.firstOrNull()
            if (lastId != null && lastId != cachedLastTrackRideId) {
                val plots = plotsFor(lastId)
                cachedLastTrack = plots.track
                cachedLastSpeeds = plots.speeds
                cachedLastTimeline = timelineFor(cachedRides.first(), plots.track)
                cachedLastTrackRideId = lastId
            } else if (lastId == null) {
                cachedLastTrack = emptyList()
                cachedLastSpeeds = emptyList()
                cachedLastTimeline = RideTimeline.empty()
                cachedLastTrackRideId = null
            }
            cachedThumbs = recentIds.associateWith { id ->
                val track = if (id == cachedLastTrackRideId) cachedLastTrack else app.capture.trackForRide(id)
                TrackPlot.downsample(track, 32)
            }
            val eco = app.capture.bikeEconomics(app.latestTelemetry.value?.fuelBars)
            cachedRange = eco.rangeKm
            cachedTankKmPerL = eco.tankKmPerL
            cachedRupeesPerKm = eco.cost?.rollingRupeesPerKm
            cachedClusters = null
        }
    }

    /**
     * Splits, time since the last stop, and the ghost for the live ride. Reads the track, so it
     * runs at most every ten seconds; the earlier rides the ghost compares with load once a ride.
     */
    private suspend fun refreshLiveExtras(now: Long) {
        val rideId = app.capture.activeRideId ?: return
        if (now - liveExtrasAt < 10_000L) return
        liveExtrasAt = now
        liveExtras = withContext(Dispatchers.IO) {
            val track = app.capture.trackForRide(rideId)
            if (ghostRideId != rideId && track.isNotEmpty()) {
                val start = track.first()
                ghostTracks = cachedRides.filter { !it.isDemo && it.mergedIntoId == null }.take(40).mapNotNull { ride ->
                    val past = app.capture.trackForRide(ride.id)
                    val first = past.firstOrNull() ?: return@mapNotNull null
                    val near = dev.gdk.bumblebee.ride.Geo.haversineMeters(first.lat, first.lng, start.lat, start.lng) <= 400.0
                    if (near) ride.id to TrackPlot.downsample(past, 600) else null
                }.toMap()
                ghostRideId = rideId
            }
            LiveExtras(
                splits = RideAnalytics.splits(track),
                sinceStopSec = RideAnalytics.sinceLastStopSec(track, now),
                ghost = RideAnalytics.ghost(TrackPlot.downsample(track, 600), ghostTracks),
            )
        }
    }

    /** Ride id → route cluster over the latest 50 rides, built on first use. */
    private suspend fun clusters(): Map<Long, Int> {
        cachedClusters?.let { return it }
        val built = withContext(Dispatchers.IO) {
            val rides = cachedRides.filter { !it.isDemo }.take(50)
            val tracks = rides.associate { it.id to TrackSimplify.douglasPeucker(app.capture.trackForRide(it.id)) }
            RouteClustering.cluster(rides.map { it.id }, tracks)
                .filter { it.clusterId != RouteClustering.UNTRACKED }
                .flatMap { c -> c.rideIds.map { it to c.clusterId } }
                .toMap()
        }
        cachedClusters = built
        return built
    }

    private suspend fun tick() {
        val now = System.currentTimeMillis()
        val gps = app.latestGps.value
        val tel = app.latestTelemetry.value
        val linked = app.connection.value is ConnectionState.Ready
        val speed = SpeedResolver.resolve(gps, now, tel?.speedKmh)
        val acc = gps?.accuracyM
        val gpsOn = gps != null && now - gps.ts <= 5_000L && (acc == null || acc <= 25f)
        val live = app.capture.liveSnapshot(now)
        val paused = app.capture.isRidePaused()
        val pausedMin = if (paused) app.capture.pausedForMin(now) else 0
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val weekStart = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val liveKm = live?.let { distanceKm(it) } ?: 0.0
        val todayKm = cachedRides.filter { it.startTs >= todayStart }.sumOf { it.km() } + liveKm
        val weekKm = cachedRides.filter { it.startTs >= weekStart }.sumOf { it.km() } + liveKm
        val monthKm = cachedRides.filter { it.startTs >= monthStart }.sumOf { it.km() } + liveKm
        val todayMax = maxOf(
            cachedRides.filter { it.startTs >= todayStart }.maxOfOrNull { it.maxSpeedKmh } ?: 0,
            live?.maxSpeedKmh ?: 0,
        )
        val monthLongest = maxOf(
            cachedRides.filter { it.startTs >= monthStart }.maxOfOrNull { it.km() } ?: 0.0,
            liveKm,
        )
        if (live != null && !paused) refreshLiveExtras(now) else ghostRideId = null
        val pb = maxOf(cachedPb, live?.maxSpeedKmh ?: 0)
        val rows = cachedRides.map { it.toRow(pb, monthLongest) }
        val last = cachedRides.firstOrNull()
        val headlineSource = live?.toInsight(now) ?: last?.toInsight()
        val headline = headlineSource?.let { RideInsights.headline(it, pb, monthLongest) }
        val detail = openRideId?.let { id ->
            if (cachedDetailId == id) cachedDetail
            else {
                val entity = cachedRides.find { it.id == id } ?: withContext(Dispatchers.IO) { app.capture.rideById(id) }
                cachedDetail = entity?.let { buildDetail(it, pb, monthLongest) }
                cachedDetailId = id
                cachedDetail
            }
        }

        _ui.value = compose(
            now = now,
            linked = linked,
            gpsOn = gpsOn,
            speed = speed,
            bearing = gps?.bearingDeg,
            live = live,
            paused = paused,
            pausedMin = pausedMin,
            last = last,
            todayKm = todayKm,
            weekKm = weekKm,
            monthKm = monthKm,
            pbKmh = pb,
            todayMaxKmh = todayMax,
            headline = headline,
            recent = rows.take(3),
            lastTrack = cachedLastTrack,
            lastSpeeds = cachedLastSpeeds,
            lastTimeline = cachedLastTimeline,
            recentThumbs = cachedThumbs,
            detail = detail,
        )
    }

    private suspend fun buildDetail(entity: RideEntity, pb: Int, monthLongest: Double): RideDetail {
        val input = entity.toInsight()
        val parked = withContext(Dispatchers.IO) { app.capture.lastPointFor(entity.id) }
        val plots = withContext(Dispatchers.IO) { plotsFor(entity.id) }
        val last = withContext(Dispatchers.IO) { app.capture.lastTelemetry(entity.id) }
        val tank = cachedTankKmPerL
        val bike = last?.fuelEconKml
        val litres = FuelEconomics.litresBurnt(entity.km(), tank, bike)
        val story = RideStory.of(input, plots.track)
        val gpxFull = if (plots.track.size >= 2) {
            GpxExport.toGpx(entity.name ?: "Ride ${entity.id}", entity.startTs, plots.track)
        } else {
            null
        }
        // Shared by default without the first and last 500 m, like cards and videos.
        val gpx = GpxExport.withoutEnds(plots.track).takeIf { it.size >= 2 }?.let {
            GpxExport.toGpx(entity.name ?: "Ride ${entity.id}", entity.startTs, it)
        }
        val csv = withContext(Dispatchers.IO) { app.capture.csvForRide(entity.id) }
        val segments = withContext(Dispatchers.IO) { app.capture.segmentsFor(entity.id) }
        val telemetry = withContext(Dispatchers.IO) { app.capture.telemetryForRide(entity.id) }
        val fuelEvents = withContext(Dispatchers.IO) { app.capture.fuelEventsForRide(entity.id) }
        val canSplit = withContext(Dispatchers.IO) { app.capture.canSplit(entity.id) }
        val endTs = entity.endTs ?: System.currentTimeMillis()
        val timeline = RideTimeline.fromRide(
            startTs = entity.startTs,
            endTs = endTs,
            gpsOnly = entity.gpsOnly,
            odoStartKm = entity.odoStartKm,
            odoEndKm = entity.odoEndKm,
            gpsDistanceM = entity.gpsDistanceM,
            track = plots.track,
            telemetry = telemetry,
            fuelEvents = fuelEvents,
            segments = segments.map { SegmentSpan(it.startTs, it.endTs) },
        )
        val overnight = Instant.ofEpochMilli(entity.startTs).atZone(ZoneId.systemDefault()).toLocalDate() !=
            Instant.ofEpochMilli(endTs).atZone(ZoneId.systemDefault()).toLocalDate()
        val extras = detailExtras(
            capture = app.capture,
            entity = entity,
            track = plots.track,
            timeline = timeline,
            litres = litres?.first,
            rupeesPerKm = cachedRupeesPerKm,
            rides = cachedRides,
            routes = if (entity.isDemo) emptyMap() else clusters(),
        )
        return RideDetail(
            row = entity.toRow(pb, monthLongest),
            insights = RideInsights.debrief(input, pb, monthLongest, plots.track),
            bands = bandShares(entity.speedHistogramJson),
            fuelBarsStart = entity.fuelBarsStart,
            fuelBarsEnd = entity.fuelBarsEnd,
            odoEndKm = entity.odoEndKm,
            odoStartKm = entity.odoStartKm,
            timeline = timeline,
            events = timeline.events,
            parked = parked,
            track = plots.track,
            speeds = plots.speeds,
            story = story,
            tripAKm = last?.tripAKm,
            tripAStartKm = telemetry.firstOrNull()?.tripAKm,
            gpsDistanceM = entity.gpsDistanceM,
            gpsOnly = entity.gpsOnly,
            tripBKm = last?.tripBKm,
            bikeEconKml = bike,
            tankKmPerL = tank,
            rupeesPerKm = cachedRupeesPerKm,
            litresEst = litres?.first,
            litresFromFills = litres?.second == true,
            gpx = gpx,
            gpxFull = gpxFull,
            csv = csv,
            canSplit = canSplit,
            overnight = overnight,
            elapsedSec = ((endTs - entity.startTs) / 1000L).toInt().coerceAtLeast(0),
            stopLines = pauseStopLines(segments),
            demo = entity.isDemo,
            score = extras.score,
            usual = extras.usual,
            cost = extras.cost,
            shareText = GpxExport.recap(
                title = "Ride ${DateTimeFormatter.ofPattern("EEE d MMM").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(entity.startTs))}",
                km = entity.km(),
                movingSec = entity.movingSec,
                maxSpeedKmh = entity.maxSpeedKmh,
                story = story,
                tankKmPerL = tank,
                bikeKmPerL = bike,
                litres = litres?.first,
                litresFromFills = litres?.second == true,
            ),
        )
    }

    private data class RidePlots(val track: List<GpsFix>, val speeds: List<Pair<Long, Int>>)

    private suspend fun timelineFor(entity: RideEntity, track: List<GpsFix>): RideTimeline {
        val telemetry = app.capture.telemetryForRide(entity.id)
        val fuelEvents = app.capture.fuelEventsForRide(entity.id)
        val segments = app.capture.segmentsFor(entity.id)
        val endTs = entity.endTs ?: System.currentTimeMillis()
        return RideTimeline.fromRide(
            startTs = entity.startTs,
            endTs = endTs,
            gpsOnly = entity.gpsOnly,
            odoStartKm = entity.odoStartKm,
            odoEndKm = entity.odoEndKm,
            gpsDistanceM = entity.gpsDistanceM,
            track = track,
            telemetry = telemetry,
            fuelEvents = fuelEvents,
            segments = segments.map { SegmentSpan(it.startTs, it.endTs) },
        )
    }

    private suspend fun plotsFor(rideId: Long): RidePlots {
        val track = app.capture.trackForRide(rideId)
        val speeds = TrackPlot.stride(app.capture.speedsForRide(rideId))
        return RidePlots(track, speeds)
    }

    private fun compose(
        now: Long,
        linked: Boolean,
        gpsOn: Boolean,
        speed: ResolvedSpeed,
        bearing: Float?,
        live: RideAggregates?,
        paused: Boolean = false,
        pausedMin: Int = 0,
        last: RideEntity?,
        todayKm: Double,
        weekKm: Double,
        monthKm: Double,
        pbKmh: Int,
        todayMaxKmh: Int,
        headline: InsightLine?,
        recent: List<RideRow>,
        lastTrack: List<GpsFix>,
        lastSpeeds: List<Pair<Long, Int>>,
        lastTimeline: RideTimeline,
        recentThumbs: Map<Long, List<GpsFix>>,
        detail: RideDetail?,
    ): RideUiState {
        val heading = bearing
            ?.takeIf { now - (app.latestGps.value?.ts ?: 0L) <= SpeedResolver.MAX_GPS_AGE_MS }
            ?.let { CompassCardinal.label(it) }
        val period = RideInsights.period(todayKm, weekKm, monthKm, pbKmh, todayMaxKmh)
        val base = RideUiState(
            linked = linked,
            gpsOn = gpsOn,
            speed = speed,
            heading = heading,
            demoRunning = demoRunning,
            todayKm = todayKm,
            weekKm = weekKm,
            monthKm = monthKm,
            pbKmh = pbKmh,
            todayMaxKmh = todayMaxKmh,
            periodInsights = period,
            headline = headline,
            parked = cachedParked,
            recent = recent,
            lastTrack = lastTrack,
            lastSpeeds = lastSpeeds,
            lastTimeline = lastTimeline,
            recentThumbs = recentThumbs,
            detail = detail,
            openShareId = pendingShareId,
        )
        val liveBits = base.copy(
            tripAKm = app.latestTelemetry.value?.tripAKm,
            tripBKm = app.latestTelemetry.value?.tripBKm,
            economyKml = app.latestTelemetry.value?.let { it.fuelEconKmlV2 ?: it.fuelEconKml },
            rangeKm = cachedRange,
        )
        return when {
            live != null && paused -> liveBits.copy(
                phase = RidePhase.PAUSED,
                pausedMin = pausedMin,
                elapsedSec = ((now - live.startTs) / 1000L).toInt().coerceAtLeast(0),
                movingSec = live.movingSec,
                idleSec = live.idleSec,
                distanceKm = distanceKm(live),
                maxSpeedKmh = live.maxSpeedKmh,
                avgMovingSpeedKmh = live.avgMovingSpeedKmh,
                fuelBarsStart = live.fuelBarsStart,
                fuelBarsEnd = live.fuelBarsEnd,
                status = "Ride paused · $pausedMin min · resumes when you start the bike",
            )
            live != null -> liveBits.copy(
                phase = RidePhase.LIVE,
                splits = liveExtras.splits,
                sinceStopSec = liveExtras.sinceStopSec,
                ghost = liveExtras.ghost,
                fuelMinutes = RideAnalytics.minutesOfRange(cachedRange, live.avgMovingSpeedKmh),
                elapsedSec = ((now - live.startTs) / 1000L).toInt().coerceAtLeast(0),
                movingSec = live.movingSec,
                idleSec = live.idleSec,
                distanceKm = distanceKm(live),
                maxSpeedKmh = live.maxSpeedKmh,
                avgMovingSpeedKmh = live.avgMovingSpeedKmh,
                fuelBarsStart = live.fuelBarsStart,
                fuelBarsEnd = live.fuelBarsEnd,
                status = when {
                    linked -> "Recording while linked. A chai stop stays this ride until the pause window runs out."
                    demoRunning -> "Capturing demo ride…"
                    else -> "Recording GPS. Stop when you're done."
                },
            )
            last != null -> liveBits.copy(
                phase = RidePhase.SUMMARY,
                elapsedSec = last.endTs?.let { ((it - last.startTs) / 1000L).toInt() } ?: 0,
                movingSec = last.movingSec,
                idleSec = last.idleSec,
                distanceKm = last.km(),
                maxSpeedKmh = last.maxSpeedKmh,
                avgMovingSpeedKmh = last.avgMovingSpeedKmh,
                fuelBarsStart = last.fuelBarsStart,
                fuelBarsEnd = last.fuelBarsEnd,
                status = "Tap a ride for the debrief. Start another whenever you're ready.",
            )
            else -> liveBits.copy(status = "Start a ride to record GPS speed, distance, and moving time.")
        }
    }
}

private fun pauseStopLines(segments: List<dev.gdk.bumblebee.data.local.RideSegmentEntity>): List<String> {
    val ordered = segments.filter { it.endTs != null }.sortedBy { it.startTs }
    if (ordered.size < 2) return emptyList()
    return (0 until ordered.lastIndex).mapNotNull { i ->
        val gap = ordered[i + 1].startTs - (ordered[i].endTs ?: return@mapNotNull null)
        if (gap < 60_000L) return@mapNotNull null
        val min = (gap / 60_000L).toInt()
        "Stop ${i + 1} · $min min"
    }
}

private fun distanceKm(live: RideAggregates): Double {
    val odo = live.odoEndKm
    val start = live.odoStartKm
    if (odo != null && start != null) return (odo - start).toDouble()
    return live.gpsDistanceM / 1000.0
}

private fun RideEntity.km(): Double {
    val s = odoStartKm
    val e = odoEndKm
    if (s != null && e != null) return (e - s).toDouble()
    return gpsDistanceM / 1000.0
}

private fun RideEntity.toInsight() = RideInsightInput(
    startTs = startTs,
    endTs = endTs ?: startTs,
    km = km(),
    movingSec = movingSec,
    idleSec = idleSec,
    maxSpeedKmh = maxSpeedKmh,
    avgMovingSpeedKmh = avgMovingSpeedKmh,
    odoStartKm = odoStartKm,
    odoEndKm = odoEndKm,
    histogram = speedHistogramJson?.let { SpeedHistogram.fromJson(it) },
    fuelBarsStart = fuelBarsStart,
    fuelBarsEnd = fuelBarsEnd,
)

private fun RideAggregates.toInsight(now: Long) = RideInsightInput(
    startTs = startTs,
    endTs = now,
    km = distanceKm(this),
    movingSec = movingSec,
    idleSec = idleSec,
    maxSpeedKmh = maxSpeedKmh,
    avgMovingSpeedKmh = avgMovingSpeedKmh,
    odoStartKm = odoStartKm,
    odoEndKm = odoEndKm,
    histogram = histogram,
    fuelBarsStart = fuelBarsStart,
    fuelBarsEnd = fuelBarsEnd,
)

private fun RideEntity.toRow(pb: Int, monthLongest: Double): RideRow {
    val input = toInsight()
    val lead = RideStory.of(input).substringBefore('.').trim()
    return RideRow(
        id = id,
        startTs = startTs,
        endTs = endTs,
        km = km(),
        movingSec = movingSec,
        idleSec = idleSec,
        maxSpeedKmh = maxSpeedKmh,
        avgMovingSpeedKmh = avgMovingSpeedKmh,
        headline = lead.ifBlank { String.format("%.1f km", km()) },
        fuelBarsEnd = fuelBarsEnd,
        name = name,
        odoStartKm = odoStartKm,
        odoEndKm = odoEndKm,
    )
}

private fun bandShares(json: String?): List<BandShare> {
    val h = json?.let { SpeedHistogram.fromJson(it) } ?: return emptyList()
    val total = SpeedBand.entries.sumOf { h.secondsIn(it) }.coerceAtLeast(1)
    val labels = mapOf(
        SpeedBand.IDLE to "Stops",
        SpeedBand.B1_30 to "<30",
        SpeedBand.B30_50 to "30–50",
        SpeedBand.B50_70 to "50–70",
        SpeedBand.B70_90 to "70–90",
        SpeedBand.B90_PLUS to "90+",
    )
    return SpeedBand.entries.map { band ->
        BandShare(labels.getValue(band), h.secondsIn(band).toFloat() / total, band)
    }
}
