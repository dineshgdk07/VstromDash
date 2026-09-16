package dev.gdk.bumblebee.ui.cockpit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.data.local.CaptureTick
import dev.gdk.bumblebee.data.local.RideCaptureRepository
import dev.gdk.bumblebee.data.local.RideSession
import dev.gdk.bumblebee.data.local.asTicks
import dev.gdk.bumblebee.ride.DemoRideSource
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
    val status: String = "Demo ride records a 10-minute urban loop into the archive.",
)

class CockpitViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = RideCaptureRepository((application as DashApp).database)
    private val session = RideSession(repo)

    private val _ui = MutableStateFlow(CockpitUiState())
    val ui: StateFlow<CockpitUiState> = _ui.asStateFlow()

    fun startDemo() {
        if (_ui.value.running) return
        viewModelScope.launch {
            _ui.update {
                it.copy(running = true, connection = ConnectionState.Ready, status = "Capturing demo ride…")
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
                                )
                            }
                        }
                    }
                }
                val km = (ride.odoEndKm ?: 0) - (ride.odoStartKm ?: 0)
                _ui.update {
                    it.copy(
                        running = false,
                        connection = ConnectionState.Idle,
                        status = "Ride archived · ${ride.movingSec}s moving · ${km} km (odo)",
                    )
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        running = false,
                        connection = ConnectionState.Failed(e.message ?: "error"),
                        status = e.message ?: "Demo ride failed",
                    )
                }
            }
        }
    }
}
