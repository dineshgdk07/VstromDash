package dev.gdk.bumblebee.ui.cockpit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.ui.theme.ForgeGraphite
import dev.gdk.bumblebee.ui.theme.ForgeWhite
import dev.gdk.bumblebee.ui.theme.SuccessGreen
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun CockpitStatusBar(
    connection: ConnectionState,
    gpsOn: Boolean = false,
    phoneBatteryPercent: Int? = null,
    modifier: Modifier = Modifier,
) {
    val bleOn = connection is ConnectionState.Ready
    val clock = produceState(initialValue = clockNow()) {
        while (true) {
            value = clockNow()
            delay(15_000)
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusChip(on = bleOn, label = "BLE")
            StatusChip(on = gpsOn, label = "GPS")
            StatusChip(on = false, label = "Net")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(14.dp), tint = ForgeGraphite)
            Text("—", style = MaterialTheme.typography.labelMedium, color = ForgeWhite.copy(alpha = 0.6f))
            Text("${phoneBatteryPercent ?: "—"}%", style = MaterialTheme.typography.labelLarge, color = ForgeWhite)
            Text(clock.value, style = MaterialTheme.typography.labelLarge, color = ForgeWhite)
        }
    }
}

@Composable
private fun StatusChip(on: Boolean, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(7.dp)) {
            drawCircle(if (on) SuccessGreen else ForgeGraphite)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (on) ForgeWhite else ForgeWhite.copy(alpha = 0.45f))
    }
}

private fun clockNow(): String = DateTimeFormatter.ofPattern("h:mm a").format(LocalTime.now())
