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
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class TransferViewModel(val repository: TransferGateway) : ViewModel() {
    private val _ui = MutableStateFlow(emptyPack())
    val ui: StateFlow<TransferUi> = _ui.asStateFlow()

    private val _commits = MutableSharedFlow<Boolean>(replay = 1, extraBufferCapacity = 1)
    val commits: SharedFlow<Boolean> = _commits.asSharedFlow()

    var pending: SnapshotBundle? = null
        private set

    private var preview: PackPreview? = null

    fun onPackLoaded(preview: PackPreview) {
        this.preview = preview
        _ui.value = packUi(preview)
    }

    private fun packUi(preview: PackPreview): TransferUi {
        val bundle = preview.bundle
        val scans = bundle.files.size
        return TransferUi(
            phase = TransferPhase.PACK,
            shout = "PACK THE BIKE",
            headline = "${preview.rideCount} rides",
            detailLine = "${preview.distanceKm} km · ${preview.fillCount} fills",
            rows = listOf(
                CheckRow("Rides and tracks", bundle.rides.size.toString(), CheckState.DONE),
                CheckRow("Fuel log", bundle.fuel.size.toString(), CheckState.DONE),
                CheckRow("Daily rollups", bundle.rollups.size.toString(), CheckState.DONE),
                CheckRow("Service and plans", (bundle.services.size + bundle.plans.size).toString(), CheckState.DONE),
                CheckRow("Garage papers", "${bundle.documents.size} (${scans} scans)", CheckState.DONE),
                CheckRow("Settings", "${bundle.settings.keyCount} values", CheckState.DONE),
                CheckRow("Paired bike", if (bundle.prefs.bikeMac != null) "yes" else "no", CheckState.DONE),
            ),
            message = preview.issues.firstOrNull()?.message,
            log = preview.issues.map { it.log },
            logOpen = false,
            // Notes never stop a pack: what can't go in is left out and said.
            canSave = true,
            canOverwrite = false,
            canKeep = false,
            overwriteLabel = "",
        )
    }

    /** Packing or bringing in has begun; the log starts with [what]. */
    fun onStarted(what: String) {
        _ui.value = TransferUi(
            phase = TransferPhase.WORKING,
            shout = if (what.startsWith("Pack")) "PACKING" else "BRINGING IN",
            headline = "",
            detailLine = "",
            rows = emptyList(),
            message = null,
            log = emptyList(),
            logOpen = true,
            canSave = false,
            canOverwrite = false,
            canKeep = false,
            overwriteLabel = "",
            step = what,
            progress = 0f,
            lines = listOf(line(what)),
        )
    }

    /** A progress report from the work: a new step adds a log line, the same step just moves on. */
    fun onStep(text: String, fraction: Float?) {
        _ui.update { ui ->
            val lines = if (ui.lines.lastOrNull()?.text == text) ui.lines else (ui.lines + line(text)).takeLast(MAX_LINES)
            ui.copy(step = text, progress = fraction ?: ui.progress, lines = lines)
        }
    }

    /** How much of the chosen file has been read, while bringing in. */
    fun onRead(fraction: Float) {
        _ui.update { if (it.phase == TransferPhase.WORKING) it.copy(progress = fraction.coerceIn(0f, 1f)) else it }
    }

    fun onExported(bytes: Long, name: String) {
        _ui.update { ui ->
            ui.copy(
                phase = TransferPhase.DONE,
                shout = "PACKED",
                headline = size(bytes),
                detailLine = name,
                step = "Saved",
                progress = 1f,
                message = "Copy this file to the other phone (or open it in the other build) and choose Bring in.",
                lines = ui.lines + line("Saved $name, ${size(bytes)}"),
                canSave = true,
            )
        }
    }

    fun onExportFailed(message: String) {
        _ui.update { ui ->
            ui.copy(
                phase = TransferPhase.HELD,
                shout = "NOT SAVED",
                message = "Couldn't save the pack: $message. The empty file was removed; try again.",
                lines = ui.lines + line("Failed: $message", CheckState.FAILED),
                logOpen = true,
                canSave = true,
            )
        }
    }

    fun onInspected(result: BringIn) {
        when (result) {
            is BringIn.Ready -> {
                pending = result.bundle
                _ui.update { it.copy(step = "Bringing it in", lines = it.lines + line("The file checks out: ${summary(result.bundle)}")) }
            }
            is BringIn.Held -> {
                pending = result.bundle
                val count = result.clashes.size
                _ui.update { ui ->
                    held(
                        ui,
                        message = "$count already live here. Nothing was replaced yet.",
                        log = result.clashes.map { it.log },
                        canWrite = true,
                        overwriteLabel = "Overwrite those $count",
                        tone = CheckState.WARNING,
                    )
                }
            }
            is BringIn.Failed -> {
                pending = null
                _ui.update { ui -> held(ui, message = result.message, log = result.log, canWrite = false, overwriteLabel = "", tone = CheckState.FAILED) }
            }
        }
    }

    fun onCommitted(done: List<String> = emptyList()) {
        pending = null
        _ui.update { ui ->
            ui.copy(
                phase = TransferPhase.DONE,
                shout = "BROUGHT IN",
                headline = "",
                detailLine = "",
                rows = done.map { CheckRow(it, "", CheckState.DONE) },
                step = "Done",
                progress = 1f,
                message = null,
                canOverwrite = false,
                canKeep = false,
                lines = ui.lines + done.map { line(it) } + line("All done"),
            )
        }
    }

    fun onCommitFailed(message: String) {
        pending = null
        _ui.update { ui -> held(ui, message = message, log = listOf(message), canWrite = false, overwriteLabel = "", tone = CheckState.FAILED) }
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

    /** Back to the pack view, after a finished or failed run. */
    fun reset() {
        _ui.value = preview?.let(::packUi) ?: emptyPack()
    }

    private fun held(ui: TransferUi, message: String, log: List<String>, canWrite: Boolean, overwriteLabel: String, tone: CheckState) = ui.copy(
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
        lines = ui.lines + line(message, tone) + log.take(20).map { line(it, tone) },
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

    private fun summary(b: SnapshotBundle) =
        "${b.rides.size} rides, ${b.fuel.size} fuel entries, ${b.documents.size} papers, ${b.settings.keyCount} settings, ${b.files.size} files"

    private companion object {
        const val MAX_LINES = 200
        val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH)

        fun line(text: String, tone: CheckState = CheckState.DONE) = LogLine(LocalTime.now().format(CLOCK), text, tone)

        fun size(bytes: Long): String = when {
            bytes >= 1_048_576 -> String.format(Locale.ENGLISH, "%.1f MB", bytes / 1_048_576.0)
            bytes >= 1024 -> String.format(Locale.ENGLISH, "%d KB", bytes / 1024)
            else -> "$bytes bytes"
        }
    }
}
