package dev.gdk.bumblebee.ui.fuel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.data.local.FuelEventEntity
import dev.gdk.bumblebee.data.local.toRecord
import dev.gdk.bumblebee.garage.GarageSnapshot
import dev.gdk.bumblebee.ride.FuelEconomics
import dev.gdk.bumblebee.ride.FuelEventSource
import dev.gdk.bumblebee.ride.FuelEventType
import dev.gdk.bumblebee.ride.FuelTankEstimator
import dev.gdk.bumblebee.ride.RefuelPredictor
import dev.gdk.bumblebee.ride.RideRhythm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class FuelLogEntry(
    val id: Long,
    val ts: Long,
    val whenLabel: String,
    val type: String,
    val isFill: Boolean,
    val odometerKm: Int,
    val barsBefore: Int,
    val barsAfter: Int,
    val litres: Double?,
    val rupees: Double?,
    val note: String?,
    val kmSincePrevFill: Int?,
    val tankKmPerL: Double?,
    val rupeesPerLitre: Double?,
    val rupeesPerKm: Double?,
    val riderOwned: Boolean,
    val kmSincePrevEvent: Int?,
    val story: String,
    val isFullTank: Boolean = true,
    val outlierConfirmed: Boolean = false,
)

data class TankHistoryRow(
    val closingFillId: Long,
    val whenLabel: String,
    val km: Double,
    val litres: Double,
    val kmPerL: Double,
    val rupeesPerKm: Double?,
    val outlier: Boolean,
    val deltaPct: Int?,
)

data class FuelLogUiState(
    val odometerKm: Int? = null,
    val fuelBars: Int? = null,
    val rangeKm: Double? = null,
    val tankKmPerL: Double? = null,
    val rupeesPerKm: Double? = null,
    val costPartial: Boolean = false,
    val kmPerBarAvg: Double? = null,
    val monthSpend: Double? = null,
    val tankLitres: Double? = null,
    val tankRough: Boolean = false,
    val refuelLabel: String? = null,
    val fillBeforeService: Boolean = false,
    val odo: String = "",
    val date: String = LocalDate.now().toString(),
    val barsAfter: String = "6",
    val litres: String = "",
    val rupees: String = "",
    val note: String = "",
    val fullTank: Boolean = true,
    val tankSummary: String = "",
    val clusterSummary: String = "",
    val tanks: List<TankHistoryRow> = emptyList(),
    val tankTrend: List<Double> = emptyList(),
    val status: String = "Log a fill at the pump. The cluster also writes bar drops and guessed fills here — you don't enter drops.",
    val editingId: Long? = null,
    val entries: List<FuelLogEntry> = emptyList(),
)

class FuelLogViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DashApp
    private val _ui = MutableStateFlow(
        FuelLogUiState(
            odometerKm = app.latestTelemetry.value?.odometerKm,
            fuelBars = app.latestTelemetry.value?.fuelBars,
            odo = app.latestTelemetry.value?.odometerKm?.toString() ?: "",
            barsAfter = app.latestTelemetry.value?.fuelBars?.toString() ?: "6",
        ),
    )
    val ui: StateFlow<FuelLogUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            app.latestTelemetry.collect { t ->
                if (t != null) {
                    _ui.update {
                        it.copy(
                            odometerKm = t.odometerKm,
                            fuelBars = t.fuelBars,
                            odo = it.odo.ifBlank { t.odometerKm.toString() },
                            barsAfter = if (it.barsAfter.isBlank()) (t.fuelBars?.toString() ?: "6") else it.barsAfter,
                        )
                    }
                }
            }
        }
        viewModelScope.launch { reload() }
    }

    fun setOdo(value: String) { _ui.update { it.copy(odo = value) } }
    fun setDate(value: String) { _ui.update { it.copy(date = value) } }
    fun setBarsAfter(value: String) { _ui.update { it.copy(barsAfter = value) } }
    fun setLitres(value: String) { _ui.update { it.copy(litres = value) } }
    fun setRupees(value: String) { _ui.update { it.copy(rupees = value) } }
    fun setNote(value: String) { _ui.update { it.copy(note = value) } }
    fun setFullTank(value: Boolean) { _ui.update { it.copy(fullTank = value) } }

    fun beginEdit(entry: FuelLogEntry) {
        val date = Instant.ofEpochMilli(entry.ts).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        _ui.update {
            it.copy(
                editingId = entry.id,
                odo = entry.odometerKm.toString(),
                date = date,
                barsAfter = entry.barsAfter.toString(),
                litres = entry.litres?.toString() ?: "",
                rupees = entry.rupees?.toString() ?: "",
                note = entry.note ?: "",
                fullTank = entry.isFullTank,
                status = if (entry.riderOwned) "Editing your fill." else "Editing a cluster fill. Add litres, rupees, or a comment, or fix the odo.",
            )
        }
    }

    fun cancelEdit() {
        _ui.update {
            it.copy(
                editingId = null,
                litres = "",
                rupees = "",
                note = "",
                status = "Edit cancelled.",
            )
        }
    }

    fun saveFill() {
        val odo = _ui.value.odo.toIntOrNull() ?: _ui.value.odometerKm
        val bars = _ui.value.barsAfter.toIntOrNull()?.coerceIn(1, 6)
        if (odo == null || bars == null) {
            _ui.update { it.copy(status = "Need odometer km and bars after fill (1–6).") }
            return
        }
        val litres = _ui.value.litres.toDoubleOrNull()
        val rupees = _ui.value.rupees.toDoubleOrNull()
        val note = _ui.value.note
        val ts = parseDate(_ui.value.date) ?: System.currentTimeMillis()
        val editingId = _ui.value.editingId
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val full = _ui.value.fullTank
                if (editingId != null) {
                    app.capture.updateFuel(editingId, odo, bars, litres, rupees, note, ts, isFullTank = full)
                } else {
                    app.capture.logRefuel(odo, bars, litres, rupees, note, ts, isFullTank = full)
                }
            }
            _ui.update {
                it.copy(
                    editingId = null,
                    litres = "",
                    rupees = "",
                    note = "",
                    status = if (editingId != null) "Updated fill at $odo km." else "Logged fill at $odo km" + if (note.isNotBlank()) " — ${note.trim()}" else ".",
                )
            }
            val prev = GarageSnapshot.read(app)
            GarageSnapshot.write(
                app,
                fuelBars = bars,
                rangeKm = prev.rangeKm,
                serviceDueKm = app.prefs.remainingServiceKm(odo) ?: prev.serviceDueKm,
                alert = if (_ui.value.fullTank) "Full tank logged" else "Partial fill logged",
                todayKm = prev.todayKm,
                open = GarageSnapshot.OPEN_FUEL,
            )
            reload()
        }
    }

    fun confirmOutlier(closingFillId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val ev = app.capture.allFuel().firstOrNull { it.id == closingFillId } ?: return@withContext
                app.capture.updateFuel(
                    ev.id, ev.odometerKm, ev.barsAfter, ev.litres, ev.cost, ev.note, ev.ts,
                    isFullTank = ev.isFullTank,
                    outlierConfirmed = true,
                )
            }
            reload()
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { app.capture.deleteFuel(id) }
            _ui.update {
                it.copy(
                    editingId = if (it.editingId == id) null else it.editingId,
                    status = "Deleted.",
                )
            }
            reload()
        }
    }

    private suspend fun reload() {
        val packed = withContext(Dispatchers.IO) {
            val events = app.capture.allFuel()
            val eco = app.capture.bikeEconomics(_ui.value.fuelBars)
            val fills = app.capture.fuelFills()
            val tank = FuelTankEstimator.estimate(
                fills = fills,
                currentOdometerKm = _ui.value.odometerKm,
                avgKmPerL = eco.tankKmPerL,
                bikeLiveKmPerL = null,
                bikeFuelBars = _ui.value.fuelBars,
                capacityL = app.prefs.tankCapacityL.toDouble(),
            )
            val pace = RideRhythm.paceKmPerDay(app.capture.finishedRides().map { it.toRecord() })
            val due = app.prefs.remainingServiceKm(_ui.value.odometerKm)
            val range = tank?.rangeKm ?: eco.rangeKm
            val refuel = RefuelPredictor.predict(range, pace.takeIf { it > 0 }, due)
            val spans = FuelEconomics.tankSpans(fills)
            val bike = app.latestTelemetry.value?.fuelEconKml
            val cluster = FuelEconomics.clusterAccuracy(bike, spans)?.summary.orEmpty()
            val avg = spans.filter { !it.outlier }.map { it.kmPerL }.takeIf { it.isNotEmpty() }?.average()
            val fmtDay = DateTimeFormatter.ofPattern("d MMM").withZone(ZoneId.systemDefault())
            val tanks = spans.asReversed().map { span ->
                val close = fills.firstOrNull { it.id == span.closingFillId }
                val delta = avg?.let { a -> if (a > 0) kotlin.math.round((span.kmPerL - a) / a * 100).toInt() else null }
                TankHistoryRow(
                    closingFillId = span.closingFillId,
                    whenLabel = close?.let { fmtDay.format(Instant.ofEpochMilli(it.ts)) } ?: "",
                    km = span.km,
                    litres = span.litres,
                    kmPerL = span.kmPerL,
                    rupeesPerKm = span.rupeesPerKm,
                    outlier = span.outlier,
                    deltaPct = delta,
                )
            }
            val summary = buildString {
                val odd = spans.count { it.outlier }
                if (odd > 0) append(if (odd == 1) "Check this fill? " else "Check $odd fills? ")
                spans.lastOrNull { !it.outlier }?.let {
                    append("Last full tank ${String.format(java.util.Locale.ROOT, "%.1f", it.kmPerL)} km/L. ")
                }
                val ordered = fills.sortedBy { it.ts }
                val tankRange = (eco.tankKmPerL ?: 0.0) * app.prefs.tankCapacityL
                for (i in 1 until ordered.size) {
                    if (FuelEconomics.missedFill(ordered[i - 1].odometerKm, ordered[i].odometerKm, tankRange)) {
                        append("Looks like a fill is missing between these dates.")
                        break
                    }
                }
            }
            Triple(events, eco, monthSpend(events)) to (tank to refuel) to Triple(summary, cluster, tanks)
        }
        val (pair, extra) = packed
        val (triple, tankPair) = pair
        val entries = lines(triple.first)
        _ui.update {
            it.copy(
                rangeKm = triple.second.rangeKm,
                tankKmPerL = triple.second.tankKmPerL,
                rupeesPerKm = triple.second.cost?.rollingRupeesPerKm,
                costPartial = triple.second.cost?.isPartial == true,
                kmPerBarAvg = triple.second.kmPerBarAvg,
                monthSpend = triple.third,
                tankLitres = tankPair.first?.litresLeft,
                tankRough = tankPair.first?.isRough == true,
                refuelLabel = tankPair.second.label().takeIf { tankPair.second.bucket != dev.gdk.bumblebee.ride.RefuelBucket.UNKNOWN },
                fillBeforeService = tankPair.second.fillBeforeService,
                entries = entries,
                tankSummary = extra.first,
                clusterSummary = extra.second,
                tanks = extra.third,
                tankTrend = extra.third.filter { !it.outlier }.map { it.kmPerL }.asReversed().takeLast(12),
            )
        }
    }

    private fun monthSpend(events: List<FuelEventEntity>): Double? {
        val month = YearMonth.now()
        val zone = ZoneId.systemDefault()
        val sum = events
            .filter { it.type == FuelEventType.REFUEL.name }
            .mapNotNull { ev ->
                val cost = ev.cost
                if (cost != null && cost > 0 &&
                    YearMonth.from(Instant.ofEpochMilli(ev.ts).atZone(zone).toLocalDate()) == month
                ) cost else null
            }
            .sum()
        return sum.takeIf { it > 0 }
    }

    private fun parseDate(iso: String): Long? {
        if (iso.isBlank()) return null
        return try {
            LocalDate.parse(iso.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
                .atTime(12, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

internal fun lines(events: List<FuelEventEntity>): List<FuelLogEntry> {
    val zone = ZoneId.systemDefault()
    val fmt = DateTimeFormatter.ofPattern("d MMM · h:mm a")
    val chrono = events.sortedBy { it.ts }
    val prevEventById = HashMap<Long, FuelEventEntity>()
    for (i in 1 until chrono.size) prevEventById[chrono[i].id] = chrono[i - 1]
    val fills = chrono.filter { it.type == FuelEventType.REFUEL.name }
    val prevFillById = HashMap<Long, FuelEventEntity>()
    for (i in 1 until fills.size) prevFillById[fills[i].id] = fills[i - 1]
    return events.sortedByDescending { it.ts }.map { ev ->
        val prevFill = prevFillById[ev.id]
        val kmFill = prevFill?.let { ev.odometerKm - it.odometerKm }?.takeIf { it > 0 }
        val kmEvent = prevEventById[ev.id]?.let { ev.odometerKm - it.odometerKm }?.takeIf { it > 0 }
        val litres = ev.litres?.takeIf { it > 0 }
        val rupees = ev.cost?.takeIf { it > 0 }
        val isFill = ev.type == FuelEventType.REFUEL.name
        val rider = ev.source == FuelEventSource.RIDER.name
        FuelLogEntry(
            id = ev.id,
            ts = ev.ts,
            whenLabel = fmt.format(Instant.ofEpochMilli(ev.ts).atZone(zone)),
            type = ev.type,
            isFill = isFill,
            odometerKm = ev.odometerKm,
            barsBefore = ev.barsBefore,
            barsAfter = ev.barsAfter,
            litres = ev.litres,
            rupees = ev.cost,
            note = ev.note,
            kmSincePrevFill = kmFill,
            tankKmPerL = if (kmFill != null && litres != null) kmFill / litres else null,
            rupeesPerLitre = if (rupees != null && litres != null) rupees / litres else null,
            rupeesPerKm = if (rupees != null && kmFill != null) rupees / kmFill.toDouble() else null,
            riderOwned = rider,
            kmSincePrevEvent = kmEvent,
            story = storyFor(
                isFill = isFill,
                riderOwned = rider,
                barsBefore = ev.barsBefore,
                barsAfter = ev.barsAfter,
                kmFill = kmFill,
                kmEvent = kmEvent,
                tankKmPerL = if (kmFill != null && litres != null) kmFill / litres else null,
                litres = litres,
                rupees = rupees,
            ),
            isFullTank = ev.isFullTank,
            outlierConfirmed = ev.outlierConfirmed,
        )
    }
}

internal fun storyFor(
    isFill: Boolean,
    riderOwned: Boolean,
    barsBefore: Int,
    barsAfter: Int,
    kmFill: Int?,
    kmEvent: Int?,
    tankKmPerL: Double?,
    litres: Double?,
    rupees: Double?,
): String {
    if (isFill) {
        val gained = (barsAfter - barsBefore).coerceAtLeast(0)
        val head = when {
            riderOwned && gained > 0 && barsBefore > 0 -> "You filled $barsBefore → $barsAfter."
            riderOwned -> "Pump stop, tank at $barsAfter/6."
            gained > 0 && barsBefore > 0 -> "Cluster saw bars climb $barsBefore → $barsAfter."
            else -> "Cluster saw the tank come back to $barsAfter/6."
        }
        val extra = buildList {
            kmFill?.let { add("$it km since last fill") }
            tankKmPerL?.let { add("%.1f km/L".format(it)) }
            litres?.let { add("%.1f L".format(it)) }
            rupees?.let { add("₹${"%.0f".format(it)}") }
        }
        return if (extra.isEmpty()) head else "$head ${extra.joinToString(" · ")}"
    }
    val lost = (barsBefore - barsAfter).coerceAtLeast(1)
    return when {
        lost == 1 && kmEvent != null -> "One bar lasted $kmEvent km. Gauge now $barsAfter/6."
        lost > 1 && kmEvent != null -> "$lost bars gone over $kmEvent km. Now $barsAfter/6."
        lost == 1 -> "Gauge stepped down to $barsAfter/6."
        else -> "Gauge dropped $barsBefore → $barsAfter."
    }
}
