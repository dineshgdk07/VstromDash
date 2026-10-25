package dev.gdk.bumblebee.ui.bike

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.bike.AccentChoice
import dev.gdk.bumblebee.bike.BikeProfile
import dev.gdk.bumblebee.bike.BikeVariant
import dev.gdk.bumblebee.bike.EmergencyContact
import dev.gdk.bumblebee.data.local.BikeDocumentEntity
import dev.gdk.bumblebee.garage.GarageFiles
import dev.gdk.bumblebee.ride.PartBaseline
import dev.gdk.bumblebee.ride.RegNo
import dev.gdk.bumblebee.ui.garage.GaragePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** One paper being filled in: RC, insurance or PUC. */
data class PaperDraft(
    val kind: String,
    val title: String,
    val expiry: LocalDate? = null,
    val number: String = "",
    val fileName: String? = null,
    val pending: Uri? = null,
)

enum class OnboardingStep(val title: String) {
    BIKE("Rider and bike"),
    OWNERSHIP("Ownership"),
    PAPERS("Papers"),
    SAFETY("Safety and pairing"),
}

data class OnboardingUi(
    val step: OnboardingStep = OnboardingStep.BIKE,
    val riderName: String = "",
    val variant: BikeVariant = BikeVariant.YELLOW,
    val accent: AccentChoice? = null,
    val regNo: String = "",
    val boughtNew: Boolean? = null,
    val purchaseDate: LocalDate? = null,
    val purchaseOdo: String = "",
    val currentOdo: Int? = null,
    val papers: List<PaperDraft> = emptyList(),
    val contacts: List<EmergencyContact> = emptyList(),
    val bloodGroup: String = "",
    val saving: Boolean = false,
    val done: Boolean = false,
)

