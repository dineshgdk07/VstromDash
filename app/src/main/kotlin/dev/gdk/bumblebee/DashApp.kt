package dev.gdk.bumblebee

import android.app.Application
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.ble.android.BikePrefs
import dev.gdk.bumblebee.data.local.BumblebeeDb
import dev.gdk.bumblebee.data.local.RideCaptureRepository
import dev.gdk.bumblebee.protocol.TelemetryFrame
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class DashApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database: BumblebeeDb by lazy { BumblebeeDb.create(this) }
    val prefs: BikePrefs by lazy { BikePrefs(this) }
    val capture: RideCaptureRepository by lazy { RideCaptureRepository(database) }
    val connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val latestTelemetry = MutableStateFlow<TelemetryFrame?>(null)
    val latestGps = MutableStateFlow<GpsFix?>(null)
    val phoneBattery = MutableStateFlow<Int?>(null)
    val weather = WeatherRepository()

    /** Set before opening the Ride tab to show that ride. The Ride screen clears it. */
    val pendingRideId = MutableStateFlow<Long?>(null)

    override fun onCreate() {
        super.onCreate()
        dev.gdk.bumblebee.bike.BikeProfile(this).applyAccent()
        appScope.launch {
            capture.restoreFuelDetector()
            capture.recoverUnfinishedRides()
            capture.lastParked()?.let { weather.maybeRefresh(it.lat, it.lng) }
        }
    }
}
