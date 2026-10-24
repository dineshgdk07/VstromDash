package dev.gdk.bumblebee.ui.garage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ble.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GarageUiState(
    val connection: ConnectionState = ConnectionState.Idle,
    val bikeMac: String? = null,
    val odometerKm: Int? = null,
    val tripAKm: Double? = null,
    val tripBKm: Double? = null,
    val fuelBars: Int? = null,
    val tankKmPerL: Double? = null,
    val documents: List<DocCard> = emptyList(),
    val plans: List<PlanCard> = emptyList(),
    val upcoming: List<UpcomingItem> = emptyList(),
)

class GarageViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DashApp
    private val _ui = MutableStateFlow(fromPrefs())
    val ui: StateFlow<GarageUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            app.connection.collect { connection ->
                _ui.update { it.copy(connection = connection, bikeMac = app.prefs.bikeMac) }
            }
        }
        viewModelScope.launch {
            app.latestTelemetry.collect { sample ->
                if (sample == null) return@collect
                val previous = _ui.value
                val changed = previous.odometerKm != sample.odometerKm || previous.fuelBars != sample.fuelBars
                _ui.update {
                    it.copy(
                        odometerKm = sample.odometerKm,
                        tripAKm = sample.tripAKm,
                        tripBKm = sample.tripBKm,
                        fuelBars = sample.fuelBars,
                    )
                }
                if (changed) refresh()
            }
        }
        refresh()
    }

    fun refresh() {
        val fuel = _ui.value.fuelBars
        val odo = _ui.value.odometerKm
        viewModelScope.launch {
            val snap = reloadGarage(app, fuel, odo)
            _ui.update {
                it.copy(
                    bikeMac = app.prefs.bikeMac,
                    tankKmPerL = snap.tankKmPerL,
                    documents = snap.docs,
                    plans = snap.plans,
                    upcoming = snap.upcoming,
                )
            }
        }
    }

    private fun fromPrefs(): GarageUiState {
        val sample = app.latestTelemetry.value
        return GarageUiState(
            connection = app.connection.value,
            bikeMac = app.prefs.bikeMac,
            odometerKm = sample?.odometerKm,
            tripAKm = sample?.tripAKm,
            tripBKm = sample?.tripBKm,
            fuelBars = sample?.fuelBars,
        )
    }
}
