package dev.gdk.bumblebee.ui.hud

import android.app.Application
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.data.local.toRecord
import dev.gdk.bumblebee.nav.MapsNavSource
import dev.gdk.bumblebee.nav.formatHudDistance
import dev.gdk.bumblebee.ride.CompassCardinal
import dev.gdk.bumblebee.ride.ResolvedSpeed
import dev.gdk.bumblebee.ride.RideRhythm
import dev.gdk.bumblebee.ride.SpeedResolver
import dev.gdk.bumblebee.ride.SpeedSource
import dev.gdk.bumblebee.ui.cockpit.ReadinessCalculator
import dev.gdk.bumblebee.ui.cockpit.ReadinessScore
import dev.gdk.bumblebee.weather.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class HudUiState(
    val speed: ResolvedSpeed = ResolvedSpeed(0, SpeedSource.NONE),
    val heading: String? = null,
    val fuelBars: Int? = null,
    val economyKml: Double? = null,
    val odometerKm: Int? = null,
    val tripAKm: Double? = null,
    val tripBKm: Double? = null,
    val rangeKm: Double? = null,
    val serviceDueKm: Int? = null,
    val serviceEta: String? = null,
    val bleReady: Boolean = false,
    val phoneBatteryPercent: Int? = null,
    val navStreet: String? = null,
    val navDist: String? = null,
    val gpsOn: Boolean = false,
    val todayKm: Double = 0.0,
    val todayMovingSec: Int = 0,
    val readiness: ReadinessScore = ReadinessCalculator.score(null, false),
    val weather: WeatherSnapshot? = null,
)

class HudViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DashApp
    private val _ui = MutableStateFlow(HudUiState(phoneBatteryPercent = app.phoneBattery.value ?: phonePct()))
    val ui: StateFlow<HudUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            var rangeKm: Double? = null
            var todayKm = 0.0
            var todayMoving = 0
            var serviceEta: String? = null
            launch(Dispatchers.IO) {
                combine(app.latestTelemetry, app.capture.observeFuel()) { tel, _ -> tel }
                    .collect { tel ->
                        val eco = app.capture.bikeEconomics(tel?.fuelBars)
                        rangeKm = eco.rangeKm
                    }
            }
            launch(Dispatchers.IO) {
                app.capture.observeFinishedRides().collect { rides ->
                    val due = app.prefs.remainingServiceKm(app.latestTelemetry.value?.odometerKm)
                    serviceEta = RideRhythm.serviceEta(due, RideRhythm.paceKmPerDay(rides.map { it.toRecord() }))?.label()
                    val today = app.capture.todayStats()
                    todayKm = today.km
                    todayMoving = today.movingSec
                }
            }
            val live = combine(app.latestTelemetry, app.latestGps, app.connection) { tel, gps, conn ->
                Triple(tel, gps, conn)
            }
            val extra = combine(MapsNavSource.frame, app.weather.snapshot, app.phoneBattery) { nav, wx, batt ->
                Triple(nav, wx, batt)
            }
            combine(live, extra) { a, b -> a to b }.collect { packed ->
                val tel = packed.first.first
                val gps = packed.first.second
                val conn = packed.first.third
                val nav = packed.second.first
                val wx = packed.second.second
                val batt = packed.second.third ?: phonePct()
                val now = System.currentTimeMillis()
                val speed = SpeedResolver.resolve(gps, now, tel?.speedKmh)
                val bleReady = conn is ConnectionState.Ready
                val acc = gps?.accuracyM
                val gpsOn = gps != null && now - gps.ts <= 5_000L && (acc == null || acc <= 25f)
                val due = app.prefs.remainingServiceKm(tel?.odometerKm)
                val pct = batt
                _ui.value = HudUiState(
                    speed = speed,
                    heading = gps
                        ?.bearingDeg
                        ?.takeIf { now - gps.ts <= SpeedResolver.MAX_GPS_AGE_MS }
                        ?.let { CompassCardinal.label(it) },
                    fuelBars = tel?.fuelBars,
                    economyKml = tel?.fuelEconKmlV2 ?: tel?.fuelEconKml,
                    odometerKm = tel?.odometerKm,
                    tripAKm = tel?.tripAKm,
                    tripBKm = tel?.tripBKm,
                    rangeKm = rangeKm,
                    serviceDueKm = due,
                    serviceEta = serviceEta,
                    bleReady = bleReady,
                    phoneBatteryPercent = pct,
                    navStreet = MapsNavSource.street.value,
                    navDist = nav?.let { formatHudDistance(it.distNext, it.distNextUnit) },
                    gpsOn = gpsOn,
                    todayKm = todayKm,
                    todayMovingSec = todayMoving,
                    readiness = ReadinessCalculator.score(
                        tel?.fuelBars,
                        bleReady,
                        pct,
                        gpsOn,
                        wx?.code,
                        wx?.label,
                        due,
                        bikePaired = app.prefs.bikeMac != null,
                        linkFailed = conn is ConnectionState.Failed,
                    ),
                    weather = wx,
                )
            }
        }
    }

    private fun phonePct(): Int? {
        val bm = getApplication<Application>().getSystemService(BatteryManager::class.java) ?: return null
        val v = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return v.takeIf { it in 0..100 }
    }
}
