package dev.gdk.bumblebee.ui.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.ForgeTopBar
import dev.gdk.bumblebee.ui.components.ProgressPill
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.theme.BlockCoral
import dev.gdk.bumblebee.ui.theme.BlockYellow
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.NumberLarge
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import dev.gdk.bumblebee.ui.theme.Warning

@Composable
fun TransferScreen(
    model: TransferViewModel,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onOpen: () -> Unit,
) {
    val ui by model.ui.collectAsStateWithLifecycle()
    TransferBody(
        ui = ui,
        onBack = onBack,
        onSave = onSave,
        onOpen = onOpen,
        onOverwrite = model::chooseOverwrite,
        onKeep = model::chooseKeep,
        onToggleLog = model::toggleLog,
    )
}

@Composable
private fun TransferBody(
    ui: TransferUi,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onOpen: () -> Unit,
    onOverwrite: () -> Unit,
    onKeep: () -> Unit,
    onToggleLog: () -> Unit,
) {
    val done = ui.rows.count { it.state == CheckState.DONE }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ForgeTopBar(title = "Transfer", onBack = onBack)
        Text(ui.shout, style = MaterialTheme.typography.displayMedium, color = TextPrimary)
        if (ui.headline.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BlockYellow, ShapeMd)
                    .padding(16.dp),
            ) {
                Text(ui.headline, style = NumberLarge, color = OnBlock)
                if (ui.detailLine.isNotEmpty()) {
                    Text(ui.detailLine, style = MaterialTheme.typography.titleMedium, color = OnBlock)
                }
            }
        }
        if (ui.message != null && ui.phase == TransferPhase.HELD) {
            Text(
                ui.message,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BlockCoral, ShapeMd)
                    .padding(16.dp),
                style = MaterialTheme.typography.titleMedium,
                color = OnBlock,
            )
        } else if (ui.message != null) {
            Text(ui.message, style = MaterialTheme.typography.bodyLarge, color = Warning)
        }
        if (ui.phase == TransferPhase.BRING_IN && ui.rows.isNotEmpty()) {
            ProgressPill(progress = done / ui.rows.size.toFloat(), current = done, total = ui.rows.size)
        }
        if (ui.rows.isNotEmpty()) {
            SectionCard {
                ui.rows.forEach { row ->
                    val color = when (row.state) {
                        CheckState.DONE -> Lime
                        CheckState.FAILED -> Danger
                        CheckState.WARNING, CheckState.RUNNING -> Warning
                        CheckState.WAITING -> TextSecondary
                    }
                    Text("${row.title}  ${row.detail}", style = MaterialTheme.typography.bodyLarge, color = color)
                }
            }
        }
        if (ui.canSave) {
            ChunkyButton(text = "Choose where to save", onClick = onSave, style = ChunkyStyle.Lime)
        }
        if (ui.phase == TransferPhase.PACK) {
            ChunkyButton(text = "Choose a file", onClick = onOpen, style = ChunkyStyle.Outline)
        }
        if (ui.canOverwrite) {
            ChunkyButton(text = ui.overwriteLabel, onClick = onOverwrite, style = ChunkyStyle.Lime)
        }
        if (ui.canKeep) {
            ChunkyButton(text = "Keep what's here", onClick = onKeep, style = ChunkyStyle.Outline)
        }
        if (ui.log.isNotEmpty()) {
            TextButton(onClick = onToggleLog) { Text("Log") }
            if (ui.logOpen) {
                SectionCard {
                    ui.log.forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PackPreview() {
    BumblebeeTheme {
        TransferBody(
            ui = sample(TransferPhase.PACK, "PACK THE BIKE", canSave = true),
            onBack = {}, onSave = {}, onOpen = {}, onOverwrite = {}, onKeep = {}, onToggleLog = {},
        )
    }
}

@Preview
@Composable
private fun BringInPreview() {
    BumblebeeTheme {
        TransferBody(
            ui = sample(TransferPhase.BRING_IN, "BRING IT IN", canSave = false),
            onBack = {}, onSave = {}, onOpen = {}, onOverwrite = {}, onKeep = {}, onToggleLog = {},
        )
    }
}

@Preview
@Composable
private fun HeldPreview() {
    BumblebeeTheme {
        TransferBody(
            ui = sample(TransferPhase.HELD, "NOT APPLIED", canSave = false).copy(
                message = "1 already live here. Nothing was replaced.",
                canOverwrite = true,
                canKeep = true,
                overwriteLabel = "Overwrite those 1",
                log = listOf("clash ride"),
            ),
            onBack = {}, onSave = {}, onOpen = {}, onOverwrite = {}, onKeep = {}, onToggleLog = {},
        )
    }
}

private fun sample(phase: TransferPhase, shout: String, canSave: Boolean) = TransferUi(
    phase = phase,
    shout = shout,
    headline = "1 rides",
    detailLine = "2 km · 1 fills",
    rows = listOf(CheckRow("Rides and tracks", "1", CheckState.DONE)),
    message = null,
    log = emptyList(),
    logOpen = false,
    canSave = canSave,
    canOverwrite = false,
    canKeep = false,
    overwriteLabel = "",
)
