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
import androidx.compose.foundation.layout.Box
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
        permissionTick += 1
    }
    val smsSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionTick += 1
    }
    RefreshOnResume {
        model.refreshEconomics()
        permissionTick += 1
    }
    val appGranted = remember(permissionTick, context) { runtimePermissionsGranted(context) }
    val locationOn = remember(permissionTick, context) { locationEnabled(context) }
    val notificationsOn = remember(permissionTick, context) { notificationListenerEnabled(context) }
    val batteryFree = remember(permissionTick, context) { batteryUnrestricted(context) }
    val smsGranted = remember(permissionTick, context) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }
    val fullScreenOk = remember(permissionTick, context) { fullScreenAllowed(context) }

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
        SectionCard(header = { SectionLabel(text = "Ride") }) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "A stop inside the pause window stays one ride.",
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Share prompt after record rides",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = state.sharePrompt, onCheckedChange = model::setSharePrompt)
                }
                ChunkyButton(text = "Do not auto-connect today", onClick = model::skipAutoConnectToday, style = ChunkyStyle.Outline)
                Text(
                    "On Xiaomi, Oppo and similar phones, allow Autostart and set battery to No restrictions, or the link dies with the screen off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
        SectionCard(header = { SectionLabel(text = "Crash alerts") }) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Watch for crashes while riding",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = state.crashDetection, onCheckedChange = model::setCrashDetection)
                }
                Text(
                    "Alarms only when a hard impact above 20 km/h is followed by a stop, no riding for a while, and the phone ending up turned over (the bike or you are down). A bump and a red light won't do it. You get 30 s to tap I'm OK before your contacts are texted with your location. A best-effort aid, not a certified emergency device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Text("Sensitivity", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("LOW" to "Low", "NORMAL" to "Normal", "HIGH" to "High").forEach { (level, label) ->
                        PillChip(
                            text = label,
                            selected = state.crashSensitivity == level,
                            onClick = { model.setCrashSensitivity(level) },
                        )
                    }
                }
                Text(
                    when (state.crashSensitivity) {
                        "LOW" -> "Fewest alarms. Needs a clear fall with the phone ending up turned over."
                        "HIGH" -> "Catches softer falls and gentler tilts. More chance of a false alarm."
                        else -> "Needs a fall, or one very hard hit. Recommended."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                GarageField(state.sosContacts, model::setSosContacts, "Emergency numbers, up to 3", KeyboardType.Phone, singleLine = false)
                GarageField(state.bloodGroup, model::setBloodGroup, "Blood group")
                GarageField(state.medicalNote, model::setMedicalNote, "Medical note", singleLine = false)
                ChunkyButton(
                    text = "Save safety details",
                    onClick = {
                        model.saveSafety()
                        if (!smsGranted) smsSaveLauncher.launch(Manifest.permission.SEND_SMS)
                    },
                    style = ChunkyStyle.Lime,
                )
                PermissionRow("SMS to contacts", smsGranted) {
                    smsSaveLauncher.launch(Manifest.permission.SEND_SMS)
                }
                PermissionRow("Alarm over the lock screen", fullScreenOk, last = true) {
                    openFullScreenSettings(context)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        ChunkyButton(text = "Practice alarm", onClick = model::practiceAlarm, compact = true, style = ChunkyStyle.Outline)
                    }
                    Box(Modifier.weight(1f)) {
                        ChunkyButton(
                            text = "Test SMS",
                            onClick = { smsLauncher.launch(Manifest.permission.SEND_SMS) },
                            compact = true,
                            style = ChunkyStyle.Outline,
                        )
                    }
                }
                Text(
                    "Practice runs the real alarm; when it ends, contacts get a text marked TEST. On Xiaomi, also allow \"Display pop-up windows while running in the background\" and \"Show on lock screen\" for Bumblebee, or the alarm may only show as a notification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
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

/** Android 14 lets people turn off full-screen alarms per app; earlier versions always allow it. */
private fun fullScreenAllowed(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < 34) return true
    val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return false
    return nm.canUseFullScreenIntent()
}

private fun openFullScreenSettings(context: android.content.Context) {
    val intent = if (Build.VERSION.SDK_INT >= 34) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.fromParts("package", context.packageName, null))
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
    runCatching { context.startActivity(intent) }
}

private fun batteryUnrestricted(context: android.content.Context): Boolean {
    val power = context.getSystemService(PowerManager::class.java) ?: return false
    return power.isIgnoringBatteryOptimizations(context.packageName)
}
