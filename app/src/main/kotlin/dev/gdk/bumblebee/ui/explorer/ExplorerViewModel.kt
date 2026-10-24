package dev.gdk.bumblebee.ui.explorer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.data.local.RideEntity
import dev.gdk.bumblebee.data.local.toRecord
import dev.gdk.bumblebee.ride.JourneyDetector
import dev.gdk.bumblebee.ride.FuelEconomics
import dev.gdk.bumblebee.ride.GpxExport
import dev.gdk.bumblebee.ride.RideInsightInput
import dev.gdk.bumblebee.ride.RideInsights
import dev.gdk.bumblebee.ride.RideRhythm
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.RideStory
import dev.gdk.bumblebee.ride.RideTimeline
import dev.gdk.bumblebee.ride.SegmentSpan
import dev.gdk.bumblebee.ride.SpeedHistogram
import dev.gdk.bumblebee.ride.StreakInfo
import dev.gdk.bumblebee.ride.TrackPlot
import dev.gdk.bumblebee.ride.WrappedRecap
import dev.gdk.bumblebee.ui.ride.BandShare
import dev.gdk.bumblebee.ui.ride.RideDetail
import dev.gdk.bumblebee.ui.ride.RideRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class DayDigest(
    val date: String,
    val title: String,
    val story: String,
    val km: Double,
    val rides: Int,
    val movingSec: Int,
    val maxSpeedKmh: Int,
)

data class ExplorerUiState(
    val month: YearMonth = YearMonth.now(),
    val kmByDate: Map<String, Double> = emptyMap(),
    val selectedDate: String? = null,
    val rides: List<RideRow> = emptyList(),
    val day: DayDigest? = null,
    val monthKm: Double = 0.0,
    val monthRideCount: Int = 0,
    val monthMovingSec: Int = 0,
    val vsPrevious: String? = null,
    val longest: LongestRide? = null,
    val tracks: Map<Long, List<GpsFix>> = emptyMap(),
    val monthDays: Int = 0,
    val detail: RideDetail? = null,
    val weekdayKm: List<Double> = List(7) { 0.0 },
    val timeOfDayKm: List<Double> = List(4) { 0.0 },
    val streak: StreakInfo = StreakInfo(0, 0),
    val wrapped: WrappedRecap? = null,
    val monthStory: String = "",
    val monthTitle: String = "",
    val routes: List<dev.gdk.bumblebee.ride.RouteStats> = emptyList(),
    val journeys: List<JourneyCard> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
)

data class LongestRide(
    val id: Long,
    val label: String,
    val km: Double,
    val movingSec: Int,
)

data class JourneyCard(
    val rideIds: List<Long>,
    val dateIso: String,
    val caption: String,
    val legs: List<String>,
    val totalKm: Int,
)

class ExplorerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DashApp
    private val _ui = MutableStateFlow(ExplorerUiState())
    val ui: StateFlow<ExplorerUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch { reload() }
    }

    fun showMonth(month: YearMonth) {
        if (month == _ui.value.month) return
        _ui.value = _ui.value.copy(month = month, selectedDate = null)
        viewModelScope.launch { reload() }
    }

    fun selectDate(date: String?) {
        val next = if (date == _ui.value.selectedDate) null else date
        _ui.value = _ui.value.copy(selectedDate = next)
        viewModelScope.launch { reload(keepMonth = true) }
    }

    /** Selects [date] without toggling it off. The calendar uses [selectDate] for tap-again-to-clear. */
    fun focusDate(date: String) {
        if (date == _ui.value.selectedDate) return
        _ui.value = _ui.value.copy(selectedDate = date)
        viewModelScope.launch { reload(keepMonth = true) }
    }

    fun openRide(id: Long) {
        viewModelScope.launch {
            val detail = withContext(Dispatchers.IO) { buildDetail(id) }
            _ui.value = _ui.value.copy(detail = detail)
        }
    }

    fun renameRide(id: Long, name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { app.capture.renameRide(id, name) }
            if (_ui.value.detail?.row?.id == id) {
                _ui.value = _ui.value.copy(detail = withContext(Dispatchers.IO) { buildDetail(id) })
            }
            reload(keepMonth = true)
        }
    }

    fun closeDetail() {
        _ui.value = _ui.value.copy(detail = null)
    }

    fun toggleSelected(id: Long) {
        _ui.update { state ->
            val next = state.selectedIds.toMutableSet()
            if (!next.add(id)) next.remove(id)
            state.copy(selectedIds = next)
        }
    }

    fun mergeSelected() {
        val ids = _ui.value.selectedIds.toList()
        if (ids.size < 2) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { app.capture.mergeRides(ids) }
            _ui.update { it.copy(selectedIds = emptySet(), detail = null) }
            reload()
        }
    }

    fun splitOpenRide() {
        val id = _ui.value.detail?.row?.id ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { app.capture.splitRide(id) }
            _ui.update { it.copy(detail = null) }
            reload()
        }
    }

    fun mergeSuggestedJourney(ids: List<Long>) {
        if (ids.size < 2) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { app.capture.mergeRides(ids) }
            reload()
        }
    }

    private suspend fun reload(keepMonth: Boolean = true) {
        val month = _ui.value.month
        val selected = _ui.value.selectedDate
        val zone = ZoneId.systemDefault()
        val packed = withContext(Dispatchers.IO) {
            val all = app.capture.finishedRides()
            val km = all.groupBy { dateOf(it.startTs) }.mapValues { (_, rides) -> rides.sumOf { it.km() } }
            val pb = app.capture.allTimeMaxSpeed()
            val monthStart = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val monthRides = all.filter { it.startTs in monthStart until monthEnd }
            val monthLongest = monthRides.maxOfOrNull { it.km() } ?: 0.0
            val filtered = if (selected != null) {
                monthRides.filter { dateOf(it.startTs) == selected }
            } else {
                monthRides
            }
            val rows = filtered.map { it.toRow(pb, monthLongest) }
            val monthKm = monthRides.sumOf { it.km() }
            val monthRideCount = monthRides.size
            val monthMovingSec = monthRides.sumOf { it.movingSec }
            val prev = month.minusMonths(1)
            val prevStart = prev.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val prevKm = all.filter { it.startTs in prevStart until monthStart }.sumOf { it.km() }
            val vsPrevious = vsPreviousCaption(monthKm, prevKm, prev)
            val dayLabel = DateTimeFormatter.ofPattern("d MMM").withZone(zone)
            val longest = monthRides.maxByOrNull { it.km() }?.takeIf { it.km() > 0 }?.let { ride ->
                LongestRide(ride.id, dayLabel.format(Instant.ofEpochMilli(ride.startTs)), ride.km(), ride.movingSec)
            }
            val tracks = monthRides.associate { ride ->
                ride.id to TrackPlot.downsample(app.capture.trackForRide(ride.id), 32)
            }
            val monthDays = monthRides.map { dateOf(it.startTs) }.toSet().size
            val day = if (selected != null) {
                val title = DateTimeFormatter.ofPattern("EEEE d MMM")
                    .withLocale(java.util.Locale.getDefault())
                    .format(java.time.LocalDate.parse(selected))
                val inputs = filtered.map { it.toInsight() }
                DayDigest(
                    date = selected,
                    title = title,
                    story = RideStory.ofDay(title, inputs),
                    km = inputs.sumOf { it.km },
                    rides = inputs.size,
                    movingSec = inputs.sumOf { it.movingSec },
                    maxSpeedKmh = inputs.maxOfOrNull { it.maxSpeedKmh } ?: 0,
                )
            } else {
                null
            }
            val records = all.map { it.toRecord() }
            val weekday = RideRhythm.weekdayKm(records.filter { it.startTs in monthStart until monthEnd })
            val tod = RideRhythm.timeOfDayKm(records.filter { it.startTs in monthStart until monthEnd })
            val streak = RideRhythm.streak(records)
            val wrapped = RideRhythm.wrapped(month.year, records, app.capture.fuelFills())
            val monthTitle = month.month.name.lowercase().replaceFirstChar { it.titlecase() } + " ${month.year}"
            val monthStory = RideStory.ofMonth(monthTitle, monthRides.map { it.toInsight() })
            val routes = app.capture.routeStats().filter { !it.isUntracked && it.runCount >= 2 }
            val byId = all.associateBy { it.id }
            val clock = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
            val journeys = app.capture.journeySuggestions()
                .filter { sug ->
                    val inMonth = sug.startTs in monthStart until monthEnd
                    val onDay = selected == null || sug.dateIso == selected
                    inMonth && onDay
                }
                .map { sug ->
                    val legs = sug.rideIds.mapNotNull { id ->
                        val ride = byId[id] ?: return@mapNotNull null
                        val km = ride.km()
                        "${clock.format(java.time.Instant.ofEpochMilli(ride.startTs))}  ·  ${String.format("%.1f km", km)}"
                    }
                    JourneyCard(
                        rideIds = sug.rideIds,
                        dateIso = sug.dateIso,
                        caption = JourneyDetector.caption(sug, zone),
                        legs = legs,
                        totalKm = sug.totalKm,
                    )
                }
            Packed(
                km, rows, day, monthKm, monthRideCount, monthMovingSec, vsPrevious, longest, tracks,
                monthDays, weekday, tod, streak, wrapped, monthStory, monthTitle, routes, journeys,
            )
        }
        _ui.value = _ui.value.copy(
            month = month,
            kmByDate = packed.kmByDate,
            selectedDate = selected,
            rides = packed.rows,
            day = packed.day,
            monthKm = packed.monthKm,
            monthRideCount = packed.monthRideCount,
            monthMovingSec = packed.monthMovingSec,
            vsPrevious = packed.vsPrevious,
            longest = packed.longest,
            tracks = packed.tracks,
            monthDays = packed.monthDays,
            weekdayKm = packed.weekdayKm,
            timeOfDayKm = packed.timeOfDayKm,
            streak = packed.streak,
            wrapped = packed.wrapped,
            monthStory = packed.monthStory,
            monthTitle = packed.monthTitle,
            routes = packed.routes,
            journeys = packed.journeys,
        )
    }

    private suspend fun buildDetail(id: Long): RideDetail? {
        val entity = app.capture.rideById(id) ?: return null
        val pb = app.capture.allTimeMaxSpeed()
        val input = entity.toInsight()
        val parked = app.capture.lastPointFor(entity.id)
        val track = app.capture.trackForRide(entity.id)
        val speeds = TrackPlot.stride(app.capture.speedsForRide(entity.id))
        val last = app.capture.lastTelemetry(entity.id)
        val tank = FuelEconomics.averageKmPerL(app.capture.fuelFills())
        val bike = last?.fuelEconKml
        val litres = FuelEconomics.litresBurnt(entity.km(), tank, bike)
        val story = RideStory.of(input, track)
        val gpx = if (track.size >= 2) GpxExport.toGpx(entity.name ?: "Ride ${entity.id}", entity.startTs, track) else null
        val csv = app.capture.csvForRide(entity.id)
        val segments = app.capture.segmentsFor(entity.id)
        val telemetry = app.capture.telemetryForRide(entity.id)
        val fuelEvents = app.capture.fuelEventsForRide(entity.id)
        val endTs = entity.endTs ?: System.currentTimeMillis()
        val timeline = RideTimeline.fromRide(
            startTs = entity.startTs,
            endTs = endTs,
            gpsOnly = entity.gpsOnly,
            odoStartKm = entity.odoStartKm,
            odoEndKm = entity.odoEndKm,
            gpsDistanceM = entity.gpsDistanceM,
            track = track,
            telemetry = telemetry,
            fuelEvents = fuelEvents,
            segments = segments.map { SegmentSpan(it.startTs, it.endTs) },
        )
        val overnight = Instant.ofEpochMilli(entity.startTs).atZone(ZoneId.systemDefault()).toLocalDate() !=
            Instant.ofEpochMilli(endTs).atZone(ZoneId.systemDefault()).toLocalDate()
        return RideDetail(
            row = entity.toRow(pb, entity.km()),
            insights = RideInsights.debrief(input, pb, entity.km(), track),
            bands = bandShares(entity.speedHistogramJson),
            fuelBarsStart = entity.fuelBarsStart,
            fuelBarsEnd = entity.fuelBarsEnd,
            odoEndKm = entity.odoEndKm,
            odoStartKm = entity.odoStartKm,
            timeline = timeline,
            events = timeline.events,
            parked = parked,
            track = track,
            speeds = speeds,
            story = story,
            tripAKm = last?.tripAKm,
            tripAStartKm = telemetry.firstOrNull()?.tripAKm,
            gpsDistanceM = entity.gpsDistanceM,
            gpsOnly = entity.gpsOnly,
            tripBKm = last?.tripBKm,
            bikeEconKml = bike,
            tankKmPerL = tank,
            rupeesPerKm = FuelEconomics.costStats(app.capture.fuelFills())?.rollingRupeesPerKm,
            litresEst = litres?.first,
            litresFromFills = litres?.second == true,
            gpx = gpx,
            csv = csv,
            canSplit = app.capture.canSplit(entity.id),
            overnight = overnight,
            elapsedSec = ((endTs - entity.startTs) / 1000L).toInt().coerceAtLeast(0),
            stopLines = pauseStopLines(segments),
            demo = entity.isDemo,
            shareText = GpxExport.recap(
                title = "Ride ${DateTimeFormatter.ofPattern("EEE d MMM").withZone(ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(entity.startTs))}",
                km = entity.km(),
                movingSec = entity.movingSec,
                maxSpeedKmh = entity.maxSpeedKmh,
                story = story,
                tankKmPerL = tank,
                bikeKmPerL = bike,
                litres = litres?.first,
                litresFromFills = litres?.second == true,
            ),
        )
    }
}

