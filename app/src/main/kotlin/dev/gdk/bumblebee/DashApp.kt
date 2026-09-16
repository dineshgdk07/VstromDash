package dev.gdk.bumblebee

import android.app.Application
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.ble.android.BikePrefs
import dev.gdk.bumblebee.data.local.BumblebeeDb
import dev.gdk.bumblebee.data.local.RideCaptureRepository
import dev.gdk.bumblebee.protocol.TelemetryFrame
import dev.gdk.bumblebee.ride.GpsFix
import kotlinx.coroutines.flow.MutableStateFlow

class DashApp : Application() {
    val database: BumblebeeDb by lazy { BumblebeeDb.create(this) }
    val prefs: BikePrefs by lazy { BikePrefs(this) }
    val capture: RideCaptureRepository by lazy { RideCaptureRepository(database) }
    val connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val latestTelemetry = MutableStateFlow<TelemetryFrame?>(null)
    val latestGps = MutableStateFlow<GpsFix?>(null)
}
