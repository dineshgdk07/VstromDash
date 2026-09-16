package dev.gdk.bumblebee.ui.hud

import android.app.Application
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.ride.CompassCardinal
import dev.gdk.bumblebee.ride.ResolvedSpeed
import dev.gdk.bumblebee.ride.SpeedResolver
import dev.gdk.bumblebee.ride.SpeedSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HudUiState(
    val speed: ResolvedSpeed = ResolvedSpeed(0, SpeedSource.NONE),
    val heading: String? = null,
    val fuelBars: Int? = null,
    val economyKml: Double? = null,
    val odometerKm: Int? = null,
    val tripAKm: Double? = null,
    val bleReady: Boolean = false,
    val phoneBatteryPercent: Int? = null,
)

class HudViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DashApp
    private val _ui = MutableStateFlow(HudUiState(phoneBatteryPercent = phonePct()))
    val ui: StateFlow<HudUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                val gps = app.latestGps.value
                val tel = app.latestTelemetry.value
                val speed = SpeedResolver.resolve(gps, System.currentTimeMillis(), tel?.speedKmh)
                _ui.value = HudUiState(
                    speed = speed,
                    heading = gps
                        ?.bearingDeg
                        ?.takeIf { System.currentTimeMillis() - gps.ts <= SpeedResolver.MAX_GPS_AGE_MS }
                        ?.let { CompassCardinal.label(it) },
                    fuelBars = tel?.fuelBars,
                    economyKml = tel?.fuelEconKml,
                    odometerKm = tel?.odometerKm,
                    tripAKm = tel?.tripAKm,
                    bleReady = app.connection.value is ConnectionState.Ready,
                    phoneBatteryPercent = phonePct(),
                )
                delay(250)
            }
        }
    }

    private fun phonePct(): Int? {
        val bm = getApplication<Application>().getSystemService(BatteryManager::class.java) ?: return null
        val v = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return v.takeIf { it in 0..100 }
    }
}
