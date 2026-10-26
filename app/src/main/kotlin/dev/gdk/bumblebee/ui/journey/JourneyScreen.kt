package dev.gdk.bumblebee.ui.journey

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.BuildConfig
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.journey.NavMode
import dev.gdk.bumblebee.journey.NavSession
import dev.gdk.bumblebee.journey.NavSettings
import dev.gdk.bumblebee.journey.NavState
import dev.gdk.bumblebee.journey.PlaceHit
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.journey.BigMode
import dev.gdk.bumblebee.journey.SavedPlace
import dev.gdk.bumblebee.nav.ArrowKind
import dev.gdk.bumblebee.nav.ArrowSource
import dev.gdk.bumblebee.nav.ArrowTeacher
import dev.gdk.bumblebee.nav.MapsNavSource
import dev.gdk.bumblebee.nav.NotificationCaptureService
import dev.gdk.bumblebee.nav.ParsedNavData
import dev.gdk.bumblebee.nav.formatHudDistance
import dev.gdk.bumblebee.nav.normalizeEta
import dev.gdk.bumblebee.ride.JourneyInsights
import dev.gdk.bumblebee.ride.Tone
import dev.gdk.bumblebee.ride.TripTip
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.ForgeSheet
import dev.gdk.bumblebee.ui.components.ListRow
import dev.gdk.bumblebee.ui.components.ListRowChevron
import dev.gdk.bumblebee.ui.components.ListRowIcon
import dev.gdk.bumblebee.ui.components.PillChip
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.garage.RefreshOnResume
import dev.gdk.bumblebee.ui.theme.BlockMint
import dev.gdk.bumblebee.ui.theme.BlockYellow
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.ShapeLg
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.Stroke
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import dev.gdk.bumblebee.ui.theme.Warning
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Marks a step sent from Cluster check, so the journey log ignores it. */
internal const val TEST_SUMMARY = "test"

