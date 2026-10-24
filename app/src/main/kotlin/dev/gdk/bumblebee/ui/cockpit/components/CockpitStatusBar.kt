package dev.gdk.bumblebee.ui.cockpit.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.ui.components.StatPill
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import dev.gdk.bumblebee.ui.theme.Warning

@Composable
fun CockpitStatusBar(
    connection: ConnectionState,
    rangeKm: Double?,
    odometerKm: Int?,
    fuelBars: Int?,
    modifier: Modifier = Modifier,
) {
    val linkTint = when (connection) {
        is ConnectionState.Ready -> Lime
        is ConnectionState.Failed -> Danger
        else -> TextSecondary
    }
    val link = when (connection) {
        is ConnectionState.Ready -> "On"
        is ConnectionState.Connecting, ConnectionState.Discovering -> "…"
        else -> "Off"
    }
    val fuelTint = if (fuelBars != null && fuelBars <= 1) Warning else TextPrimary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatPill(
            icon = Icons.Rounded.Bluetooth,
            value = link,
            valueTint = linkTint,
            contentDescription = "Bike connection",
        )
        StatPill(
            icon = Icons.Rounded.LocalGasStation,
            value = rangeKm?.let { "est. ${it.toInt()}" } ?: "—",
            valueTint = fuelTint,
            contentDescription = "Estimated range",
        )
        StatPill(
            icon = Icons.Rounded.Speed,
            value = odometerKm?.let { "%,d".format(it) } ?: "—",
            contentDescription = "Odometer",
        )
    }
}