internal fun vsPreviousCaption(monthKm: Double, prevKm: Double, prevMonth: YearMonth): String? {
    if (prevKm <= 0.0) return null
    val pct = (((monthKm - prevKm) / prevKm) * 100).toInt()
    val sign = if (pct > 0) "+" else ""
    val name = prevMonth.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    return "vs $name $sign$pct%"
}

private data class Packed(
    val kmByDate: Map<String, Double>,
    val rows: List<RideRow>,
    val day: DayDigest?,
    val monthKm: Double,
    val monthRideCount: Int,
    val monthMovingSec: Int,
    val vsPrevious: String?,
    val longest: LongestRide?,
    val tracks: Map<Long, List<GpsFix>>,
    val monthDays: Int,
    val weekdayKm: List<Double>,
    val timeOfDayKm: List<Double>,
    val streak: StreakInfo,
    val wrapped: WrappedRecap?,
    val monthStory: String,
    val monthTitle: String,
    val routes: List<dev.gdk.bumblebee.ride.RouteStats>,
    val journeys: List<JourneyCard>,
)

private fun dateOf(ts: Long): String =
    Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate().toString()

private fun pauseStopLines(segments: List<dev.gdk.bumblebee.data.local.RideSegmentEntity>): List<String> {
    val ordered = segments.filter { it.endTs != null }.sortedBy { it.startTs }
    if (ordered.size < 2) return emptyList()
    return (0 until ordered.lastIndex).mapNotNull { i ->
        val gap = ordered[i + 1].startTs - (ordered[i].endTs ?: return@mapNotNull null)
        if (gap < 60_000L) return@mapNotNull null
        val min = (gap / 60_000L).toInt()
        "Stop ${i + 1} · $min min"
    }
}