/** First-run setup, and "Bike profile" later: everything is prefilled from what is already saved. */
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DashApp
    private val profile = BikeProfile(application)
    private val garage = GaragePrefs(application)
    private val _ui = MutableStateFlow(OnboardingUi())
    val ui: StateFlow<OnboardingUi> = _ui.asStateFlow()

    init {
        val purchase = garage.purchase
        _ui.value = OnboardingUi(
            riderName = app.prefs.riderName.takeIf { it != "RIDER" }.orEmpty(),
            variant = profile.variant,
            accent = profile.accentChoice,
            regNo = profile.regNo,
            boughtNew = purchase?.odoKm?.let { it == 0 },
            purchaseDate = purchase?.date,
            purchaseOdo = purchase?.odoKm?.takeIf { it > 0 }?.toString().orEmpty(),
            contacts = profile.contacts,
            bloodGroup = app.prefs.bloodGroup,
        )
        viewModelScope.launch {
            val (docs, odo) = withContext(Dispatchers.IO) {
                app.capture.allDocuments() to (app.latestTelemetry.value?.odometerKm ?: app.capture.latestKnownOdometerKm())
            }
            fun draft(kind: String, title: String): PaperDraft {
                val d = docs.firstOrNull { it.kind == kind }
                return PaperDraft(
                    kind = kind,
                    title = title,
                    expiry = d?.expiryIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    number = d?.number.orEmpty(),
                    fileName = d?.filePath?.substringAfterLast('/'),
                )
            }
            _ui.update {
                it.copy(
                    currentOdo = odo,
                    papers = listOf(draft("RC", "RC"), draft("INSURANCE", "Insurance"), draft("PUC", "PUC")),
                    regNo = it.regNo.ifBlank { docs.firstOrNull { d -> d.kind == "RC" }?.number?.let(RegNo::format).orEmpty() },
                )
            }
        }
    }

    fun go(step: OnboardingStep) = _ui.update { it.copy(step = step) }
    fun setName(v: String) = _ui.update { it.copy(riderName = v) }

    /** Colour changes apply at once, so the rider sees the app take on the new accent. */
    fun setVariant(v: BikeVariant) {
        profile.variant = v
        _ui.update { it.copy(variant = v) }
    }

    fun setAccent(a: AccentChoice?) {
        profile.accentChoice = a
        _ui.update { it.copy(accent = a) }
    }

    fun setRegNo(v: String) = _ui.update { it.copy(regNo = v) }

    fun setBoughtNew(new: Boolean) = _ui.update { it.copy(boughtNew = new, purchaseOdo = if (new) "" else it.purchaseOdo) }
    fun setPurchaseDate(d: LocalDate) = _ui.update { it.copy(purchaseDate = d) }
    fun setPurchaseOdo(v: String) = _ui.update { it.copy(purchaseOdo = v.filter(Char::isDigit)) }

    fun setPaperExpiry(kind: String, d: LocalDate) = updatePaper(kind) { it.copy(expiry = d) }
    fun setPaperNumber(kind: String, v: String) = updatePaper(kind) { it.copy(number = v) }
    fun setPaperFile(kind: String, uri: Uri?) {
        if (uri == null) return
        val name = GarageFiles.displayName(getApplication(), uri) ?: "file"
        updatePaper(kind) { it.copy(pending = uri, fileName = name) }
    }

    private fun updatePaper(kind: String, change: (PaperDraft) -> PaperDraft) =
        _ui.update { s -> s.copy(papers = s.papers.map { if (it.kind == kind) change(it) else it }) }

    fun setContacts(c: List<EmergencyContact>) {
        profile.contacts = c
        _ui.update { it.copy(contacts = profile.contacts) }
    }

    fun setBlood(v: String) = _ui.update { it.copy(bloodGroup = v) }

    /** Saves every step, including the ones skipped with what was already there. */
    fun finish() {
        val s = _ui.value
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            s.riderName.trim().takeIf { it.isNotEmpty() }?.let { app.prefs.riderName = it }
            val reg = s.regNo.takeIf { it.isNotBlank() }?.let(RegNo::format).orEmpty()
            profile.regNo = reg
            when (s.boughtNew) {
                true -> garage.purchase = PartBaseline(odoKm = 0, date = s.purchaseDate)
                false -> garage.purchase = PartBaseline(odoKm = s.purchaseOdo.toIntOrNull(), date = s.purchaseDate)
                null -> Unit
            }
            app.prefs.bloodGroup = s.bloodGroup.trim()
            withContext(Dispatchers.IO) { savePapers(s.papers, reg) }
            profile.onboarded = true
            _ui.update { it.copy(saving = false, done = true) }
        }
    }

    /** Skipping still counts as onboarded, so the flow doesn't come back on every launch. */
    fun skip() {
        profile.onboarded = true
        _ui.update { it.copy(done = true) }
    }

    private suspend fun savePapers(papers: List<PaperDraft>, reg: String) {
        val docs = app.capture.allDocuments()
        papers.forEach { p ->
            val existing = docs.firstOrNull { it.kind == p.kind }
            val number = p.number.trim().ifBlank { if (p.kind == "RC") reg else "" }.ifBlank { null }
            val expiry = p.expiry?.toString()
            if (existing == null && expiry == null && number == null && p.pending == null) return@forEach
            val id = app.capture.upsertDocument(
                (existing ?: BikeDocumentEntity(title = p.title, kind = p.kind, createdTs = System.currentTimeMillis()))
                    .copy(expiryIso = expiry ?: existing?.expiryIso, number = number ?: existing?.number),
            )
            p.pending?.let { uri ->
                val path = GarageFiles.copyIn(getApplication(), uri, "docs", id, GarageFiles.displayName(getApplication(), uri))
                if (path != null) {
                    val row = app.capture.allDocuments().first { it.id == id }
                    GarageFiles.delete(existing?.filePath?.takeIf { it != path })
                    app.capture.upsertDocument(row.copy(filePath = path, mime = GarageFiles.mime(getApplication(), uri)))
                }
            }
        }
        val saved = app.capture.allDocuments()
        saved.firstOrNull { it.kind == "RC" }?.expiryIso?.let { app.prefs.rcExpiry = it }
        saved.firstOrNull { it.kind == "INSURANCE" }?.expiryIso?.let { app.prefs.insuranceExpiry = it }
        saved.firstOrNull { it.kind == "PUC" }?.expiryIso?.let { app.prefs.pucExpiry = it }
    }
}
