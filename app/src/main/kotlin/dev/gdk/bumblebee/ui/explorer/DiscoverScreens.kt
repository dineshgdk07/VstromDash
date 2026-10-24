package dev.gdk.bumblebee.ui.explorer

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.ride.Badge
import dev.gdk.bumblebee.ride.Challenge
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.PersonalRecord
import dev.gdk.bumblebee.ride.RideFact
import dev.gdk.bumblebee.ride.RideHistory
import dev.gdk.bumblebee.ride.RidingDna
import dev.gdk.bumblebee.ride.ShareMetrics
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyIconButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.EmptyState
import dev.gdk.bumblebee.ui.components.ForgeTopBar
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.ride.replay.MapLeg
import dev.gdk.bumblebee.ui.ride.replay.RideMapCanvas
import dev.gdk.bumblebee.ui.sharecard.DnaCard
import dev.gdk.bumblebee.ui.sharecard.RidingDnaPainter
import dev.gdk.bumblebee.ui.theme.BlockCoral
import dev.gdk.bumblebee.ui.theme.BlockLavender
import dev.gdk.bumblebee.ui.theme.BlockMint
import dev.gdk.bumblebee.ui.theme.BlockYellow
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.NumberMedium
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.ShapeLg
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.ShapeXs
import dev.gdk.bumblebee.ui.theme.Surface
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.TextDisabled
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The Discover screens, opened from the strip on the Explorer tab. */
enum class Discover(val route: String, val label: String, val icon: ImageVector, val tint: Color) {
    RECORDS("explorer/records", "Records", Icons.Rounded.EmojiEvents, BlockYellow),
    YEAR("explorer/heat", "Year", Icons.Rounded.CalendarMonth, Lime),
    TERRITORY("explorer/territory", "Territory", Icons.Rounded.Map, BlockMint),
    BADGES("explorer/badges", "Badges", Icons.Rounded.MilitaryTech, BlockCoral),
    DNA("explorer/dna", "Riding DNA", Icons.Rounded.Fingerprint, BlockLavender),
}

/** One row of small tiles, so the Explorer tab stays uncluttered. */
@Composable
internal fun DiscoverStrip(onOpen: (Discover) -> Unit, onYearRecap: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(text = "Discover")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StripTile("Year recap", Icons.Rounded.AutoAwesome, BlockLavender, onYearRecap)
            Discover.entries.forEach { StripTile(it.label, it.icon, it.tint) { onOpen(it) } }
        }
    }
}

@Composable
private fun StripTile(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .width(92.dp)
            .clip(ShapeMd)
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(40.dp).background(tint.copy(alpha = 0.18f), ShapeMd), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextPrimary, maxLines = 1)
    }
}

@Composable
private fun discoverModel(): DiscoverViewModel =
    viewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)

@Composable
private fun DiscoverPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ForgeTopBar(title = title, onBack = onBack)
        content()
    }
}

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)

// ---- records ----