@Composable
fun JourneyScreen(model: JourneyViewModel = viewModel()) {
    val context = LocalContext.current
    val app = context.applicationContext as DashApp
    val state by model.ui.collectAsStateWithLifecycle()
    val step by MapsNavSource.parsed.collectAsStateWithLifecycle()
    val connection by app.connection.collectAsStateWithLifecycle()
    val weather by app.weather.snapshot.collectAsStateWithLifecycle()
    val linked = connection is ConnectionState.Ready
    var access by remember { mutableStateOf(notificationAccessGranted(context)) }
    var toCluster by remember { mutableStateOf(app.prefs.mapsNavToCluster) }
    var checkOpen by remember { mutableStateOf(false) }
    var fixOpen by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var navMode by remember { mutableStateOf(NavSettings(context).mode) }
    val session by NavSession.state.collectAsStateWithLifecycle()
    var normalView by remember { mutableStateOf(false) }
    RefreshOnResume(model::refresh)
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                access = notificationAccessGranted(context)
                navMode = NavSettings(context).mode
                toCluster = app.prefs.mapsNavToCluster
            }
        }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // On Android 13+ the switch may be greyed out for a downloaded APK; explain first.
    var accessHelp by remember { mutableStateOf(false) }
    if (accessHelp) dev.gdk.bumblebee.ui.components.NotificationAccessHelp(context) { accessHelp = false }
    fun enableAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (dev.gdk.bumblebee.ui.components.mayRestrictNotificationAccess()) accessHelp = true
        else context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
    fun go(destination: String) {
        model.heading(destination)
        // In the Google and Open modes this app navigates itself; otherwise Google Maps does.
        if (navMode == NavMode.NOTIFICATIONS) openMaps(context, JourneyInsights.navigateUri(destination))
        else NavSession.start(context, PlaceHit(destination, null))
    }

    // Biggest remaining distance seen this trip, for the progress bar.
    var tripKm by remember { mutableStateOf<Double?>(null) }
    val live = step
    LaunchedEffect(live) {
        val km = live?.let { JourneyInsights.km(it.distTotal, it.distTotalUnit) }
        tripKm = when {
            live == null -> null
            km != null && (tripKm ?: 0.0) < km -> km
            else -> tripKm
        }
    }

    val big = live != null && !normalView && (state.bigMode == BigMode.ON || (state.bigMode == BigMode.AUTO && state.darkOrWet))

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Journey", style = MaterialTheme.typography.titleLarge, color = TextPrimary, modifier = Modifier.weight(1f))
            Text(
                "Cluster check",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                modifier = Modifier.clip(ShapePill).clickable { checkOpen = true }.padding(8.dp),
            )
        }
        if (navMode == NavMode.NOTIFICATIONS && (!access || !toCluster)) SetupBanner(access, toCluster, onEnable = ::enableAccess, onCheck = { checkOpen = true })

        session?.let { s -> NavPanel(s, onRetry = { NavSession.start(context, s.destination) }, onEnd = { NavSession.stop() }) }

        if (live != null) {
            if (big) {
                BigGuide(live, onNormal = { normalView = true })
            } else {
                LiveTripCard(live, linked, tripKm, onFix = { fixOpen = true })
                TripTips(live, state, weather?.let { w -> { ts: Long -> w.rainAt(ts) } })
                ShareEta(live, state.sharedEtaMinute, app.prefs.riderName, onShared = model::sharedEta)
                if (normalView && state.bigMode != BigMode.OFF) {
                    Text(
                        "Back to the big view",
                        style = MaterialTheme.typography.labelLarge,
                        color = Lime,
                        modifier = Modifier.clip(ShapePill).clickable { normalView = false }.padding(8.dp),
                    )
                }
            }
        } else {
            state.newPlace?.let { NewPlaceCard(it.name) }
            PlacesCard(
                state.places,
                state.visited.take(6).map { it.name },
                inApp = navMode != NavMode.NOTIFICATIONS,
                onGo = ::go,
                onSearch = { searchOpen = true },
                onAdd = { addOpen = true },
                onRemove = model::removePlace,
            )
            if (state.routes.isNotEmpty()) RoutesCard(state.routes, onOpen = { openMaps(context, it.url) })
            LogCard(state)
            if (state.visited.isNotEmpty()) BeenCard(state.visited)
            PrefsCard(state, onAuto = model::setAutoRecord, onBig = model::setBigMode)
        }
    }

    if (fixOpen && step != null) {
        ArrowFixSheet(
            current = step?.arrow,
            instruction = step?.instruction,
            onPick = {
                ArrowTeacher.teach(context, it)
                fixOpen = false
            },
            onDismiss = { fixOpen = false },
        )
    }
    if (checkOpen) {
        ClusterCheckSheet(
            access = access,
            linked = linked,
            toCluster = toCluster,
            onEnable = ::enableAccess,
            onToggleCluster = {
                toCluster = it
                app.prefs.mapsNavToCluster = it
                if (!it) MapsNavSource.clear()
            },
            onDismiss = { checkOpen = false },
        )
    }
    if (searchOpen) {
        PlaceSearchSheet(
            onPick = { hit ->
                searchOpen = false
                model.heading(hit.label)
                NavSession.start(context, hit)
            },
            onDismiss = { searchOpen = false },
        )
    }
    if (addOpen) {
        AddPlaceSheet(
            onSave = { name, query ->
                model.addPlace(name, query)
                addOpen = false
            },
            onDismiss = { addOpen = false },
        )
    }
}

// ---- Not navigating ----

@Composable
private fun SetupBanner(access: Boolean, toCluster: Boolean, onEnable: () -> Unit, onCheck: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(ShapeLg).background(BlockMint).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (!access) "Let the app see Google Maps" else "Arrows to the bike are off",
            style = MaterialTheme.typography.titleMedium,
            color = OnBlock,
        )
        Text(
            if (!access) "Turn on notification access so Maps' directions can reach the cluster. Nothing else is read." else "Turn it on in Cluster check to send Maps' turns to the bike.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnBlock.copy(alpha = 0.8f),
        )
        ChunkyButton(text = if (!access) "Turn on access" else "Open cluster check", onClick = if (!access) onEnable else onCheck, style = ChunkyStyle.Outline)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlacesCard(
    saved: List<SavedPlace>,
    frequent: List<String>,
    inApp: Boolean,
    onGo: (String) -> Unit,
    onSearch: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (SavedPlace) -> Unit,
) {
    SectionCard(header = { SectionLabel(text = "Where to?") }) {
        Text(
            if (inApp) "Search or tap a place and this app guides you, turn by turn on the cluster."
            else "Tap a place: Google Maps opens already navigating, two-wheeler mode, and the cluster follows.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )
        if (inApp) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(ShapeMd).background(SurfaceRaised).clickable(onClick = onSearch).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = Lime, modifier = Modifier.size(20.dp))
                Text("Search a place or address", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.padding(start = 10.dp))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            saved.forEach { p -> PlaceChip(p.name, onClick = { onGo(p.query) }, onRemove = { onRemove(p) }) }
            frequent.filter { f -> saved.none { it.query.equals(f, ignoreCase = true) } }.forEach { name ->
                PlaceChip(name, onClick = { onGo(name) })
            }
            Row(
                Modifier.clip(ShapePill).border(1.dp, Stroke, ShapePill).clickable(onClick = onAdd).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = Lime, modifier = Modifier.size(16.dp))
                Text(if (saved.isEmpty()) "Add Home, Work…" else "Add", style = MaterialTheme.typography.labelLarge, color = Lime)
            }
        }
    }
}

