package dev.gdk.bumblebee.ui.garage

import android.app.Application
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ride.GarageInsights
import dev.gdk.bumblebee.ride.GarageYear
import dev.gdk.bumblebee.ride.MonthSpend
import dev.gdk.bumblebee.ride.OdoStory
import dev.gdk.bumblebee.ride.PassportEvent
import dev.gdk.bumblebee.ride.PassportKind
import dev.gdk.bumblebee.ride.ServiceSpend
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.EmptyState
import dev.gdk.bumblebee.ui.components.ForgeTopBar
import dev.gdk.bumblebee.ui.components.ListRow
import dev.gdk.bumblebee.ui.components.PillChip
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.sharecard.GarageYearCard
import dev.gdk.bumblebee.ui.sharecard.GarageYearPainter
import dev.gdk.bumblebee.ui.sharecard.HistoryLine
import dev.gdk.bumblebee.ui.sharecard.ServiceHistoryCard
import dev.gdk.bumblebee.ui.sharecard.ServiceHistoryPainter
import dev.gdk.bumblebee.ui.sharecard.YearStat
import dev.gdk.bumblebee.ui.theme.BlockLavender
import dev.gdk.bumblebee.ui.theme.BlockMint
import dev.gdk.bumblebee.ui.theme.BlockYellow
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.ShapeXs
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

data class GarageMoreUi(val extras: GarageExtras? = null, val services: List<ServiceSpend> = emptyList())

/** Backs the Costs and Passport screens. */
class GarageMoreViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DashApp
    private val _ui = MutableStateFlow(GarageMoreUi())
    val ui: StateFlow<GarageMoreUi> = _ui.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val extras = loadGarageExtras(app, app.latestTelemetry.value?.odometerKm)
            val services = withContext(Dispatchers.IO) { app.capture.allServices() }
            _ui.value = GarageMoreUi(extras, services)
        }
    }
}

