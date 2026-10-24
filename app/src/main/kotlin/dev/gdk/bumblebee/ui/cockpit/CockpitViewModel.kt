package dev.gdk.bumblebee.ui.cockpit

import android.app.Application
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.data.local.MaintenancePlanEntity
import dev.gdk.bumblebee.data.local.toRecord
import dev.gdk.bumblebee.gps.PhoneGps
import dev.gdk.bumblebee.ride.CockpitInsights
import dev.gdk.bumblebee.ride.Geo
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.OdoMilestone
import dev.gdk.bumblebee.ride.OnThisDay
import dev.gdk.bumblebee.ride.PastRide
import dev.gdk.bumblebee.ride.RideRhythm
import dev.gdk.bumblebee.ride.SunTimes
import dev.gdk.bumblebee.ui.components.StatusChipTone
import dev.gdk.bumblebee.ui.garage.PlanDue
import dev.gdk.bumblebee.ui.garage.planDue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

enum class CockpitRidePhase { Start, Resume, Live }

data class LastRideSnippet(
    val id: Long,
    val km: Double,
    val movingSec: Int,
    val track: List<Pair<Float, Float>>,
)

data class CockpitServiceDue(
    val title: String,
    val due: PlanDue,
)

data class CockpitUiState(
    val connection: ConnectionState = ConnectionState.Idle,
    val speedKmh: Int? = null,
    val fuelBars: Int? = null,
    val odometerKm: Int? = null,
    val economyKml: Double? = null,
    val tripAKm: Double? = null,
    val tripBKm: Double? = null,
    val rangeKm: Double? = null,
    val tankKmPerL: Double? = null,
    val rupeesPerKm: Double? = null,
    val costPartial: Boolean = false,
    val serviceEta: String? = null,
    val phoneBatteryPercent: Int? = null,
    val bikeMac: String? = null,
    val gpsOn: Boolean = false,
    val todayKm: Double = 0.0,
    val todayMovingSec: Int = 0,
    val weatherTemp: String? = null,
    val weatherLabel: String? = null,
    val weatherCode: Int? = null,
    val serviceDueKm: Int? = null,
    val parked: GpsFix? = null,
    val ridePhase: CockpitRidePhase = CockpitRidePhase.Start,
    val lastRide: LastRideSnippet? = null,
    val servicePlan: CockpitServiceDue? = null,
    val readiness: ReadinessScore = ReadinessCalculator.score(null, false),
    val warnings: List<CockpitWarning> = emptyList(),
    val weatherTempC: Double? = null,
    val milestone: OdoMilestone? = null,
    /** One line suggesting a ride when the weather is fair. */
    val suggestion: String? = null,
    val onThisDay: OnThisDay? = null,
    /** "1.2 km away" from the parked bike, when the phone knows where it is. */
    val parkedDistance: String? = null,
)

class CockpitViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DashApp
    private val phoneGps = PhoneGps(application)
    private val refreshGate = Mutex()
    private var loadedTrackRideId: Long? = null
    private var loadedTrack: List<Pair<Float, Float>> = emptyList()

    private val _ui = MutableStateFlow(
        CockpitUiState(
            bikeMac = app.prefs.bikeMac,
            phoneBatteryPercent = phonePct(),
        ).derived(),
    )
    val ui: StateFlow<CockpitUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            app.connection.collect { c ->
                _ui.update { it.copy(connection = c, bikeMac = app.prefs.bikeMac).derived() }
            }
        }
        viewModelScope.launch {
            app.latestTelemetry.collect { t ->
                if (t != null) {
                    val previous = _ui.value.odometerKm
                    _ui.update {
                        it.copy(
                            speedKmh = t.speedKmh,
                            fuelBars = t.fuelBars,
                            odometerKm = t.odometerKm,
                            economyKml = t.fuelEconKmlV2 ?: t.fuelEconKml,
                            tripAKm = t.tripAKm,
                            tripBKm = t.tripBKm,
                            serviceDueKm = app.prefs.remainingServiceKm(t.odometerKm),
                        ).derived()
                    }
                    if (previous != t.odometerKm) refreshToday()
                }
            }
        }
        viewModelScope.launch {
            app.latestGps.collect { gps ->
                val acc = gps?.accuracyM
                val on = gps != null &&
                    System.currentTimeMillis() - gps.ts <= 5_000L &&
                    (acc == null || acc <= 25f)
                _ui.update { it.copy(gpsOn = on).derived() }
            }
        }
        viewModelScope.launch {
            app.phoneBattery.collect { pct ->
                _ui.update { it.copy(phoneBatteryPercent = pct ?: phonePct()).derived() }
            }
        }
        viewModelScope.launch {
            app.weather.snapshot.collect { wx ->
                _ui.update {
                    it.copy(
                        weatherTemp = wx?.let { w -> "${w.tempC.toInt()}°C" },
                        weatherTempC = wx?.tempC,
                        weatherLabel = wx?.label,
                        weatherCode = wx?.code,
                    ).derived().withSuggestion(sunPlace(it.parked))
                }
            }
        }
        viewModelScope.launch {
            app.capture.observeFuel().collect {
                val bars = _ui.value.fuelBars
                val eco = withContext(Dispatchers.IO) { app.capture.bikeEconomics(bars) }
                _ui.update {
                    it.copy(
                        rangeKm = eco.rangeKm,
                        tankKmPerL = eco.tankKmPerL,
                        rupeesPerKm = eco.cost?.rollingRupeesPerKm,
                        costPartial = eco.cost?.isPartial == true,
                    ).derived()
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(if (app.capture.isRecording) 2_000 else 15_000)
                refreshToday()
            }
        }
        viewModelScope.launch {
            app.capture.observeFinishedRides().collect { refreshToday() }
        }
    }

    private suspend fun refreshToday() = refreshGate.withLock {
        val snapshot = withContext(Dispatchers.IO) {
            val today = app.capture.todayStats()
            val parked = app.capture.lastParked()
            val paused = app.capture.isRidePaused()
            val recording = app.capture.isRecording
            val phase = when {
                paused -> CockpitRidePhase.Resume
                recording -> CockpitRidePhase.Live
                else -> CockpitRidePhase.Start
            }
            val last = app.capture.lastFinished()
            val track = when {
                last == null -> emptyList()
                last.id == loadedTrackRideId -> loadedTrack
                else -> downsample(app.capture.trackForRide(last.id)).also {
                    loadedTrackRideId = last.id
                    loadedTrack = it
                }
            }
            val odo = _ui.value.odometerKm ?: app.capture.latestKnownOdometerKm()
            val due = app.prefs.remainingServiceKm(odo)
            val finished = app.capture.finishedRides()
            val rides = finished.map { it.toRecord() }
            val eta = RideRhythm.serviceEta(due, RideRhythm.paceKmPerDay(rides))
            val zone = ZoneId.systemDefault()
            val past = finished
                .filter { !it.isDemo && it.mergedIntoId == null && it.gpsDistanceM > 0 }
                .map { PastRide(it.id, it.startTs, it.gpsDistanceM / 1000.0, rideLabel(it.name, it.startPlace, it.endPlace)) }
            val onThisDay = CockpitInsights.onThisDay(past, LocalDate.now(zone), zone)
            val parkedDistance = parked?.let { spot ->
                val here = app.latestGps.value?.takeIf { System.currentTimeMillis() - it.ts < 10 * 60_000L }
                    ?: phoneGps.lastKnown()?.takeIf { System.currentTimeMillis() - it.ts < 60 * 60_000L }
                here?.let { CockpitInsights.distanceLabel(Geo.haversineMeters(it.lat, it.lng, spot.lat, spot.lng)) }
            }
            TodaySnapshot(
                todayKm = today.km,
                todayMovingSec = today.movingSec,
                parked = parked,
                phase = phase,
                lastRide = last?.let {
                    LastRideSnippet(it.id, it.gpsDistanceM / 1000.0, it.movingSec, track)
                },
                servicePlan = soonestPlan(
                    app.capture.allPlans().filter { it.active },
                    odo,
                    app.prefs.remindKm,
                    app.prefs.remindDays,
                ),
                odometerKm = odo,
                serviceDueKm = due,
                serviceEta = eta?.label(),
                onThisDay = onThisDay,
                parkedDistance = parkedDistance,
            )
        }
        _ui.update {
            it.copy(
                todayKm = snapshot.todayKm,
                todayMovingSec = snapshot.todayMovingSec,
                parked = snapshot.parked,
                ridePhase = snapshot.phase,
                lastRide = snapshot.lastRide,
                servicePlan = snapshot.servicePlan,
                odometerKm = it.odometerKm ?: snapshot.odometerKm,
                serviceDueKm = snapshot.serviceDueKm,
                serviceEta = snapshot.serviceEta,
                onThisDay = snapshot.onThisDay,
                parkedDistance = snapshot.parkedDistance,
            ).derived().withSuggestion(sunPlace(snapshot.parked))
        }
    }

    /** Where to take sunrise and sunset from: the phone now, else the parked bike, else the last ride. */
    private fun sunPlace(parked: GpsFix?): Pair<Double, Double>? =
        app.latestGps.value?.let { it.lat to it.lng }
            ?: parked?.let { it.lat to it.lng }
            ?: loadedTrack.firstOrNull()?.let { it.first.toDouble() to it.second.toDouble() }

    private fun CockpitUiState.withSuggestion(place: Pair<Double, Double>?): CockpitUiState {
        if (ridePhase != CockpitRidePhase.Start) return copy(suggestion = null)
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val sun = place?.let { (lat, lng) -> SunTimes.of(now.toLocalDate(), lat, lng, zone) }
        return copy(suggestion = CockpitInsights.rideSuggestion(weatherCode, weatherTempC, now, sun))
    }

    private fun phonePct(): Int? {
        val bm = getApplication<Application>().getSystemService(BatteryManager::class.java) ?: return null
        val v = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return v.takeIf { it in 0..100 }
    }
}

