package dev.gdk.bumblebee.ui.garage

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gdk.bumblebee.BuildConfig
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ble.BikeBridgeService
import dev.gdk.bumblebee.bike.BikeProfile
import dev.gdk.bumblebee.dev.AdvancedPrefs
import dev.gdk.bumblebee.dev.DevLog
import dev.gdk.bumblebee.journey.NavSettings
import dev.gdk.bumblebee.map.MapTiles
import dev.gdk.bumblebee.nav.ArrowTeacher
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.ForgeTopBar
import dev.gdk.bumblebee.ui.components.PillChip
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.theme.Surface
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import dev.gdk.bumblebee.widget.HomeWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Something destructive waiting on the rider's yes. */
private data class Confirm(val title: String, val body: String, val action: String, val job: suspend () -> String)

/**
 * Settings → Advanced: tunables, data clean-up and, after seven taps on the version, a developer
 * section with the live link, database counts and a log. Nothing here is needed to ride.
 */
@Composable
fun AdvancedScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as DashApp
    val prefs = remember { AdvancedPrefs(app) }
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var junk by remember { mutableStateOf(0 to 0) }
    var tileBytes by remember { mutableLongStateOf(0L) }
    var devUnlocked by remember { mutableStateOf(prefs.devUnlocked) }
    var versionTaps by remember { mutableIntStateOf(0) }

    // Prefs aren't observable; re-read them whenever something changes so every row stays current.
    val v = remember(tick) { prefs.snapshot() }
    LaunchedEffect(tick) {
        junk = app.capture.junkRideCount()
        tileBytes = withContext(Dispatchers.IO) { MapTiles.diskBytes(app) }
    }

    fun runTool(label: String, block: suspend () -> String) {
        scope.launch {
            status = "$label…"
            status = runCatching { block() }.getOrElse { "Failed: ${it.message}" }
            DevLog.d("advanced", status)
            tick++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ForgeTopBar(title = "Advanced", onBack = onBack)
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }

        SectionCard(header = { SectionLabel(text = "Cluster and nav") }) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ToggleRow("24-hour arrival time on the cluster", v.clock24h) {
                    prefs.clock24h = it
                    tick++
                }
                Choices(
                    "Off route after",
                    "How far from the line before the app counts you off route. Raise it if service lanes or bad GPS cause re-routes.",
                    AdvancedPrefs.OFF_ROUTE_CHOICES, v.offRouteM, { "$it m" },
                ) {
                    prefs.offRouteM = it
                    tick++
                }
                Choices(
                    "Re-route after",
                    "How long you must stay off route before a new route is planned.",
                    AdvancedPrefs.REROUTE_CHOICES, v.rerouteDelayS, { "$it s" },
                ) {
                    prefs.rerouteDelayS = it
                    tick++
                }
                Choices(
                    "Hold a Maps step",
                    "How long the last Maps arrow stays on the cluster after its notification goes quiet, as in a tunnel.",
                    AdvancedPrefs.STALE_CHOICES, v.mapsStaleS, { if (it < 60) "$it s" else "${it / 60} min" },
                ) {
                    prefs.mapsStaleS = it
                    tick++
                }
                Text("Applies from the next step or route.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }

        SectionCard(header = { SectionLabel(text = "Crash alarm") }) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Choices(
                    "Countdown before texting",
                    "Shorter gets help moving sooner; longer gives more time to tap I'm OK after a false alarm.",
                    AdvancedPrefs.COUNTDOWN_CHOICES, v.crashCountdownS, { "$it s" },
                ) {
                    prefs.crashCountdownS = it
                    tick++
                }
            }
        }

        SectionCard(header = { SectionLabel(text = "Map storage") }) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Choices(
                    "Tile cache limit",
                    "Map tiles kept on the phone for offline and faster maps. ${mb(tileBytes)} used now.",
                    AdvancedPrefs.TILE_CHOICES, v.tileCacheMb, { "$it MB" },
                ) {
                    prefs.tileCacheMb = it
                    MapTiles.setDiskLimit(app, it)
                    tick++
                }
                ChunkyButton(
                    text = "Clear map cache",
                    onClick = { runTool("Clearing tiles") { withContext(Dispatchers.IO) { MapTiles.clear(app) }; "Map cache cleared." } },
                    style = ChunkyStyle.Outline,
                    compact = true,
                )
            }
        }

        SectionCard(header = { SectionLabel(text = "Data tools") }) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val (demo, unsynced) = junk
                Tool(
                    "Delete demo rides",
                    if (demo == 0) "None saved." else "$demo demo ride${plural(demo)} from trying the app.",
                    enabled = demo > 0,
                ) {
                    confirm = Confirm("Delete demo rides?", "Removes $demo demo ride${plural(demo)} and their tracks.", "Delete") {
                        "Deleted ${app.capture.deleteJunkRides(demo = true, unsynced = false)} demo ride(s)."
                    }
                }
                Tool(
                    "Delete rides dated before 2000",
                    if (unsynced == 0) "None found." else "$unsynced ride${plural(unsynced)} recorded before the phone's clock was set.",
                    enabled = unsynced > 0,
                ) {
                    confirm = Confirm("Delete misdated rides?", "Removes $unsynced ride${plural(unsynced)} stamped before 2000, with their tracks.", "Delete") {
                        "Deleted ${app.capture.deleteJunkRides(demo = false, unsynced = true)} ride(s)."
                    }
                }
                Tool("Rebuild daily totals", "Recounts each day's km and ride time from the rides, if the calendar or streaks look off.") {
                    runTool("Rebuilding") { app.capture.rebuildAllRollups(); "Daily totals rebuilt." }
                }
                Tool("Relearn fuel gauge", "Recomputes km per bar from the fuel log and forgets the remembered steady level. Fills are kept.") {
                    confirm = Confirm("Relearn fuel gauge?", "Range estimates may be blank until the next bar drop.", "Relearn") {
                        app.capture.relearnFuel()
                        "Fuel gauge will relearn."
                    }
                }
                Tool("Forget arrow fixes", "Clears the turn arrows you corrected in Journey, so Maps' wording decides again.") {
                    confirm = Confirm("Forget arrow fixes?", "Every arrow you taught goes back to automatic.", "Forget") {
                        ArrowTeacher.forgetAll(app)
                        "Arrow fixes forgotten."
                    }
                }
                Tool("Clear share images", "Deletes the ride cards and exports left in the app's cache.") {
                    runTool("Clearing") {
                        val dir = File(app.cacheDir, "share")
                        val bytes = withContext(Dispatchers.IO) { dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }.also { dir.deleteRecursively() } }
                        "Freed ${mb(bytes)}."
                    }
                }
                Tool("Refresh widgets", "Redraws every home-screen widget now.") {
                    HomeWidgets.refreshAll(app)
                    status = "Widgets refreshed."
                }
                Tool("Run setup again", "Shows the first-run setup to change the bike, colour or contacts. Nothing is deleted.") {
                    BikeProfile(app).onboarded = false
                    (context as? Activity)?.recreate()
                }
                Tool("Reset these settings", "Puts every tunable on this page back to its default.") {
                    prefs.resetTunables()
                    MapTiles.setDiskLimit(app, prefs.tileCacheMb)
                    status = "Advanced settings reset."
                    tick++
                }
            }
        }

        if (devUnlocked) {
            DeveloperSection(
                app = app,
                prefs = prefs,
                tick = tick,
                onStatus = { status = it },
                onRun = ::runTool,
                onHide = {
                    prefs.devUnlocked = false
                    devUnlocked = false
                    versionTaps = 0
                },
            )
        }

        Text(
            "Bumblebee ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})${if (BuildConfig.DEBUG) " · debug" else ""}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (devUnlocked) {
                        toast(context, "Developer options are already on")
                        return@clickable
                    }
                    versionTaps++
                    val left = 7 - versionTaps
                    when {
                        left <= 0 -> {
                            prefs.devUnlocked = true
                            devUnlocked = true
                            DevLog.d("advanced", "developer options on")
                            toast(context, "Developer options on")
                        }
                        left <= 3 -> toast(context, "$left more tap${plural(left)} for developer options")
                    }
                }
                .padding(vertical = 8.dp),
        )
    }

    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Surface,
            title = { Text(c.title, color = TextPrimary) },
            text = { Text(c.body, color = TextSecondary) },
            confirmButton = {
                ChunkyButton(
                    text = c.action,
                    onClick = {
                        confirm = null
                        runTool(c.action) { c.job() }
                    },
                    style = ChunkyStyle.Danger,
                    compact = true,
                )
            },
            dismissButton = {
                ChunkyButton(text = "Cancel", onClick = { confirm = null }, style = ChunkyStyle.Outline, compact = true)
            },
        )
    }
}