@Composable
private fun PlaceChip(name: String, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    Row(
        Modifier.clip(ShapePill).background(SurfaceRaised).clickable(onClick = onClick).padding(start = 12.dp, end = if (onRemove != null) 4.dp else 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Place, contentDescription = null, tint = Lime, modifier = Modifier.size(16.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, color = TextPrimary, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
        if (onRemove != null) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Remove $name",
                tint = TextSecondary,
                modifier = Modifier.padding(start = 4.dp).size(20.dp).clip(ShapePill).clickable(onClick = onRemove).padding(3.dp),
            )
        }
    }
}

@Composable
private fun RoutesCard(routes: List<RouteAgain>, onOpen: (RouteAgain) -> Unit) {
    SectionCard(header = { SectionLabel(text = "Ride a route again") }) {
        Column(Modifier.padding(top = 6.dp)) {
            routes.forEachIndexed { i, r ->
                ListRow(
                    title = r.label,
                    subtitle = "${"%.0f".format(r.km)} km · ${dayLabel(r.dateTs)} · follows your track",
                    onClick = { onOpen(r) },
                    showDivider = i < routes.lastIndex,
                    leading = { ListRowIcon(Icons.Rounded.Route) },
                    trailing = { ListRowChevron() },
                )
            }
        }
    }
}

