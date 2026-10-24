package dev.gdk.bumblebee.ui.garage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.gdk.bumblebee.ride.Ownership
import dev.gdk.bumblebee.ui.components.ChunkyDialog
import dev.gdk.bumblebee.ui.components.ForgeSheet
import dev.gdk.bumblebee.ui.theme.BlockLavender
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
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
import dev.gdk.bumblebee.ui.theme.ShapeLg
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
    onOpenCosts: () -> Unit = {},
    onOpenPassport: () -> Unit = {},
) {
    val state by model.ui.collectAsStateWithLifecycle()
    RefreshOnResume(model::refresh)
    var healthOpen by remember { mutableStateOf(false) }
    var checkOpen by remember { mutableStateOf(false) }
    var lubeAsk by remember { mutableStateOf(false) }
    var efficiencyOpen by remember { mutableStateOf(false) }
    GarageBody(
        state = state,
        onOpenFuel = onOpenFuel,
        onOpenTransfer = onOpenTransfer,
        onOpenDocuments = onOpenDocuments,
        onOpenService = onOpenService,
        onOpenSettings = onOpenSettings,
        onOpenCosts = onOpenCosts,
        onOpenPassport = onOpenPassport,
        onOpenHealth = { healthOpen = true },
        onOpenCheck = { checkOpen = true },
        onChain = { lubeAsk = true },
        onEfficiency = { efficiencyOpen = true },
    )
    val extras = state.extras
    if (healthOpen && extras != null) HealthSheet(extras.health, onDismiss = { healthOpen = false })
    if (checkOpen) {
        PreRideCheckSheet(
            onDismiss = { checkOpen = false },
            onDone = {
                checkOpen = false
                model.refresh()
            },
        )
    }
    if (lubeAsk && extras?.chain != null) {
        ChunkyDialog(
            title = "Lube the chain?",
            body = extras.chain.message + ". Mark it lubed at ${extras.odoKm?.let { "%,d km".format(it) } ?: "the current odo"}?",
            dismissLabel = "Later",
            confirmLabel = "Lubed it",
            onDismiss = { lubeAsk = false },
            onConfirm = {
                lubeAsk = false
                model.lubeChain()
            },
        )
    }
    val alert = extras?.efficiency
    if (efficiencyOpen && alert != null) {
        ForgeSheet(
            title = "Fuel efficiency",
            onDismiss = { efficiencyOpen = false },
            primaryLabel = "Open fuel log",
            onPrimary = {
                efficiencyOpen = false
                onOpenFuel()
            },
        ) {
            Text(
                "Your last tank gave ${"%.1f".format(alert.latestKmPerL)} km/l. You usually get ${"%.1f".format(alert.usualKmPerL)}.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
            )
            Text(
                "Likely reasons",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            alert.causes.forEach { cause ->
                Text("•  $cause", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

/** One page of the "Needs attention" pager. */
private data class Attention(val title: String, val urgent: Boolean, val onClick: () -> Unit)

@Composable
private fun GarageBody(
    state: GarageUiState,
    onOpenFuel: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenService: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCosts: () -> Unit = {},
    onOpenPassport: () -> Unit = {},
    onOpenHealth: () -> Unit = {},
    onOpenCheck: () -> Unit = {},
    onChain: () -> Unit = {},
    onEfficiency: () -> Unit = {},
) {
    val docs = documentTrailing(state.documents)
    val extras = state.extras
    val chainPlanKey = extras?.chainPlanId?.let { "p$it" }
    val chain = extras?.chain?.takeIf { it.due }
    val attention = buildList {
        state.upcoming.forEach { item ->
            if (chain != null && item.key == chainPlanKey) return@forEach
            add(
                Attention(item.label, item.band == CareBand.OVERDUE) {
                    if (item.key.startsWith("d")) onOpenDocuments() else onOpenService()
                },
            )
        }
        chain?.let { add(Attention(it.message, urgent = false, onClick = onChain)) }
        extras?.efficiency?.let {
            add(Attention("Last tank ${(it.drop * 100).roundToInt()}% below your usual km/l. Find out why", urgent = false, onClick = onEfficiency))
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("How is the motorcycle", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        BikeHero(state, onOpenHealth = onOpenHealth, onOpenCheck = onOpenCheck)
        if (state.bikeMac == null) {
            BlockTile(
                title = "Pair the bike",
                color = BlockColor.Mint,
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth().height(96.dp),
            )
        }
        if (attention.isNotEmpty()) {
            SectionLabel(text = "Needs attention")
            InsightPager(pageCount = attention.size) { page ->
                val item = attention[page]
                BlockTile(
                    title = item.title,
                    color = if (item.urgent) BlockColor.Coral else BlockColor.Yellow,
                    onClick = item.onClick,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )
            }
        }
        extras?.ownership?.let { CostCard(it, onOpenCosts) }
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
                    subtitle = state.nextService?.title,
                    onClick = onOpenService,
                    leading = { ListRowIcon(Icons.Rounded.Build) },
                    trailing = {
                        Text(
                            text = state.nextService?.caption ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 148.dp),
                        )
                    },
                )
                ListRow(
                    title = "Bike passport",
                    subtitle = extras?.odo?.since?.let { "Since ${it.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))}" },
                    onClick = onOpenPassport,
                    leading = { ListRowIcon(Icons.Rounded.Badge) },
                    trailing = {
                        Text(
                            text = extras?.odo?.loggedKm?.takeIf { it > 0 }?.let { "%,.0f km logged".format(it) } ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            maxLines = 1,
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

/** ₹ per km, this year's spend and the year ahead, with the fuel and service split. */
@Composable
private fun CostCard(o: Ownership, onClick: () -> Unit) {
    SectionCard(
        modifier = Modifier.clip(ShapeLg).clickable(onClick = onClick),
        header = { SectionLabel(text = "Cost of ownership") },
    ) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                o.rupeesPerKm?.let { "₹%.2f".format(it) } ?: "₹%,.0f".format(o.thisYear),
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
            )
            Text(
                if (o.rupeesPerKm != null) " per km" else " this year",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp).weight(1f),
            )
            ListRowChevron()
        }
        Text(
            listOfNotNull(
                "₹%,.0f this year".format(o.thisYear).takeIf { o.rupeesPerKm != null },
                o.projectedYear?.let { "≈ ${compactRupees(it)} a year" },
            ).joinToString(" · ").ifBlank { "Add prices to fills and services to see more" },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        SplitBar(o.fuelFraction.toFloat(), Modifier.padding(top = 12.dp))
        Row(Modifier.padding(top = 6.dp)) {
            Text("Fuel ${(o.fuelFraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = Lime, modifier = Modifier.weight(1f))
            Text("Service ${100 - (o.fuelFraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = BlockLavender)
        }
    }
}

@Composable
internal fun SplitBar(fuel: Float, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(10.dp).clip(ShapePill)) {
        val f = fuel.coerceIn(0f, 1f)
        if (f > 0f) Box(Modifier.weight(f).fillMaxHeight().background(Lime))
        if (f < 1f) Box(Modifier.weight(1f - f).fillMaxHeight().background(BlockLavender))
    }
}

/** ₹27k, ₹1.2L. */
internal fun compactRupees(value: Double): String = when {
    value >= 100_000 -> "₹%.1fL".format(value / 100_000)
    value >= 1_000 -> "₹%.0fk".format(value / 1_000)
    else -> "₹%.0f".format(value)
}

private fun documentTrailing(docs: List<DocCard>): Pair<StatusChipTone, String> {
    if (docs.isEmpty()) return StatusChipTone.NoExpiry to "Add"
    val hot = docs.filter { it.band == CareBand.OVERDUE || it.band == CareBand.DUE_SOON }
    if (hot.isEmpty()) return StatusChipTone.Ok to "OK"
    val tone = if (hot.any { it.band == CareBand.OVERDUE }) StatusChipTone.Overdue else StatusChipTone.DueSoon
    val label = if (hot.size == 1) "1 expiring" else "${hot.size} expiring"
    return tone to label
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
