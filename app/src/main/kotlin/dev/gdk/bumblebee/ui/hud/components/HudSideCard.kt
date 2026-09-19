package dev.gdk.bumblebee.ui.hud.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.ForgeCard
import dev.gdk.bumblebee.ui.theme.ForgeWhite
import dev.gdk.bumblebee.ui.theme.SuzukiYellow

@Composable
fun HudSideCard(
    label: String,
    value: String,
    sub: String,
    icon: ImageVector,
    accent: Color = ForgeWhite,
    stripe: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ForgeCard)
            .fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (stripe) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(SuzukiYellow),
            )
        }
        Icon(
            icon,
            contentDescription = null,
            tint = SuzukiYellow,
            modifier = Modifier.padding(start = 12.dp).size(22.dp),
        )
        Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.Center) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = ForgeWhite.copy(alpha = 0.5f))
            Text(value, style = MaterialTheme.typography.headlineMedium, color = accent)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = ForgeWhite.copy(alpha = 0.5f))
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = ForgeWhite.copy(alpha = 0.25f),
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}
