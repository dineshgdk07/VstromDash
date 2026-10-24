package dev.gdk.bumblebee.ui.garage

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.ForgeTopBar
import dev.gdk.bumblebee.ui.components.ListRow
import dev.gdk.bumblebee.ui.components.ListRowChevron
import dev.gdk.bumblebee.ui.components.PillChip
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary

@Composable
fun GarageSettingsScreen(
    onBack: () -> Unit,
    model: GarageSettingsViewModel = viewModel(),
) {
    val state by model.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionTick by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.all { it }) model.startScan()
    }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.onTestSosPermission(granted)
    }
    RefreshOnResume {
        model.refreshEconomics()
        permissionTick += 1
    }
    val appGranted = remember(permissionTick, context) { runtimePermissionsGranted(context) }
    val locationOn = remember(permissionTick, context) { locationEnabled(context) }
    val notificationsOn = remember(permissionTick, context) { notificationListenerEnabled(context) }
    val batteryFree = remember(permissionTick, context) { batteryUnrestricted(context) }

    fun requestThenScan() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (perms.isEmpty()) model.startScan()
        else permissionLauncher.launch(perms.toTypedArray())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ForgeTopBar(title = "Settings", onBack = onBack)
        if (state.status.isNotBlank()) {
            Text(state.status, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        SectionCard(header = { SectionLabel(text = "Bike") }) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ListRow(
                    title = "MAC",
                    showDivider = false,
                    trailing = {
                        Text(
                            text = state.bikeMac ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                        )
                    },
                )
                GarageField(state.riderName, model::setRiderName, "Rider name")
                ChunkyButton(text = "Save name", onClick = model::saveRiderName, style = ChunkyStyle.Lime)
                ChunkyButton(
                    text = if (state.scanning) "Stop scan" else "Scan",
                    onClick = { if (state.scanning) model.stopScan() else requestThenScan() },
                    style = ChunkyStyle.Lime,
                )
                ChunkyButton(text = "Connect", onClick = model::connectSaved, enabled = state.bikeMac != null, style = ChunkyStyle.Outline)
                ChunkyButton(text = "Disconnect", onClick = model::disconnectBike, style = ChunkyStyle.Outline)
                ChunkyButton(text = "Forget", onClick = model::forgetBike, enabled = state.bikeMac != null, style = ChunkyStyle.Danger)
                state.devices.forEach { bike ->
                    val label = buildString {
                        append(bike.name ?: bike.mac)
                        if (bike.looksLikeSuzuki) append("  · Suzuki")
                        if (bike.bonded) append("  · bonded")
                        if (bike.rssi != Int.MIN_VALUE) append("  · ${bike.rssi} dBm")
                    }
                    Text(
                        text = label,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { model.pair(bike.mac) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        }
        SectionCard(header = { SectionLabel(text = "Reminders") }) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Notify overdue and due-soon items",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = state.remindersEnabled, onCheckedChange = model::setRemindersEnabled)
                }
                GarageField(state.remindDays, model::setRemindDays, "Days ahead", KeyboardType.Number)
                GarageField(state.remindKm, model::setRemindKm, "Km ahead", KeyboardType.Number)
                ChunkyButton(text = "Save reminder window", onClick = model::saveReminders, style = ChunkyStyle.Lime)
            }
        }
        SectionCard(header = { SectionLabel(text = "Ride and safety") }) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "A stop inside the pause window stays one ride. Crash alerts are a best-effort aid, not a certified emergency device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Text("Pause window", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Off", 15 to "15", 30 to "30", 45 to "45", 60 to "60", 120 to "120").forEach { (min, label) ->
                        PillChip(text = label, selected = state.pauseWindowMin == min, onClick = { model.setPauseWindow(min) })
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Auto-connect when I turn on the bike",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = state.autoConnect, onCheckedChange = model::setAutoConnect)
                }
                ChunkyButton(text = "Do not auto-connect today", onClick = model::skipAutoConnectToday, style = ChunkyStyle.Outline)
                Text(
                    "On Xiaomi, Oppo and similar phones, allow Autostart and set battery to No restrictions, or the link dies with the screen off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                GarageField(state.sosContacts, model::setSosContacts, "Emergency numbers, up to 3", singleLine = false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("LOW", "NORMAL", "HIGH").forEach { level ->
                        PillChip(
                            text = level,
                            selected = state.crashSensitivity == level,
                            onClick = { model.setCrashSensitivity(level) },
                        )
                    }
                }
                GarageField(state.bloodGroup, model::setBloodGroup, "Blood group")
                GarageField(state.medicalNote, model::setMedicalNote, "Medical note", singleLine = false)
                ChunkyButton(text = "Save safety details", onClick = model::saveSafety, style = ChunkyStyle.Lime)
                ChunkyButton(text = "Test SOS", onClick = { smsLauncher.launch(Manifest.permission.SEND_SMS) }, style = ChunkyStyle.Outline)
            }
        }
        SectionCard(header = { SectionLabel(text = "Fuel") }) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    state.rangeKm?.let { "est. range  ${it.toInt()} km" } ?: "Range after bar drops on linked rides.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
                Text(
                    buildString {
                        append(state.tankKmPerL?.let { "est. ${"%.1f".format(it)} km/L" } ?: "Tank km/L after two fills")
                        append("  ·  ")
                        append(state.rupeesPerKm?.let { "₹${"%.2f".format(it)}/km" } ?: "₹/km after priced fills")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
        SectionCard(header = { SectionLabel(text = "Cluster nav") }) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Turn arrows on the bike come from Google Maps notifications, not a Maps API. Leave this on while riding with Maps in the background.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Maps notifications → cluster",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = state.mapsNavToCluster, onCheckedChange = model::setMapsNavToCluster)
                }
            }
        }
        SectionCard(header = { SectionLabel(text = "Permissions") }) {
            Column(Modifier.padding(top = 4.dp)) {
                PermissionRow("App permissions", appGranted) {
                    val uri = Uri.fromParts("package", context.packageName, null)
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
                }
                PermissionRow("Location", locationOn) {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                PermissionRow("Notification access", notificationsOn) {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                PermissionRow("Battery optimisation", batteryFree, last = true) {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    last: Boolean = false,
    onClick: () -> Unit,
) {
    ListRow(
        title = title,
        onClick = onClick,
        showDivider = !last,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (granted) "Granted" else "Denied",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (granted) Lime else Danger,
                )
                ListRowChevron()
            }
        },
    )
}

private fun runtimePermissionsGranted(context: android.content.Context): Boolean {
    val perms = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    return perms.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

private fun locationEnabled(context: android.content.Context): Boolean {
    val manager = context.getSystemService(LocationManager::class.java) ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        manager.isLocationEnabled
    } else {
        runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
    }
}

private fun notificationListenerEnabled(context: android.content.Context): Boolean {
    val flat = runCatching {
        Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    }.getOrNull() ?: return false
    return flat.contains(context.packageName)
}

private fun batteryUnrestricted(context: android.content.Context): Boolean {
    val power = context.getSystemService(PowerManager::class.java) ?: return false
    return power.isIgnoringBatteryOptimizations(context.packageName)
}