@Composable
private fun LogCard(state: JourneyUi) {
    val stats = state.stats ?: return
    SectionCard(header = { SectionLabel(text = "Journey log") }) {
        Text(
            stats.avgBeatMin?.let {
                val m = it.roundToInt()
                when {
                    m > 0 -> "You beat Maps by $m min on average"
                    m < 0 -> "You arrive ${-m} min after Maps' estimate on average"
                    else -> "You arrive right on Maps' estimate"
                }
            } ?: "${stats.trips} navigated trip${if (stats.trips == 1) "" else "s"}",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "${stats.trips} trips · ${stats.arrived} arrived · ${"%,.0f".format(stats.km)} km guided",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        Column(Modifier.padding(top = 6.dp)) {
            state.log.take(5).forEachIndexed { i, e ->
                val diff = if (e.arrived && e.endTs != null && e.firstEtaTs != null) ((e.firstEtaTs!! - e.endTs!!) / 60_000.0).roundToInt() else null
                ListRow(
                    title = e.destination ?: "Trip on ${dayLabel(e.startTs)}",
                    subtitle = listOfNotNull(
                        dayLabel(e.startTs),
                        e.firstTotalKm?.let { "%.0f km".format(it) },
                        if (!e.arrived) "stopped early" else null,
                    ).joinToString(" · "),
                    showDivider = i < minOf(4, state.log.lastIndex),
                    trailing = {
                        diff?.let {
                            Text(
                                if (it >= 0) "−$it min" else "+${-it} min",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (it >= 0) Lime else Warning,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun NewPlaceCard(name: String) {
    Column(Modifier.fillMaxWidth().clip(ShapeLg).background(BlockYellow).padding(16.dp)) {
        Text("FIRST TIME HERE", style = MaterialTheme.typography.labelSmall, color = OnBlock.copy(alpha = 0.7f))
        Text(name, style = MaterialTheme.typography.headlineSmall, color = OnBlock)
        Text("A new place on your map.", style = MaterialTheme.typography.bodyMedium, color = OnBlock.copy(alpha = 0.8f))
    }
}

@Composable
private fun BeenCard(visited: List<dev.gdk.bumblebee.ride.VisitedPlace>) {
    SectionCard(header = { SectionLabel(text = "Places you've been", count = visited.size) }) {
        Column(Modifier.padding(top = 6.dp)) {
            visited.take(6).forEachIndexed { i, p ->
                ListRow(
                    title = p.name,
                    subtitle = "First ${dayLabel(p.firstTs)}" + if (p.visits > 1) " · last ${dayLabel(p.lastTs)}" else "",
                    showDivider = i < minOf(5, visited.lastIndex),
                    trailing = { Text(if (p.visits == 1) "once" else "${p.visits}×", style = MaterialTheme.typography.labelLarge, color = TextPrimary) },
                )
            }
        }
    }
}

@Composable
private fun PrefsCard(state: JourneyUi, onAuto: (Boolean) -> Unit, onBig: (BigMode) -> Unit) {
    SectionCard(header = { SectionLabel(text = "While navigating") }) {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Record when Maps starts", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Text("Starts a ride if nothing is recording yet", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Switch(checked = state.autoRecord, onCheckedChange = onAuto)
            }
            Text("Big view for night and rain", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(BigMode.AUTO to "Auto", BigMode.ON to "Always", BigMode.OFF to "Never").forEach { (m, label) ->
                    PillChip(text = label, selected = state.bigMode == m, onClick = { onBig(m) })
                }
            }
            Text(
                "Auto switches to a huge turn arrow after sunset or when it's raining.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

// ---- Navigating ----

/** The app's own navigation: the map (when allowed), status, and the way out. */
@Composable
private fun NavPanel(s: NavState, onRetry: () -> Unit, onEnd: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val mapBox = Modifier.fillMaxWidth().height(260.dp).clip(ShapeLg)
        when {
            s.route == null -> Unit
            s.mode == NavMode.OPEN -> OsmRouteMap(s, mapBox)
            s.mode == NavMode.GOOGLE && BuildConfig.HAS_MAPS_KEY -> GoogleRouteMap(s, mapBox)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("To ${s.destination.label}", style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        s.error != null -> s.error
                        s.arrived -> "You've arrived"
                        s.rerouting && s.route == null -> "Planning the route…"
                        s.rerouting -> "Off the route. Finding a new one…"
                        else -> s.progress?.nextStep?.let { "Then: ${it.arrow.label.lowercase()}" } ?: "${s.mode.label} navigation"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (s.error != null) Danger else TextSecondary,
                )
            }
            if (s.error != null) {
                Text("Retry", style = MaterialTheme.typography.labelLarge, color = Lime, modifier = Modifier.clip(ShapePill).clickable(onClick = onRetry).padding(8.dp))
            }
            Text("End", style = MaterialTheme.typography.labelLarge, color = Danger, modifier = Modifier.clip(ShapePill).clickable(onClick = onEnd).padding(8.dp))
        }
    }
}

@Composable
private fun LiveTripCard(step: ParsedNavData, linked: Boolean, tripKm: Double?, onFix: () -> Unit) {
    val arrow = step.arrow ?: ArrowKind.STRAIGHT
    val remaining = JourneyInsights.km(step.distTotal, step.distTotalUnit)
    val eta = JourneyInsights.parseEta(step.eta)
    Column(
        Modifier.fillMaxWidth().clip(ShapeLg).background(SurfaceRaised).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(88.dp).clip(ShapeMd).background(Lime), contentAlignment = Alignment.Center) {
                ArrowGlyph(arrow, OnBlock, Modifier.size(68.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(formatHudDistance(step.distNext, step.distNextUnit), style = MaterialTheme.typography.displaySmall, color = TextPrimary)
                Text(
                    step.instruction ?: arrow.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mini("ETA", eta?.let { JourneyInsights.clockLabel(it) } ?: "—", Modifier.weight(1f))
            Mini("Left", remaining?.let { if (it < 10) "%.1f km".format(it) else "%.0f km".format(it) } ?: "—", Modifier.weight(1f))
        }
        if (tripKm != null && remaining != null && tripKm > 0) {
            val done = (1 - remaining / tripKm).toFloat().coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(ShapePill).background(Stroke)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(done.coerceAtLeast(0.02f)).background(Lime, ShapePill))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(ShapePill).background(if (linked) Lime else TextSecondary))
            Text(
                if (linked) "On the cluster · ${arrow.label.lowercase()}" + if (step.arrowSource == ArrowSource.TAUGHT) " (your fix)" else ""
                else "Bike not linked · showing here only",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
            Text(
                "Wrong arrow?",
                style = MaterialTheme.typography.labelLarge,
                color = Lime,
                modifier = Modifier.clip(ShapePill).clickable(onClick = onFix).padding(8.dp),
            )
        }
    }
}

@Composable
private fun Mini(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(ShapeMd).background(Color.White.copy(alpha = 0.05f)).padding(10.dp)) {
        Text(label.uppercase(Locale.ENGLISH), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
    }
}

/** Night and rain: just the turn, huge, high contrast. */
@Composable
private fun BigGuide(step: ParsedNavData, onNormal: () -> Unit) {
    val arrow = step.arrow ?: ArrowKind.STRAIGHT
    Column(
        Modifier.fillMaxWidth().clip(ShapeLg).background(Color.Black).border(2.dp, Lime, ShapeLg).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArrowGlyph(arrow, Lime, Modifier.fillMaxWidth(0.6f).aspectRatio(1f))
        Text(
            formatHudDistance(step.distNext, step.distNextUnit),
            fontSize = 64.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
        )
        Text(
            step.instruction ?: arrow.label,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "Normal view",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
            modifier = Modifier.clip(ShapePill).clickable(onClick = onNormal).padding(8.dp),
        )
    }
}

@Composable
private fun TripTips(step: ParsedNavData, state: JourneyUi, rainAt: ((Long) -> Int?)?) {
    val zone = ZoneId.systemDefault()
    val now = LocalDateTime.now(zone)
    val remaining = JourneyInsights.km(step.distTotal, step.distTotalUnit)
    val arrival = JourneyInsights.parseEta(step.eta)?.let { JourneyInsights.arrivalAt(now, it) }
    val tips = listOfNotNull(JourneyInsights.fuelCheck(remaining, state.rangeKm, state.pumps)) +
        JourneyInsights.arrivalCheck(
            now,
            arrival,
            state.sunset,
            arrival?.let { a -> rainAt?.invoke(a.atZone(zone).toInstant().toEpochMilli()) },
        )
    if (tips.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tips.forEach { TipRow(it) }
    }
}

@Composable
private fun TipRow(tip: TripTip) {
    val tone = when (tip.tone) {
        Tone.GOOD -> Lime
        Tone.WARN -> Warning
        Tone.BAD -> Danger
    }
    Row(
        Modifier.fillMaxWidth().clip(ShapeMd).background(tone.copy(alpha = 0.12f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(ShapePill).background(tone))
        Text(tip.text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun ShareEta(step: ParsedNavData, sharedMinute: Int?, name: String, onShared: (Int) -> Unit) {
    val context = LocalContext.current
    val eta = JourneyInsights.parseEta(step.eta)
    val remaining = JourneyInsights.km(step.distTotal, step.distTotalUnit)
    val shared = sharedMinute?.let { java.time.LocalTime.of(it / 60, it % 60) }
    val slip = JourneyInsights.etaSlip(shared, eta)
    fun share() {
        val text = JourneyInsights.shareText(eta, remaining, name.takeIf { it != "RIDER" })
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "Share ETA"))
        eta?.let { onShared(it.hour * 60 + it.minute) }
    }
    if (shared != null && slip != null && kotlin.math.abs(slip) >= 5) {
        Row(
            Modifier.fillMaxWidth().clip(ShapeMd).background(Warning.copy(alpha = 0.12f)).clickable { share() }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "ETA ${if (slip > 0) "slipped $slip" else "moved up ${-slip}"} min since you shared it. Send an update?",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text("Update", style = MaterialTheme.typography.labelLarge, color = Lime)
        }
    } else {
        ChunkyButton(text = if (shared == null) "Share my ETA" else "Share ETA again", onClick = { share() }, style = ChunkyStyle.Outline)
    }
}

// ---- Sheets ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArrowFixSheet(current: ArrowKind?, instruction: String?, onPick: (ArrowKind) -> Unit, onDismiss: () -> Unit) {
    ForgeSheet(title = "Which arrow is it?", onDismiss = onDismiss, primaryLabel = "Cancel", onPrimary = onDismiss) {
        Text(
            "Maps said: \"${instruction ?: "—"}\". Pick the turn you actually see. The bike updates now and this turn is remembered.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        FlowRow(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ArrowKind.entries.forEach { k -> ArrowTile(k, selected = k == current, onClick = { onPick(k) }) }
        }
    }
}

@Composable
private fun ArrowTile(kind: ArrowKind, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .size(width = 76.dp, height = 84.dp)
            .clip(ShapeMd)
            .background(if (selected) Lime.copy(alpha = 0.18f) else SurfaceRaised)
            .border(if (selected) 2.dp else 0.dp, if (selected) Lime else Color.Transparent, ShapeMd)
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArrowGlyph(kind, TextPrimary, Modifier.size(48.dp))
        Text(kind.label, style = MaterialTheme.typography.labelSmall, color = TextPrimary, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClusterCheckSheet(
    access: Boolean,
    linked: Boolean,
    toCluster: Boolean,
    onEnable: () -> Unit,
    onToggleCluster: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val log by ArrowTeacher.recent.collectAsStateWithLifecycle()
    var taught by remember { mutableStateOf(ArrowTeacher.memory(context).size) }
    ForgeSheet(title = "Cluster check", onDismiss = onDismiss, primaryLabel = "Done", onPrimary = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CheckLine("Reading Google Maps", access, if (access) "On" else "Off · tap to turn on", onClick = if (access) null else onEnable)
            CheckLine("Bike", linked, if (linked) "Linked" else "Not linked")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Send Maps' turns to the bike", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                Switch(checked = toCluster, onCheckedChange = onToggleCluster)
            }
            SectionLabel(text = "Test an arrow")
            Text(
                "Tap one and look at the cluster. If it shows a different arrow, tell us in an issue with the name.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ArrowKind.entries.forEach { k ->
                    ArrowTile(k, selected = false, onClick = { MapsNavSource.update(testStep(k)) })
                }
            }
            ChunkyButton(text = "Clear the cluster", onClick = { MapsNavSource.clear() }, style = ChunkyStyle.Outline)
            if (taught > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$taught arrow fix${if (taught == 1) "" else "es"} remembered", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                    Text(
                        "Forget",
                        style = MaterialTheme.typography.labelLarge,
                        color = Danger,
                        modifier = Modifier.clip(ShapePill).clickable {
                            ArrowTeacher.forgetAll(context)
                            taught = 0
                        }.padding(8.dp),
                    )
                }
            }
            if (log.isNotEmpty()) {
                SectionLabel(text = "What Maps sent lately")
                log.take(8).forEach { l ->
                    Column(Modifier.fillMaxWidth().clip(ShapeMd).background(SurfaceRaised).padding(10.dp)) {
                        Text(listOfNotNull(l.title, l.text).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = TextPrimary, maxLines = 2)
                        Text(
                            "→ ${l.arrow?.label ?: "—"} (" + when (l.source) {
                                ArrowSource.TAUGHT -> "your fix"
                                ArrowSource.TEXT -> "from wording"
                                ArrowSource.DEFAULT -> "guess"
                            } + ")",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckLine(label: String, ok: Boolean, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(ShapePill).background(if (ok) Lime else Danger))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.padding(start = 10.dp).weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = if (ok) Lime else Danger)
    }
}

@Composable
private fun AddPlaceSheet(onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    ForgeSheet(
        title = "Add a place",
        onDismiss = onDismiss,
        primaryLabel = "Save",
        primaryEnabled = name.isNotBlank() && query.isNotBlank(),
        onPrimary = { onSave(name, query) },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Home", "Work", "Mechanic").forEach { n -> PillChip(text = n, selected = name == n, onClick = { name = n }) }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Address or place, as you'd type it in Maps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}

// ---- Helpers ----

private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

private fun dayLabel(ts: Long): String = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate().format(dayFmt)

private fun testStep(kind: ArrowKind) = ParsedNavData(
    maneuverId = kind.clusterByte,
    distNext = "0200",
    distNextUnit = "M",
    eta = normalizeEta("5 min", twelveHour = true),
    distTotal = "01.2",
    distTotalUnit = "K",
    streetName = "Test · ${kind.label}",
    arrow = kind,
    arrowSource = ArrowSource.TEXT,
    instruction = "Test · ${kind.label}",
    summary = TEST_SUMMARY,
)

/** Opens Google Maps on [uri], or whatever handles it if Maps isn't installed. */
private fun openMaps(context: Context, uri: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val withMaps = Intent(intent).setPackage("com.google.android.apps.maps")
    runCatching { context.startActivity(withMaps) }.onFailure { runCatching { context.startActivity(intent) } }
}

private fun notificationAccessGranted(context: Context): Boolean {
    val cn = ComponentName(context, NotificationCaptureService::class.java)
    val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    return enabled.split(':').any { it.equals(cn.flattenToString(), ignoreCase = true) }
}
