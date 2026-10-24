package dev.gdk.bumblebee.ui.journey

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.nav.ManeuverMap
import dev.gdk.bumblebee.nav.MapsNavSource
import dev.gdk.bumblebee.nav.NotificationCaptureService
import dev.gdk.bumblebee.nav.ParsedNavData
import dev.gdk.bumblebee.nav.toNavFrame
import dev.gdk.bumblebee.protocol.NavFrame
import dev.gdk.bumblebee.nav.normalizeEta
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.hud.ClusterNavFace
import dev.gdk.bumblebee.ui.theme.BlockMint
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.ShapeXs
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary

@Composable
fun JourneyScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as DashApp
    var access by remember { mutableStateOf(notificationAccessGranted(context)) }
    var toCluster by remember { mutableStateOf(app.prefs.mapsNavToCluster) }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) access = notificationAccessGranted(context)
        }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val frame by MapsNavSource.frame.collectAsStateWithLifecycle()
    val street by MapsNavSource.street.collectAsStateWithLifecycle()
    val connection by app.connection.collectAsStateWithLifecycle()
    val linked = connection is ConnectionState.Ready

    JourneyBody(
        access = access,
        toCluster = toCluster,
        linked = linked,
        frame = frame,
        street = street,
        onToggleCluster = {
            toCluster = it
            app.prefs.mapsNavToCluster = it
            if (!it) MapsNavSource.clear()
        },
        onEnableAccess = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        },
        onDemo = {
            MapsNavSource.update(
                ParsedNavData(
                    maneuverId = ManeuverMap.mapplsIdToClusterByte(0) ?: 1,
                    distNext = "0200",
                    distNextUnit = "M",
                    eta = normalizeEta("5 min", twelveHour = true),
                    distTotal = "01.2",
                    distTotalUnit = "K",
                    streetName = "Turn left · MG Road",
                ),
            )
        },
        onClear = { MapsNavSource.clear() },
    )
}

@Composable
private fun JourneyBody(
    access: Boolean,
    toCluster: Boolean,
    linked: Boolean,
    frame: NavFrame?,
    street: String?,
    onToggleCluster: (Boolean) -> Unit,
    onEnableAccess: () -> Unit,
    onDemo: () -> Unit,
    onClear: () -> Unit,
) {
    val guiding = frame != null
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Journey", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text(
            "Google Maps stays in the background. This screen reads its notification and, with the switch on, sends a531 to the bike. No Maps API.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )

        if (!access) {
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier
                                .width(4.dp)
                                .height(28.dp)
                                .clip(ShapeXs)
                                .background(BlockMint),
                        )
                        Text(
                            "Notification access",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                        )
                    }
                    Text(
                        "Notification access is off. Maps never reaches the phone or the bike.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    ChunkyButton(
                        text = "Enable notification access",
                        onClick = onEnableAccess,
                    )
                }
            }
        }

        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    when {
                        !access -> "Notification access is off — Maps never reaches the phone or the bike."
                        !toCluster -> "Listening to Maps. Cluster forwarding is off."
                        !linked -> "Maps is wired. Connect the bike to push arrows."
                        guiding -> "Live. Cluster follows Maps until the notification goes stale (60s)."
                        else -> "Waiting for Maps guidance."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
                StatusLine("Maps listener", if (access) "ON" else "OFF", access)
                StatusLine("Bike", if (linked) "Connected" else "Not connected", linked)
                StatusLine("Guidance", if (guiding) "Live" else "Idle", guiding)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Maps → cluster",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = toCluster, onCheckedChange = onToggleCluster)
                }
                if (frame == null) {
                    Text(
                        "No active Maps guidance",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Text(
                        "Start a Google Maps trip, keep Maps in the background, and leave this app open or connected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                } else {
                    ClusterNavFace(street = street, frame = frame)
                }
                Text(
                    "Writes a531 only while BikeBridge is running. Real arrows are guessed from the Maps instruction, so the demo is the honest check.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                ChunkyButton(
                    text = "Demo left in 200 m",
                    onClick = onDemo,
                    style = ChunkyStyle.Coral,
                )
                ChunkyButton(
                    text = "Clear cluster nav",
                    onClick = onClear,
                    style = ChunkyStyle.Outline,
                )
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = if (ok) Lime else Danger,
        )
    }
}

private fun notificationAccessGranted(context: android.content.Context): Boolean {
    val cn = ComponentName(context, NotificationCaptureService::class.java)
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners",
    ) ?: return false
    return enabled.split(':').any { it.equals(cn.flattenToString(), ignoreCase = true) }
}

@Preview
@Composable
private fun JourneyNoAccessPreview() {
    BumblebeeTheme {
        Column(Modifier.background(Canvas)) {
            JourneyBody(
                access = false,
                toCluster = true,
                linked = false,
                frame = null,
                street = null,
                onToggleCluster = {},
                onEnableAccess = {},
                onDemo = {},
                onClear = {},
            )
        }
    }
}

@Preview
@Composable
private fun JourneyGuidingPreview() {
    BumblebeeTheme {
        Column(Modifier.background(Canvas)) {
            JourneyBody(
                access = true,
                toCluster = true,
                linked = true,
                frame = ParsedNavData(
                    maneuverId = 1,
                    distNext = "0200",
                    distNextUnit = "M",
                    eta = "0500AM",
                    distTotal = "01.2",
                    distTotalUnit = "K",
                    streetName = "MG Road",
                ).toNavFrame(),
                street = "MG Road",
                onToggleCluster = {},
                onEnableAccess = {},
                onDemo = {},
                onClear = {},
            )
        }
    }
}
