package dev.gdk.bumblebee.ui.transfer

/** PACK: what would go in; WORKING: packing or bringing in; DONE: finished; HELD: stopped, see message. */
enum class TransferPhase { PACK, WORKING, DONE, BRING_IN, HELD }

enum class CheckState { WAITING, RUNNING, DONE, WARNING, FAILED }

data class CheckRow(val title: String, val detail: String, val state: CheckState)

/** One line of the live log: when, what, and whether it was fine, a note, or a failure. */
data class LogLine(val time: String, val text: String, val tone: CheckState = CheckState.DONE)

data class TransferUi(
    val phase: TransferPhase,
    val shout: String,
    val headline: String,
    val detailLine: String,
    val rows: List<CheckRow>,
    val message: String?,
    val log: List<String>,
    val logOpen: Boolean,
    val canSave: Boolean,
    val canOverwrite: Boolean,
    val canKeep: Boolean,
    val overwriteLabel: String,
    /** The step under way, and how far through (null when it can't tell). */
    val step: String? = null,
    val progress: Float? = null,
    val lines: List<LogLine> = emptyList(),
)
