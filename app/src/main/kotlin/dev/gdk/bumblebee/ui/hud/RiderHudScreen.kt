package dev.gdk.bumblebee.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.ride.SpeedSource
import dev.gdk.bumblebee.ui.cockpit.ReadinessBand
import dev.gdk.bumblebee.ui.cockpit.components.CockpitStatusBar
import dev.gdk.bumblebee.ui.hud.components.EconomyArc
import dev.gdk.bumblebee.ui.hud.components.FuelArc
import dev.gdk.bumblebee.ui.hud.components.HudSideCard
import dev.gdk.bumblebee.ui.hud.components.SpeedDial
import dev.gdk.bumblebee.ui.theme.ForgeBlack
import dev.gdk.bumblebee.ui.theme.ForgeWhite
import dev.gdk.bumblebee.ui.theme.SuccessGreen
import dev.gdk.bumblebee.ui.theme.SuzukiYellow
import dev.gdk.bumblebee.ui.theme.WarningAmber
import dev.gdk.bumblebee.ble.ConnectionState

@Composable
fun RiderHudScreen(model: HudViewModel = viewModel()) {
    val state by model.ui.collectAsStateWithLifecycle()
    val digital = if (state.speed.source == SpeedSource.NONE) "—" else state.speed.speedKmh.toString()
    val readyColor = when (state.readiness.band) {
        ReadinessBand.READY -> SuccessGreen
        ReadinessBand.CAUTION -> WarningAmber
        else -> ForgeWhite
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ForgeBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        CockpitStatusBar(
            connection = if (state.bleReady) ConnectionState.Ready else ConnectionState.Idle,
            gpsOn = state.gpsOn,
            phoneBatteryPercent = state.phoneBatteryPercent,
        )
        if (state.navStreet != null) {
            Text(
                "${state.navDist ?: ""} · ${state.navStreet}",
                color = SuzukiYellow,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HudSideCard("Ride readiness", "${state.readiness.percent}%", state.readiness.label, readyColor)
                HudSideCard("Fuel", state.fuelBars?.let { "$it bars" } ?: "—", "est. range —")
                HudSideCard("Today's ride", "—", "no ride yet")
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                FuelArc(bars = state.fuelBars)
                SpeedDial(
                    speedKmh = state.speed.speedKmh,
                    source = state.speed.source,
                    digital = digital,
                    heading = state.heading,
                    odometerKm = state.odometerKm,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                EconomyArc(kmL = state.economyKml)
            }
            Column(
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HudSideCard("Weather", "—", "no forecast")
                HudSideCard("Service due", "—", "not set")
                HudSideCard(
                    "Warnings",
                    if (state.bleReady) "All clear" else "Check link",
                    "app & connection",
                    if (state.bleReady) SuccessGreen else WarningAmber,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudAction(Icons.Outlined.Navigation, "Navigate")
            HudAction(Icons.Outlined.MyLocation, "Find Bike")
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(ForgeBlack)
                    .border(3.dp, SuzukiYellow, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("S", style = MaterialTheme.typography.headlineMedium, color = SuzukiYellow)
            }
            HudAction(Icons.Outlined.Share, "Share")
            HudAction(Icons.Outlined.Explore, "Explore")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (state.bleReady) "LINK READY" else "NO LINK",
                color = if (state.bleReady) SuccessGreen else WarningAmber,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                "Trip ${state.tripAKm?.let { "%.1f".format(it) } ?: "—"} km",
                color = ForgeWhite,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                "☎ ${state.phoneBatteryPercent?.toString() ?: "—"}%",
                color = ForgeWhite,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun HudAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = SuzukiYellow)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = ForgeWhite)
    }
}
