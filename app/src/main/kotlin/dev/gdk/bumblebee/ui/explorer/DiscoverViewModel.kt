package dev.gdk.bumblebee.ui.explorer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.data.local.RideEntity
import dev.gdk.bumblebee.ride.Badge
import dev.gdk.bumblebee.ride.Challenge
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.PersonalRecord
import dev.gdk.bumblebee.ride.RideFact
import dev.gdk.bumblebee.ride.RideHistory
import dev.gdk.bumblebee.ride.RidingDna
import dev.gdk.bumblebee.ride.Territory
import dev.gdk.bumblebee.ride.TrackPlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Two rides side by side. */
data class RideCompare(val a: RideFact, val b: RideFact, val trackA: List<GpsFix>, val trackB: List<GpsFix>)

data class DiscoverUi(
    val loaded: Boolean = false,
    val rides: List<RideFact> = emptyList(),
    val records: List<PersonalRecord> = emptyList(),
    val year: Int = LocalDate.now().year,
    val heat: Map<LocalDate, Double> = emptyMap(),
    val territory: Territory? = null,
    /** Every ride's track, thinned, for the territory map. */
    val tracks: Map<Long, List<GpsFix>> = emptyMap(),
    val badges: List<Badge> = emptyList(),
    val challenges: List<Challenge> = emptyList(),
    val dna: RidingDna? = null,
    val compare: RideCompare? = null,
)

/**
 * Everything the Discover screens show, from the whole ride history. Built once and shared by
 * Records, Year, Territory, Badges and Riding DNA; rebuilt when a ride finishes.
 */
class DiscoverViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DashApp
    private val zone = ZoneId.systemDefault()
    private val _ui = MutableStateFlow(DiscoverUi())
    val ui: StateFlow<DiscoverUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            app.capture.observeFinishedRides().collect { reload() }
        }
    }

    fun showYear(year: Int) {
        _ui.update { it.copy(year = year, heat = RideHistory.yearHeat(it.rides, year, zone)) }
    }

    fun compare(a: Long, b: Long) {
        viewModelScope.launch {
            val pair = withContext(Dispatchers.IO) {
                val ra = app.capture.rideById(a) ?: return@withContext null
                val rb = app.capture.rideById(b) ?: return@withContext null
                // Oldest on the left, so "then" and "now" read in order.
                val (first, second) = if (ra.startTs <= rb.startTs) ra to rb else rb to ra
                RideCompare(
                    first.toFact(),
                    second.toFact(),
                    TrackPlot.downsample(app.capture.trackForRide(first.id), 400),
                    TrackPlot.downsample(app.capture.trackForRide(second.id), 400),
                )
            }
            _ui.update { it.copy(compare = pair) }
        }
    }

    private suspend fun reload() {
        val built = withContext(Dispatchers.IO) {
            val entities = app.capture.finishedRides().filter { !it.isDemo && it.mergedIntoId == null }
            val rides = entities.map(RideEntity::toFact)
            val tracks = entities.associate { it.id to TrackPlot.downsample(app.capture.trackForRide(it.id), 300) }
            val territory = RideHistory.territory(rides, tracks)
            val newGround = { r: RideFact -> RideHistory.foundNewGround(r, territory, tracks) }
            val mostRuns = app.capture.routeStats().filter { !it.isUntracked }.maxOfOrNull { it.runCount } ?: 0
            val year = _ui.value.year
            DiscoverUi(
                loaded = true,
                rides = rides,
                records = RideHistory.records(rides, zone),
                year = year,
                heat = RideHistory.yearHeat(rides, year, zone),
                territory = territory,
                tracks = tracks,
                badges = RideHistory.badges(rides, territory, mostRuns, zone),
                challenges = RideHistory.challenges(rides, YearMonth.now(zone), newGround, zone),
                dna = RideHistory.dna(rides, newGround, zone),
                compare = _ui.value.compare,
            )
        }
        _ui.value = built
    }
}
