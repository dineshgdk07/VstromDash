package dev.gdk.bumblebee.ui.share

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.data.local.RideEntity
import dev.gdk.bumblebee.ride.DayRide
import dev.gdk.bumblebee.ride.Geo
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.RideInsightInput
import dev.gdk.bumblebee.ride.ShareCard
import dev.gdk.bumblebee.ride.ShareCardModel
import dev.gdk.bumblebee.ride.ShareExtras
import dev.gdk.bumblebee.ride.ShareLook
import dev.gdk.bumblebee.ride.ShareMetricKind
import dev.gdk.bumblebee.ride.SharePrivacy
import dev.gdk.bumblebee.ride.ShareSwitches
import dev.gdk.bumblebee.ride.SunTimes
import dev.gdk.bumblebee.weather.WmoCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface ShareSubject {
    data class Ride(val rideId: Long) : ShareSubject
    data class Day(val date: LocalDate) : ShareSubject
}

data class ShareUi(
    val switches: ShareSwitches = ShareSwitches(),
    val name: String = "",
    val previews: Map<ShareLook, ShareCardModel> = emptyMap(),
    val selected: ShareLook? = null,
    val photo: Bitmap? = null,
    val mapFallback: Boolean = false,
    val canName: Boolean = false,
    val busy: Boolean = true,
)

