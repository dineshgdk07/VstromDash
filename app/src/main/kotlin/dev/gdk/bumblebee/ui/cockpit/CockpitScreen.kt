package dev.gdk.bumblebee.ui.cockpit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.OnThisDay
import dev.gdk.bumblebee.ui.cockpit.components.BikeHeroCard
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.StatusChipTone
import dev.gdk.bumblebee.ui.components.ForgeSheet
import dev.gdk.bumblebee.ui.garage.GaragePrefs
import dev.gdk.bumblebee.ui.garage.PreRideCheckSheet
import dev.gdk.bumblebee.ui.components.ListRow
import dev.gdk.bumblebee.ui.components.ListRowChevron
import dev.gdk.bumblebee.ui.components.ListRowDot
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.openParkedMaps
import dev.gdk.bumblebee.ui.theme.BlockCoral
import dev.gdk.bumblebee.ui.theme.BlockLavender
import dev.gdk.bumblebee.ui.theme.BlockMint
import dev.gdk.bumblebee.ui.theme.BlockYellow
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import dev.gdk.bumblebee.ui.theme.Canvas as CanvasColor
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.NumberMedium
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.ShapeLg
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.Stroke as StrokeColor
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import dev.gdk.bumblebee.ui.theme.Warning
import java.util.Locale

@Composable
fun CockpitScreen(
    model: CockpitViewModel = viewModel(),
    onStartRide: () -> Unit = {},
    onLiveRide: () -> Unit = {},
    onOpenLastRide: (Long) -> Unit = {},
    onFuel: () -> Unit = {},
    onService: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val state by model.ui.collectAsStateWithLifecycle()
    CockpitContent(
        state = state,
        onStartRide = onStartRide,
        onLiveRide = onLiveRide,
        onOpenLastRide = onOpenLastRide,
        onFuel = onFuel,
        onService = onService,
        onSettings = onSettings,
    )
}

@Composable
private fun CockpitContent(
    state: CockpitUiState,
    onStartRide: () -> Unit,
    onLiveRide: () -> Unit,
    onOpenLastRide: (Long) -> Unit,
    onFuel: () -> Unit,
    onService: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    var readinessOpen by remember { mutableStateOf(false) }
    var checkOpen by remember { mutableStateOf(false) }
    val checkedToday = remember(readinessOpen, checkOpen) { java.time.LocalDate.now() in GaragePrefs(context).checkDays }
    val pages = contextPages(state)
    val (cta, onCta) = when (state.ridePhase) {
        CockpitRidePhase.Start -> "Start ride" to onStartRide
        CockpitRidePhase.Resume -> "Resume ride" to onLiveRide
        CockpitRidePhase.Live -> "View live ride" to onLiveRide
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BikeHeroCard(
            model = "V-Strom 250 SX",
            connection = state.connection,
            verdict = readinessShout(state.readiness),
            score = state.readiness,
            onOpen = { readinessOpen = true },
        )
        ChunkyButton(text = cta, onClick = onCta, style = ChunkyStyle.Coral)
        state.suggestion?.let { SuggestionBanner(it) }
        RightNow(state)
        TripComputer(state)
        if (state.warnings.isNotEmpty()) {
            SectionCard(header = { SectionLabel(text = "Heads up", count = state.warnings.size) }) {
                state.warnings.forEachIndexed { index, warning ->
                    ListRow(
                        title = warning.title,
                        subtitle = warning.detail,
                        onClick = {
                            when (warning.fix) {
                                WarningFix.Settings -> onSettings()
                                WarningFix.Fuel -> onFuel()
                                WarningFix.Service -> onService()
                            }
                        },
                        showDivider = index < state.warnings.lastIndex,
                        leading = { ListRowDot(warningColor(warning.severity)) },
                        trailing = { ListRowChevron() },
                    )
                }
            }
        }
        // Three cards at most, so all of them show at once: last ride full width, the rest paired below.
        val lastRide = pages.filterIsInstance<ContextPage.LastRide>().firstOrNull()
        val small = pages.filter { it !is ContextPage.LastRide }
        lastRide?.let { page -> LastRideCard(page.ride) { onOpenLastRide(page.ride.id) } }
        state.onThisDay?.let { memory -> OnThisDayCard(memory) { onOpenLastRide(memory.rideId) } }
        if (small.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                small.forEach { page ->
                    val cell = Modifier.weight(1f).fillMaxHeight()
                    when (page) {
                        is ContextPage.ServiceDue -> ServiceDueCard(page.due, onService, cell)
                        is ContextPage.Parked -> ParkedCard(page.ago, state.parkedDistance, cell) { openParkedMaps(context, page.fix) }
                        is ContextPage.LastRide -> Unit
                    }
                }
            }
        }
    }

    if (checkOpen) {
        PreRideCheckSheet(onDismiss = { checkOpen = false }, onDone = { checkOpen = false })
    }
    if (readinessOpen) {
        ForgeSheet(
            title = "Readiness",
            onDismiss = { readinessOpen = false },
            primaryLabel = "Close",
            onPrimary = { readinessOpen = false },
        ) {
            ReadinessSheetBody(
                score = state.readiness,
                checkedToday = checkedToday,
                onCheck = {
                    readinessOpen = false
                    checkOpen = true
                },
                onFix = { pillar ->
                    readinessOpen = false
                    when (pillar) {
                        "Fuel" -> onFuel()
                        "Service" -> onService()
                        "Link" -> onSettings()
                    }
                },
            )
        }
    }
}

