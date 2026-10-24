package dev.gdk.bumblebee.ui.garage

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ble.BikeBridgeService
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.ble.android.BleScanner
import dev.gdk.bumblebee.ble.android.DiscoveredBike
import dev.gdk.bumblebee.data.local.BikeDocumentEntity
import dev.gdk.bumblebee.data.local.MaintenancePlanEntity
import dev.gdk.bumblebee.data.local.RideCaptureRepository
import dev.gdk.bumblebee.data.local.toRecord
import dev.gdk.bumblebee.garage.GarageFiles
import dev.gdk.bumblebee.garage.GarageReminders
import dev.gdk.bumblebee.nav.MapsNavSource
import dev.gdk.bumblebee.ride.CareBand
import dev.gdk.bumblebee.ride.GarageCare
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class DocCard(
    val id: Long,
    val title: String,
    val kind: String,
    val number: String?,
    val expiryIso: String?,
    val filePath: String?,
    val mime: String?,
    val band: CareBand,
    val label: String,
)

data class PlanCard(
    val id: Long,
    val title: String,
    val kind: String,
    val intervalKm: Int?,
    val intervalDays: Int?,
    val dueOnIso: String?,
    val lastDoneOdoKm: Int?,
    val lastDoneOnIso: String?,
    val band: CareBand,
    val label: String,
)

data class ServiceCard(
    val id: Long,
    val whenLabel: String,
    val story: String,
    val filePath: String?,
    val mimeGuess: String? = null,
    val odometerKm: Int,
    val item: String,
    val rupees: Double?,
    val note: String?,
    val shop: String?,
    val planId: Long?,
    val ts: Long,
)

data class UpcomingItem(
    val key: String,
    val label: String,
    val band: CareBand,
)

data class GarageUiState(
    val connection: ConnectionState = ConnectionState.Idle,
    val bikeMac: String? = null,
    val riderName: String = "RIDER",
    val odometerKm: Int? = null,
    val tripAKm: Double? = null,
    val tripBKm: Double? = null,
    val fuelBars: Int? = null,
    val scanning: Boolean = false,
    val devices: List<DiscoveredBike> = emptyList(),
    val status: String = "Pair the bike here. Papers, service, and reminders stay with the motorcycle.",
    val tankKmPerL: Double? = null,
    val rupeesPerKm: Double? = null,
    val costPartial: Boolean = false,
    val rangeKm: Double? = null,
    val mapsNavToCluster: Boolean = true,
    val runningRupeesPerKm: Double? = null,
    val runningPartial: Boolean = false,
    val documents: List<DocCard> = emptyList(),
    val plans: List<PlanCard> = emptyList(),
    val upcoming: List<UpcomingItem> = emptyList(),
    val serviceLogs: List<ServiceCard> = emptyList(),
    val remindersEnabled: Boolean = true,
    val remindDays: String = "14",
    val remindKm: String = "200",
    val pauseWindowMin: Int = 45,
    val autoConnect: Boolean = true,
    val sosContacts: String = "",
    val crashSensitivity: String = "NORMAL",
    val bloodGroup: String = "",
    val medicalNote: String = "",
    val docId: Long? = null,
    val docTitle: String = "RC",
    val docKind: String = "RC",
    val docExpiry: String = "",
    val docNumber: String = "",
    val docNote: String = "",
    val docHasFile: Boolean = false,
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
)

class GarageViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DashApp
    private val scanner = BleScanner(application)
    private val _ui = MutableStateFlow(fromPrefs())
    val ui: StateFlow<GarageUiState> = _ui.asStateFlow()
    private var pendingDocUri: Uri? = null
    private var pendingServiceUri: Uri? = null

    init {
        viewModelScope.launch {
            app.connection.collect { c ->
                _ui.update { it.copy(connection = c, bikeMac = app.prefs.bikeMac) }
            }
        }
        viewModelScope.launch {
            app.latestTelemetry.collect { t ->
                if (t != null) {
                    _ui.update {
                        it.copy(
                            odometerKm = t.odometerKm,
                            tripAKm = t.tripAKm,
                            tripBKm = t.tripBKm,
                            fuelBars = t.fuelBars,
                            serviceOdo = it.serviceOdo.ifBlank { t.odometerKm.toString() },
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            scanner.results.collect { map ->
                val sorted = map.values.sortedWith(
                    compareByDescending<DiscoveredBike> { it.looksLikeSuzuki }
                        .thenByDescending { it.rssi },
                )
                _ui.update { it.copy(devices = sorted) }
            }
        }
        viewModelScope.launch { reloadCare() }
    }

    fun setRiderName(value: String) { _ui.update { it.copy(riderName = value) } }

    fun saveRiderName() {
        val name = _ui.value.riderName.trim().ifBlank { "RIDER" }
        app.prefs.riderName = name
        _ui.update { it.copy(riderName = name, status = "Rider name saved.") }
    }

    fun setRemindersEnabled(on: Boolean) {
        app.prefs.remindersEnabled = on
        _ui.update { it.copy(remindersEnabled = on, status = if (on) "Reminders on." else "Reminders off.") }
    }

    fun setRemindDays(value: String) { _ui.update { it.copy(remindDays = value) } }
    fun setRemindKm(value: String) { _ui.update { it.copy(remindKm = value) } }

    fun saveReminders() {
        app.prefs.remindDays = _ui.value.remindDays.toIntOrNull() ?: 14
        app.prefs.remindKm = _ui.value.remindKm.toIntOrNull() ?: 200
        app.prefs.lastReminderStamp = ""
        _ui.update {
            it.copy(
                remindDays = app.prefs.remindDays.toString(),
                remindKm = app.prefs.remindKm.toString(),
                status = "Remind ${app.prefs.remindDays} days or ${app.prefs.remindKm} km ahead.",
            )
        }
        viewModelScope.launch { GarageReminders.refresh(app); reloadCare() }
    }

    fun setDocTitle(value: String) { _ui.update { it.copy(docTitle = value) } }
    fun setDocKind(value: String) { _ui.update { it.copy(docKind = value, docTitle = if (it.docId == null) prettyKind(value) else it.docTitle) } }
    fun setDocExpiry(value: String) { _ui.update { it.copy(docExpiry = value) } }
    fun setDocNumber(value: String) { _ui.update { it.copy(docNumber = value) } }
    fun setDocNote(value: String) { _ui.update { it.copy(docNote = value) } }
    fun pickDocFile(uri: Uri?) { pendingDocUri = uri }

    fun editDocument(card: DocCard) {
        pendingDocUri = null
        _ui.update {
            it.copy(
                docId = card.id,
                docTitle = card.title,
                docKind = card.kind,
                docExpiry = card.expiryIso ?: "",
                docNumber = card.number ?: "",
                docHasFile = card.filePath != null,
                status = "Editing ${card.title}.",
            )
        }
    }

    fun clearDocumentForm() {
        pendingDocUri = null
        _ui.update {
            it.copy(docId = null, docTitle = "RC", docKind = "RC", docExpiry = "", docNumber = "", docNote = "", docHasFile = false)
        }
    }

    fun saveDocument() {
        val s = _ui.value
        val title = s.docTitle.trim().ifBlank { prettyKind(s.docKind) }
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                val existing = s.docId?.let { id -> app.capture.allDocuments().firstOrNull { it.id == id } }
                val savedId = app.capture.upsertDocument(
                    BikeDocumentEntity(
                        id = s.docId ?: 0,
                        title = title,
                        kind = s.docKind,
                        expiryIso = s.docExpiry.trim().ifBlank { null },
                        number = s.docNumber.trim().ifBlank { null },
                        filePath = existing?.filePath,
                        mime = existing?.mime,
                        note = s.docNote.trim().ifBlank { null },
                        createdTs = existing?.createdTs ?: System.currentTimeMillis(),
                    ),
                )
                pendingDocUri?.let { uri ->
                    val path = GarageFiles.copyIn(getApplication(), uri, "docs", savedId, GarageFiles.displayName(getApplication(), uri))
                    if (path != null) {
                        val row = app.capture.allDocuments().first { it.id == savedId }
                        app.capture.upsertDocument(
                            row.copy(filePath = path, mime = GarageFiles.mime(getApplication(), uri)),
                        )
                    }
                }
                pendingDocUri = null
                savedId
            }
            syncPaperPrefs()
            _ui.update { it.copy(status = "Saved $title.", docId = id, docHasFile = true) }
            reloadCare()
        }
    }

    fun deleteDocument(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val row = app.capture.allDocuments().firstOrNull { it.id == id }
                GarageFiles.delete(row?.filePath)
                app.capture.deleteDocument(id)
            }
            if (_ui.value.docId == id) clearDocumentForm()
            reloadCare()
        }
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

    fun editPlan(card: PlanCard) {
        _ui.update {
            it.copy(
                planId = card.id,
                planTitle = card.title,
                planKind = card.kind,
                planKm = card.intervalKm?.toString() ?: "",
                planDays = card.intervalDays?.toString() ?: "",
                planDueOn = card.dueOnIso ?: "",
                planLastOdo = card.lastDoneOdoKm?.toString() ?: "",
                planLastOn = card.lastDoneOnIso ?: "",
                status = "Editing ${card.title}.",
            )
        }
    }

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
        val s = _ui.value
        val km = s.planKm.toIntOrNull()
        val days = s.planDays.toIntOrNull()
        val due = s.planDueOn.trim().ifBlank { null }
        if (km == null && days == null && due == null) {
            _ui.update { it.copy(status = "A plan needs km, days, or a due date.") }
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val existing = s.planId?.let { id -> app.capture.allPlans().firstOrNull { it.id == id } }
                app.capture.upsertPlan(
                    MaintenancePlanEntity(
                        id = s.planId ?: 0,
                        title = s.planTitle.trim().ifBlank { prettyKind(s.planKind) },
                        kind = s.planKind,
                        intervalKm = km,
                        lastDoneOdoKm = s.planLastOdo.toIntOrNull(),
                        intervalDays = days,
                        lastDoneOnIso = s.planLastOn.trim().ifBlank { null },
                        dueOnIso = due,
                        createdTs = existing?.createdTs ?: System.currentTimeMillis(),
                    ),
                )
            }
            syncServicePrefsFromPlans()
            _ui.update { it.copy(status = "Plan saved.") }
            reloadCare()
        }
    }

    fun deletePlan(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { app.capture.deletePlan(id) }
            if (_ui.value.planId == id) clearPlanForm()
            reloadCare()
        }
    }

    fun setServiceItem(value: String) { _ui.update { it.copy(serviceItem = value) } }
    fun setServiceOdo(value: String) { _ui.update { it.copy(serviceOdo = value) } }
    fun setServiceDate(value: String) { _ui.update { it.copy(serviceDate = value) } }
    fun setServiceShop(value: String) { _ui.update { it.copy(serviceShop = value) } }
    fun setServiceRupees(value: String) { _ui.update { it.copy(serviceRupees = value) } }
    fun setServiceNote(value: String) { _ui.update { it.copy(serviceNote = value) } }
    fun setServicePlan(id: Long?) { _ui.update { it.copy(servicePlanId = id) } }
    fun pickServiceFile(uri: Uri?) { pendingServiceUri = uri }

    fun applyServiceKind(kind: String) {
        _ui.update {
            it.copy(
                serviceItem = prettyKind(kind).lowercase(),
                servicePlanId = it.plans.firstOrNull { p -> p.kind == kind }?.id,
            )
        }
    }

    fun editService(card: ServiceCard) {
        pendingServiceUri = null
        _ui.update {
            it.copy(
                serviceId = card.id,
                serviceItem = card.item,
                serviceOdo = card.odometerKm.toString(),
                serviceDate = Instant.ofEpochMilli(card.ts).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                serviceShop = card.shop ?: "",
                serviceRupees = card.rupees?.let { r -> "%.0f".format(r) } ?: "",
                serviceNote = card.note ?: "",
                servicePlanId = card.planId,
                status = "Editing ${card.item}.",
            )
        }
    }

    fun clearServiceForm() {
        pendingServiceUri = null
        _ui.update {
            it.copy(
                serviceId = null,
                serviceItem = "oil",
                serviceOdo = it.odometerKm?.toString() ?: "",
                serviceDate = LocalDate.now().toString(),
                serviceShop = "",
                serviceRupees = "",
                serviceNote = "",
                servicePlanId = it.plans.firstOrNull { p -> p.kind == "OIL" }?.id,
            )
        }
    }

    fun logServiceWork() {
        val s = _ui.value
        val odo = s.serviceOdo.toIntOrNull() ?: s.odometerKm
        if (odo == null) {
            _ui.update { it.copy(status = "Need an odometer km.") }
            return
        }
        val date = runCatching { LocalDate.parse(s.serviceDate.trim()) }.getOrNull() ?: LocalDate.now()
        val ts = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val rupees = s.serviceRupees.toDoubleOrNull()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val editId = s.serviceId
                val id = if (editId == null) {
                    app.capture.logService(
                        odo, s.serviceItem, rupees, s.serviceNote, ts,
                        shop = s.serviceShop, planId = s.servicePlanId,
                    )
                } else {
                    val existing = app.capture.allServices().first { it.id == editId }
                    app.capture.updateService(
                        existing.copy(
                            ts = ts,
                            odometerKm = odo,
                            item = s.serviceItem,
                            rupees = rupees,
                            note = s.serviceNote,
                            shop = s.serviceShop,
                            planId = s.servicePlanId,
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
                val plan = app.capture.allPlans().firstOrNull { it.id == s.servicePlanId } ?: app.capture.allPlans().firstOrNull { it.kind == "SERVICE" }
                if (plan?.kind == "SERVICE" || s.serviceItem.contains("service", true)) {
                    app.prefs.lastServiceOdoKm = odo
                    plan?.intervalKm?.let { app.prefs.serviceIntervalKm = it }
                }
            }
            _ui.update { it.copy(status = "Logged ${s.serviceItem} at $odo km.") }
            clearServiceForm()
            reloadCare()
        }
    }

    fun deleteService(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val row = app.capture.allServices().firstOrNull { it.id == id }
                GarageFiles.delete(row?.filePath)
                app.capture.deleteService(id)
            }
            reloadCare()
        }
    }

    fun startScan() {
        scanner.start()
        _ui.update { it.copy(scanning = true, status = "Scanning…") }
    }

    fun stopScan() {
        scanner.stop()
        _ui.update { it.copy(scanning = false) }
    }

    fun pair(mac: String) {
        stopScan()
        app.prefs.bikeMac = mac
        _ui.update { it.copy(bikeMac = mac, status = "Connecting $mac") }
        BikeBridgeService.connect(getApplication(), mac)
    }

    fun connectSaved() {
        val mac = app.prefs.bikeMac ?: return
        pair(mac)
    }

    fun disconnectBike() {
        BikeBridgeService.stop(getApplication())
        _ui.update { it.copy(status = "Disconnecting…") }
    }

    fun setPauseWindow(minutes: Int) {
        app.prefs.pauseWindowMin = minutes
        _ui.update { it.copy(pauseWindowMin = minutes) }
    }

    fun setAutoConnect(on: Boolean) {
        app.prefs.autoConnect = on
        _ui.update { it.copy(autoConnect = on, status = if (on) "Auto-connect is on." else "Auto-connect is off.") }
    }

    fun skipAutoConnectToday() {
        app.prefs.skipAutoConnectDate = LocalDate.now().toString()
        _ui.update { it.copy(status = "Auto-connect is off for the rest of today.") }
    }

    fun setSosContacts(value: String) { _ui.update { it.copy(sosContacts = value) } }
    fun setCrashSensitivity(value: String) {
        app.prefs.crashSensitivity = value
        _ui.update { it.copy(crashSensitivity = value) }
    }
    fun setBloodGroup(value: String) { _ui.update { it.copy(bloodGroup = value) } }
    fun setMedicalNote(value: String) { _ui.update { it.copy(medicalNote = value) } }

    fun onTestSosPermission(granted: Boolean) {
        if (!granted) {
            _ui.update { it.copy(status = "SMS permission is required to send a test.") }
            return
        }
        val numbers = app.prefs.emergencyContacts.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.take(3)
        if (numbers.isEmpty()) {
            _ui.update { it.copy(status = "Save at least one emergency number first.") }
            return
        }
        val sms = android.telephony.SmsManager.getDefault()
        val body = "TEST from Bumblebee. This is not an emergency."
        for (number in numbers) {
            runCatching { sms.sendTextMessage(number, null, body, null, null) }
        }
        _ui.update { it.copy(status = "Test SMS sent to ${numbers.size} contact${if (numbers.size == 1) "" else "s"}.") }
    }

    fun saveSafety() {
        val contacts = _ui.value.sosContacts.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.take(3)
        app.prefs.emergencyContacts = contacts.joinToString(",")
        app.prefs.bloodGroup = _ui.value.bloodGroup.trim()
        app.prefs.medicalNote = _ui.value.medicalNote.trim()
        _ui.update { it.copy(sosContacts = app.prefs.emergencyContacts, status = "Safety details saved. This is a best-effort aid, not a certified emergency device.") }
    }

    fun forgetBike() {
        BikeBridgeService.stop(getApplication())
        app.prefs.bikeMac = null
        _ui.update { it.copy(bikeMac = null, status = "Saved bike cleared.") }
    }

    fun setPauseWindowMin(minutes: Int) {
        val next = if (minutes in dev.gdk.bumblebee.ride.RideContinuity.WINDOW_CHOICES_MIN) minutes else 45
        app.prefs.pauseWindowMin = next
        _ui.update {
            it.copy(
                pauseWindowMin = next,
                status = if (next == 0) {
                    "Auto-continue is off. Each ignition-off ends the ride."
                } else {
                    "A stop up to $next min keeps the same ride."
                },
            )
        }
    }

    fun setMapsNavToCluster(enabled: Boolean) {
        app.prefs.mapsNavToCluster = enabled
        if (!enabled) MapsNavSource.clear()
        _ui.update {
            it.copy(
                mapsNavToCluster = enabled,
                status = if (enabled) {
                    "Cluster will follow Google Maps notifications. Maps APIs can wait."
                } else {
                    "Cluster nav from Maps notifications is off."
                },
            )
        }
    }

    override fun onCleared() {
        scanner.stop()
        super.onCleared()
    }

    private suspend fun reloadCare() {
        val packed = withContext(Dispatchers.IO) {
            app.capture.ensureGarageDefaults(
                app.prefs.rcExpiry,
                app.prefs.insuranceExpiry,
                app.prefs.pucExpiry,
                app.prefs.serviceIntervalKm,
                app.prefs.lastServiceOdoKm,
            )
            val eco = app.capture.bikeEconomics(_ui.value.fuelBars)
            val rides = app.capture.finishedRides().map { it.toRecord() }
            val kmPerDay = RideRhythm.paceKmPerDay(rides)
            val today = LocalDate.now()
            val odo = _ui.value.odometerKm
            val remindDays = app.prefs.remindDays
            val remindKm = app.prefs.remindKm
            val docs = app.capture.allDocuments().map { doc ->
                val insight = GarageCare.documentInsight(doc.expiryIso, today, doc.title, remindDays)
                DocCard(doc.id, doc.title, doc.kind, doc.number, doc.expiryIso, doc.filePath, doc.mime, insight.band, insight.label)
            }
            val plans = app.capture.allPlans().filter { it.active }.map { plan ->
                val insight = GarageCare.planInsight(
                    title = plan.title,
                    currentOdoKm = odo,
                    lastDoneOdoKm = plan.lastDoneOdoKm,
                    intervalKm = plan.intervalKm,
                    lastDoneOn = plan.lastDoneOnIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    intervalDays = plan.intervalDays,
                    dueOn = plan.dueOnIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    today = today,
                    kmPerDay = kmPerDay,
                    remindKm = remindKm,
                    remindDays = remindDays,
                )
                PlanCard(
                    plan.id, plan.title, plan.kind, plan.intervalKm, plan.intervalDays, plan.dueOnIso,
                    plan.lastDoneOdoKm, plan.lastDoneOnIso, insight.band, insight.label,
                )
            }
            val logs = app.capture.allServices()
            val cards = logs.mapIndexed { index, log ->
                val newer = logs.getOrNull(index + 1)
                val since = newer?.let { log.odometerKm - it.odometerKm }?.takeIf { it > 0 }
                ServiceCard(
                    id = log.id,
                    whenLabel = DateTimeFormatter.ofPattern("d MMM yyyy").format(Instant.ofEpochMilli(log.ts).atZone(ZoneId.systemDefault())),
                    story = GarageCare.historyStory(log.item, log.odometerKm, log.rupees, log.shop, since),
                    filePath = log.filePath,
                    odometerKm = log.odometerKm,
                    item = log.item,
                    rupees = log.rupees,
                    note = log.note,
                    shop = log.shop,
                    planId = log.planId,
                    ts = log.ts,
                )
            }
            val upcoming = (docs.map { UpcomingItem("d${it.id}", it.label, it.band) } +
                plans.map { UpcomingItem("p${it.id}", it.label, it.band) })
                .filter { it.band == CareBand.OVERDUE || it.band == CareBand.DUE_SOON }
                .sortedBy { if (it.band == CareBand.OVERDUE) 0 else 1 }
            val running = app.capture.runningCost()
            CarePack(eco, docs, plans, cards, upcoming, running)
        }
        syncServicePrefsFromPlans()
        _ui.update {
            it.copy(
                tankKmPerL = packed.eco.tankKmPerL,
                rupeesPerKm = packed.eco.cost?.rollingRupeesPerKm,
                costPartial = packed.eco.cost?.isPartial == true,
                rangeKm = packed.eco.rangeKm,
                documents = packed.docs,
                plans = packed.plans,
                serviceLogs = packed.logs,
                upcoming = packed.upcoming,
                runningRupeesPerKm = packed.running?.rupeesPerKm,
                runningPartial = packed.running?.let { c -> c.fuelFillsPriced < c.fuelFillsTotal || c.servicesPriced < c.servicesTotal } == true,
                remindersEnabled = app.prefs.remindersEnabled,
                remindDays = app.prefs.remindDays.toString(),
                remindKm = app.prefs.remindKm.toString(),
                servicePlanId = it.servicePlanId ?: packed.plans.firstOrNull { p -> p.kind == "OIL" }?.id,
            )
        }
        GarageReminders.refresh(app)
    }

    private suspend fun syncServicePrefsFromPlans() = withContext(Dispatchers.IO) {
        val periodic = app.capture.allPlans().firstOrNull { it.kind == "SERVICE" } ?: return@withContext
        periodic.intervalKm?.let { app.prefs.serviceIntervalKm = it }
        periodic.lastDoneOdoKm?.let { app.prefs.lastServiceOdoKm = it }
    }

    private suspend fun syncPaperPrefs() = withContext(Dispatchers.IO) {
        val docs = app.capture.allDocuments()
        docs.firstOrNull { it.kind == "RC" }?.expiryIso?.let { app.prefs.rcExpiry = it }
        docs.firstOrNull { it.kind == "INSURANCE" }?.expiryIso?.let { app.prefs.insuranceExpiry = it }
        docs.firstOrNull { it.kind == "PUC" }?.expiryIso?.let { app.prefs.pucExpiry = it }
    }

    private fun fromPrefs(): GarageUiState {
        val odo = app.latestTelemetry.value?.odometerKm
        return GarageUiState(
            connection = app.connection.value,
            bikeMac = app.prefs.bikeMac,
            riderName = app.prefs.riderName,
            odometerKm = odo,
            tripAKm = app.latestTelemetry.value?.tripAKm,
            tripBKm = app.latestTelemetry.value?.tripBKm,
            fuelBars = app.latestTelemetry.value?.fuelBars,
            mapsNavToCluster = app.prefs.mapsNavToCluster,
            remindersEnabled = app.prefs.remindersEnabled,
            remindDays = app.prefs.remindDays.toString(),
            remindKm = app.prefs.remindKm.toString(),
            pauseWindowMin = app.prefs.pauseWindowMin,
            autoConnect = app.prefs.autoConnect,
            sosContacts = app.prefs.emergencyContacts,
            crashSensitivity = app.prefs.crashSensitivity,
            bloodGroup = app.prefs.bloodGroup,
            medicalNote = app.prefs.medicalNote,
            serviceOdo = odo?.toString() ?: "",
        )
    }

    private data class CarePack(
        val eco: RideCaptureRepository.BikeEconomics,
        val docs: List<DocCard>,
        val plans: List<PlanCard>,
        val logs: List<ServiceCard>,
        val upcoming: List<UpcomingItem>,
        val running: dev.gdk.bumblebee.ride.RunningCost?,
    )
}

internal fun prettyKind(kind: String): String = when (kind) {
    "RC" -> "RC"
    "INSURANCE" -> "Insurance"
    "PUC" -> "PUC"
    "WARRANTY" -> "Warranty"
    "INVOICE" -> "Invoice"
    "PERMIT" -> "Permit"
    "OIL" -> "Engine oil"
    "CHAIN" -> "Drive chain"
    "BRAKES" -> "Brake pads"
    "TYRES" -> "Tyres"
    "BATTERY" -> "Battery"
    "SERVICE" -> "Periodic service"
    else -> kind.lowercase().replaceFirstChar { it.uppercase() }
}
