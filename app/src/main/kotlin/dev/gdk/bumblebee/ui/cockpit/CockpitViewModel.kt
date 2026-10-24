package dev.gdk.bumblebee.ui.cockpit

import android.app.Application
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.data.local.toRecord
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.RideRhythm
import dev.gdk.bumblebee.ui.cockpit.components.CockpitWarning
import dev.gdk.bumblebee.ui.cockpit.components.WarningSeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val readiness: ReadinessScore = ReadinessCalculator.score(null, false),
    val warnings: List<CockpitWarning> = emptyList(),
)

class CockpitViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DashApp

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
                        weatherLabel = wx?.label,
                        weatherCode = wx?.code,
                    ).derived()
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

    private suspend fun refreshToday() {
        val today = withContext(Dispatchers.IO) { app.capture.todayStats() }
        val parked = withContext(Dispatchers.IO) { app.capture.lastParked() }
        val due = app.prefs.remainingServiceKm(_ui.value.odometerKm)
        val eta = withContext(Dispatchers.IO) {
            val rides = app.capture.finishedRides().map { it.toRecord() }
            RideRhythm.serviceEta(due, RideRhythm.paceKmPerDay(rides))
        }
        _ui.update {
            it.copy(
                todayKm = today.km,
                todayMovingSec = today.movingSec,
                serviceDueKm = due,
                parked = parked,
                serviceEta = eta?.label(),
            ).derived()
        }
    }

    private fun phonePct(): Int? {
        val bm = getApplication<Application>().getSystemService(BatteryManager::class.java) ?: return null
        val v = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return v.takeIf { it in 0..100 }
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
            add(CockpitWarning(WarningSeverity.INFO, "Pair your bike", "Garage → scan and save the MAC."))
        } else if (connection is ConnectionState.Failed) {
            add(CockpitWarning(WarningSeverity.WARN, "Couldn't reach the bike", "Tap Connect in Garage."))
        }
        val f = fuelBars
        if (f != null && f <= 1) {
            add(CockpitWarning(WarningSeverity.WARN, "Low fuel", "$f bar remaining."))
        }
        val due = serviceDueKm
        if (due != null && due <= 0) {
            add(CockpitWarning(WarningSeverity.WARN, "Service overdue", "Log the last service in Garage."))
        }
    }
    return copy(readiness = scored, warnings = nextWarnings)
}
