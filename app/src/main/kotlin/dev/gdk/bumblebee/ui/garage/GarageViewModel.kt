package dev.gdk.bumblebee.ui.garage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.ui.components.StatusChipTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

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
    /** Newest stored odometer, shown when no live sample has arrived yet. */
    val storedOdometerKm: Int? = null,
    val nextService: NextService? = null,
)

/** The plan closest to due, for the hub's Service row. */
data class NextService(val title: String, val caption: String)

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
            val stored = if (odo == null) withContext(Dispatchers.IO) { app.capture.latestKnownOdometerKm() } else null
            val snap = reloadGarage(app, fuel, odo ?: stored)
            _ui.update {
                it.copy(
                    storedOdometerKm = stored,
                    nextService = nextService(snap.plans, odo ?: stored),
                    bikeMac = app.prefs.bikeMac,
                    tankKmPerL = snap.tankKmPerL,
                    documents = snap.docs,
                    plans = snap.plans,
                    upcoming = snap.upcoming,
                )
            }
        }
    }

    private fun nextService(plans: List<PlanCard>, odoKm: Int?): NextService? {
        val today = LocalDate.now()
        val rank = listOf(StatusChipTone.Overdue, StatusChipTone.DueSoon, StatusChipTone.Ok)
        return plans
            .map { plan ->
                plan to planDue(
                    currentOdoKm = odoKm,
                    lastDoneOdoKm = plan.lastDoneOdoKm,
                    intervalKm = plan.intervalKm,
                    lastDoneOn = plan.lastDoneOnIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    intervalDays = plan.intervalDays,
                    dueOn = plan.dueOnIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    today = today,
                    remindKm = app.prefs.remindKm,
                    remindDays = app.prefs.remindDays,
                )
            }
            .filter { (_, due) -> due.tone in rank }
            .minWithOrNull(compareBy({ rank.indexOf(it.second.tone) }, { -it.second.fraction }))
            ?.let { (plan, due) -> NextService(plan.title, due.caption) }
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
