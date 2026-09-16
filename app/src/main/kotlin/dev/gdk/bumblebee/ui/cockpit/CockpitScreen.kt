package dev.gdk.bumblebee.ui.cockpit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Cockpit", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = when (val c = state.connection) {
                is ConnectionState.Ready -> "LINK READY"
                is ConnectionState.Failed -> "FAILED · ${c.reason}"
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
        Text(state.status, style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = { model.startDemo() },
            enabled = !state.running,
        ) {
            Text(if (state.running) "Capturing…" else "Start demo ride")
        }
    }
}
