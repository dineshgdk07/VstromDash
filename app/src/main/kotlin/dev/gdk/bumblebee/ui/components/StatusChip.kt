package dev.gdk.bumblebee.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.BlockYellow
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.Stroke
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary

enum class StatusChipTone {
    Ok,
    DueSoon,
    Overdue,
    NoExpiry,
}

/** Small status pill (§4.19). */
@Composable
fun StatusChip(
    tone: StatusChipTone,
    modifier: Modifier = Modifier,
    text: String? = null,
) {
    val colors = tone.colors()
    val label = text ?: tone.defaultLabel()
    val outline = if (colors.border == Color.Transparent) {
        Modifier
    } else {
        Modifier.border(1.dp, colors.border, ShapePill)
    }
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = colors.content,
        modifier = modifier
            .background(colors.container, ShapePill)
            .then(outline)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private fun StatusChipTone.defaultLabel(): String = when (this) {
    StatusChipTone.Ok -> "OK"
    StatusChipTone.DueSoon -> "DUE SOON"
    StatusChipTone.Overdue -> "OVERDUE"
    StatusChipTone.NoExpiry -> "NO EXPIRY"
}

private data class ToneColors(
    val container: Color,
    val border: Color,
    val content: Color,
)

private fun StatusChipTone.colors(): ToneColors = when (this) {
    StatusChipTone.Ok -> ToneColors(Color.Transparent, Lime, Lime)
    StatusChipTone.DueSoon -> ToneColors(BlockYellow, Color.Transparent, OnBlock)
    StatusChipTone.Overdue -> ToneColors(Danger, Color.Transparent, TextPrimary)
    StatusChipTone.NoExpiry -> ToneColors(Color.Transparent, Stroke, TextSecondary)
}
