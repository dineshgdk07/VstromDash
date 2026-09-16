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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.ble.ConnectionState

@Composable
fun CockpitScreen(model: CockpitViewModel = viewModel()) {
    val state by model.ui.collectAsStateWithLifecycle()
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
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Cockpit", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = when (val c = state.connection) {
                is ConnectionState.Ready -> "LINK READY"
                is ConnectionState.Connecting -> "CONNECTING"
                is ConnectionState.Discovering -> "DISCOVERING"
                is ConnectionState.Failed -> "FAILED · ${c.reason}"
                is ConnectionState.Disconnected -> "DISCONNECTED · ${c.status}"
                else -> "IDLE"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = (state.speedKmh?.toString() ?: "—") + " km/h",
            style = MaterialTheme.typography.displayMedium,
        )
        Text("Fuel  ${state.fuelBars ?: "—"} / 6 bars")
        Text("Odo   ${state.odometerKm ?: "—"} km")
        Text("MAC   ${state.bikeMac ?: "—"}")
        Text(state.status, style = MaterialTheme.typography.bodyMedium)
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
        Button(
            onClick = { model.startDemo() },
            enabled = !state.running,
        ) {
            Text(if (state.running) "Capturing…" else "Start demo ride")
        }
    }
}
