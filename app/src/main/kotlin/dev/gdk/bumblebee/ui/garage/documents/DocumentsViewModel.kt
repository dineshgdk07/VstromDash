package dev.gdk.bumblebee.ui.garage.documents

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.data.local.BikeDocumentEntity
import dev.gdk.bumblebee.garage.GarageFiles
import dev.gdk.bumblebee.ui.garage.DocCard
import dev.gdk.bumblebee.ui.garage.prettyKind
import dev.gdk.bumblebee.ui.garage.reloadGarage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DocumentsUiState(
    val status: String = "",
    val documents: List<DocCard> = emptyList(),
    val remindDays: Int = 14,
    val editing: Boolean = false,
    val docId: Long? = null,
    val docTitle: String = "RC",
    val docKind: String = "RC",
    val docExpiry: String = "",
    val docNumber: String = "",
    val docNote: String = "",
    val docHasFile: Boolean = false,
)

class DocumentsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DashApp
    private val _ui = MutableStateFlow(DocumentsUiState(remindDays = app.prefs.remindDays))
    val ui: StateFlow<DocumentsUiState> = _ui.asStateFlow()
    private var pendingDocUri: Uri? = null

    init {
        refresh()
    }

    fun refresh() {
        val fuel = app.latestTelemetry.value?.fuelBars
        val odo = app.latestTelemetry.value?.odometerKm
        viewModelScope.launch {
            val snap = reloadGarage(app, fuel, odo)
            _ui.update { it.copy(documents = snap.docs, remindDays = app.prefs.remindDays) }
        }
    }

    fun beginAdd() {
        pendingDocUri = null
        _ui.update {
            it.copy(
                editing = true,
                docId = null,
                docTitle = "RC",
                docKind = "RC",
                docExpiry = "",
                docNumber = "",
                docNote = "",
                docHasFile = false,
                status = "",
            )
        }
    }

    fun beginEdit(card: DocCard) {
        pendingDocUri = null
        _ui.update {
            it.copy(
                editing = true,
                docId = card.id,
                docTitle = card.title,
                docKind = card.kind,
                docExpiry = card.expiryIso ?: "",
                docNumber = card.number ?: "",
                docNote = card.note ?: "",
                docHasFile = card.filePath != null,
                status = "",
            )
        }
    }

    fun dismissEditor() {
        _ui.update { it.copy(editing = false) }
    }

    fun setDocTitle(value: String) { _ui.update { it.copy(docTitle = value) } }
    fun setDocKind(value: String) {
        _ui.update { it.copy(docKind = value, docTitle = if (it.docId == null) prettyKind(value) else it.docTitle) }
    }
    fun setDocExpiry(value: String) { _ui.update { it.copy(docExpiry = value) } }
    fun setDocNumber(value: String) { _ui.update { it.copy(docNumber = value) } }
    fun setDocNote(value: String) { _ui.update { it.copy(docNote = value) } }
    fun pickDocFile(uri: Uri?) {
        pendingDocUri = uri
        if (uri != null) _ui.update { it.copy(docHasFile = true) }
    }

    fun saveDocument() {
        val state = _ui.value
        val title = state.docTitle.trim().ifBlank { prettyKind(state.docKind) }
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                val existing = state.docId?.let { id -> app.capture.allDocuments().firstOrNull { it.id == id } }
                val savedId = app.capture.upsertDocument(
                    BikeDocumentEntity(
                        id = state.docId ?: 0,
                        title = title,
                        kind = state.docKind,
                        expiryIso = state.docExpiry.trim().ifBlank { null },
                        number = state.docNumber.trim().ifBlank { null },
                        filePath = existing?.filePath,
                        mime = existing?.mime,
                        note = state.docNote.trim().ifBlank { null },
                        createdTs = existing?.createdTs ?: System.currentTimeMillis(),
                    ),
                )
                pendingDocUri?.let { uri ->
                    val path = GarageFiles.copyIn(getApplication(), uri, "docs", savedId, GarageFiles.displayName(getApplication(), uri))
                    if (path != null) {
                        val row = app.capture.allDocuments().first { it.id == savedId }
                        app.capture.upsertDocument(row.copy(filePath = path, mime = GarageFiles.mime(getApplication(), uri)))
                    }
                }
                pendingDocUri = null
                savedId
            }
            syncPaperPrefs()
            // The RC's number is the bike's registration; keep the profile in step.
            if (state.docKind == "RC" && state.docNumber.isNotBlank()) {
                dev.gdk.bumblebee.bike.BikeProfile(getApplication()).regNo = dev.gdk.bumblebee.ride.RegNo.format(state.docNumber)
            }
            _ui.update { it.copy(status = "Saved $title.", docId = id, docHasFile = true, editing = false) }
            refresh()
        }
    }

    fun deleteDocument(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val row = app.capture.allDocuments().firstOrNull { it.id == id }
                GarageFiles.delete(row?.filePath)
                app.capture.deleteDocument(id)
            }
            if (_ui.value.docId == id) {
                pendingDocUri = null
                _ui.update {
                    it.copy(
                        editing = false,
                        docId = null,
                        docTitle = "RC",
                        docKind = "RC",
                        docExpiry = "",
                        docNumber = "",
                        docNote = "",
                        docHasFile = false,
                    )
                }
            }
            refresh()
        }
    }

    private suspend fun syncPaperPrefs() = withContext(Dispatchers.IO) {
        val docs = app.capture.allDocuments()
        docs.firstOrNull { it.kind == "RC" }?.expiryIso?.let { app.prefs.rcExpiry = it }
        docs.firstOrNull { it.kind == "INSURANCE" }?.expiryIso?.let { app.prefs.insuranceExpiry = it }
        docs.firstOrNull { it.kind == "PUC" }?.expiryIso?.let { app.prefs.pucExpiry = it }
    }
}
