package dev.gdk.bumblebee.ui.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LinearProgressIndicator
import dev.gdk.bumblebee.ui.theme.SurfaceSunken
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
        onReset = model::reset,
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
    onReset: () -> Unit = {},
) {
    val done = ui.rows.count { it.state == CheckState.DONE }
    val working = ui.phase == TransferPhase.WORKING
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
        // While it works: the step, a bar (moving on its own when it can't tell how far), and %.
        if (ui.step != null && ui.phase != TransferPhase.PACK) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(ui.step, style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                    ui.progress?.let { Text("${(it * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = Lime) }
                }
                if (working && ui.progress == null) {
                    LinearProgressIndicator(color = Lime, trackColor = SurfaceSunken, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { ui.progress ?: 0f }, color = if (ui.phase == TransferPhase.HELD) Danger else Lime, trackColor = SurfaceSunken, modifier = Modifier.fillMaxWidth())
                }
                if (working) Text("Keep this screen open until it finishes.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
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
        if (ui.canSave && !working) {
            ChunkyButton(text = if (ui.phase == TransferPhase.PACK) "Pack and choose where to save" else "Pack again", onClick = onSave, style = ChunkyStyle.Lime)
        }
        if (ui.phase == TransferPhase.PACK) {
            ChunkyButton(text = "Bring in a pack file", onClick = onOpen, style = ChunkyStyle.Outline)
            Text(
                "A pack holds your rides, fuel log, garage papers and scans, settings, ride video photos and music. " +
                    "Navigation API keys stay on this phone (they're locked to it): enter them again on the other one.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (ui.phase == TransferPhase.DONE || (ui.phase == TransferPhase.HELD && !ui.canOverwrite)) {
            ChunkyButton(text = "Back to Transfer", onClick = onReset, style = ChunkyStyle.Outline)
        }
        if (ui.canOverwrite) {
            ChunkyButton(text = ui.overwriteLabel, onClick = onOverwrite, style = ChunkyStyle.Lime)
        }
        if (ui.canKeep) {
            ChunkyButton(text = "Keep what's here", onClick = onKeep, style = ChunkyStyle.Outline)
        }
        if (ui.lines.isNotEmpty() || ui.log.isNotEmpty()) {
            TextButton(onClick = onToggleLog) { Text(if (ui.logOpen) "Hide log" else "Show log") }
            if (ui.logOpen) {
                SectionCard {
                    ui.lines.forEach { line ->
                        val color = when (line.tone) {
                            CheckState.FAILED -> Danger
                            CheckState.WARNING -> Warning
                            else -> TextSecondary
                        }
                        Text("${line.time}  ${line.text}", style = MaterialTheme.typography.bodySmall, color = color)
                    }
                    if (ui.lines.isEmpty()) {
                        ui.log.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
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
