package dev.gdk.bumblebee.ui.journey

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.data.local.RideEntity
import dev.gdk.bumblebee.journey.BigMode
import dev.gdk.bumblebee.journey.JourneyPrefs
import dev.gdk.bumblebee.journey.SavedPlace
import dev.gdk.bumblebee.ride.FuelEventType
import dev.gdk.bumblebee.ride.GarageInsights
import dev.gdk.bumblebee.ride.Geo
import dev.gdk.bumblebee.ride.JourneyEntry
import dev.gdk.bumblebee.ride.JourneyInsights
import dev.gdk.bumblebee.ride.JourneyStats
import dev.gdk.bumblebee.ride.SunTimes
import dev.gdk.bumblebee.ride.TrackPlot
import dev.gdk.bumblebee.ride.VisitedPlace
import dev.gdk.bumblebee.ui.explorer.toFact
import dev.gdk.bumblebee.ui.fuel.FuelTrends
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** A past ride that can be ridden again through Google Maps. */
data class RouteAgain(val rideId: Long, val label: String, val km: Double, val dateTs: Long, val url: String)

data class JourneyUi(
    val places: List<SavedPlace> = emptyList(),
    val visited: List<VisitedPlace> = emptyList(),
    val newPlace: VisitedPlace? = null,
    val routes: List<RouteAgain> = emptyList(),
    val log: List<JourneyEntry> = emptyList(),
    val stats: JourneyStats? = null,
    val autoRecord: Boolean = false,
    val bigMode: BigMode = BigMode.AUTO,
    /** Range left in the tank at the rider's own economy, km. */
    val rangeKm: Double? = null,
    /** Pumps the rider has used near here. */
    val pumps: List<String> = emptyList(),
    val sunset: LocalDateTime? = null,
    val darkOrWet: Boolean = false,
    val sharedEtaMinute: Int? = null,
)

class JourneyViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DashApp
    private val prefs = JourneyPrefs(application)
    private val zone = ZoneId.systemDefault()
    private val _ui = MutableStateFlow(JourneyUi())
    val ui: StateFlow<JourneyUi> = _ui.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val built = withContext(Dispatchers.IO) { build() }
            _ui.value = built
        }
    }

    private suspend fun build(): JourneyUi {
        val rides = app.capture.finishedRides().filter { !it.isDemo && it.mergedIntoId == null }
        val visitedRaw = rides.map { r ->
            val end = if (r.endPlace.isNullOrBlank()) null else app.capture.lastPointFor(r.id)
            Triple(r.endPlace, r.startTs, end?.let { it.lat to it.lng })
        }
        val visited = JourneyInsights.places(visitedRaw)
        val latest = rides.maxByOrNull { it.startTs }
        val fresh = JourneyInsights.firstVisit(visited, latest?.endPlace)
            ?.takeIf { latest != null && System.currentTimeMillis() - latest.startTs < 3 * 86_400_000L }
        // A list, not a sequence: routeOf suspends, and only inline list lambdas may call it.
        val routes = rides
            .map { it to it.toFact() }
            .filter { (_, f) -> f.km >= 15 }
            .take(6)
            .mapNotNull { (r, f) -> routeOf(r, f.label, f.km) }
        val log = prefs.log
        val gps = app.latestGps.value ?: app.capture.lastParked()
        val bars = app.latestTelemetry.value?.fuelBars
        val range = app.capture.bikeEconomics(bars).rangeKm
        val pumps = gps?.let { here ->
            app.capture.allFuel()
                .filter { it.type == FuelEventType.REFUEL.name && it.lat != null && it.lng != null }
                .filter { Geo.haversineMeters(here.lat, here.lng, it.lat!!, it.lng!!) < 20_000 }
                .mapNotNull { FuelTrends.station(it.note) }
                .groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key }
        }.orEmpty()
        val sun = gps?.let { SunTimes.of(LocalDate.now(zone), it.lat, it.lng, zone) }
        val sunset = sun?.sunsetTs?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
        val now = System.currentTimeMillis()
        val set = sun?.sunsetTs
        val rise = sun?.sunriseTs
        val dark = (set != null && now > set) || (rise != null && now < rise)
        val wet = GarageInsights.isWet(app.weather.snapshot.value?.code)
        return JourneyUi(
            places = prefs.places,
            visited = visited,
            newPlace = fresh,
            routes = routes,
            log = log.sortedByDescending { it.startTs },
            stats = JourneyInsights.stats(log).takeIf { log.isNotEmpty() },
            autoRecord = prefs.autoRecord,
            bigMode = prefs.bigMode,
            rangeKm = range,
            pumps = pumps,
            sunset = sunset,
            darkOrWet = dark || wet,
            sharedEtaMinute = prefs.sharedEtaMinute,
        )
    }

    private suspend fun routeOf(r: RideEntity, label: String, km: Double): RouteAgain? {
        val track = TrackPlot.downsample(app.capture.trackForRide(r.id), 120)
        val url = JourneyInsights.routeUrl(track.map { it.lat to it.lng }) ?: return null
        return RouteAgain(r.id, label, km, r.startTs, url)
    }

    /** Remember where the rider is heading, so the journey log can name the trip. */
    fun heading(destination: String) {
        prefs.pendingDestination = destination
    }

    fun addPlace(name: String, query: String) {
        prefs.places = (prefs.places.filter { !it.name.equals(name, ignoreCase = true) } + SavedPlace(name.trim(), query.trim())).take(8)
        dev.gdk.bumblebee.widget.HomeWidgets.refresh(app, dev.gdk.bumblebee.widget.HomeWidget.PLACES)
        refresh()
    }

    fun removePlace(place: SavedPlace) {
        prefs.places = prefs.places - place
        dev.gdk.bumblebee.widget.HomeWidgets.refresh(app, dev.gdk.bumblebee.widget.HomeWidget.PLACES)
        refresh()
    }

    fun setAutoRecord(on: Boolean) {
        prefs.autoRecord = on
        _ui.update { it.copy(autoRecord = on) }
    }

    fun setBigMode(mode: BigMode) {
        prefs.bigMode = mode
        _ui.update { it.copy(bigMode = mode) }
    }

    fun sharedEta(minuteOfDay: Int) {
        prefs.sharedEtaMinute = minuteOfDay
        _ui.update { it.copy(sharedEtaMinute = minuteOfDay) }
    }
}
