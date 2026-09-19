package dev.gdk.bumblebee.ui.cockpit.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.ForgeWhite
import dev.gdk.bumblebee.ui.theme.SuzukiYellow

@Composable
fun QuickActionRow(
    onNavigate: () -> Unit,
    onFindBike: () -> Unit,
    onShare: () -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        QuickAction(Icons.Outlined.Navigation, "Navigate", "Plan a route", onNavigate)
        QuickAction(Icons.Outlined.MyLocation, "Find Bike", "Last parked", onFindBike)
        QuickAction(Icons.Outlined.Share, "Share", "Share location", onShare)
        QuickAction(Icons.Outlined.Explore, "Explore", "Riding journal", onExplore)
    }
}

@Composable
private fun QuickAction(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(4.dp)
            .clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = title, tint = SuzukiYellow, modifier = Modifier.padding(bottom = 4.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, color = ForgeWhite)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = ForgeWhite.copy(alpha = 0.45f))
    }
}