private data class TodaySnapshot(
    val todayKm: Double,
    val todayMovingSec: Int,
    val parked: GpsFix?,
    val phase: CockpitRidePhase,
    val lastRide: LastRideSnippet?,
    val servicePlan: CockpitServiceDue?,
    val odometerKm: Int?,
    val serviceDueKm: Int?,
    val serviceEta: String?,
    val onThisDay: OnThisDay?,
    val parkedDistance: String?,
)

/** The ride's name, else where it went, else plain "Ride". */
private fun rideLabel(name: String?, from: String?, to: String?): String = when {
    !name.isNullOrBlank() -> name
    !from.isNullOrBlank() && !to.isNullOrBlank() && from != to -> "$from → $to"
    !to.isNullOrBlank() -> to
    else -> "Ride"
}

private fun soonestPlan(
    plans: List<MaintenancePlanEntity>,
    odometerKm: Int?,
    remindKm: Int,
    remindDays: Int,
): CockpitServiceDue? {
    val today = LocalDate.now()
    val ranked = plans.mapNotNull { plan ->
        val due = planDue(
            currentOdoKm = odometerKm,
            lastDoneOdoKm = plan.lastDoneOdoKm,
            intervalKm = plan.intervalKm,
            lastDoneOn = plan.lastDoneOnIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            intervalDays = plan.intervalDays,
            dueOn = plan.dueOnIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            today = today,
            remindKm = remindKm,
            remindDays = remindDays,
        )
        if (due.tone == StatusChipTone.NoExpiry) null else CockpitServiceDue(plan.title, due)
    }
    return ranked.minWithOrNull(
        compareBy<CockpitServiceDue> { toneRank(it.due.tone) }.thenByDescending { it.due.fraction },
    )
}

private fun toneRank(tone: StatusChipTone): Int = when (tone) {
    StatusChipTone.Overdue -> 0
    StatusChipTone.DueSoon -> 1
    StatusChipTone.Ok -> 2
    StatusChipTone.NoExpiry -> 3
}

private fun downsample(fixes: List<GpsFix>, maxPoints: Int = 48): List<Pair<Float, Float>> {
    if (fixes.isEmpty()) return emptyList()
    if (fixes.size <= maxPoints) return fixes.map { it.lat.toFloat() to it.lng.toFloat() }
    val step = (fixes.size - 1).toFloat() / (maxPoints - 1)
    return List(maxPoints) { index ->
        val point = fixes[(index * step).toInt().coerceIn(0, fixes.lastIndex)]
        point.lat.toFloat() to point.lng.toFloat()
    }
}

private fun CockpitUiState.derived(bikeMac: String? = this.bikeMac): CockpitUiState {
    val bleReady = connection is ConnectionState.Ready
    val scored = ReadinessCalculator.score(
        fuelBars,
        bleReady,
        phoneBatteryPercent,
        gpsOn,
        weatherCode,
        weatherLabel,
        serviceDueKm,
        bikePaired = bikeMac != null,
        linkFailed = connection is ConnectionState.Failed,
    )
    val nextWarnings = buildList {
        if (bikeMac == null) {
            add(CockpitWarning(WarningSeverity.INFO, "Pair your bike", "Garage → scan and save the MAC.", WarningFix.Settings))
        } else if (connection is ConnectionState.Failed) {
            add(CockpitWarning(WarningSeverity.WARN, "Couldn't reach the bike", "Tap Connect in Garage.", WarningFix.Settings))
        }
        val f = fuelBars
        if (f != null && f <= 1) {
            add(CockpitWarning(WarningSeverity.WARN, "Low fuel", "$f bar remaining.", WarningFix.Fuel))
        }
        val due = serviceDueKm
        if (due != null && due <= 0) {
            add(CockpitWarning(WarningSeverity.WARN, "Service overdue", "Log the last service in Garage.", WarningFix.Service))
        }
    }
    return copy(
        readiness = scored,
        warnings = nextWarnings,
        milestone = odometerKm?.takeIf { it > 0 }?.let { CockpitInsights.milestone(it) },
    )
}
