package dev.gdk.bumblebee.ui.ride

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Movie
import dev.gdk.bumblebee.video.VideoExportService
import dev.gdk.bumblebee.video.VideoExportState
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.RideEventKind
import dev.gdk.bumblebee.ride.RideStop
import dev.gdk.bumblebee.ride.RideTimeline
import dev.gdk.bumblebee.ride.InsightKind
import dev.gdk.bumblebee.ride.InsightLine
import dev.gdk.bumblebee.ride.TrackInsights
import dev.gdk.bumblebee.ui.share.ShareScreen
import dev.gdk.bumblebee.ui.share.ShareSubject
import dev.gdk.bumblebee.ui.openParkedMaps
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyIconButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.ForgeTopBar
import dev.gdk.bumblebee.ui.components.ListRow
import dev.gdk.bumblebee.ui.components.ListRowChevron
import dev.gdk.bumblebee.ui.components.ListRowIcon
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.components.StatTone
import dev.gdk.bumblebee.ui.components.StatTriple
import dev.gdk.bumblebee.ui.components.StatTripleColumn
import dev.gdk.bumblebee.ui.theme.BlockYellow
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.NumberMedium
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.ShapeLg
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.ShapeSm
import dev.gdk.bumblebee.ui.theme.Stroke as GridStroke
import dev.gdk.bumblebee.ui.theme.Scrim
import dev.gdk.bumblebee.ui.theme.ShapeSheet
import dev.gdk.bumblebee.ui.theme.Surface
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.TextDisabled
import dev.gdk.bumblebee.ui.theme.TextDisabled
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import dev.gdk.bumblebee.ui.ride.replay.RideReplay
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import dev.gdk.bumblebee.ui.ride.replay.bandColor
import dev.gdk.bumblebee.ui.theme.Warning
import java.time.Instant
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RideCalendar(
    month: YearMonth,
    kmByDate: Map<String, Double>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { java.time.LocalDate.now().toString() }
    val monthPrefix = month.toString()
    val peak = (kmByDate.filterKeys { it.startsWith(monthPrefix) }.values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    val first = month.atDay(1)
    val lead = (first.dayOfWeek.value + 6) % 7
    val days = month.lengthOfMonth()
    Column(
        modifier
            .clip(ShapeLg)
            .background(Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        val cells = lead + days
        val rows = (cells + 6) / 7
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { c ->
                    val idx = r * 7 + c - lead
                    if (idx in 0 until days) {
                        val date = month.atDay(idx + 1).toString()
                        val km = kmByDate[date] ?: 0.0
                        val on = km > 0
                        val sel = date == selected
                        val isToday = date == today
                        val heat = (km / peak).toFloat().coerceIn(0f, 1f)
                        val fill = when {
                            sel -> BlockYellow
                            on -> lerp(SurfaceRaised, BlockYellow, 0.35f + 0.65f * heat)
                            else -> SurfaceRaised
                        }
                        // Dark text once the fill is bright enough, so mid-heat days stay readable.
                        val onYellow = fill.luminance() > 0.3f
                        Box(
                            Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(ShapeSm)
                                .background(fill)
                                .then(
                                    if (isToday) Modifier.border(2.dp, Lime, ShapeSm) else Modifier,
                                )
                                .clickable { onSelect(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "${idx + 1}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = when {
                                        onYellow -> OnBlock
                                        on -> TextPrimary
                                        else -> TextDisabled
                                    },
                                )
                                if (on) {
                                    Text(
                                        if (km >= 10) String.format("%.0f", km) else String.format("%.1f", km),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (onYellow) OnBlock else TextPrimary,
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f).height(52.dp))
                    }
                }
            }
        }
        Text(
            "Brighter days rode further. Lime ring is today.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
internal fun InsightCard(line: InsightLine, featured: Boolean = false, onJump: ((Long) -> Unit)? = null) {
    val jumpTo = line.atTs
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(ShapeLg)
            .background(Surface)
            .then(if (jumpTo != null && onJump != null) Modifier.clickable { onJump(jumpTo) } else Modifier),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(if (line.kind == InsightKind.INSIGHT) Lime else GridStroke),
        )
        Column(Modifier.padding(14.dp).weight(1f)) {
            Text(line.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(line.body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            if (jumpTo != null && onJump != null) {
                Text("Show this moment on the map", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
fun RideDetailPane(
    detail: RideDetail,
    onBack: () -> Unit,
    onSplit: (() -> Unit)? = null,
    onRename: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var seekTs by remember(detail.row.id) { mutableStateOf<Long?>(null) }
    var seekNonce by remember(detail.row.id) { mutableIntStateOf(0) }
    var renaming by remember(detail.row.id) { mutableStateOf(false) }
    var shareMenu by remember(detail.row.id) { mutableStateOf(false) }
    var shareCard by remember(detail.row.id) { mutableStateOf(false) }
    val video by VideoExportService.state.collectAsState()
    val actions = shareActions(detail, context, openCard = { shareCard = true }, videoBusy = video is VideoExportState.Working)
    // When this ride's video is ready, offer it straight away; a failure says why.
    LaunchedEffect(video) {
        when (val v = video) {
            is VideoExportState.Done -> if (v.rideId == detail.row.id) {
                val send = Intent(Intent.ACTION_SEND).setType("video/mp4")
                    .putExtra(Intent.EXTRA_STREAM, v.uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, "Share ride video"))
                VideoExportService.consume()
            }
            is VideoExportState.Failed -> if (v.rideId == detail.row.id) {
                android.widget.Toast.makeText(context, "Video failed: ${v.reason}", android.widget.Toast.LENGTH_LONG).show()
                VideoExportService.consume()
            }
            else -> {}
        }
    }
    var draft by remember(detail.row.id, detail.row.name) { mutableStateOf(detail.row.name.orEmpty()) }
    fun jump(ts: Long) {
        seekTs = ts
        seekNonce += 1
        scope.launch { scroll.scrollTo(0) }
    }

    /** Moves the replay without scrolling, for scrubbing from further down the page. */
    fun seek(ts: Long) {
        seekTs = ts
        seekNonce += 1
    }
    val avg = if (detail.row.avgMovingSpeedKmh > 0) {
        String.format("%.0f", detail.row.avgMovingSpeedKmh)
    } else {
        "—"
    }
    val max = if (detail.row.maxSpeedKmh > 0) "${detail.row.maxSpeedKmh} km/h" else "—"
    val stops = detail.timeline.events.filter { it.kind == RideEventKind.STOP }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ForgeTopBar(
            title = detailTitle(detail.row),
            onBack = onBack,
            trailing = if (onRename != null || actions.isNotEmpty()) {
                {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onRename != null) {
                            ChunkyIconButton(
                                icon = Icons.Rounded.Edit,
                                contentDescription = "Rename ride",
                                onClick = { renaming = true },
                            )
                        }
                        if (actions.isNotEmpty()) {
                            ChunkyIconButton(
                                icon = Icons.Rounded.Share,
                                contentDescription = "Share",
                                onClick = { shareMenu = true },
                            )
                        }
                    }
                }
            } else {
                null
            },
        )
        (video as? VideoExportState.Working)?.takeIf { it.rideId == detail.row.id }?.let { w ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Making video · ${w.step}${w.fraction?.let { " ${(it * 100).toInt()}%" }.orEmpty()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Lime,
                    modifier = Modifier.clickable { VideoExportService.cancel(context) }.padding(8.dp),
                )
            }
        }
        if (shareMenu) {
            ShareMenu(actions, onDismiss = { shareMenu = false })
        }
        if (shareCard) {
            ShareScreen(ShareSubject.Ride(detail.row.id), onDismiss = { shareCard = false })
        }
        if (renaming && onRename != null) {
            AlertDialog(
                onDismissRequest = { renaming = false },
                containerColor = Surface,
                title = { Text("Name this ride", color = TextPrimary) },
                text = {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(30) },
                        placeholder = { Text(formatWhen(detail.row.startTs)) },
                        singleLine = true,
                        shape = ShapeMd,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = Lime,
                            unfocusedBorderColor = GridStroke,
                            cursorColor = Lime,
                            focusedPlaceholderColor = TextDisabled,
                            unfocusedPlaceholderColor = TextDisabled,
                        ),
                    )
                },
                confirmButton = {
                    ChunkyButton(
                        text = "Save",
                        onClick = {
                            onRename(draft)
                            renaming = false
                        },
                        style = ChunkyStyle.Lime,
                        compact = true,
                    )
                },
                dismissButton = {
                    ChunkyButton(
                        text = "Cancel",
                        onClick = { renaming = false },
                        style = ChunkyStyle.Outline,
                        compact = true,
                    )
                },
            )
        }
        if (detail.canSplit && onSplit != null) {
            ChunkyButton(text = "Split back", onClick = onSplit, style = ChunkyStyle.Neutral)
        }
        RideReplay(
            timeline = detail.timeline,
            track = detail.track,
            seekTs = seekTs,
            seekNonce = seekNonce,
        )
        StatTriple(
            headlineLabel = "Max",
            headlineValue = max,
            columns = listOf(
                StatTripleColumn(formatKm(detail.row.km), "Distance", StatTone.Plain),
                StatTripleColumn(formatClock(detail.row.movingSec), "Moving", StatTone.Plain),
                StatTripleColumn(avg, "Avg", StatTone.Plain),
            ),
        )
        if (detail.overnight) {
            Text(
                "Continued overnight. Counted on the day it started.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        detail.score?.let { RideScoreCard(it) }
        UsualCard(detail.usual, detail.cost)
        SpeedElevationChart(detail.track) { seek(it) }
        OdoCard(detail)
        if (detail.insights.isNotEmpty()) {
            SectionLabel(text = "What stood out")
            Text(
                "Tap a note to jump the replay.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            detail.insights.forEach { line -> InsightCard(line, onJump = { jump(it) }) }
        }
        if (detail.bands.isNotEmpty()) {
            SectionCard(header = { SectionLabel(text = "Speed bands") }) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Tap a band to jump to its fastest sample.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Histogram(detail.bands) { share ->
                        TrackInsights.peakInBand(detail.speeds, share.band)?.let { jump(it.first) }
                    }
                }
            }
        }
        if (stops.isNotEmpty()) {
            SectionCard(header = { SectionLabel(text = "Stops") }) {
                Column(Modifier.padding(top = 8.dp)) {
                    stops.forEachIndexed { index, event ->
                        ListRow(
                            title = stopClock(event.ts),
                            subtitle = stopDuration(event.durationSec),
                            showDivider = index < stops.lastIndex,
                            onClick = { jump(event.ts) },
                        )
                    }
                }
            }
        }
        detail.parked?.let { parked ->
            SectionCard {
                ListRow(
                    title = "Where's the bike",
                    subtitle = String.format("%.5f, %.5f · tap for maps", parked.lat, parked.lng),
                    leading = { ListRowIcon(Icons.Rounded.LocationOn) },
                    trailing = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = TextSecondary,
                        )
                    },
                    showDivider = false,
                    onClick = { openParkedMaps(context, parked) },
                )
            }
        }
        DetailFuel(detail)
        actions.forEach { action ->
            ChunkyButton(text = action.label, onClick = action.run, style = ChunkyStyle.Outline)
        }
    }
}

