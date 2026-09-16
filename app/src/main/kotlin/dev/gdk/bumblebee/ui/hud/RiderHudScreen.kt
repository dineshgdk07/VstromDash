package dev.gdk.bumblebee.ui.hud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.ride.SpeedSource
import dev.gdk.bumblebee.ui.hud.components.EconomyArc
import dev.gdk.bumblebee.ui.hud.components.FuelArc
import dev.gdk.bumblebee.ui.hud.components.SpeedDial
import dev.gdk.bumblebee.ui.theme.ForgeWhite
import dev.gdk.bumblebee.ui.theme.SuccessGreen
import dev.gdk.bumblebee.ui.theme.SuzukiYellow
import dev.gdk.bumblebee.ui.theme.WarningAmber

@Composable
fun RiderHudScreen(model: HudViewModel = viewModel()) {
    val state by model.ui.collectAsStateWithLifecycle()
    val digital = if (state.speed.source == SpeedSource.NONE) "—" else state.speed.speedKmh.toString()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        if (state.navStreet != null) {
            Text(
                "${state.navDist ?: ""} · ${state.navStreet}",
                color = SuzukiYellow,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            FuelArc(bars = state.fuelBars, modifier = Modifier.fillMaxHeight(0.7f))
            SpeedDial(
                speedKmh = state.speed.speedKmh,
                source = state.speed.source,
                digital = digital,
                heading = state.heading,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            EconomyArc(kmL = state.economyKml, modifier = Modifier.fillMaxHeight(0.7f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("ODO ${state.odometerKm?.toString() ?: "—"} km", color = ForgeWhite)
            Text("Trip ${state.tripAKm?.let { "%.1f".format(it) } ?: "—"} km", color = ForgeWhite)
            Text(
                if (state.bleReady) "BLE" else "NO LINK",
                color = if (state.bleReady) SuccessGreen else WarningAmber,
                style = MaterialTheme.typography.labelLarge,
            )
            Text("☎ ${state.phoneBatteryPercent?.toString() ?: "—"}%", color = ForgeWhite)
        }
    }
}
