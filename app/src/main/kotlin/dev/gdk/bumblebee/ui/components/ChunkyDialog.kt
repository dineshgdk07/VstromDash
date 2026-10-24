package dev.gdk.bumblebee.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.gdk.bumblebee.ui.theme.Scrim
import dev.gdk.bumblebee.ui.theme.ShapeLg
import dev.gdk.bumblebee.ui.theme.Surface
import dev.gdk.bumblebee.ui.theme.TextPrimary

enum class ChunkyDialogConfirmStyle {
    Lime,
    Danger,
}

/** Confirm dialog with chunky actions (§4.12). Ref: `dialog.jpeg`. */
@Composable
fun ChunkyDialog(
    title: String,
    body: String,
    dismissLabel: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmStyle: ChunkyDialogConfirmStyle = ChunkyDialogConfirmStyle.Lime,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Scrim)
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .padding(horizontal = 40.dp)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = MutableInteractionSource(),
                        indication = null,
                        onClick = {},
                    )
                    .background(Surface, ShapeLg)
                    .padding(24.dp),
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
                )
                Row(Modifier.fillMaxWidth()) {
                    ChunkyButton(
                        text = dismissLabel,
                        onClick = onDismiss,
                        style = ChunkyStyle.Neutral,
                        compact = true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp),
                    )
                    ChunkyButton(
                        text = confirmLabel,
                        onClick = onConfirm,
                        style = when (confirmStyle) {
                            ChunkyDialogConfirmStyle.Lime -> ChunkyStyle.Lime
                            ChunkyDialogConfirmStyle.Danger -> ChunkyStyle.Danger
                        },
                        compact = true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp),
                    )
                }
            }
        }
    }
}
