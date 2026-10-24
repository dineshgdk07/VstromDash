package dev.gdk.bumblebee.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.BlockYellow
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.ShapeSm
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.SurfaceSunken
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary

/** Grid icon with chunky slab (§4.5). Ref: `home.jpeg`. */
@Composable
fun IconTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    contentDescription: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val faceHeight = 64.dp
    val depth = 3.dp
    val faceColor = if (selected) BlockYellow else SurfaceRaised
    val iconTint = if (selected) OnBlock else TextPrimary
    val captionColor = if (selected) BlockYellow else TextSecondary

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, BlockYellow, ShapeSm)
                    } else {
                        Modifier
                    },
                )
                .padding(if (selected) 3.dp else 0.dp),
        ) {
            ChunkySlot(
                modifier = Modifier.width(64.dp),
                faceHeight = faceHeight,
                depth = depth,
                shape = ShapeSm,
                slabColor = SurfaceSunken,
                interactionSource = interactionSource,
                enabled = true,
                onClick = onClick,
                faceModifier = Modifier.background(faceColor, ShapeSm),
            ) {
                Icon(
                    icon,
                    contentDescription = contentDescription,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = captionColor,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
