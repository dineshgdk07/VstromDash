package dev.gdk.bumblebee.ui.explorer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.data.local.RideEntity
import dev.gdk.bumblebee.ride.FuelEconomics
import dev.gdk.bumblebee.ride.FuelFill
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.RideInsightInput
import dev.gdk.bumblebee.ride.RouteClustering
import dev.gdk.bumblebee.ride.RouteLeaderboard
import dev.gdk.bumblebee.ride.RouteRide
import dev.gdk.bumblebee.ride.ShareBackground
import dev.gdk.bumblebee.ride.ShareCard
import dev.gdk.bumblebee.ride.ShareCardModel
import dev.gdk.bumblebee.ride.ShareShape
import dev.gdk.bumblebee.ride.ShareStyle
import dev.gdk.bumblebee.ride.ShareSwitches
import dev.gdk.bumblebee.ride.TrackPlot
import dev.gdk.bumblebee.ride.TrackSimplify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal const val CHENNAI_DELHI_KM = 2_200.0

sealed interface RecapPage {
    data class Km(val km: Double, val comparison: String) : RecapPage
    data class Rides(val count: Int, val movingSec: Int) : RecapPage
    data class Longest(val label: String, val km: Double, val movingSec: Int, val track: List<GpsFix>) : RecapPage
    data class TopSpeed(val kmh: Int) : RecapPage
    data class BusiestMonth(val label: String, val km: Double) : RecapPage
    data class Repeat(val runs: Int, val medianKm: Double?, val litresEst: Double?) : RecapPage
    data class Fuel(
        val litres: Double?,
        val litresPartial: Boolean,
        val rupees: Double?,
        val spendPartial: Boolean,
    ) : RecapPage
    data object Share : RecapPage
}

data class YearRecapUi(
    val year: Int,
    val pages: List<RecapPage> = emptyList(),
    val card: ShareCardModel? = null,
    val loaded: Boolean = false,
)

class YearRecapViewModel(application: Application, private val year: Int) : AndroidViewModel(application) {
    private val app = application as DashApp
    private val _ui = MutableStateFlow(YearRecapUi(year = year))
    val ui: StateFlow<YearRecapUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val zone = ZoneId.systemDefault()
        val built = withContext(Dispatchers.IO) {
            val rides = app.capture.finishedRides().filter { ride ->
                Instant.ofEpochMilli(ride.startTs).atZone(zone).year == year && ride.km() > 0.0
            }
            val fills = app.capture.fuelFills().filter { fill ->
                Instant.ofEpochMilli(fill.ts).atZone(zone).year == year
            }
            buildRecap(year, rides, fills, zone) { id -> app.capture.trackForRide(id) }
        }
        _ui.value = YearRecapUi(year = year, pages = built.pages, card = built.card, loaded = true)
    }
}

internal data class BuiltRecap(val pages: List<RecapPage>, val card: ShareCardModel?)

internal suspend fun buildRecap(
    year: Int,
    rides: List<RideEntity>,
    fills: List<FuelFill>,
    zone: ZoneId,
    trackFor: suspend (Long) -> List<GpsFix>,
): BuiltRecap {
    if (rides.isEmpty()) return BuiltRecap(emptyList(), null)
    val pages = mutableListOf<RecapPage>()
    val totalKm = rides.sumOf { it.km() }
    pages += RecapPage.Km(totalKm, chennaiDelhiLine(totalKm))
    val moving = rides.sumOf { it.movingSec }
    pages += RecapPage.Rides(rides.size, moving)

    val longest = rides.maxBy { it.km() }
    val simplified = rides.associate { ride ->
        ride.id to TrackSimplify.douglasPeucker(trackFor(ride.id))
    }
    val longestTrack = TrackPlot.downsample(simplified[longest.id].orEmpty(), 80)
    val day = DateTimeFormatter.ofPattern("d MMM").withZone(zone)
    pages += RecapPage.Longest(
        label = day.format(Instant.ofEpochMilli(longest.startTs)),
        km = longest.km(),
        movingSec = longest.movingSec,
        track = longestTrack,
    )

    val top = rides.maxOf { it.maxSpeedKmh }
    if (top > 0) pages += RecapPage.TopSpeed(top)

    val byMonth = rides.groupBy { Instant.ofEpochMilli(it.startTs).atZone(zone).month }
    if (byMonth.size > 1) {
        val best = byMonth.maxBy { (_, monthRides) -> monthRides.sumOf { it.km() } }
        pages += RecapPage.BusiestMonth(
            label = best.key.getDisplayName(TextStyle.FULL, Locale.getDefault()),
            km = best.value.sumOf { it.km() },
        )
    }

    val clusters = RouteClustering.cluster(rides.map { it.id }, simplified)
    val favourite = RouteLeaderboard.leaderboard(
        clusters,
        rides.map { RouteRide(it.id, it.startTs, it.endTs, it.km(), it.movingSec) },
        FuelEconomics.averageKmPerL(fills),
    ).firstOrNull { !it.isUntracked && it.runCount >= 2 }
    if (favourite != null) {
        pages += RecapPage.Repeat(favourite.runCount, favourite.medianKm, favourite.litresEst)
    }

    val loggedLitres = fills.mapNotNull { it.litres }.filter { it > 0.0 }
    val litres = loggedLitres.takeIf { it.isNotEmpty() }?.sum()
    val priced = fills.mapNotNull { it.rupees }.filter { it > 0.0 }
    val spend = priced.takeIf { it.isNotEmpty() }?.sum()
    if (litres != null || spend != null) {
        pages += RecapPage.Fuel(
            litres = litres,
            litresPartial = litres != null && loggedLitres.size < fills.size,
            rupees = spend,
            spendPartial = spend != null && priced.size < fills.size,
        )
    }

    val hours = moving / 3600.0
    val input = RideInsightInput(
        startTs = rides.minOf { it.startTs },
        endTs = rides.maxOf { it.endTs ?: it.startTs },
        km = totalKm,
        movingSec = moving,
        idleSec = 0,
        maxSpeedKmh = top,
        avgMovingSpeedKmh = if (hours > 0.0) totalKm / hours else 0.0,
    )
    val painted = ShareCard.of(
        ride = input,
        track = longestTrack,
        switches = ShareSwitches(
            shape = ShareShape.STORY,
            background = ShareBackground.OWN,
        ),
        demo = rides.all { it.isDemo },
        zone = zone,
    )
    val card = painted.copy(
        style = if (longestTrack.size >= 2) ShareStyle.CORRIDOR else painted.style,
        sentence = "${kmText(totalKm)} km across ${rides.size} rides.",
        whenLabel = year.toString(),
        dateText = year.toString(),
        timeOfDay = year.toString(),
    )
    pages += RecapPage.Share
    return BuiltRecap(pages, card)
}

internal fun chennaiDelhiLine(km: Double): String {
    val times = km / CHENNAI_DELHI_KM
    val n = if (times >= 10.0) "%.0f".format(times) else "%.1f".format(times)
    return "That's Chennai → Delhi ×$n"
}

internal fun kmText(km: Double): String = "%,.0f".format(km)

private fun RideEntity.km(): Double {
    val start = odoStartKm
    val end = odoEndKm
    if (start != null && end != null) return (end - start).toDouble()
    return gpsDistanceM / 1000.0
}
