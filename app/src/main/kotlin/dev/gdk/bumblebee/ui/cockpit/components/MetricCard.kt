package dev.gdk.bumblebee.ui.cockpit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.ForgeGraphite
import dev.gdk.bumblebee.ui.theme.ForgeWhite
import dev.gdk.bumblebee.ui.theme.SuzukiYellow

@Composable
fun MetricCard(
    label: String,
    value: String,
    sub: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(ForgeGraphite)
            .padding(12.dp)
            .height(96.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = SuzukiYellow, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = ForgeWhite.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = ForgeWhite.copy(alpha = 0.35f), modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = ForgeWhite)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = ForgeWhite.copy(alpha = 0.55f))
    }
}