@Composable
private fun RightNow(state: CockpitUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Right now", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val cell = Modifier.weight(1f).fillMaxHeight()
            NowTile(
                Icons.Rounded.LocalGasStation,
                "Fuel",
                state.fuelBars?.let { "$it bars" } ?: "—",
                caption = state.rangeKm?.let { "${it.toInt()} km est." },
                modifier = cell,
            )
            val milestone = state.milestone
            NowTile(
                Icons.Rounded.Speed,
                "Odo",
                state.odometerKm?.let { String.format(Locale.US, "%,d", it) } ?: "—",
                modifier = cell,
                caption = milestone?.let { m ->
                    m.justCrossed?.let { String.format(Locale.US, "Crossed %,d!", it) }
                        ?: String.format(Locale.US, "%d km to %,d", m.kmToGo, m.target)
                },
                captionColor = if (milestone?.justCrossed != null) Lime else TextSecondary,
                progress = milestone?.takeIf { it.justCrossed == null }?.fraction,
            )
            NowTile(Icons.Rounded.Route, "Today", todayValue(state.todayKm), modifier = cell)
        }
    }
}

@Composable
private fun NowTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    captionColor: Color = TextSecondary,
    progress: Float? = null,
) {
    Column(
        modifier
            .background(SurfaceRaised, ShapeMd)
            .border(1.dp, StrokeColor, ShapeMd)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Text(value, style = NumberMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        caption?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = captionColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        progress?.let { fraction ->
            Box(Modifier.fillMaxWidth().height(4.dp).clip(ShapePill).background(StrokeColor)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0.02f, 1f)).background(Lime, ShapePill))
            }
        }
    }
}

/** A nudge to ride when the sky is fair: golden hour, an early start, or a weekend morning. */
@Composable
private fun SuggestionBanner(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ShapeMd)
            .background(BlockYellow.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = BlockYellow)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}

/** The cluster's own trip meters and live economy, while the bike is linked. */
@Composable
private fun TripComputer(state: CockpitUiState) {
    val linked = state.connection is ConnectionState.Ready
    val avg = if (state.todayMovingSec >= 60 && state.todayKm > 0) state.todayKm / (state.todayMovingSec / 3600.0) else null
    if (!linked || (state.tripAKm == null && state.tripBKm == null && state.economyKml == null)) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Trip computer", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val cell = Modifier.weight(1f).fillMaxHeight()
            TripTile("Trip A", state.tripAKm?.let { String.format(Locale.US, "%.1f", it) }, "km", cell)
            TripTile("Trip B", state.tripBKm?.let { String.format(Locale.US, "%.1f", it) }, "km", cell)
            TripTile("Live", state.economyKml?.let { String.format(Locale.US, "%.1f", it) }, "km/L", cell)
            TripTile("Avg today", avg?.let { String.format(Locale.US, "%.0f", it) }, "km/h", cell)
        }
    }
}