class ShareViewModel(
    app: Application,
    private val subject: ShareSubject,
    private val places: PlaceNamer = PlaceNamer.android(app),
) : AndroidViewModel(app) {
    private val dash = app as DashApp
    private val prefs = SharePrefs(app)
    private val _ui = MutableStateFlow(ShareUi())
    val ui: StateFlow<ShareUi> = _ui.asStateFlow()
    private var facts: Facts? = null

    init {
        viewModelScope.launch {
            val switches = prefs.load(ShareMetricKind.entries)
            _ui.value = _ui.value.copy(switches = switches, busy = true)
            val loaded = withContext(Dispatchers.IO) { load() }
            facts = loaded
            publish(loaded.name, switches, selected = null)
        }
    }

    fun pick(look: ShareLook) {
        _ui.value = _ui.value.copy(selected = look)
        update { it.copy(look = look) }
    }

    fun back() {
        _ui.value = _ui.value.copy(selected = null)
    }

    fun update(transform: (ShareSwitches) -> ShareSwitches) {
        val next = transform(_ui.value.switches)
        prefs.save(next)
        publish(_ui.value.name, next, _ui.value.selected)
    }

    fun rename(name: String) {
        publish(name, _ui.value.switches, _ui.value.selected)
    }

    fun commitName() {
        val ride = subject as? ShareSubject.Ride ?: return
        val name = _ui.value.name
        viewModelScope.launch(Dispatchers.IO) {
            dash.capture.renameRide(ride.rideId, name)
        }
    }

    fun setPhoto(bitmap: Bitmap?) {
        _ui.value = _ui.value.copy(photo = bitmap)
    }

    private fun publish(name: String, switches: ShareSwitches, selected: ShareLook?) {
        val loaded = facts
        val previews = if (loaded == null) emptyMap() else previews(loaded, name, switches)
        val shown = previews[switches.look]
        _ui.value = _ui.value.copy(
            switches = switches,
            name = name,
            previews = previews,
            selected = selected,
            mapFallback = shown?.mapFallback == true,
            canName = loaded != null,
            busy = false,
        )
    }

    private fun previews(loaded: Facts, name: String, switches: ShareSwitches): Map<ShareLook, ShareCardModel> =
        ShareLook.entries.associateWith { look -> loaded.card(name, switches.copy(look = look)) }

    private suspend fun load(): Facts = when (val subject = subject) {
        is ShareSubject.Ride -> loadRide(subject.rideId)
        is ShareSubject.Day -> loadDay(subject.date)
    }

    private suspend fun loadRide(id: Long): Facts {
        val ride = dash.capture.rideById(id) ?: return Facts.Missing
        val track = dash.capture.trackForRide(id)
        val breaks = dash.capture.segmentsFor(id).mapNotNull { it.endTs }.dropLast(1)
        if (ride.startPlace == null) {
            trimmed(track)?.let { (start, end) ->
                val from = places.locality(start.lat, start.lng)
                val to = places.locality(end.lat, end.lng)
                if (from != null || to != null) dash.capture.setPlaces(id, from, to)
            }
        }
        val fresh = dash.capture.rideById(id) ?: ride
        val zone = ZoneId.systemDefault()
        val weather = weatherLine(fresh, track, zone)
        val fills = dash.capture.fuelEventsForRide(id)
        val tank = if (fills.isEmpty()) null else dash.capture.bikeEconomics(fresh.fuelBarsEnd).tankKmPerL
        return Facts.One(
            input = fresh.toInsight(),
            track = track,
            breaks = breaks,
            climbM = fresh.climbM,
            tank = tank,
            extras = ShareExtras(
                rideName = fresh.name,
                startPlace = fresh.startPlace,
                endPlace = fresh.endPlace,
                rideNumber = dash.capture.rideNumber(id),
                highlight = dash.capture.highlightFor(id),
                weatherLine = weather,
                odoEndKm = fresh.odoEndKm,
                gpsOnly = fresh.gpsOnly,
            ),
            demo = fresh.isDemo,
            zone = zone,
            storedName = fresh.name.orEmpty(),
        )
    }

    private suspend fun loadDay(date: LocalDate): Facts {
        val zone = ZoneId.systemDefault()
        val rides = dash.capture.ridesOnDay(date, zone).map { entity ->
            val track = dash.capture.trackForRide(entity.id)
            val breaks = dash.capture.segmentsFor(entity.id).mapNotNull { it.endTs }.dropLast(1)
            DayRide(entity.toInsight(), track, entity.climbM, breaks)
        }
        return Facts.Day(date, rides, zone)
    }

    private fun weatherLine(ride: RideEntity, track: List<GpsFix>, zone: ZoneId): String? {
        val mid = track.getOrNull(track.size / 2)
        val sun = mid?.let {
            val date = Instant.ofEpochMilli(it.ts).atZone(zone).toLocalDate()
            SunTimes.of(date, it.lat, it.lng, zone)
        }
        val tag = sun?.let { SunTimes.tag(ride.startTs, ride.endTs ?: ride.startTs, it) }
        val label = ride.weatherCode?.let(WmoCode::label)?.takeUnless { it == "—" }
        return SunTimes.weatherLine(ride.weatherTempC, label, tag)
    }

    private fun trimmed(track: List<GpsFix>): Pair<GpsFix, GpsFix>? {
        if (track.size < 2) return null
        val cum = DoubleArray(track.size)
        for (i in 1 until track.size) {
            val a = track[i - 1]
            val b = track[i]
            cum[i] = cum[i - 1] + Geo.haversineMeters(a.lat, a.lng, b.lat, b.lng)
        }
        val kept = SharePrivacy.retainAfterRide(cum.toList(), cum.last())
        if (kept.size < 2) return null
        var next = 0
        val out = ArrayList<GpsFix>(2)
        for (i in track.indices) {
            if (next < kept.size && cum[i] == kept[next]) {
                if (next == 0 || next == kept.lastIndex) out += track[i]
                next++
            }
        }
        return if (out.size >= 2) out.first() to out.last() else null
    }

    private sealed interface Facts {
        val name: String
        fun card(name: String, switches: ShareSwitches): ShareCardModel

        data object Missing : Facts {
            override val name = ""
            override fun card(name: String, switches: ShareSwitches) = ShareCard.of(RideInsightInput(0, 0, 0.0, 0, 0, 0, 0.0), switches = switches)
        }

        data class One(
            val input: RideInsightInput,
            val track: List<GpsFix>,
            val breaks: List<Long>,
            val climbM: Double?,
            val tank: Double?,
            val extras: ShareExtras,
            val demo: Boolean,
            val zone: ZoneId,
            val storedName: String,
        ) : Facts {
            override val name = storedName
            override fun card(name: String, switches: ShareSwitches) = ShareCard.of(
                ride = input,
                track = track,
                switches = switches,
                climbM = climbM,
                tankKmPerL = tank,
                extras = extras.copy(rideName = name.ifBlank { null }),
                demo = demo,
                zone = zone,
                segmentBreaks = breaks,
            )
        }

        data class Day(
            val date: LocalDate,
            val rides: List<DayRide>,
            val zone: ZoneId,
        ) : Facts {
            override val name = ""
            override fun card(name: String, switches: ShareSwitches) = ShareCard.ofDay(
                date = date,
                rides = rides,
                switches = switches,
                dayName = name.ifBlank { null },
                zone = zone,
            )
        }
    }

    class Factory(
        private val app: Application,
        private val subject: ShareSubject,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ShareViewModel(app, subject) as T
        }
    }
}

private fun RideEntity.toInsight() = RideInsightInput(
    startTs = startTs,
    endTs = endTs ?: startTs,
    km = km(),
    movingSec = movingSec,
    idleSec = idleSec,
    maxSpeedKmh = maxSpeedKmh,
    avgMovingSpeedKmh = avgMovingSpeedKmh,
)

private fun RideEntity.km(): Double {
    val start = odoStartKm
    val end = odoEndKm
    if (start != null && end != null && end >= start) return (end - start).toDouble()
    return gpsDistanceM / 1000.0
}
