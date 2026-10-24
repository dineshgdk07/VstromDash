package dev.gdk.bumblebee.ui.transfer

enum class TransferPhase { PACK, BRING_IN, HELD }

enum class CheckState { WAITING, RUNNING, DONE, WARNING, FAILED }

data class CheckRow(val title: String, val detail: String, val state: CheckState)

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
)
