package dev.gdk.bumblebee.journey

import android.content.Context
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.gps.PhoneGps
import dev.gdk.bumblebee.nav.ArrowSource
import dev.gdk.bumblebee.nav.LatLng
import dev.gdk.bumblebee.nav.MapsNavSource
import dev.gdk.bumblebee.nav.NavProgress
import dev.gdk.bumblebee.nav.NavRoute
import dev.gdk.bumblebee.nav.ParsedNavData
import dev.gdk.bumblebee.nav.RouteGuide
import dev.gdk.bumblebee.nav.formatClusterDistance
import dev.gdk.bumblebee.nav.normalizeEta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/** In-app navigation as the screen sees it. */
data class NavState(
    val mode: NavMode,
    val destination: PlaceHit,
    val route: NavRoute?,
    val progress: NavProgress?,
    val here: LatLng?,
    val rerouting: Boolean = false,
    val error: String? = null,
    val arrived: Boolean = false,
)

/**
 * The app's own turn-by-turn, for the Google and OpenRouteService modes. It plans a route, follows
 * GPS along it with [RouteGuide], and posts each step to [MapsNavSource] exactly as the Maps
 * notification path does, so the cluster, trip card, journey log and auto-record all just work.
 */
object NavSession {
    const val KEY = "in-app"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var guide: RouteGuide? = null
    private var lastReroute = 0L

    private val _state = MutableStateFlow<NavState?>(null)
    val state: StateFlow<NavState?> = _state.asStateFlow()

    val active: Boolean get() = _state.value != null

    fun start(context: Context, destination: PlaceHit) {
        stop()
        val app = context.applicationContext as DashApp
        val settings = NavSettings(app)
        val routing = Routing.of(app, settings)
        if (routing == null) {
            _state.value = NavState(settings.mode, destination, null, null, null, error = "Add a ${settings.mode.label} key in Settings → Navigation first.")
            return
        }
        JourneyPrefs(app).pendingDestination = destination.label
        _state.value = NavState(settings.mode, destination, null, null, null, rerouting = true)
        job = scope.launch {
            val gps = PhoneGps(app)
            val first = app.latestGps.value ?: gps.lastKnown()
            if (first == null) {
                _state.value = _state.value?.copy(rerouting = false, error = "Waiting for a GPS fix. Step outside and try again.")
                return@launch
            }
            plan(routing, LatLng(first.lat, first.lng), destination) ?: return@launch
            gps.fixes().collect { fix -> onFix(routing, LatLng(fix.lat, fix.lng)) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        guide = null
        if (MapsNavSource.activeKey == KEY) MapsNavSource.clear()
        _state.value = null
    }

    private suspend fun plan(routing: Routing, from: LatLng, to: PlaceHit): NavRoute? {
        val route = runCatching { routing.route(from, to) }.getOrElse { e ->
            _state.value = _state.value?.copy(rerouting = false, error = e.message ?: "Couldn't plan a route.")
            return null
        }
        if (route.points.size < 2) {
            _state.value = _state.value?.copy(rerouting = false, error = "No route found to ${to.label}.")
            return null
        }
        guide = RouteGuide(route)
        lastReroute = System.currentTimeMillis()
        _state.value = _state.value?.copy(route = route, rerouting = false, error = null)
        onFix(routing, from)
        return route
    }

    private suspend fun onFix(routing: Routing, here: LatLng) {
        val g = guide ?: return
        val now = System.currentTimeMillis()
        val p = g.progress(here)
        _state.value = _state.value?.copy(progress = p, here = here)
        publish(p, g.route)
        if (p.arrived) {
            if (_state.value?.arrived == true) return
            _state.value = _state.value?.copy(arrived = true)
            // Leave the arrival flag up briefly, then finish.
            scope.launch {
                delay(15_000)
                if (_state.value?.arrived == true) stop()
            }
            return
        }
        // Off route for a while, and not re-planned in the last 20 s: ask for a new route.
        if (g.shouldReroute(p, now) && now - lastReroute > 20_000) {
            val current = _state.value ?: return
            _state.value = current.copy(rerouting = true)
            plan(routing, here, current.destination)
        }
    }

    private fun publish(p: NavProgress, route: NavRoute) {
        val step = p.step
        val arrow = if (p.arrived) dev.gdk.bumblebee.nav.ArrowKind.ARRIVE else step?.arrow ?: dev.gdk.bumblebee.nav.ArrowKind.STRAIGHT
        val (dn, du) = formatClusterDistance(if (p.arrived) 0.0 else p.toTurnM)
        val (dt, dtu) = formatClusterDistance(p.remainingM)
        val minutes = (p.remainingS / 60).roundToInt()
        MapsNavSource.update(
            ParsedNavData(
                maneuverId = arrow.clusterByte,
                distNext = dn,
                distNextUnit = du,
                eta = normalizeEta("$minutes min", twelveHour = true),
                distTotal = dt,
                distTotalUnit = dtu,
                streetName = step?.instruction,
                arrow = arrow,
                arrowSource = ArrowSource.TEXT,
                instruction = if (p.arrived) "Arrived at ${route.destination}" else step?.instruction,
                summary = "$minutes min · ${"%.1f".format(Locale.ENGLISH, p.remainingM / 1000)} km",
            ),
            KEY,
            staleAfterMs = null,
        )
    }
}
