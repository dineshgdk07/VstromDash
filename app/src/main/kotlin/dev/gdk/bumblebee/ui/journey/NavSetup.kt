package dev.gdk.bumblebee.ui.journey

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.BuildConfig
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.journey.GoogleRouting
import dev.gdk.bumblebee.journey.NavMode
import dev.gdk.bumblebee.journey.NavSettings
import dev.gdk.bumblebee.journey.OrsRouting
import dev.gdk.bumblebee.journey.PlaceHit
import dev.gdk.bumblebee.journey.Routing
import dev.gdk.bumblebee.nav.LatLng
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.ForgeSheet
import dev.gdk.bumblebee.ui.components.PillChip
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Settings → Navigation: where turn-by-turn comes from, and the keys for the in-app modes. */
@Composable
fun NavigationSettingsCard() {
    val context = LocalContext.current
    val settings = remember { NavSettings(context) }
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(settings.mode) }
    var googleSaved by remember { mutableStateOf(settings.googleKey.isNotBlank()) }
    var orsSaved by remember { mutableStateOf(settings.orsKey.isNotBlank()) }
    var draft by remember(mode) { mutableStateOf("") }
    var status by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    SectionCard(header = { SectionLabel(text = "Navigation") }) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Turn-by-turn comes from", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(NavMode.NOTIFICATIONS to "Maps app", NavMode.GOOGLE to "Google", NavMode.OPEN to "Open").forEach { (m, label) ->
                    PillChip(text = label, selected = mode == m, onClick = {
                        mode = m
                        settings.mode = m
                        status = null
                    })
                }
            }
            Text(
                when (mode) {
                    NavMode.NOTIFICATIONS -> "Navigate in Google Maps as usual; this app reads its directions and sends the turns to the bike. No key needed."
                    NavMode.GOOGLE -> "This app navigates: Google search and two-wheeler routes with exact turns for the cluster. Your key is billed by Google; normal personal use stays inside the free monthly credit."
                    NavMode.OPEN -> "This app navigates on its own map with free OpenRouteService routing. Exact turns; road data and traffic are weaker than Google's."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            if (mode != NavMode.NOTIFICATIONS) {
                val saved = if (mode == NavMode.GOOGLE) googleSaved else orsSaved
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.trim() },
                    label = { Text(if (mode == NavMode.GOOGLE) "Google API key" else "OpenRouteService key") },
                    placeholder = { Text(if (saved) "Saved · paste a new one to replace" else "Paste your key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        ChunkyButton(
                            text = "Save and test",
                            enabled = draft.isNotBlank(),
                            onClick = {
                                val key = draft
                                status = true to "Testing…"
                                scope.launch {
                                    val routing = if (mode == NavMode.GOOGLE) GoogleRouting(key) else OrsRouting(key)
                                    val result = runCatching { routing.search("Bengaluru", null) }
                                    if (result.isSuccess) {
                                        if (mode == NavMode.GOOGLE) {
                                            settings.googleKey = key
                                            googleSaved = true
                                        } else {
                                            settings.orsKey = key
                                            orsSaved = true
                                        }
                                        draft = ""
                                        status = true to "Key works and is saved on this phone, encrypted."
                                    } else {
                                        status = false to (result.exceptionOrNull()?.message ?: "The key didn't work.")
                                    }
                                }
                            },
                            compact = true,
                            style = ChunkyStyle.Lime,
                        )
                    }
                    if (saved) {
                        Box(Modifier.weight(1f)) {
                            ChunkyButton(
                                text = "Remove key",
                                onClick = {
                                    if (mode == NavMode.GOOGLE) {
                                        settings.googleKey = ""
                                        googleSaved = false
                                    } else {
                                        settings.orsKey = ""
                                        orsSaved = false
                                    }
                                    status = null
                                },
                                compact = true,
                                style = ChunkyStyle.Outline,
                            )
                        }
                    }
                }
                status?.let { (ok, msg) -> Text(msg, style = MaterialTheme.typography.bodySmall, color = if (ok) Lime else Danger) }
                if (mode == NavMode.GOOGLE) {
                    Text(
                        "In Google Cloud: enable Routes API and Places API (New), and restrict the key to Android app dev.gdk.bumblebee. " +
                            if (BuildConfig.HAS_MAPS_KEY) "The map is on (Maps SDK key built in)." else "To see Google's map too, add MAPS_API_KEY to local.properties and rebuild; without it you get the turn card and cluster arrows only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Text(
                        "Open Google Cloud console",
                        style = MaterialTheme.typography.labelLarge,
                        color = Lime,
                        modifier = Modifier.clickable { open("https://console.cloud.google.com/google/maps-apis/credentials") }.padding(vertical = 4.dp),
                    )
                } else {
                    Text(
                        "Get a free key at openrouteservice.org (sign up → Dashboard → Request a token, Standard).",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Text(
                        "Open openrouteservice.org",
                        style = MaterialTheme.typography.labelLarge,
                        color = Lime,
                        modifier = Modifier.clickable { open("https://openrouteservice.org/dev/#/signup") }.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** Search for a destination with the current mode's service. */
@Composable
fun PlaceSearchSheet(onPick: (PlaceHit) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val routing = remember { Routing.of(NavSettings(context)) }
    val near = remember { (context.applicationContext as DashApp).latestGps.value?.let { LatLng(it.lat, it.lng) } }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceHit>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        if (query.length < 3 || routing == null) {
            results = emptyList()
            return@LaunchedEffect
        }
        // Wait for a pause in typing so each keystroke isn't a billed request.
        delay(450)
        runCatching { routing.search(query, near) }
            .onSuccess {
                results = it
                error = null
            }
            .onFailure { error = it.message }
    }
    ForgeSheet(title = "Where to?", onDismiss = onDismiss, primaryLabel = "Cancel", onPrimary = onDismiss) {
        if (routing == null) {
            Text("Add a key in Settings → Navigation to search here.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            return@ForgeSheet
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search a place or address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Danger, modifier = Modifier.padding(top = 8.dp)) }
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            results.take(8).forEach { hit ->
                Column(
                    Modifier.fillMaxWidth().clip(ShapeMd).background(SurfaceRaised).clickable { onPick(hit) }.padding(12.dp),
                ) {
                    Text(hit.label, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    hit.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1) }
                }
            }
        }
    }
}
