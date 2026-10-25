package dev.gdk.bumblebee.ui.garage

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.data.local.MaintenancePlanEntity
import dev.gdk.bumblebee.garage.GarageFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import dev.gdk.bumblebee.ride.GarageInsights
import dev.gdk.bumblebee.ride.PartBaseline
import java.time.LocalDate
import java.time.ZoneId

data class ServiceUiState(
    val status: String = "",
    val odometerKm: Int? = null,
    val plans: List<PlanCard> = emptyList(),
    val serviceLogs: List<ServiceCard> = emptyList(),
    val runningRupeesPerKm: Double? = null,
    val runningPartial: Boolean = false,
    val remindKm: Int = 200,
    val remindDays: Int = 14,
    val extras: GarageExtras? = null,
    val editingPlan: Boolean = false,
    val editingLog: Boolean = false,
    val planId: Long? = null,
    val planTitle: String = "Engine oil",
    val planKind: String = "OIL",
    val planKm: String = "4000",
    val planDays: String = "180",
    val planDueOn: String = "",
    val planLastOdo: String = "",
    val planLastOn: String = "",
    val serviceId: Long? = null,
    val serviceItem: String = "oil",
    val serviceOdo: String = "",
    val serviceDate: String = LocalDate.now().toString(),
    val serviceShop: String = "",
    val serviceRupees: String = "",
    val serviceNote: String = "",
    val servicePlanId: Long? = null,
    val serviceExistingFile: String? = null,
    val serviceFilePending: Boolean = false,
)

class ServiceViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DashApp
    private val _ui = MutableStateFlow(fromTelemetry())
    val ui: StateFlow<ServiceUiState> = _ui.asStateFlow()
    private var pendingServiceUri: Uri? = null

    init {
        viewModelScope.launch {
            app.latestTelemetry.collect { sample ->
                if (sample == null) return@collect
                val previous = _ui.value.odometerKm
                _ui.update {
                    it.copy(
                        odometerKm = sample.odometerKm,
                        serviceOdo = it.serviceOdo.ifBlank { sample.odometerKm.toString() },
                    )
                }
                if (previous != sample.odometerKm) refresh()
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val odo = app.latestTelemetry.value?.odometerKm
                ?: withContext(Dispatchers.IO) { app.capture.latestKnownOdometerKm() }
            val snap = reloadService(app, odo)
            dev.gdk.bumblebee.widget.HomeWidgets.refresh(app, dev.gdk.bumblebee.widget.HomeWidget.CARE)
            val extras = loadGarageExtras(app, odo)
            _ui.update {
                it.copy(
                    odometerKm = odo,
                    serviceOdo = it.serviceOdo.ifBlank { odo?.toString().orEmpty() },
                    plans = snap.plans,
                    serviceLogs = snap.logs,
                    runningRupeesPerKm = snap.runningRupeesPerKm,
                    runningPartial = snap.runningPartial,
                    remindKm = snap.remindKm,
                    remindDays = snap.remindDays,
                    extras = extras,
                    servicePlanId = it.servicePlanId ?: snap.plans.firstOrNull { plan -> plan.kind == "OIL" }?.id,
                )
            }
        }
    }

    fun beginAddPlan() {
        clearPlanForm()
        _ui.update { it.copy(editingPlan = true, editingLog = false, status = "") }
    }

    fun beginEditPlan(card: PlanCard) {
        _ui.update {
            it.copy(
                editingPlan = true,
                editingLog = false,
                planId = card.id,
                planTitle = card.title,
                planKind = card.kind,
                planKm = card.intervalKm?.toString() ?: "",
                planDays = card.intervalDays?.toString() ?: "",
                planDueOn = card.dueOnIso ?: "",
                planLastOdo = card.lastDoneOdoKm?.toString() ?: "",
                planLastOn = card.lastDoneOnIso ?: "",
                status = "",
            )
        }
    }

    fun dismissPlan() {
        _ui.update { it.copy(editingPlan = false) }
    }

    fun setPlanTitle(value: String) { _ui.update { it.copy(planTitle = value) } }
    fun setPlanKind(value: String) {
        _ui.update {
            it.copy(
                planKind = value,
                planTitle = if (it.planId == null) prettyKind(value) else it.planTitle,
            )
        }
    }
    fun setPlanKm(value: String) { _ui.update { it.copy(planKm = value) } }
    fun setPlanDays(value: String) { _ui.update { it.copy(planDays = value) } }
    fun setPlanDueOn(value: String) { _ui.update { it.copy(planDueOn = value) } }
    fun setPlanLastOdo(value: String) { _ui.update { it.copy(planLastOdo = value) } }
    fun setPlanLastOn(value: String) { _ui.update { it.copy(planLastOn = value) } }

    fun clearPlanForm() {
        _ui.update {
            it.copy(
                planId = null,
                planTitle = "Engine oil",
                planKind = "OIL",
                planKm = "4000",
                planDays = "180",
                planDueOn = "",
                planLastOdo = "",
                planLastOn = "",
            )
        }
    }

    fun savePlan() {
        val state = _ui.value
        val km = state.planKm.toIntOrNull()
        val days = state.planDays.toIntOrNull()
        val due = state.planDueOn.trim().ifBlank { null }
        if (km == null && days == null && due == null) {
            _ui.update { it.copy(status = "A plan needs km, days, or a due date.") }
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val existing = state.planId?.let { id -> app.capture.allPlans().firstOrNull { it.id == id } }
                app.capture.upsertPlan(
                    MaintenancePlanEntity(
                        id = state.planId ?: 0,
                        title = state.planTitle.trim().ifBlank { prettyKind(state.planKind) },
                        kind = state.planKind,
                        intervalKm = km,
                        lastDoneOdoKm = state.planLastOdo.toIntOrNull(),
                        intervalDays = days,
                        lastDoneOnIso = state.planLastOn.trim().ifBlank { null },
                        dueOnIso = due,
                        createdTs = existing?.createdTs ?: System.currentTimeMillis(),
                    ),
                )
            }
            syncServicePrefsFromPlans()
            _ui.update { it.copy(editingPlan = false, status = "Plan saved.") }
            refresh()
        }
    }

    fun deletePlan(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { app.capture.deletePlan(id) }
            if (_ui.value.planId == id) {
                clearPlanForm()
                _ui.update { it.copy(editingPlan = false) }
            }
            refresh()
        }
    }

    fun beginLog() {
        clearServiceForm()
        _ui.update { it.copy(editingLog = true, editingPlan = false, status = "") }
    }

    fun markDone(plan: PlanCard) {
        pendingServiceUri = null
        _ui.update {
            it.copy(
                editingLog = true,
                editingPlan = false,
                serviceId = null,
                serviceItem = plan.title,
                serviceOdo = it.odometerKm?.toString().orEmpty(),
                serviceDate = LocalDate.now().toString(),
                serviceShop = "",
                serviceRupees = "",
                serviceNote = "",
                servicePlanId = plan.id,
                serviceExistingFile = null,
                serviceFilePending = false,
                status = "",
            )
        }
    }

    fun beginEditLog(card: ServiceCard) {
        pendingServiceUri = null
        _ui.update {
            it.copy(
                editingLog = true,
                editingPlan = false,
                serviceId = card.id,
                serviceItem = card.item,
                serviceOdo = card.odometerKm.toString(),
                serviceDate = Instant.ofEpochMilli(card.ts).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                serviceShop = card.shop ?: "",
                serviceRupees = card.rupees?.let { rupees -> "%.0f".format(rupees) } ?: "",
                serviceNote = card.note ?: "",
                servicePlanId = card.planId,
                serviceExistingFile = card.filePath,
                serviceFilePending = false,
                status = "",
            )
        }
    }

    fun dismissLog() {
        _ui.update { it.copy(editingLog = false) }
    }

    fun setServiceItem(value: String) { _ui.update { it.copy(serviceItem = value) } }
    fun setServiceOdo(value: String) { _ui.update { it.copy(serviceOdo = value) } }
    fun setServiceDate(value: String) { _ui.update { it.copy(serviceDate = value) } }
    fun setServiceShop(value: String) { _ui.update { it.copy(serviceShop = value) } }
    fun setServiceRupees(value: String) { _ui.update { it.copy(serviceRupees = value) } }
    fun setServiceNote(value: String) { _ui.update { it.copy(serviceNote = value) } }
    fun setServicePlan(id: Long?) { _ui.update { it.copy(servicePlanId = id) } }
    fun pickServiceFile(uri: Uri?) {
        pendingServiceUri = uri
        _ui.update { it.copy(serviceFilePending = uri != null) }
    }

    /** Adds or removes one job from what the visit did. */
    fun toggleWork(job: String) {
        _ui.update {
            val jobs = GarageInsights.workItems(it.serviceItem).toMutableList()
            val existing = jobs.firstOrNull { j -> j.equals(job, ignoreCase = true) }
            if (existing != null) jobs.remove(existing) else jobs += job
            it.copy(serviceItem = jobs.joinToString(", "), servicePlanId = null)
        }
    }

    /** Saves when the bike was bought; original parts count from here. */
    fun setPurchase(odoKm: Int?, date: LocalDate?) {
        GaragePrefs(app).purchase = if (odoKm == null && date == null) null else PartBaseline(odoKm, date)
        refresh()
    }

    /** The rider's own record for one part, or null to go back to the default. */
    fun setPartBaseline(title: String, baseline: PartBaseline?) {
        GaragePrefs(app).setPartBaseline(title, baseline)
        refresh()
    }

    fun applyServiceKind(kind: String) {
        _ui.update {
            it.copy(
                serviceItem = prettyKind(kind).lowercase(),
                servicePlanId = it.plans.firstOrNull { plan -> plan.kind == kind }?.id,
            )
        }
    }

    fun clearServiceForm() {
        pendingServiceUri = null
        _ui.update {
            it.copy(
                editingLog = false,
                serviceId = null,
                serviceItem = "oil",
                serviceOdo = it.odometerKm?.toString().orEmpty(),
                serviceDate = LocalDate.now().toString(),
                serviceShop = "",
                serviceRupees = "",
                serviceNote = "",
                servicePlanId = it.plans.firstOrNull { plan -> plan.kind == "OIL" }?.id,
                serviceExistingFile = null,
                serviceFilePending = false,
            )
        }
    }

    fun logServiceWork() {
        val state = _ui.value
        val odo = state.serviceOdo.toIntOrNull() ?: state.odometerKm
        if (odo == null) {
            _ui.update { it.copy(status = "Need an odometer km.") }
            return
        }
        val date = runCatching { LocalDate.parse(state.serviceDate.trim()) }.getOrNull() ?: LocalDate.now()
        val ts = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val rupees = state.serviceRupees.toDoubleOrNull()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val editId = state.serviceId
                val id = if (editId == null) {
                    app.capture.logService(
                        odo, state.serviceItem, rupees, state.serviceNote, ts,
                        shop = state.serviceShop, planId = state.servicePlanId,
                    )
                } else {
                    val existing = app.capture.allServices().first { it.id == editId }
                    app.capture.updateService(
                        existing.copy(
                            ts = ts,
                            odometerKm = odo,
                            item = state.serviceItem,
                            rupees = rupees,
                            note = state.serviceNote,
                            shop = state.serviceShop,
                            planId = state.servicePlanId,
                        ),
                    )
                    editId
                }
                pendingServiceUri?.let { uri ->
                    val path = GarageFiles.copyIn(getApplication(), uri, "service", id, GarageFiles.displayName(getApplication(), uri))
                    if (path != null) {
                        val row = app.capture.allServices().first { it.id == id }
                        app.capture.updateService(row.copy(filePath = path))
                    }
                }
                pendingServiceUri = null
                val plan = app.capture.allPlans().firstOrNull { it.id == state.servicePlanId }
                    ?: app.capture.allPlans().firstOrNull { it.kind == "SERVICE" }
                if (plan?.kind == "SERVICE" || state.serviceItem.contains("service", true)) {
                    app.prefs.lastServiceOdoKm = odo
                    plan?.intervalKm?.let { app.prefs.serviceIntervalKm = it }
                }
            }
            _ui.update { it.copy(status = "Logged ${state.serviceItem} at $odo km.") }
            clearServiceForm()
            refresh()
        }
    }

    fun deleteService(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val row = app.capture.allServices().firstOrNull { it.id == id }
                GarageFiles.delete(row?.filePath)
                app.capture.deleteService(id)
            }
            if (_ui.value.serviceId == id) clearServiceForm()
            refresh()
        }
    }

    private suspend fun syncServicePrefsFromPlans() = withContext(Dispatchers.IO) {
        val periodic = app.capture.allPlans().firstOrNull { it.kind == "SERVICE" } ?: return@withContext
        periodic.intervalKm?.let { app.prefs.serviceIntervalKm = it }
        periodic.lastDoneOdoKm?.let { app.prefs.lastServiceOdoKm = it }
    }

    private fun fromTelemetry(): ServiceUiState {
        val odo = app.latestTelemetry.value?.odometerKm
        return ServiceUiState(
            odometerKm = odo,
            serviceOdo = odo?.toString() ?: "",
        )
    }
}
