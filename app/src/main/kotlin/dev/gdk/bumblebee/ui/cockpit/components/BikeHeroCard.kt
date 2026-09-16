package dev.gdk.bumblebee.ui.cockpit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.ui.theme.ForgeGraphite
import dev.gdk.bumblebee.ui.theme.ForgeWhite
import dev.gdk.bumblebee.ui.theme.SuccessGreen
import dev.gdk.bumblebee.ui.theme.WarningAmber

@Composable
fun BikeHeroCard(
    model: String,
    connection: ConnectionState,
    fuelBars: Int?,
    economyKml: Double?,
    tripAKm: Double?,
    odometerKm: Int?,
    modifier: Modifier = Modifier,
) {
    val bleReady = connection is ConnectionState.Ready
    val health = if (bleReady && (fuelBars == null || fuelBars >= 2)) {
        "Link & Fuel OK"
    } else {
        "Check link"
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ForgeGraphite),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(model, style = MaterialTheme.typography.titleLarge, color = ForgeWhite)
            Text(
                text = if (bleReady) "Connected" else "Not connected",
                style = MaterialTheme.typography.labelLarge,
                color = if (bleReady) SuccessGreen else WarningAmber,
            )
            HeroRow("Fuel", if (fuelBars != null) "$fuelBars bars" else "—")
            HeroRow("Economy", economyKml?.let { "%.1f km/L".format(it) } ?: "—")
            HeroRow("Link", if (bleReady) "connected" else "none")
            HeroRow("Trip", tripAKm?.let { "%.1f km".format(it) } ?: "—")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Odo ${odometerKm?.toString() ?: "—"} km", style = MaterialTheme.typography.bodySmall)
                Text("Service —", style = MaterialTheme.typography.bodySmall)
            }
            SuggestionChip(onClick = {}, label = { Text(health) })
        }
    }
}

@Composable
private fun HeroRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = ForgeWhite)
    }
}
