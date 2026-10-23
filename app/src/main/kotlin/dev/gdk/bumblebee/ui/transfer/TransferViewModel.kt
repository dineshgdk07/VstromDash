package dev.gdk.bumblebee.ui.transfer

import androidx.lifecycle.ViewModel
import dev.gdk.bumblebee.snapshot.SnapshotBundle
import dev.gdk.bumblebee.transfer.BringIn
import dev.gdk.bumblebee.transfer.PackPreview
import dev.gdk.bumblebee.transfer.TransferGateway
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class TransferViewModel(val repository: TransferGateway) : ViewModel() {
    private val _ui = MutableStateFlow(emptyPack())
    val ui: StateFlow<TransferUi> = _ui.asStateFlow()

    private val _commits = MutableSharedFlow<Boolean>(replay = 1, extraBufferCapacity = 1)
    val commits: SharedFlow<Boolean> = _commits.asSharedFlow()

    var pending: SnapshotBundle? = null
        private set

    fun onPackLoaded(preview: PackPreview) {
        val issue = preview.issues.firstOrNull()
        val bundle = preview.bundle
        _ui.value = TransferUi(
            phase = TransferPhase.PACK,
            shout = "PACK THE BIKE",
            headline = "${preview.rideCount} rides",
            detailLine = "${preview.distanceKm} km · ${preview.fillCount} fills",
            rows = listOf(
                CheckRow("Rides and tracks", bundle.rides.size.toString(), CheckState.DONE),
                CheckRow("Fuel log", bundle.fuel.size.toString(), CheckState.DONE),
                CheckRow("Daily rollups", bundle.rollups.size.toString(), CheckState.DONE),
                CheckRow("Service and plans", (bundle.services.size + bundle.plans.size).toString(), CheckState.DONE),
                CheckRow("Garage papers", bundle.documents.size.toString(), CheckState.DONE),
                CheckRow("Paired bike", if (bundle.prefs.bikeMac != null) "yes" else "no", CheckState.DONE),
            ),
            message = issue?.message,
            log = preview.issues.map { it.log },
            logOpen = false,
            canSave = issue == null,
            canOverwrite = false,
            canKeep = false,
            overwriteLabel = "",
        )
    }

    fun onInspected(result: BringIn) {
        when (result) {
            is BringIn.Ready -> {
                pending = result.bundle
                _ui.value = bringIn(doneThrough = 4)
            }
            is BringIn.Held -> {
                pending = result.bundle
                val count = result.clashes.size
                _ui.value = held(
                    message = "$count already live here. Nothing was replaced.",
                    log = result.clashes.map { it.log },
                    canWrite = true,
                    overwriteLabel = "Overwrite those $count",
                )
            }
            is BringIn.Failed -> {
                pending = null
                _ui.value = held(
                    message = result.message,
                    log = result.log,
                    canWrite = false,
                    overwriteLabel = "",
                )
            }
        }
    }

    fun onCommitted() {
        pending = null
        _ui.value = bringIn(doneThrough = 9)
    }

    fun onCommitFailed(message: String) {
        pending = null
        _ui.value = held(message = message, log = listOf(message), canWrite = false, overwriteLabel = "")
    }

    fun toggleLog() {
        _ui.value = _ui.value.copy(logOpen = !_ui.value.logOpen)
    }

    fun chooseOverwrite() {
        _commits.tryEmit(true)
    }

    fun chooseKeep() {
        _commits.tryEmit(false)
    }

    private fun bringIn(doneThrough: Int): TransferUi {
        val titles = listOf(
            "File is a Bumblebee snapshot",
            "This install can read it",
            "Checksum and counts",
            "Phone is empty",
            "Fuel log",
            "Rides and tracks",
            "Rollups and calibration",
            "Garage papers",
            "Paired bike",
        )
        return TransferUi(
            phase = TransferPhase.BRING_IN,
            shout = "BRING IT IN",
            headline = "",
            detailLine = "",
            rows = titles.mapIndexed { index, title ->
                CheckRow(title, "", if (index < doneThrough) CheckState.DONE else CheckState.WAITING)
            },
            message = null,
            log = emptyList(),
            logOpen = false,
            canSave = false,
            canOverwrite = false,
            canKeep = false,
            overwriteLabel = "",
        )
    }

    private fun held(message: String, log: List<String>, canWrite: Boolean, overwriteLabel: String) = TransferUi(
        phase = TransferPhase.HELD,
        shout = "NOT APPLIED",
        headline = "",
        detailLine = "",
        rows = emptyList(),
        message = message,
        log = log,
        logOpen = false,
        canSave = false,
        canOverwrite = canWrite,
        canKeep = canWrite,
        overwriteLabel = overwriteLabel,
    )

    private fun emptyPack() = TransferUi(
        phase = TransferPhase.PACK,
        shout = "PACK THE BIKE",
        headline = "",
        detailLine = "",
        rows = emptyList(),
        message = null,
        log = emptyList(),
        logOpen = false,
        canSave = false,
        canOverwrite = false,
        canKeep = false,
        overwriteLabel = "",
    )
}