@Composable
private fun OdoCard(detail: RideDetail) {
    val start = detail.odoStartKm
    val end = detail.odoEndKm
    if (detail.gpsOnly || (start == null && end == null)) return
    val bikeKm = if (start != null && end != null) (end - start).toDouble() else null
    val gpsKm = detail.gpsDistanceM / 1000.0
    val tripStart = detail.tripAStartKm
    val tripEnd = detail.tripAKm
    val bars = barsRange(detail.fuelBarsStart, detail.fuelBarsEnd)
    SectionCard(header = { SectionLabel(text = "Odometer") }) {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (start != null && end != null) {
                Text(
                    "%,d → %,d km".format(start, end),
                    style = NumberMedium,
                    color = TextPrimary,
                )
            }
            if (bikeKm != null && gpsKm > 0) {
                // The odo counts whole km, so on short rides Δ is rounding, not calibration.
                val delta = (bikeKm - gpsKm) / gpsKm * 100
                val deltaText = if (gpsKm >= MIN_DELTA_KM) " · Δ %+.1f%%".format(delta) else ""
                Text(
                    "bike %.0f km · GPS %.1f km".format(bikeKm, gpsKm) + deltaText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            if (tripStart != null && tripEnd != null) {
                Text(
                    "Trip A %.1f → %.1f km".format(tripStart, tripEnd),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            if (bars != null) {
                Text(bars, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
    }
}

private fun detailTitle(row: RideRow): String {
    val name = row.name?.takeIf { it.isNotBlank() } ?: return formatWhen(row.startTs)
    val date = DateTimeFormatter.ofPattern("EEE d MMM")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(row.startTs))
    return "$date · $name"
}

private fun stopClock(ts: Long): String =
    DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(ts))

private fun stopDuration(sec: Int?): String {
    val seconds = sec ?: return ""
    val minutes = seconds / 60
    return if (minutes >= 1) "$minutes min" else "${seconds}s"
}

private fun barsRange(start: Int?, end: Int?): String? = when {
    start != null && end != null -> "$start → $end bars"
    end != null -> "$end bars"
    start != null -> "$start bars"
    else -> null
}

@Preview(showBackground = true, backgroundColor = 0xFF171717)
@Composable
private fun RideDetailPanePreview() {
    val start = 1_700_000_000_000L
    val track = listOf(
        GpsFix(start, 12.97, 77.59),
        GpsFix(start + 120_000, 12.99, 77.61),
    )
    val timeline = RideTimeline.build(
        track = track,
        startTs = start,
        endTs = track.last().ts,
        odoStartKm = 12340,
        odoGpsFactor = 1.02,
        stops = listOf(RideStop(start + 30_000, 90)),
    )
    BumblebeeTheme {
        RideDetailPane(
            detail = RideDetail(
                row = RideRow(
                    id = 1,
                    startTs = start,
                    endTs = track.last().ts,
                    km = 47.0,
                    movingSec = 2400,
                    idleSec = 90,
                    maxSpeedKmh = 72,
                    avgMovingSpeedKmh = 48.0,
                    headline = "A clear run",
                    name = "Morning ride",
                ),
                insights = emptyList(),
                bands = emptyList(),
                fuelBarsStart = 6,
                fuelBarsEnd = 4,
                odoEndKm = 12387,
                odoStartKm = 12340,
                timeline = timeline,
                events = timeline.events,
                parked = track.last(),
                track = track,
                tripAStartKm = 112.4,
                tripAKm = 159.4,
                gpsDistanceM = 46_200.0,
                shareText = "Morning ride",
            ),
            onBack = {},
        )
    }
}

@Composable
private fun DetailFuel(detail: RideDetail) {
    val low = detail.fuelBarsEnd != null && detail.fuelBarsEnd <= 1
    SectionCard(header = { SectionLabel(text = "Fuel") }) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            detail.fuelBarsEnd?.let {
                FuelFact("Bars", "$it/6", if (low) Warning else TextPrimary)
            }
            detail.fuelBarsStart?.let { FuelFact("Started", "$it/6", TextPrimary) }
            detail.tripAKm?.let { FuelFact("Trip A", String.format("%.1f km", it), TextPrimary) }
            detail.tripBKm?.let { FuelFact("Trip B", String.format("%.1f km", it), TextPrimary) }
            (detail.tankKmPerL ?: detail.bikeEconKml)?.let {
                val label = if (detail.tankKmPerL != null) "est. tank km/L" else "Cluster km/L"
                FuelFact(label, String.format("%.1f", it), TextPrimary)
            }
        }
    }
    detail.litresEst?.let {
        Text(
            String.format(
                "est. %.1f L used this ride — %s. Not a pump measurement.",
                it,
                if (detail.litresFromFills) "from your fill km/L" else "from the cluster km/L",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
    detail.rupeesPerKm?.let {
        Text(
            "Running cost about ₹${"%.2f".format(it)}/km from priced fills.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun FuelFact(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor)
    }
}

/** One way to share a ride. The header menu and the buttons at the bottom list the same ones. */
private class ShareAction(val label: String, val hint: String, val icon: ImageVector, val run: () -> Unit)

private fun shareActions(detail: RideDetail, context: Context, openCard: () -> Unit, videoBusy: Boolean): List<ShareAction> = buildList {
    if (detail.row.endTs != null) add(ShareAction("Share card", "Pick a design and share an image", Icons.Rounded.Image, openCard))
    if (detail.row.endTs != null && detail.track.size >= 2) {
        add(
            ShareAction(
                "Share video (beta)",
                if (videoBusy) "A video is being made; it'll open when ready" else "A 3D flyover of the route, made on this phone",
                Icons.Rounded.Movie,
            ) {
                if (!videoBusy) {
                    VideoExportService.start(context, detail.row.id)
                    android.widget.Toast.makeText(context, "Making your ride video. It opens when ready.", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
    if (detail.shareText.isNotBlank()) {
        add(ShareAction("Share recap", "A short text summary", Icons.AutoMirrored.Rounded.Notes) { shareRide(context, detail.shareText, null, detail.row.id) })
        detail.gpx?.let { gpx ->
            add(ShareAction("Share GPX", "The track, for maps and Strava", Icons.Rounded.Route) { shareRide(context, detail.shareText, gpx, detail.row.id) })
        }
        detail.csv?.let { csv ->
            add(ShareAction("Share CSV", "Raw ride data", Icons.Rounded.TableChart) { shareCsv(context, csv, detail.row.id) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareMenu(actions: List<ShareAction>, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = ShapeSheet,
        containerColor = Surface,
        contentColor = TextPrimary,
        tonalElevation = 0.dp,
        scrimColor = Scrim,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary) },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                "SHARE",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )
            actions.forEachIndexed { i, action ->
                ListRow(
                    title = action.label,
                    subtitle = action.hint,
                    onClick = {
                        onDismiss()
                        action.run()
                    },
                    showDivider = i < actions.lastIndex,
                    leading = { ListRowIcon(action.icon) },
                    trailing = { ListRowChevron() },
                )
            }
        }
    }
}

@Composable
internal fun Histogram(bands: List<BandShare>, onSelect: ((BandShare) -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        bands.forEach { band ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onSelect != null) Modifier.clickable { onSelect(band) } else Modifier),
            ) {
                Text(band.label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.width(56.dp))
                Box(
                    Modifier.weight(1f).height(10.dp).clip(ShapePill).background(SurfaceRaised),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(band.fraction.coerceIn(0f, 1f))
                            .background(bandColor(band.band)),
                    )
                }
                Text(
                    "${(band.fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    modifier = Modifier.width(36.dp),
                )
            }
        }
    }
}

fun formatClock(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatKm(km: Double): String = if (km <= 0) "—" else String.format("%.1f km", km)

internal fun formatWhen(ts: Long): String {
    val dt = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern("EEE d MMM · h:mm a").format(dt)
}

fun shareRide(context: Context, recap: String, gpx: String?, rideId: Long) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Bumblebee ride")
        putExtra(Intent.EXTRA_TEXT, recap)
    }
    if (gpx != null) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "ride-$rideId.gpx")
        file.writeText(gpx)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        send.type = "application/gpx+xml"
        send.putExtra(Intent.EXTRA_STREAM, uri)
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri("ride.gpx", uri)
    }
    context.startActivity(Intent.createChooser(send, "Share ride"))
}

fun shareCsv(context: Context, csv: String, rideId: Long) {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "ride-$rideId.csv")
    file.writeText(csv)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, "Bumblebee ride $rideId")
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("ride.csv", uri)
    }
    context.startActivity(Intent.createChooser(send, "Share CSV"))
}

/** Below this GPS distance the whole-km odo makes the bike-vs-GPS Δ meaningless. */
private const val MIN_DELTA_KM = 20.0
