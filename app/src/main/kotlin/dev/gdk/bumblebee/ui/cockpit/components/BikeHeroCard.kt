package dev.gdk.bumblebee.ui.cockpit.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.R
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.ui.theme.ForgeBlack
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
    val health = if (bleReady && (fuelBars == null || fuelBars >= 2)) "Link & Fuel OK" else "Check link"
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(188.dp)
            .background(ForgeGraphite),
    ) {
        Image(
            painter = painterResource(R.drawable.vstrom_hero),
            contentDescription = model,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(ForgeBlack.copy(alpha = 0.35f)),
        )
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
        ) {
            Text(model.uppercase(), style = MaterialTheme.typography.titleLarge)
            Text(
                if (bleReady) "Connected" else "Not connected",
                style = MaterialTheme.typography.labelLarge,
                color = if (bleReady) SuccessGreen else WarningAmber,
            )
        }
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeroStat(Icons.Outlined.LocalGasStation, "Fuel", fuelBars?.let { "$it bars" } ?: "—")
            HeroStat(Icons.Outlined.Speed, "Economy", economyKml?.let { "%.1f km/L".format(it) } ?: "—")
            HeroStat(Icons.Outlined.Bluetooth, "Link", if (bleReady) "connected" else "none")
            HeroStat(Icons.Outlined.Route, "Trip", tripAKm?.let { "%.1f km".format(it) } ?: "—")
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(ForgeBlack.copy(alpha = 0.55f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FooterStat("Odometer", odometerKm?.let { "%,d km".format(it) } ?: "—")
            FooterStat("Next service", "—")
            FooterStat("Health", health)
        }
    }
}

@Composable
private fun HeroStat(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = ForgeWhite.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
        Column {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = ForgeWhite.copy(alpha = 0.55f))
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun FooterStat(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = ForgeWhite.copy(alpha = 0.5f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = ForgeWhite)
    }
}
