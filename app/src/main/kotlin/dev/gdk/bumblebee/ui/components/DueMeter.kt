package dev.gdk.bumblebee.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.BlockYellow
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.NumberMedium
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.Stroke
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary

/** Used-vs-interval pill (§4.20). The caller supplies the fraction and the tone. */
@Composable
fun DueMeter(
    usedLabel: String,
    totalLabel: String,
    fraction: Float,
    tone: StatusChipTone,
    modifier: Modifier = Modifier,
) {
    val fill = tone.meterFill()
    val onFill = tone.onMeterFill()
    val clamped = fraction.coerceIn(0f, 1f)

    Row(
        modifier = modifier.height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .background(fill, ShapePill)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$usedLabel / $totalLabel",
                style = NumberMedium,
                color = onFill,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .padding(start = 8.dp)
                .weight(1f)
                .height(40.dp)
                .clip(ShapePill)
                .background(SurfaceRaised, ShapePill),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(clamped)
                    .background(fill, ShapePill),
            )
        }
    }
}

private fun StatusChipTone.meterFill(): Color = when (this) {
    StatusChipTone.Ok -> Lime
    StatusChipTone.DueSoon -> BlockYellow
    StatusChipTone.Overdue -> Danger
    StatusChipTone.NoExpiry -> Stroke
}

private fun StatusChipTone.onMeterFill(): Color = when (this) {
    StatusChipTone.Ok, StatusChipTone.DueSoon -> OnBlock
    StatusChipTone.Overdue -> TextPrimary
    StatusChipTone.NoExpiry -> TextSecondary
}
