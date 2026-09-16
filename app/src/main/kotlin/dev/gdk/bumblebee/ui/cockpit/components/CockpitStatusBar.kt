package dev.gdk.bumblebee.ui.cockpit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.ui.theme.ForgeGraphite
import dev.gdk.bumblebee.ui.theme.SuccessGreen

@Composable
fun CockpitStatusBar(
    connection: ConnectionState,
    modifier: Modifier = Modifier,
) {
    val bleOn = connection is ConnectionState.Ready
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(on = bleOn, label = "BLE")
            StatusDot(on = false, label = "GPS")
            StatusDot(on = false, label = "Net")
        }
        Text(
            text = when (connection) {
                is ConnectionState.Ready -> "LINK READY"
                is ConnectionState.Connecting -> "CONNECTING"
                is ConnectionState.Discovering -> "DISCOVERING"
                is ConnectionState.Failed -> "FAILED"
                is ConnectionState.Disconnected -> "DISCONNECTED"
                else -> "IDLE"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun StatusDot(on: Boolean, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (on) SuccessGreen else ForgeGraphite),
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
