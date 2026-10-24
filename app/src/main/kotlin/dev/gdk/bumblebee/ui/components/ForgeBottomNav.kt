package dev.gdk.bumblebee.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary

sealed interface ForgeNavBadge {
    data object None : ForgeNavBadge
    data object Dot : ForgeNavBadge
    data class Count(val value: Int) : ForgeNavBadge
}

data class ForgeNavItem(
    val icon: ImageVector,
    val label: String,
    val selected: Boolean = false,
    val badge: ForgeNavBadge = ForgeNavBadge.None,
)

/** Five-slot bottom navigation (§4.15). Ref: `home.jpeg`. */
@Composable
fun ForgeBottomNav(
    items: List<ForgeNavItem>,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Canvas),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            ForgeBottomNavSlot(
                item = item,
                onClick = { onItemClick(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ForgeBottomNavSlot(
    item: ForgeNavItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (item.selected) Lime else TextSecondary
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                item.icon,
                contentDescription = item.label,
                tint = tint,
                modifier = Modifier.size(26.dp),
            )
            when (val badge = item.badge) {
                ForgeNavBadge.None -> Unit
                ForgeNavBadge.Dot -> {
                    Box(
                        Modifier
                            .offset(x = 4.dp, y = (-2).dp)
                            .size(8.dp)
                            .background(Danger, CircleShape),
                    )
                }
                is ForgeNavBadge.Count -> {
                    Text(
                        text = badge.value.coerceAtMost(99).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        modifier = Modifier
                            .offset(x = 10.dp, y = (-6).dp)
                            .background(Danger, ShapePill)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Text(
            text = item.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