@Composable
private fun TripTile(label: String, value: String?, unit: String, modifier: Modifier) {
    Column(
        modifier
            .background(SurfaceRaised, ShapeMd)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
        Text(value ?: "—", style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

/** Each check with what it found, coloured by how it stands, and a tap to fix the ones the app can. */
@Composable
private fun ReadinessSheetBody(
    score: ReadinessScore,
    onFix: (String) -> Unit,
    checkedToday: Boolean = false,
    onCheck: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (score.band != ReadinessBand.COMPUTING) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${score.percent}%", style = NumberMedium, color = statusColor(bandStatus(score.band)))
                Text(
                    "  ${score.label}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        score.pillars.forEach { pillar ->
            val fixable = pillar.name in setOf("Fuel", "Service", "Link") &&
                pillar.status != PillarStatus.READY
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(ShapeMd)
                    .background(SurfaceRaised)
                    .then(if (fixable) Modifier.clickable { onFix(pillar.name) } else Modifier)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tone = statusColor(pillar.status)
                Box(
                    Modifier.size(40.dp).background(tone.copy(alpha = 0.16f), ShapeMd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(pillarIcon(pillar.name), contentDescription = null, tint = tone, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(pillar.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(
                        pillar.detail + if (fixable) " · tap to fix" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Text(
                    statusLabel(pillar.status).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pillar.status == PillarStatus.UNKNOWN) TextSecondary else OnBlock,
                    modifier = Modifier
                        .background(if (pillar.status == PillarStatus.UNKNOWN) StrokeColor else tone, ShapePill)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(SurfaceRaised)
                .clickable(onClick = onCheck)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tone = if (checkedToday) Lime else TextSecondary
            Box(
                Modifier.size(40.dp).background(tone.copy(alpha = 0.16f), ShapeMd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Checklist, contentDescription = null, tint = tone, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Pre-ride check", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(
                    if (checkedToday) "Done today" else "Tyres, chain, lights, brakes · tap to check",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

private fun pillarIcon(name: String): ImageVector = when (name) {
    "Fuel" -> Icons.Rounded.LocalGasStation
    "Weather" -> Icons.Rounded.WbSunny
    "Route" -> Icons.Rounded.MyLocation
    "Link" -> Icons.Rounded.Bluetooth
    "Service" -> Icons.Rounded.Build
    else -> Icons.Rounded.Speed
}

private fun statusColor(status: PillarStatus): Color = when (status) {
    PillarStatus.READY -> Lime
    PillarStatus.CAUTION -> Warning
    PillarStatus.NOT_READY -> Danger
    PillarStatus.UNKNOWN -> TextSecondary
}

private fun statusLabel(status: PillarStatus): String = when (status) {
    PillarStatus.READY -> "Ready"
    PillarStatus.CAUTION -> "Check"
    PillarStatus.NOT_READY -> "Not ready"
    PillarStatus.UNKNOWN -> "Unknown"
}

private fun bandStatus(band: ReadinessBand): PillarStatus = when (band) {
    ReadinessBand.READY -> PillarStatus.READY
    ReadinessBand.CAUTION -> PillarStatus.CAUTION
    ReadinessBand.NOT_READY -> PillarStatus.NOT_READY
    ReadinessBand.COMPUTING -> PillarStatus.UNKNOWN
}

@Composable
private fun LastRideCard(ride: LastRideSnippet, onClick: () -> Unit) {
    ContextCard(title = "Last ride", color = BlockLavender, onClick = onClick) {
        if (ride.track.size >= 2) {
            TrackLine(ride.track, Modifier.padding(top = 12.dp).fillMaxWidth().height(72.dp))
        }
        Text(
            text = "${String.format(Locale.US, "%.1f km", ride.km)} · ${formatMoving(ride.movingSec)}",
            style = MaterialTheme.typography.bodyMedium,
            color = OnBlock,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ServiceDueCard(due: CockpitServiceDue, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ContextCard(title = due.title, color = BlockYellow, onClick = onClick, modifier = modifier) {
        // Half-width card: DueMeter's label pill does not fit, so numbers on one line and a thin bar under them.
        Text(
            text = "${due.due.usedLabel} / ${due.due.totalLabel}",
            style = NumberMedium,
            color = OnBlock,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp),
        )
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(8.dp)
                .clip(ShapePill)
                .background(OnBlock.copy(alpha = 0.15f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(due.due.fraction.coerceIn(0f, 1f))
                    .background(if (due.due.tone == StatusChipTone.Overdue) Danger else OnBlock, ShapePill),
            )
        }
        Text(
            text = due.due.caption,
            style = MaterialTheme.typography.bodyMedium,
            color = OnBlock,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** A ride from this date in an earlier year, or last month. */
@Composable
private fun OnThisDayCard(memory: OnThisDay, onClick: () -> Unit) {
    ContextCard(title = memory.title, color = BlockCoral, onClick = onClick) {
        Text(
            text = memory.detail,
            style = MaterialTheme.typography.bodyLarge,
            color = OnBlock,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "Open ride ›",
            style = MaterialTheme.typography.bodyMedium,
            color = OnBlock,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ParkedCard(ago: String, distance: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ContextCard(title = "Parked here", color = BlockMint, onClick = onClick, modifier = modifier) {
        Text(
            text = distance ?: ago,
            style = MaterialTheme.typography.titleMedium,
            color = OnBlock,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (distance != null) {
            Text(text = "Parked $ago", style = MaterialTheme.typography.bodySmall, color = OnBlock)
        }
        Text(
            text = "Open in Maps ›",
            style = MaterialTheme.typography.bodyMedium,
            color = OnBlock,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ContextCard(
    title: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(ShapeLg)
            .background(color)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = OnBlock,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        content()
    }
}

@Composable
private fun TrackLine(points: List<Pair<Float, Float>>, modifier: Modifier) {
    Canvas(modifier) {
        val minLat = points.minOf { it.first }
        val maxLat = points.maxOf { it.first }
        val minLng = points.minOf { it.second }
        val maxLng = points.maxOf { it.second }
        val latSpan = (maxLat - minLat).takeIf { it > 0f } ?: 1f
        val lngSpan = (maxLng - minLng).takeIf { it > 0f } ?: 1f
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = ((point.second - minLng) / lngSpan) * size.width
            val y = (1f - (point.first - minLat) / latSpan) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            OnBlock,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

private sealed interface ContextPage {
    data class LastRide(val ride: LastRideSnippet) : ContextPage
    data class ServiceDue(val due: CockpitServiceDue) : ContextPage
    data class Parked(val ago: String, val fix: GpsFix) : ContextPage
}

private fun contextPages(state: CockpitUiState): List<ContextPage> = buildList {
    state.lastRide?.let { add(ContextPage.LastRide(it)) }
    state.servicePlan?.let { add(ContextPage.ServiceDue(it)) }
    state.parked?.let { add(ContextPage.Parked(parkedAgo(it.ts), it)) }
}

internal fun readinessShout(score: ReadinessScore): String {
    val checks = score.pillars.count {
        it.status == PillarStatus.CAUTION || it.status == PillarStatus.NOT_READY
    }
    return when {
        score.band == ReadinessBand.READY -> "READY TO RIDE"
        score.band == ReadinessBand.COMPUTING -> "CHECKING"
        checks == 1 -> "CHECK 1 THING"
        checks > 1 -> "CHECK $checks THINGS"
        else -> "NOT READY"
    }
}

private fun todayValue(km: Double): String =
    if (km <= 0.0) "—" else String.format(Locale.US, "%.1f km", km)

private fun formatMoving(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun parkedAgo(ts: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - ts) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        else -> "${minutes / (60 * 24)}d ago"
    }
}

private fun warningColor(severity: WarningSeverity) = when (severity) {
    WarningSeverity.CRITICAL -> Danger
    WarningSeverity.WARN -> Warning
    WarningSeverity.INFO -> TextSecondary
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
                CockpitWarning(WarningSeverity.WARN, "Low fuel", "1 bar remaining.", WarningFix.Fuel),
                CockpitWarning(WarningSeverity.WARN, "Service overdue", "Log the last service in Garage.", WarningFix.Service),
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
                CockpitWarning(WarningSeverity.INFO, "Pair your bike", "Garage → scan and save the MAC.", WarningFix.Settings),
            ),
        ),
    )
}

@Composable
private fun PreviewCockpit(state: CockpitUiState) {
    BumblebeeTheme {
        Column(Modifier.background(CanvasColor)) {
            CockpitContent(state, {}, {}, {}, {}, {}, {})
        }
    }
}
