package dev.gdk.bumblebee.ui.garage

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.R
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.ride.CareBand
import dev.gdk.bumblebee.ui.components.BlockColor
import dev.gdk.bumblebee.ui.components.BlockTile
import dev.gdk.bumblebee.ui.components.InsightPager
import dev.gdk.bumblebee.ui.components.ListRow
import dev.gdk.bumblebee.ui.components.ListRowChevron
import dev.gdk.bumblebee.ui.components.ListRowIcon
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.components.StatTone
import dev.gdk.bumblebee.ui.components.StatTriple
import dev.gdk.bumblebee.ui.components.StatTripleColumn
import dev.gdk.bumblebee.ui.components.StatusChip
import dev.gdk.bumblebee.ui.components.StatusChipTone
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.ShapeSm
import dev.gdk.bumblebee.ui.theme.Stroke
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary

@Composable
fun GarageScreen(
    model: GarageViewModel = viewModel(),
    onOpenFuel: () -> Unit = {},
    onOpenTransfer: () -> Unit = {},
    onOpenDocuments: () -> Unit = {},
    onOpenService: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val state by model.ui.collectAsStateWithLifecycle()
    RefreshOnResume(model::refresh)
    GarageBody(
        state = state,
        onOpenFuel = onOpenFuel,
        onOpenTransfer = onOpenTransfer,
        onOpenDocuments = onOpenDocuments,
        onOpenService = onOpenService,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
private fun GarageBody(
    state: GarageUiState,
    onOpenFuel: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenService: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val docs = documentTrailing(state.documents)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("How is the motorcycle", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        SectionCard {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(ShapeSm)
                        .background(OnBlock),
                ) {
                    Image(
                        painter = painterResource(R.drawable.vstrom_hero),
                        contentDescription = "V-Strom 250 SX",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("V-Strom 250 SX", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    ConnectionChip(state.connection)
                }
            }
            StatTriple(
                modifier = Modifier.padding(top = 16.dp),
                columns = listOf(
                    StatTripleColumn(state.odometerKm?.let { "%,d".format(it) } ?: "—", "Odo", StatTone.Plain),
                    StatTripleColumn(state.tripAKm?.let { "%.1f".format(it) } ?: "—", "Trip A", StatTone.Plain),
                    StatTripleColumn(state.tripBKm?.let { "%.1f".format(it) } ?: "—", "Trip B", StatTone.Plain),
                ),
            )
            Text(
                text = state.fuelBars?.let { "$it bars" } ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (state.bikeMac == null) {
            BlockTile(
                title = "Pair the bike",
                color = BlockColor.Mint,
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth().height(96.dp),
            )
        }
        if (state.upcoming.isNotEmpty()) {
            SectionLabel(text = "Needs attention")
            InsightPager(pageCount = state.upcoming.size) { page ->
                val item = state.upcoming[page]
                BlockTile(
                    title = item.label,
                    color = if (item.band == CareBand.OVERDUE) BlockColor.Coral else BlockColor.Yellow,
                    onClick = { if (item.key.startsWith("d")) onOpenDocuments() else onOpenService() },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )
            }
        }
        SectionCard(header = { SectionLabel(text = "Bike") }) {
            Column(Modifier.padding(top = 8.dp)) {
                ListRow(
                    title = "Fuel log",
                    onClick = onOpenFuel,
                    leading = { ListRowIcon(Icons.Rounded.LocalGasStation) },
                    trailing = {
                        Text(
                            text = state.tankKmPerL?.let { "%.1f km/l est.".format(it) } ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                ListRow(
                    title = "Documents",
                    onClick = onOpenDocuments,
                    leading = { ListRowIcon(Icons.Rounded.Description) },
                    trailing = { StatusChip(docs.first, text = docs.second) },
                )
                ListRow(
                    title = "Service",
                    onClick = onOpenService,
                    leading = { ListRowIcon(Icons.Rounded.Build) },
                    trailing = {
                        Text(
                            text = serviceTrailing(state.plans),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 148.dp),
                        )
                    },
                )
                ListRow(
                    title = "Settings",
                    onClick = onOpenSettings,
                    leading = { ListRowIcon(Icons.Rounded.Settings) },
                    trailing = { ListRowChevron() },
                )
                ListRow(
                    title = "Transfer",
                    onClick = onOpenTransfer,
                    showDivider = false,
                    leading = { ListRowIcon(Icons.Rounded.SwapHoriz) },
                    trailing = { ListRowChevron() },
                )
            }
        }
    }
}

@Composable
private fun ConnectionChip(connection: ConnectionState) {
    val ready = connection is ConnectionState.Ready
    val label = when (connection) {
        ConnectionState.Ready -> "Connected"
        ConnectionState.Connecting, ConnectionState.Discovering -> "Connecting"
        else -> "Not connected"
    }
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (ready) Lime else TextSecondary,
        modifier = Modifier
            .border(1.dp, if (ready) Lime else Stroke, ShapePill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private fun documentTrailing(docs: List<DocCard>): Pair<StatusChipTone, String> {
    if (docs.isEmpty()) return StatusChipTone.NoExpiry to "Add"
    val hot = docs.filter { it.band == CareBand.OVERDUE || it.band == CareBand.DUE_SOON }
    if (hot.isEmpty()) return StatusChipTone.Ok to "OK"
    val tone = if (hot.any { it.band == CareBand.OVERDUE }) StatusChipTone.Overdue else StatusChipTone.DueSoon
    val label = if (hot.size == 1) "1 expiring" else "${hot.size} expiring"
    return tone to label
}

private fun serviceTrailing(plans: List<PlanCard>): String {
    val urgent = plans
        .filter { it.band == CareBand.OVERDUE || it.band == CareBand.DUE_SOON }
        .sortedBy { if (it.band == CareBand.OVERDUE) 0 else 1 }
    return (urgent.firstOrNull() ?: plans.firstOrNull())?.label ?: "—"
}

@Preview
@Composable
private fun GaragePairedPreview() {
    BumblebeeTheme {
        Column(Modifier.background(Canvas)) {
            GarageBody(
                state = GarageUiState(
                    connection = ConnectionState.Ready,
                    bikeMac = "80:C4:1B:47:82:BE",
                    odometerKm = 20520,
                    tripAKm = 112.4,
                    tripBKm = 48.0,
                    fuelBars = 4,
                    tankKmPerL = 38.2,
                ),
                onOpenFuel = {},
                onOpenTransfer = {},
                onOpenDocuments = {},
                onOpenService = {},
                onOpenSettings = {},
            )
        }
    }
}

@Preview
@Composable
private fun GarageUnpairedPreview() {
    BumblebeeTheme {
        Column(Modifier.background(Canvas)) {
            GarageBody(
                state = GarageUiState(connection = ConnectionState.Idle),
                onOpenFuel = {},
                onOpenTransfer = {},
                onOpenDocuments = {},
                onOpenService = {},
                onOpenSettings = {},
            )
        }
    }
}

@Preview
@Composable
private fun GarageAttentionPreview() {
    BumblebeeTheme {
        Column(Modifier.background(Canvas)) {
            GarageBody(
                state = GarageUiState(
                    connection = ConnectionState.Ready,
                    bikeMac = "80:C4:1B:47:82:BE",
                    odometerKm = 20520,
                    fuelBars = 2,
                    documents = listOf(
                        DocCard(1, "Insurance", "INSURANCE", null, "2026-11-01", null, null, CareBand.DUE_SOON, "Insurance"),
                    ),
                    plans = listOf(
                        PlanCard(2, "Engine oil", "OIL", 4000, 180, null, 18000, null, CareBand.DUE_SOON, "Engine oil · 420 km"),
                    ),
                    upcoming = listOf(
                        UpcomingItem("d1", "Insurance expires soon", CareBand.DUE_SOON),
                        UpcomingItem("p2", "Engine oil · 420 km", CareBand.OVERDUE),
                    ),
                ),
                onOpenFuel = {},
                onOpenTransfer = {},
                onOpenDocuments = {},
                onOpenService = {},
                onOpenSettings = {},
            )
        }
    }
}
