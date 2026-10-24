package dev.gdk.bumblebee.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.NumberMedium
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.Stroke
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.TextPrimary

/** Compact stat chip with icon (§4.8). Ref: `home.jpeg`. */
@Composable
fun StatPill(
    icon: ImageVector,
    value: String,
    modifier: Modifier = Modifier,
    valueTint: Color = TextPrimary,
    contentDescription: String? = null,
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .background(SurfaceRaised, ShapePill)
            .border(1.dp, Stroke, ShapePill)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = TextPrimary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = value,
            style = NumberMedium,
            color = valueTint,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
