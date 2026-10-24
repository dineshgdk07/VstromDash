package dev.gdk.bumblebee.ui.cockpit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.ui.cockpit.components.BikeHeroCard
import dev.gdk.bumblebee.ui.cockpit.components.CockpitStatusBar
import dev.gdk.bumblebee.ui.cockpit.components.CockpitWarning
import dev.gdk.bumblebee.ui.cockpit.components.ReadinessRadial
import dev.gdk.bumblebee.ui.cockpit.components.WarningSeverity
import dev.gdk.bumblebee.ui.cockpit.components.WarningsCard
import dev.gdk.bumblebee.ui.components.BlockColor
import dev.gdk.bumblebee.ui.components.BlockTile
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.NumberLarge
import dev.gdk.bumblebee.ui.theme.OnBlock

@Composable
fun CockpitScreen(
    model: CockpitViewModel = viewModel(),
    onNavigate: () -> Unit = {},
    onExplore: () -> Unit = {},
    onRide: () -> Unit = {},
    onGarage: () -> Unit = {},
) {
    val state by model.ui.collectAsStateWithLifecycle()
    CockpitContent(
        state = state,
        onStartRide = onNavigate,
        onRideStats = onRide,
        onGarage = onGarage,
        onSetup = onGarage,
    )
}

@Composable
private fun CockpitContent(
    state: CockpitUiState,
    onStartRide: () -> Unit,
    onRideStats: () -> Unit,
    onGarage: () -> Unit,
    onSetup: () -> Unit,
) {
    val due = state.serviceDueKm
    val serviceBadge = when {
        due == null -> null
        due <= 0 -> "Overdue"
        else -> "est. $due km"
    }
    val setupBadge = when {
        state.bikeMac == null -> "Pair"
        state.connection is ConnectionState.Ready -> "Linked"
        else -> "Offline"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CockpitStatusBar(
            connection = state.connection,
            rangeKm = state.rangeKm,
            odometerKm = state.odometerKm,
            fuelBars = state.fuelBars,
        )
        BikeHeroCard(
            model = "V-Strom 250 SX",
            connection = state.connection,
            onOpen = onGarage,
        )
        ReadinessRadial(score = state.readiness)
        CockpitBento(
            state = state,
            serviceBadge = serviceBadge,
            setupBadge = setupBadge,
            onStartRide = onStartRide,
            onRideStats = onRideStats,
            onGarage = onGarage,
            onSetup = onSetup,
        )
        WarningsCard(warnings = state.warnings)
    }
}

@Composable
private fun CockpitBento(
    state: CockpitUiState,
    serviceBadge: String?,
    setupBadge: String,
    onStartRide: () -> Unit,
    onRideStats: () -> Unit,
    onGarage: () -> Unit,
    onSetup: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(248.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BlockTile(
                title = "Ride stats",
                color = BlockColor.Lavender,
                onClick = onRideStats,
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight(),
                art = { TodayStatsArt(state.todayKm, state.todayMovingSec) },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BlockTile(
                    title = "Garage",
                    color = BlockColor.Yellow,
                    onClick = onGarage,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    art = {
                        if (serviceBadge != null) TileFoot(serviceBadge)
                    },
                )
                BlockTile(
                    title = "Setup",
                    color = BlockColor.Mint,
                    onClick = onSetup,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    art = { TileFoot(setupBadge) },
                )
            }
        }
        BlockTile(
            title = "Start ride",
            color = BlockColor.Coral,
            onClick = onStartRide,
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
        )
    }
}

@Composable
private fun BoxScope.TileFoot(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = OnBlock,
        modifier = Modifier.align(Alignment.BottomStart),
    )
}

@Composable
private fun BoxScope.TodayStatsArt(todayKm: Double, todayMovingSec: Int) {
    Column(Modifier.align(Alignment.BottomStart)) {
        Text(
            text = if (todayKm > 0) String.format("%.1f km", todayKm) else "—",
            style = NumberLarge,
            color = OnBlock,
        )
        Text(
            text = if (todayMovingSec > 0) formatMoving(todayMovingSec) else "no ride yet",
            style = MaterialTheme.typography.labelSmall,
            color = OnBlock,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun formatMoving(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m moving"
}

@Preview
@Composable
private fun CockpitConnectedPreview() {
    PreviewCockpit(
        CockpitUiState(
            connection = ConnectionState.Ready,
            fuelBars = 4,
            odometerKm = 12480,
            rangeKm = 186.0,
            bikeMac = "AA:BB",
            todayKm = 42.6,
            todayMovingSec = 84 * 60,
            serviceDueKm = 640,
            readiness = ReadinessCalculator.score(4, true, 80, true, 0, "Clear", 640, true, false),
        ),
    )
}

@Preview
@Composable
private fun CockpitDisconnectedPreview() {
    PreviewCockpit(
        CockpitUiState(
            connection = ConnectionState.Failed("gone"),
            bikeMac = "AA:BB",
            fuelBars = 1,
            odometerKm = 12480,
            rangeKm = 22.0,
            serviceDueKm = 0,
            readiness = ReadinessCalculator.score(1, false, 40, false, null, null, 0, true, true),
            warnings = listOf(
                CockpitWarning(WarningSeverity.WARN, "Low fuel", "1 bar remaining."),
                CockpitWarning(WarningSeverity.WARN, "Service overdue", "Log the last service in Garage."),
            ),
        ),
    )
}

@Preview
@Composable
private fun CockpitEmptyPreview() {
    PreviewCockpit(
        CockpitUiState(
            readiness = ReadinessCalculator.score(null, false, bikePaired = false),
            warnings = listOf(
                CockpitWarning(WarningSeverity.INFO, "Pair your bike", "Garage → scan and save the MAC."),
            ),
        ),
    )
}

@Composable
private fun PreviewCockpit(state: CockpitUiState) {
    BumblebeeTheme {
        Column(Modifier.background(Canvas)) {
            CockpitContent(state, onStartRide = {}, onRideStats = {}, onGarage = {}, onSetup = {})
        }
    }
}
