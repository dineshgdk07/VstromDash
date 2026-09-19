package dev.gdk.bumblebee.ui.cockpit

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.ui.cockpit.components.BikeHeroCard
import dev.gdk.bumblebee.ui.cockpit.components.CockpitStatusBar
import dev.gdk.bumblebee.ui.cockpit.components.MetricCard
import dev.gdk.bumblebee.ui.cockpit.components.QuickActionRow
import dev.gdk.bumblebee.ui.cockpit.components.ReadinessRadial
import dev.gdk.bumblebee.ui.cockpit.components.WarningsCard
import dev.gdk.bumblebee.ui.theme.ForgeWhite

@Composable
fun CockpitScreen(
    model: CockpitViewModel = viewModel(),
    onNavigate: () -> Unit = {},
    onExplore: () -> Unit = {},
) {
    val state by model.ui.collectAsStateWithLifecycle()
    var linkOpen by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.all { it }) model.startScan()
    }

    fun requestThenScan() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (perms.isEmpty()) model.startScan()
        else permissionLauncher.launch(perms.toTypedArray())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CockpitStatusBar(
            connection = state.connection,
            gpsOn = state.gpsOn,
            phoneBatteryPercent = state.phoneBatteryPercent,
        )
        ReadinessRadial(score = state.readiness)
        BikeHeroCard(
            model = "V-Strom 250 SX",
            connection = state.connection,
            fuelBars = state.fuelBars,
            economyKml = state.economyKml,
            tripAKm = state.tripAKm,
            odometerKm = state.odometerKm,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("Fuel", state.fuelBars?.let { "$it bars" } ?: "—", "est. range —", Icons.Outlined.LocalGasStation, Modifier.weight(1f))
            MetricCard("Today", "—", "no ride yet", Icons.Outlined.Route, Modifier.weight(1f))
            MetricCard("Weather", "—", "no forecast", Icons.Outlined.Cloud, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("Odometer", state.odometerKm?.let { "%,d".format(it) } ?: "—", "all time km", Icons.Outlined.Speed, Modifier.weight(1f))
            MetricCard(
                "Warnings",
                if (state.warnings.isEmpty()) "None" else state.warnings.size.toString(),
                "app & connection",
                Icons.Outlined.Warning,
                Modifier.weight(1f),
            )
            MetricCard("Service", "—", "not set", Icons.Outlined.Build, Modifier.weight(1f))
        }
        WarningsCard(warnings = state.warnings)
        QuickActionRow(
            onNavigate = onNavigate,
            onFindBike = { linkOpen = true },
            onShare = {},
            onExplore = onExplore,
        )
        Text(
            "Bike link",
            style = MaterialTheme.typography.labelLarge,
            color = ForgeWhite.copy(alpha = 0.7f),
            modifier = Modifier.clickable { linkOpen = !linkOpen },
        )
        if (linkOpen) {
            Text(state.status, style = MaterialTheme.typography.bodyMedium)
            Text("MAC  ${state.bikeMac ?: "—"}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { if (state.scanning) model.stopScan() else requestThenScan() }) {
                    Text(if (state.scanning) "Stop scan" else "Scan")
                }
                OutlinedButton(onClick = { model.connectSaved() }, enabled = state.bikeMac != null) {
                    Text("Connect")
                }
                OutlinedButton(onClick = { model.disconnectBike() }) {
                    Text("Disconnect")
                }
            }
            state.devices.forEach { bike ->
                val label = buildString {
                    append(bike.name ?: bike.mac)
                    if (bike.looksLikeSuzuki) append("  · Suzuki")
                    if (bike.bonded) append("  · bonded")
                    if (bike.rssi != Int.MIN_VALUE) append("  · ${bike.rssi} dBm")
                }
                Text(
                    text = label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { model.pair(bike.mac) }
                        .padding(vertical = 8.dp),
                )
            }
            Button(onClick = { model.startDemo() }, enabled = !state.running) {
                Text(if (state.running) "Capturing…" else "Start demo ride")
            }
        }
    }
}