private fun RideEntity.km(): Double {
    val s = odoStartKm
    val e = odoEndKm
    if (s != null && e != null) return (e - s).toDouble()
    return gpsDistanceM / 1000.0
}

private fun RideEntity.toInsight() = RideInsightInput(
    startTs = startTs,
    endTs = endTs ?: startTs,
    km = km(),
    movingSec = movingSec,
    idleSec = idleSec,
    maxSpeedKmh = maxSpeedKmh,
    avgMovingSpeedKmh = avgMovingSpeedKmh,
    odoStartKm = odoStartKm,
    odoEndKm = odoEndKm,
    histogram = speedHistogramJson?.let { SpeedHistogram.fromJson(it) },
    fuelBarsStart = fuelBarsStart,
    fuelBarsEnd = fuelBarsEnd,
)

private fun RideEntity.toRow(pb: Int, monthLongest: Double): RideRow {
    val lead = RideStory.of(toInsight()).substringBefore('.').trim()
    return RideRow(
        id = id,
        startTs = startTs,
        endTs = endTs,
        km = km(),
        movingSec = movingSec,
        idleSec = idleSec,
        maxSpeedKmh = maxSpeedKmh,
        avgMovingSpeedKmh = avgMovingSpeedKmh,
        headline = lead.ifBlank { String.format("%.1f km", km()) },
        fuelBarsEnd = fuelBarsEnd,
        name = name,
        odoStartKm = odoStartKm,
        odoEndKm = odoEndKm,
    )
}

private fun bandShares(json: String?): List<BandShare> {
    val h = json?.let { SpeedHistogram.fromJson(it) } ?: return emptyList()
    val total = dev.gdk.bumblebee.ride.SpeedBand.entries.sumOf { h.secondsIn(it) }.coerceAtLeast(1)
    val labels = mapOf(
        dev.gdk.bumblebee.ride.SpeedBand.IDLE to "Stops",
        dev.gdk.bumblebee.ride.SpeedBand.B1_30 to "<30",
        dev.gdk.bumblebee.ride.SpeedBand.B30_50 to "30–50",
        dev.gdk.bumblebee.ride.SpeedBand.B50_70 to "50–70",
        dev.gdk.bumblebee.ride.SpeedBand.B70_90 to "70–90",
        dev.gdk.bumblebee.ride.SpeedBand.B90_PLUS to "90+",
    )
    return dev.gdk.bumblebee.ride.SpeedBand.entries.map { band ->
        BandShare(labels.getValue(band), h.secondsIn(band).toFloat() / total, band)
    }
}