@Composable
private fun DeveloperSection(
    app: DashApp,
    prefs: AdvancedPrefs,
    tick: Int,
    onStatus: (String) -> Unit,
    onRun: (String, suspend () -> String) -> Unit,
    onHide: () -> Unit,
) {
    val context = LocalContext.current
    val link by app.connection.collectAsState()
    val telemetry by app.latestTelemetry.collectAsState()
    val frame by DevLog.lastFrame.collectAsState()
    val log by DevLog.flow.collectAsState()
    var counts by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var frames by remember { mutableStateOf(prefs.logFrames) }

    LaunchedEffect(tick) { counts = app.capture.tableCounts() }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    SectionCard(header = { SectionLabel(text = "Developer · live link") }) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Mono("state   $link")
            val t = telemetry
            Mono(
                if (t == null) "frame   none yet"
                else "speed   ${t.speedKmh} km/h   odo ${t.odometerKm} km\nfuel    ${t.fuelBars ?: "-"} bars   trip A ${"%.1f".format(t.tripAKm)}   B ${"%.1f".format(t.tripBKm)}",
            )
            frame?.let { (ts, bytes) ->
                Mono("raw     ${bytes.size} B, ${((now - ts) / 1000).coerceAtLeast(0)} s ago\n${DevLog.hex(bytes)}")
            }
            ChunkyButton(
                text = "Force reconnect",
                onClick = {
                    if (app.prefs.bikeMac == null) onStatus("No bike paired yet.")
                    else {
                        BikeBridgeService.reconnect(app)
                        onStatus("Reconnecting…")
                    }
                },
                style = ChunkyStyle.Outline,
                compact = true,
            )
            ToggleRow("Log every raw frame", frames) {
                prefs.logFrames = it
                DevLog.frames = it
                frames = it
            }
        }
    }

    SectionCard(header = { SectionLabel(text = "Developer · database") }) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Mono(counts.joinToString("\n") { (t, n) -> "${t.padEnd(18)} ${if (n < 0) "?" else n}" }.ifBlank { "Counting…" })
            val dbFile = app.getDatabasePath(app.database.openHelper.databaseName ?: "")
            Mono("size    ${mb(dbFile.length())}")
            ChunkyButton(
                text = "Compact database",
                onClick = { onRun("Compacting") { app.capture.vacuum(); "Database compacted to ${mb(dbFile.length())}." } },
                style = ChunkyStyle.Outline,
                compact = true,
            )
        }
    }

    SectionCard(header = { SectionLabel(text = "Developer · log", count = log.size) }) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Mono(log.takeLast(60).asReversed().joinToString("\n") { it.format() }.ifBlank { "Nothing logged since the app started." })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    ChunkyButton(text = "Clear", onClick = { DevLog.clear() }, style = ChunkyStyle.Outline, compact = true)
                }
                Box(Modifier.weight(1f)) {
                    ChunkyButton(
                        text = "Share report",
                        onClick = { shareReport(context, app, prefs, counts) },
                        style = ChunkyStyle.Lime,
                        compact = true,
                    )
                }
            }
            Text(
                "The report has versions, settings, table counts and this log. No keys, contacts, bike address or locations.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            ChunkyButton(text = "Hide developer options", onClick = onHide, style = ChunkyStyle.Outline, compact = true)
        }
    }
}