@Composable
private fun GaragePage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ForgeTopBar(title = title, onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

private val shortDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

// ---- Costs ----

@Composable
fun CostsScreen(onBack: () -> Unit, model: GarageMoreViewModel = viewModel()) {
    val state by model.ui.collectAsStateWithLifecycle()
    RefreshOnResume(model::refresh)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extras = state.extras
    GaragePage("Costs", onBack) {
        if (extras == null) return@GaragePage
        val o = extras.ownership
        if (o == null) {
            EmptyState(
                headline = "No prices yet",
                subtitle = "Add what you paid to fuel fills and services, and the cost of keeping the bike shows here.",
                primaryLabel = "Back",
                onPrimary = onBack,
                modifier = Modifier.padding(top = 24.dp),
            )
            return@GaragePage
        }
        SectionCard {
            Text("COST PER KM", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(o.rupeesPerKm?.let { "₹%.2f".format(it) } ?: "—", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                o.fuelPerKm?.let { Text("Fuel ₹%.2f".format(it), style = MaterialTheme.typography.bodyMedium, color = Lime) }
                o.servicePerKm?.let { Text("Service ₹%.2f".format(it), style = MaterialTheme.typography.bodyMedium, color = BlockLavender) }
            }
            SplitBar(o.fuelFraction.toFloat(), Modifier.padding(top = 12.dp))
            Row(Modifier.padding(top = 16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("THIS YEAR", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("₹%,.0f".format(o.thisYear), style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                }
                o.projectedYear?.let {
                    Column(Modifier.weight(1f)) {
                        Text("A YEAR AT THIS RATE", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("≈ ₹%,.0f".format(it), style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    }
                }
            }
            if (o.partial) {
                Text(
                    "Some fills or services have no price, so this is a floor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        SectionCard(header = { SectionLabel(text = "Last 6 months") }) {
            SpendBars(o.months)
        }
        PlannerCard(extras)
        YearReview(extras.years) { year, prev ->
            scope.launch { shareYear(context, year, prev) }
        }
    }
}

@Composable
private fun SpendBars(months: List<MonthSpend>) {
    val peak = months.maxOfOrNull { it.total }?.takeIf { it > 0 } ?: 1.0
    Row(
        Modifier.fillMaxWidth().height(150.dp).padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        months.forEach { m ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (m.total > 0) compactRupees(m.total) else "", style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
                Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Bottom) {
                    if (m.service > 0) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight((m.service / peak).toFloat().coerceIn(0.02f, 1f)).background(BlockLavender, ShapeXs))
                    }
                    if (m.fuel > 0) {
                        // Fuel sits on the bottom; its share of what's left keeps the stack to scale.
                        val left = 1.0 - m.service / peak
                        val frac = if (left <= 0) 1.0 else (m.fuel / peak / left)
                        Box(Modifier.fillMaxWidth().fillMaxHeight(frac.toFloat().coerceIn(0.02f, 1f)).background(Lime, ShapeXs))
                    }
                }
                Text(m.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }
    }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Legend(Lime, "Fuel")
        Legend(BlockLavender, "Service")
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(color, ShapeXs))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

/** What the next three months will likely cost, priced from the rider's own bills. */
@Composable
private fun PlannerCard(extras: GarageExtras) {
    val plan = extras.plan
    val today = LocalDate.now()
    SectionCard(header = { SectionLabel(text = "Next 3 months") }) {
        Text(
            if (plan.total > 0) "Budget about ${"₹%,.0f".format(plan.total)}" else "Nothing priced to plan for yet",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.padding(top = 8.dp),
        )
        Column(Modifier.padding(top = 4.dp)) {
            plan.items.forEach { item ->
                ListRow(
                    title = item.title,
                    subtitle = GarageInsights.forecastLabel(item.date, true, today).replaceFirstChar { it.uppercase() },
                    trailing = {
                        Text(
                            item.rupees?.let { "₹%,.0f".format(it) } ?: "no past bill",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (item.rupees == null) TextSecondary else TextPrimary,
                        )
                    },
                )
            }
            plan.fuelRupees?.let {
                ListRow(
                    title = "Fuel",
                    subtitle = "At your usual pace and last pump price",
                    showDivider = false,
                    trailing = { Text("₹%,.0f".format(it), style = MaterialTheme.typography.bodyMedium, color = TextPrimary) },
                )
            }
        }
        if (plan.unpriced > 0) {
            Text(
                "Items with no past bill aren't in the total.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

private fun deltaText(now: Double?, before: Double?, prevYear: Int, higherIsGood: Boolean): Pair<String, Boolean>? {
    val pct = GarageInsights.change(now, before) ?: return null
    if (pct == 0) return "Same as $prevYear" to true
    val arrow = if (pct > 0) "▲" else "▼"
    return "$arrow ${abs(pct)}% vs $prevYear" to ((pct > 0) == higherIsGood)
}

private fun yearStats(y: GarageYear, prev: GarageYear?): List<YearStat> {
    fun stat(label: String, value: String, now: Double?, before: Double?, higherIsGood: Boolean): YearStat {
        val d = prev?.let { deltaText(now, before, it.year, higherIsGood) }
        return YearStat(label, value, d?.first, d?.second)
    }
    return listOf(
        stat("Km ridden", "%,.0f".format(y.km), y.km, prev?.km, true),
        stat("Spent", "₹%,.0f".format(y.spent), y.spent, prev?.spent, false),
        stat("Per km", y.rupeesPerKm?.let { "₹%.2f".format(it) } ?: "—", y.rupeesPerKm, prev?.rupeesPerKm, false),
        stat("Best tank", y.bestKmPerL?.let { "%.1f km/l".format(it) } ?: "—", y.bestKmPerL, prev?.bestKmPerL, true),
        YearStat("Services", "${y.services}", "₹%,.0f on care".format(y.service).takeIf { y.service > 0 }, null),
        YearStat("Fuel", if (y.litres > 0) "%.0f L".format(y.litres) else "₹%,.0f".format(y.fuel), if (y.fills == 1) "1 fill" else "${y.fills} fills", null),
    )
}

@Composable
private fun YearReview(years: List<GarageYear>, onShare: (GarageYear, GarageYear?) -> Unit) {
    if (years.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }
    val y = years[index.coerceIn(0, years.lastIndex)]
    val prev = years.firstOrNull { it.year == y.year - 1 }
    SectionCard(header = { SectionLabel(text = "Year in review") }) {
        if (years.size > 1) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                years.take(4).forEachIndexed { i, yr ->
                    PillChip(text = "${yr.year}", selected = i == index, onClick = { index = i })
                }
            }
        }
        val stats = yearStats(y, prev)
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stats.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { s ->
                        Column(Modifier.weight(1f).background(SurfaceRaised, ShapeMd).padding(12.dp)) {
                            Text(s.label.uppercase(Locale.ENGLISH), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(s.value, style = MaterialTheme.typography.titleLarge, color = TextPrimary, maxLines = 1)
                            Text(
                                s.delta ?: " ",
                                style = MaterialTheme.typography.labelSmall,
                                color = when (s.good) {
                                    true -> Lime
                                    false -> BlockYellow
                                    null -> TextSecondary
                                },
                            )
                        }
                    }
                }
            }
        }
        ChunkyButton(
            text = "Share ${y.year}",
            onClick = { onShare(y, prev) },
            style = ChunkyStyle.Lime,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private suspend fun shareYear(context: Context, y: GarageYear, prev: GarageYear?) {
    val headline = listOfNotNull(
        "%,.0f km".format(y.km).takeIf { y.km > 0 },
        y.rupeesPerKm?.let { "₹%.2f a km to keep on the road".format(it) },
    ).joinToString(" · ").ifBlank { "A year in the garage" }
    val bmp = withContext(Dispatchers.Default) { GarageYearPainter.paint(context, GarageYearCard(y.year, headline, yearStats(y, prev))) }
    sharePng(context, bmp, "garage-${y.year}.png", "My ${y.year} in the garage: $headline", "Share garage year")
}

internal suspend fun sharePng(context: Context, bmp: Bitmap, name: String, text: String, title: String) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        File(dir, name).also { out -> FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri(name, uri)
    }
    context.startActivity(Intent.createChooser(send, title))
}

// ---- Passport ----

@Composable
fun PassportScreen(onBack: () -> Unit, onOpenRide: (Long) -> Unit, model: GarageMoreViewModel = viewModel()) {
    val state by model.ui.collectAsStateWithLifecycle()
    RefreshOnResume(model::refresh)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extras = state.extras
    GaragePage("Bike passport", onBack) {
        if (extras == null) return@GaragePage
        OdoCard(extras.odo, extras.odoKm)
        ChunkyButton(
            text = "Share service history",
            onClick = { scope.launch { shareHistory(context, extras, state.services) } },
            style = ChunkyStyle.Lime,
        )
        if (extras.passport.isEmpty()) {
            EmptyState(
                headline = "The story starts with a ride",
                subtitle = "Rides, fills, services and papers line up here as you log them.",
                primaryLabel = "Back",
                onPrimary = onBack,
            )
            return@GaragePage
        }
        val zone = ZoneId.systemDefault()
        extras.passport.groupBy { Instant.ofEpochMilli(it.ts).atZone(zone).year }.forEach { (year, events) ->
            SectionCard(header = { SectionLabel(text = "$year") }) {
                Column(Modifier.padding(top = 8.dp)) {
                    events.forEachIndexed { i, e ->
                        PassportRow(e, last = i == events.lastIndex, onOpenRide = onOpenRide)
                    }
                }
            }
        }
    }
}

@Composable
private fun OdoCard(story: OdoStory, odoKm: Int?) {
    SectionCard {
        Text("ODOMETER", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(odoKm?.let { "%,d km".format(it) } ?: "—", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
        val bits = listOfNotNull(
            story.since?.let { "Logging since ${it.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))}" },
            story.loggedKm.takeIf { it > 0 }?.let { "%,.0f km logged".format(it) },
            story.avgPerMonth?.let { "%,.0f a month".format(it) },
        )
        if (bits.isNotEmpty()) Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        val peak = story.months.maxOfOrNull { it.km }?.takeIf { it > 0 }
        if (peak != null) {
            Row(
                Modifier.fillMaxWidth().height(90.dp).padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                story.months.forEachIndexed { i, m ->
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                Modifier.fillMaxWidth().fillMaxHeight((m.km / peak).toFloat().coerceIn(0.02f, 1f))
                                    .background(if (i == story.months.lastIndex) BlockYellow else BlockYellow.copy(alpha = 0.45f), ShapeXs),
                            )
                        }
                        Text(
                            m.month.month.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }
        story.busiestMonth?.let {
            Text(
                "${it.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} is usually your busiest month",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

private fun passportIcon(kind: PassportKind): Pair<ImageVector, Color> = when (kind) {
    PassportKind.SERVICE -> Icons.Rounded.Build to BlockLavender
    PassportKind.FUEL -> Icons.Rounded.LocalGasStation to Lime
    PassportKind.DOCUMENT -> Icons.Rounded.Description to BlockMint
    PassportKind.MILESTONE -> Icons.Rounded.Star to BlockYellow
    PassportKind.TRIP -> Icons.Rounded.Route to BlockYellow
    PassportKind.START -> Icons.Rounded.Flag to Lime
}

@Composable
private fun PassportRow(e: PassportEvent, last: Boolean, onOpenRide: (Long) -> Unit) {
    val (icon, tone) = passportIcon(e.kind)
    val date = Instant.ofEpochMilli(e.ts).atZone(ZoneId.systemDefault()).toLocalDate()
    ListRow(
        title = e.title,
        subtitle = listOfNotNull(date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)), e.detail).joinToString(" · "),
        onClick = e.rideId?.let { id -> { onOpenRide(id) } },
        showDivider = !last,
        leading = {
            Box(Modifier.size(40.dp).background(tone.copy(alpha = 0.16f), ShapeMd), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(20.dp))
            }
        },
    )
}

private suspend fun shareHistory(context: Context, extras: GarageExtras, services: List<ServiceSpend>) {
    val zone = ZoneId.systemDefault()
    val newest = services.sortedByDescending { it.ts }
    val card = ServiceHistoryCard(
        bike = "V-Strom 250 SX",
        odoKm = extras.odoKm,
        since = extras.odo.since?.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)),
        services = services.size,
        careRupees = services.sumOf { it.rupees ?: 0.0 },
        lines = newest.map { s ->
            HistoryLine(
                Instant.ofEpochMilli(s.ts).atZone(zone).toLocalDate().format(shortDate),
                s.item.replaceFirstChar { it.uppercase() },
                listOfNotNull("%,d km".format(s.odometerKm), s.rupees?.takeIf { it > 0 }?.let { "₹%,.0f".format(it) }).joinToString(" · "),
            )
        },
    )
    val bmp = withContext(Dispatchers.Default) { ServiceHistoryPainter.paint(context, card) }
    sharePng(context, bmp, "service-history.png", "V-Strom 250 SX service history: ${services.size} services logged", "Share service history")
}
