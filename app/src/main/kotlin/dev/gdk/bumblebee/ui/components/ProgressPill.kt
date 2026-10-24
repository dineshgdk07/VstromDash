package dev.gdk.bumblebee.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.LimeDeep
import dev.gdk.bumblebee.ui.theme.NumberMedium
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.TextPrimary

/** Daily-challenge style progress track (§4.11). Ref: `home.jpeg`. */
@Composable
fun ProgressPill(
    progress: Float,
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val capsuleFace = 32.dp
    val capsuleDepth = 3.dp

    Row(
        modifier = modifier.height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChunkySlot(
            modifier = Modifier.padding(end = 8.dp),
            faceHeight = capsuleFace,
            depth = capsuleDepth,
            shape = ShapePill,
            slabColor = LimeDeep,
            interactionSource = interactionSource,
            enabled = false,
            onClick = null,
            faceModifier = Modifier.background(Lime, ShapePill),
        ) {
            Text(
                text = "$current/$total",
                style = NumberMedium,
                color = OnBlock,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        Box(
            Modifier
                .weight(1f)
                .height(40.dp)
                .clip(ShapePill)
                .background(SurfaceRaised, ShapePill),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(clamped)
                    .background(Lime, ShapePill),
            )
            if (trailingIcon != null) {
                Icon(
                    trailingIcon,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                        .size(20.dp),
                )
            }
        }
    }
}