@Composable
fun RecordsScreen(onBack: () -> Unit, onOpenRide: (Long) -> Unit) {
    val state by discoverModel().ui.collectAsStateWithLifecycle()
    DiscoverPage("Personal records", onBack) {
        if (state.loaded && state.records.isEmpty()) {
            EmptyState(headline = "No records yet", subtitle = "Your first ride sets them all.", primaryLabel = "Back", onPrimary = onBack)
        }
        state.records.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { RecordTile(it, Modifier.weight(1f), onOpenRide) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RecordTile(record: PersonalRecord, modifier: Modifier, onOpenRide: (Long) -> Unit) {
    val id = record.rideId
    Column(
        modifier
            .clip(ShapeLg)
            .background(Surface)
            .then(if (id != null) Modifier.clickable { onOpenRide(id) } else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = BlockYellow, modifier = Modifier.size(20.dp))
        Text(record.title.uppercase(Locale.ENGLISH), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(record.value, style = NumberMedium, color = TextPrimary, maxLines = 1)
        Text(record.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ---- year ----

/** The year as a grid of days, shaded by km, a column per week. */
@Composable
fun YearHeatScreen(onBack: () -> Unit) {
    val model = discoverModel()
    val state by model.ui.collectAsStateWithLifecycle()
    var picked by remember(state.year) { mutableStateOf<LocalDate?>(null) }
    DiscoverPage("Year in rides", onBack) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChunkyIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onClick = { model.showYear(state.year - 1) }, contentDescription = "Previous year")
            Text("${state.year}", style = MaterialTheme.typography.headlineSmall, color = TextPrimary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            ChunkyIconButton(
                Icons.AutoMirrored.Rounded.ArrowForward,
                onClick = { model.showYear(state.year + 1) },
                contentDescription = "Next year",
                enabled = state.year < LocalDate.now().year,
            )
        }
        val heat = state.heat
        val total = heat.values.sum()
        val streak = RideHistory.longestStreak(heat.keys).second
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            YearStat(String.format(Locale.US, "%,.0f", total), "km", Modifier.weight(1f))
            YearStat("${heat.size}", "riding days", Modifier.weight(1f))
            YearStat("$streak", "best streak", Modifier.weight(1f))
        }
        SectionCard {
            HeatGrid(state.year, heat, picked) { picked = if (picked == it) null else it }
            val day = picked
            Text(
                if (day == null) "Tap a day" else "${dayFmt.format(day)} · " + (heat[day]?.let { String.format(Locale.US, "%.0f km", it) } ?: "no ride"),
                style = MaterialTheme.typography.bodyMedium,
                color = if (day == null) TextSecondary else TextPrimary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun YearStat(value: String, label: String, modifier: Modifier) {
    Column(modifier.background(Surface, ShapeMd).padding(12.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun HeatGrid(year: Int, heat: Map<LocalDate, Double>, picked: LocalDate?, onPick: (LocalDate) -> Unit) {
    val first = LocalDate.of(year, 1, 1).with(DayOfWeek.MONDAY).let { if (it.year < year) it else it.minusWeeks(1) }
    val last = LocalDate.of(year, 12, 31)
    val weeks = ((last.toEpochDay() - first.toEpochDay()) / 7 + 1).toInt()
    val peak = heat.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val cell = 13.dp
    val gap = 3.dp
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(gap)) {
        for (w in 0 until weeks) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                val monday = first.plusWeeks(w.toLong())
                Text(
                    if (monday.dayOfMonth <= 7 && monday.year == year) monday.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(1) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.height(16.dp),
                )
                for (d in 0 until 7) {
                    val date = monday.plusDays(d.toLong())
                    val km = heat[date]
                    val color = when {
                        date.year != year -> Color.Transparent
                        km == null -> SurfaceRaised
                        else -> Lime.copy(alpha = 0.25f + 0.75f * (km / peak).toFloat().coerceIn(0f, 1f))
                    }
                    Box(
                        Modifier
                            .size(cell)
                            .clip(ShapeXs)
                            .background(if (date == picked) TextPrimary else color)
                            .clickable(enabled = date.year == year) { onPick(date) },
                    )
                }
            }
        }
    }
}

// ---- territory ----

/** Every road ridden, cleared out of a fog that covers the rest of the map. */
@Composable
fun TerritoryScreen(onBack: () -> Unit, onOpenRide: (Long) -> Unit) {
    val state by discoverModel().ui.collectAsStateWithLifecycle()
    val territory = state.territory
    DiscoverPage("Your territory", onBack) {
        if (state.loaded && (territory == null || territory.cells.isEmpty())) {
            EmptyState(headline = "The map is all fog", subtitle = "Ride with GPS on and the roads you take clear it.", primaryLabel = "Back", onPrimary = onBack)
            return@DiscoverPage
        }
        if (territory == null) return@DiscoverPage
        val tracks = state.tracks.values.filter { it.size >= 2 }
        val bounds = remember(tracks) { tracks.flatten().let { all -> if (all.size <= 4_000) all else all.filterIndexed { i, _ -> i % (all.size / 4_000 + 1) == 0 } } }
        Text(
            "Fog covers what you haven't ridden. Pinch to look around.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        RideMapCanvas(
            fixes = bounds,
            legs = emptyList(),
            pins = emptyList(),
            marker = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.85f).clip(ShapeLg),
            overlay = { project -> drawFog(tracks, project) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            YearStat(String.format(Locale.US, "%,.0f km²", territory.areaKm2), "uncovered", Modifier.weight(1f))
            YearStat(territory.farthestKm?.let { String.format(Locale.US, "%,.0f km", it) } ?: "—", "farthest from home", Modifier.weight(1f))
        }
        val fresh = state.rides
            .filter { (territory.newCells[it.id] ?: 0) >= 4 }
            .sortedByDescending { it.startTs }
            .take(5)
        if (fresh.isNotEmpty()) {
            SectionCard(header = { SectionLabel(text = "Recent new ground") }) {
                Column(Modifier.padding(top = 8.dp)) {
                    fresh.forEach { ride ->
                        val cells = territory.newCells[ride.id] ?: 0
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenRide(ride.id) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(ride.label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(dayFmt.format(dateOf(ride)), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Text(String.format(Locale.US, "+%.1f km²", cells * 0.29), style = MaterialTheme.typography.titleSmall, color = Lime)
                        }
                    }
                }
            }
        }
    }
}

/** A fog layer with every track wiped out of it, then the tracks drawn thin on top. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFog(
    tracks: List<List<GpsFix>>,
    project: (Double, Double) -> Offset,
) {
    val paths = tracks.map { track ->
        Path().apply {
            track.forEachIndexed { i, fix ->
                val p = project(fix.lat, fix.lng)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
        }
    }
    // Big enough to cover the view at any pan and zoom.
    val world = Rect(-20_000f, -20_000f, size.width + 20_000f, size.height + 20_000f)
    drawIntoCanvas { it.saveLayer(world, Paint()) }
    drawRect(Color(0xF00D0D12), topLeft = world.topLeft, size = world.size)
    paths.forEach { drawPath(it, Color.Black, style = Stroke(26.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Clear) }
    drawIntoCanvas { it.restore() }
    paths.forEach { drawPath(it, Lime.copy(alpha = 0.9f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)) }
}

private fun dateOf(ride: RideFact): LocalDate = Instant.ofEpochMilli(ride.startTs).atZone(ZoneId.systemDefault()).toLocalDate()

// ---- badges and challenges ----

@Composable
fun BadgesScreen(onBack: () -> Unit) {
    val state by discoverModel().ui.collectAsStateWithLifecycle()
    DiscoverPage("Badges", onBack) {
        if (state.challenges.isNotEmpty()) {
            SectionLabel(text = "This month's challenges")
            state.challenges.forEach { ChallengeRow(it) }
        }
        val earned = state.badges.count { it.earned }
        SectionLabel(text = "Badges", count = earned)
        state.badges.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { BadgeTile(it, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ChallengeRow(challenge: Challenge) {
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().background(Surface, ShapeMd).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(challenge.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(challenge.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(8.dp).clip(ShapePill).background(SurfaceRaised)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(challenge.fraction.coerceAtLeast(0.02f)).background(if (challenge.done) Lime else BlockYellow, ShapePill))
                }
                Text(
                    "${fmt(challenge.progress)}/${fmt(challenge.target)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        if (challenge.done) {
            Text(
                "DONE",
                style = MaterialTheme.typography.labelLarge,
                color = Lime,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .rotate(-12f)
                    .background(Lime.copy(alpha = 0.12f), ShapeXs)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

private fun fmt(v: Double) = String.format(Locale.US, "%.0f", v)

@Composable
private fun BadgeTile(badge: Badge, modifier: Modifier) {
    val on = badge.earned
    Column(
        modifier
            .clip(ShapeLg)
            .background(if (on) BlockYellow.copy(alpha = 0.14f) else Surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.MilitaryTech, contentDescription = null, tint = if (on) BlockYellow else TextDisabled, modifier = Modifier.size(28.dp))
        Text(badge.title, style = MaterialTheme.typography.titleSmall, color = if (on) TextPrimary else TextSecondary)
        Text(badge.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (on) {
            Text(badge.earnedOn?.let { "Earned ${DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).format(it)}" }.orEmpty(), style = MaterialTheme.typography.labelSmall, color = BlockYellow)
        } else {
            Box(Modifier.fillMaxWidth().height(6.dp).clip(ShapePill).background(SurfaceRaised)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(badge.progress.coerceAtLeast(0.02f)).background(TextSecondary, ShapePill))
            }
        }
    }
}

// ---- riding DNA ----

@Composable
fun DnaScreen(onBack: () -> Unit) {
    val state by discoverModel().ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    DiscoverPage("Riding DNA", onBack) {
        val dna = state.dna
        if (dna == null) {
            if (state.loaded) {
                EmptyState(headline = "Not enough rides yet", subtitle = "Five rides in, your riding personality shows.", primaryLabel = "Back", onPrimary = onBack)
            }
            return@DiscoverPage
        }
        Column(
            Modifier.fillMaxWidth().background(BlockLavender, ShapeLg).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("YOU ARE A", style = MaterialTheme.typography.labelMedium, color = OnBlock.copy(alpha = 0.7f))
            Text(dna.persona.uppercase(Locale.ENGLISH), style = MaterialTheme.typography.headlineLarge, color = OnBlock)
            Text(dna.tagline, style = MaterialTheme.typography.bodyLarge, color = OnBlock)
        }
        SectionCard(header = { SectionLabel(text = "Traits") }) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                dna.traits.forEach { trait ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row {
                            Text(trait.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                            Text(trait.caption, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(ShapePill).background(SurfaceRaised)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(trait.value.coerceIn(0.02f, 1f)).background(BlockYellow, ShapePill))
                        }
                    }
                }
            }
        }
        SectionCard(header = { SectionLabel(text = "Signature") }) {
            Column(Modifier.padding(top = 8.dp)) {
                dna.signature.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(k, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
                        Text(v, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    }
                }
            }
        }
        ChunkyButton(text = "Share my DNA", onClick = { scope.launch { shareDna(context, dna) } }, style = ChunkyStyle.Lime)
    }
}

private suspend fun shareDna(context: Context, dna: RidingDna) {
    val file = withContext(Dispatchers.Default) {
        val card = DnaCard(dna.persona, dna.tagline, dna.traits.map { it.name to it.value }, dna.signature)
        val bmp = RidingDnaPainter.paint(context, card)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        File(dir, "riding-dna.png").also { out -> FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "My riding DNA: ${dna.persona}. ${dna.tagline}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("riding-dna.png", uri)
    }
    context.startActivity(Intent.createChooser(send, "Share riding DNA"))
}

// ---- compare ----

private val CompareB = Color(0xFF5EC8FF)

/** Two rides side by side: both tracks on one map, and their numbers row by row. */
@Composable
fun CompareScreen(a: Long, b: Long, onBack: () -> Unit) {
    val model = discoverModel()
    val state by model.ui.collectAsStateWithLifecycle()
    LaunchedEffect(a, b) { model.compare(a, b) }
    DiscoverPage("Compare rides", onBack) {
        val pair = state.compare?.takeIf { setOf(it.a.id, it.b.id) == setOf(a, b) } ?: return@DiscoverPage
        val legs = remember(pair) {
            fun legsOf(track: List<GpsFix>, color: Color) = track.zipWithNext { s, e -> MapLeg(s, e, color) }
            legsOf(pair.trackA, Lime) + legsOf(pair.trackB, CompareB)
        }
        if (legs.isNotEmpty()) {
            RideMapCanvas(
                fixes = pair.trackA + pair.trackB,
                legs = legs,
                pins = emptyList(),
                marker = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(ShapeLg),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CompareHead(pair.a, Lime, Modifier.weight(1f))
            CompareHead(pair.b, CompareB, Modifier.weight(1f))
        }
        SectionCard {
            Column {
                CompareRow("Distance", pair.a.km, pair.b.km, { String.format(Locale.US, "%.1f km", it) }, higherIsBetter = true)
                CompareRow("Moving", pair.a.movingSec.toDouble(), pair.b.movingSec.toDouble(), { ShareMetrics.clock(it.toInt()) }, higherIsBetter = false)
                val avgA = if (pair.a.movingSec > 0) pair.a.km / (pair.a.movingSec / 3600.0) else 0.0
                val avgB = if (pair.b.movingSec > 0) pair.b.km / (pair.b.movingSec / 3600.0) else 0.0
                CompareRow("Avg speed", avgA, avgB, { String.format(Locale.US, "%.0f km/h", it) }, higherIsBetter = true)
                CompareRow("Top speed", pair.a.maxSpeedKmh.toDouble(), pair.b.maxSpeedKmh.toDouble(), { String.format(Locale.US, "%.0f km/h", it) }, higherIsBetter = true)
                if (pair.a.climbM != null || pair.b.climbM != null) {
                    CompareRow("Climb", pair.a.climbM ?: 0.0, pair.b.climbM ?: 0.0, { String.format(Locale.US, "%.0f m", it) }, higherIsBetter = true)
                }
            }
        }
    }
}

@Composable
private fun CompareHead(ride: RideFact, color: Color, modifier: Modifier) {
    Column(modifier.background(Surface, ShapeMd).padding(12.dp)) {
        Box(Modifier.size(width = 24.dp, height = 4.dp).background(color, ShapePill))
        Text(ride.label, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(dayFmt.format(dateOf(ride)), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun CompareRow(label: String, a: Double, b: Double, show: (Double) -> String, higherIsBetter: Boolean) {
    val aWins = if (higherIsBetter) a > b else a < b && a > 0
    val bWins = if (higherIsBetter) b > a else b < a && b > 0
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(show(a), style = MaterialTheme.typography.titleSmall, color = if (aWins) Lime else TextPrimary, modifier = Modifier.weight(1f))
        Text(label.uppercase(Locale.ENGLISH), style = MaterialTheme.typography.labelSmall, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Text(show(b), style = MaterialTheme.typography.titleSmall, color = if (bWins) CompareB else TextPrimary, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}