/** Plain text, so it pastes straight into an issue. */
private fun shareReport(context: Context, app: DashApp, prefs: AdvancedPrefs, counts: List<Pair<String, Long>>) {
    val nav = NavSettings(app)
    val text = buildString {
        appendLine("Bumblebee ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})${if (BuildConfig.DEBUG) " debug" else ""}")
        appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Link: ${app.connection.value}; bike paired: ${app.prefs.bikeMac != null}")
        appendLine("Nav mode: ${nav.mode}; Google key set: ${nav.googleKey.isNotBlank()}; ORS key set: ${nav.orsKey.isNotBlank()}; Maps SDK key: ${BuildConfig.HAS_MAPS_KEY}")
        appendLine(
            "Advanced: clock24h=${prefs.clock24h} countdown=${prefs.crashCountdownS}s offRoute=${prefs.offRouteM}m " +
                "reroute=${prefs.rerouteDelayS}s mapsHold=${prefs.mapsStaleS}s tiles=${prefs.tileCacheMb}MB",
        )
        appendLine()
        counts.forEach { (t, n) -> appendLine("$t: $n") }
        appendLine()
        DevLog.flow.value.takeLast(200).forEach { appendLine(it.format()) }
    }
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Bumblebee debug report")
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Share debug report"))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Choices(title: String, help: String, options: List<Int>, selected: Int, label: (Int) -> String, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { v -> PillChip(text = label(v), selected = v == selected, onClick = { onPick(v) }) }
        }
        Text(help, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun Tool(title: String, detail: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = if (enabled) TextPrimary else TextSecondary)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun Mono(text: String) {
    Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp, color = TextSecondary)
}

private fun mb(bytes: Long): String = if (bytes < 1024 * 1024) "${bytes / 1024} KB" else "%.1f MB".format(bytes / 1048576.0)

private fun plural(n: Int) = if (n == 1) "" else "s"

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
