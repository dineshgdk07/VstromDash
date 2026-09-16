package dev.gdk.bumblebee.ui.cockpit

import android.app.Application
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ble.BikeBridgeService
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.ble.android.BleScanner
import dev.gdk.bumblebee.ble.android.DiscoveredBike
import dev.gdk.bumblebee.data.local.CaptureTick
import dev.gdk.bumblebee.data.local.RideSession
import dev.gdk.bumblebee.data.local.asTicks
import dev.gdk.bumblebee.ride.DemoRideSource
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
    val running: Boolean = false,
    val speedKmh: Int? = null,
    val fuelBars: Int? = null,
    val odometerKm: Int? = null,
    val economyKml: Double? = null,
    val tripAKm: Double? = null,
    val phoneBatteryPercent: Int? = null,
    val status: String = "Scan to pair the bike, or run a demo ride without one.",
    val bikeMac: String? = null,
    val scanning: Boolean = false,
    val devices: List<DiscoveredBike> = emptyList(),
    val gpsOn: Boolean = false,
    val readiness: ReadinessScore = ReadinessCalculator.score(null, false),
    val warnings: List<CockpitWarning> = emptyList(),
)

class CockpitViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DashApp
    private val session = RideSession(app.capture)
    private val scanner = BleScanner(application)

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
                            economyKml = t.fuelEconKml,
                            tripAKm = t.tripAKm,
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
                _ui.update { it.copy(gpsOn = on) }
            }
        }
        viewModelScope.launch {
            scanner.results.collect { map ->
                val sorted = map.values.sortedWith(
                    compareByDescending<DiscoveredBike> { it.looksLikeSuzuki }
                        .thenByDescending { it.rssi },
                )
                _ui.update { it.copy(devices = sorted) }
            }
        }
    }

    fun startScan() {
        scanner.start()
        _ui.update { it.copy(scanning = true, status = "Scanning…") }
    }

    fun stopScan() {
        scanner.stop()
        _ui.update { it.copy(scanning = false) }
    }

    fun pair(mac: String) {
        stopScan()
        app.prefs.bikeMac = mac
        _ui.update { it.copy(bikeMac = mac, status = "Connecting $mac") }
        BikeBridgeService.start(getApplication())
    }

    fun connectSaved() {
        val mac = app.prefs.bikeMac ?: return
        pair(mac)
    }

    fun disconnectBike() {
        BikeBridgeService.stop(getApplication())
        _ui.update { it.copy(status = "Disconnecting…") }
    }

    fun startDemo() {
        if (_ui.value.running) return
        if (_ui.value.connection is ConnectionState.Ready) {
            _ui.update { it.copy(status = "Stop the live bike link before running demo.") }
            return
        }
        viewModelScope.launch {
            _ui.update {
                it.copy(running = true, connection = ConnectionState.Ready, status = "Capturing demo ride…").derived()
            }
            try {
                val ride = withContext(Dispatchers.IO) {
                    val ticks = DemoRideSource().asTicks()
                    session.run(ticks) { tick ->
                        if (tick is CaptureTick.Telemetry) {
                            _ui.update { s ->
                                s.copy(
                                    speedKmh = tick.frame.speedKmh,
                                    fuelBars = tick.frame.fuelBars,
                                    odometerKm = tick.frame.odometerKm,
                                    economyKml = tick.frame.fuelEconKml,
                                    tripAKm = tick.frame.tripAKm,
                                ).derived()
                            }
                        }
                    }
                }
                val km = (ride.odoEndKm ?: 0) - (ride.odoStartKm ?: 0)
                _ui.update {
                    it.copy(
                        running = false,
                        connection = app.connection.value,
                        status = "Ride archived · ${ride.movingSec}s moving · ${km} km (odo)",
                    ).derived()
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        running = false,
                        connection = ConnectionState.Failed(e.message ?: "error"),
                        status = e.message ?: "Demo ride failed",
                    ).derived()
                }
            }
        }
    }

    override fun onCleared() {
        scanner.stop()
        super.onCleared()
    }

    private fun phonePct(): Int? {
        val bm = getApplication<Application>().getSystemService(BatteryManager::class.java) ?: return null
        val v = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return v.takeIf { it in 0..100 }
    }
}

private fun CockpitUiState.derived(): CockpitUiState {
    val bleReady = connection is ConnectionState.Ready
    val scored = ReadinessCalculator.score(fuelBars, bleReady, phoneBatteryPercent)
    val nextWarnings = buildList {
        if (connection !is ConnectionState.Ready) {
            add(CockpitWarning(WarningSeverity.CRITICAL, "BLE disconnected", "Reconnect or pair the bike."))
        }
        val f = fuelBars
        if (f != null && f <= 1) {
            add(CockpitWarning(WarningSeverity.WARN, "Low fuel", "$f bar remaining."))
        }
    }
    return copy(readiness = scored, warnings = nextWarnings)
}
