package dev.gdk.bumblebee.ui.journey

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gdk.bumblebee.nav.ManeuverMap
import dev.gdk.bumblebee.nav.MapsNavSource
import dev.gdk.bumblebee.nav.NotificationCaptureService
import dev.gdk.bumblebee.nav.ParsedNavData
import dev.gdk.bumblebee.nav.normalizeEta

@Composable
fun JourneyScreen() {
    val context = LocalContext.current
    var access by remember { mutableStateOf(notificationAccessGranted(context)) }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) access = notificationAccessGranted(context)
        }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }
    val frame by MapsNavSource.frame.collectAsStateWithLifecycle()
    val street by MapsNavSource.street.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Journey", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Google Maps turn-by-turn is scraped from the Maps notification and sent as a531. " +
                "Arrows are guessed from instruction text — they can be wrong.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            if (access) "Notification access ON" else "Notification access OFF — cluster will not see Maps.",
            style = MaterialTheme.typography.labelLarge,
            color = if (access) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        if (!access) {
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) {
                Text("Enable notification access")
            }
        }
        Text(street ?: "No active Maps guidance", style = MaterialTheme.typography.titleMedium)
        if (frame != null) {
            Text("Arrow byte ${frame!!.maneuverId}")
            Text("Next ${frame!!.distNext} ${frame!!.distNextUnit}")
            Text("ETA ${frame!!.eta}")
            Text("Remain ${frame!!.distTotal} ${frame!!.distTotalUnit}")
        }
        OutlinedButton(onClick = {
            MapsNavSource.update(
                ParsedNavData(
                    maneuverId = ManeuverMap.mapplsIdToClusterByte(0) ?: 1,
                    distNext = "0200",
                    distNextUnit = "M",
                    eta = normalizeEta("5 min", twelveHour = true),
                    distTotal = "01.2",
                    distTotalUnit = "K",
                    streetName = "Demo · turn left in 200 m",
                ),
            )
        }) {
            Text("Send demo left turn")
        }
        OutlinedButton(onClick = { MapsNavSource.clear() }) {
            Text("Clear cluster nav")
        }
        Text(
            "Connect the bike first. Demo writes a531 only while BikeBridge is running.",
            style = MaterialTheme.typography.bodySmall,
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
