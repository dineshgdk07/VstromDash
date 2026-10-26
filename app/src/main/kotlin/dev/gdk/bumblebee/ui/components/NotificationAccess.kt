package dev.gdk.bumblebee.ui.components

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.InstallSourceInfo
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.ShapeLg
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.ShapeSm
import dev.gdk.bumblebee.ui.theme.Surface
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.SurfaceSunken
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary

/**
 * Android 13+ locks notification access and SMS for apps installed from a downloaded APK
 * ("Restricted setting", "App was denied access") until the rider turns on "Allow restricted
 * settings" in App info. That menu item only appears after the app has asked once and been
 * refused, so the guide walks through: try once, unlock, then turn it on.
 */
object RestrictedSettings {
    private const val PREFS = "restricted_settings"
    private const val OP = "android:access_restricted_settings"

    /** True when this install is likely locked: Android 13+, not from a store, and not unlocked yet. */
    fun applies(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (unlocked(context)) return false
        val source: InstallSourceInfo? = runCatching { context.packageManager.getInstallSourceInfo(context.packageName) }.getOrNull()
        return source?.packageSource != PackageInstaller.PACKAGE_SOURCE_STORE
    }

    /** True once the rider has allowed restricted settings for this app. */
    fun unlocked(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(AppOpsManager::class.java)
        ops.unsafeCheckOpNoThrow(OP, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** The rider has asked for the permission once, so Android now offers the unlock. */
    fun markTried(context: Context, what: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(what, true).apply()
    }

    fun tried(context: Context, what: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(what, false)
}

fun openNotificationAccess(context: Context) {
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}

fun openAppInfo(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
}

/**
 * A step-by-step guide to unlock a restricted permission, with a picture of the App info screen
 * showing where to tap. [what] names it ("notification access", "SMS"); [onTry] asks for it the
 * normal way (which Android refuses the first time, and which then makes the unlock appear);
 * [onUnlocked] runs when the rider comes back with it unlocked, to ask again for real.
 */
@Composable
fun RestrictedSettingsGuide(context: Context, what: String, onTry: () -> Unit, onUnlocked: () -> Unit, onDismiss: () -> Unit) {
    var tried by remember { mutableStateOf(RestrictedSettings.tried(context, what)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                tried = RestrictedSettings.tried(context, what)
                if (RestrictedSettings.unlocked(context)) onUnlocked()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(16.dp)
                .clip(ShapeLg)
                .background(Surface)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Unlock $what", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Text(
                "Android protects $what for apps installed from a download, so it needs your OK once. " +
                    "It takes three taps, and you only do it once.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Step(1, "Try turning it on", done = tried) {
                Text(
                    "Android will say \"App was denied access\". That's expected: tap Close and come back here. " +
                        "Asking once is what makes the unlock appear.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                if (!tried) {
                    ChunkyButton(
                        text = "Try it",
                        onClick = {
                            RestrictedSettings.markTried(context, what)
                            tried = true
                            onTry()
                        },
                        style = ChunkyStyle.Lime,
                        compact = true,
                    )
                }
            }
            Step(2, "Allow restricted settings", done = false) {
                Text(
                    "In App info, tap ⋮ at the top right, then Allow restricted settings, and confirm with your PIN or fingerprint.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                AppInfoPreview()
                ChunkyButton(
                    text = "Open App info",
                    onClick = { openAppInfo(context) },
                    style = if (tried) ChunkyStyle.Lime else ChunkyStyle.Outline,
                    compact = true,
                )
                if (!tried) {
                    Text("Do step 1 first: the option only shows after Android has said no once.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            Step(3, "Come back and turn it on", done = false) {
                Text(
                    "Return to Bumblebee and this carries on by itself. If it doesn't, tap below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                ChunkyButton(text = "Turn on $what", onClick = onUnlocked, style = ChunkyStyle.Outline, compact = true)
            }
            ChunkyButton(text = "Not now", onClick = onDismiss, style = ChunkyStyle.Neutral, compact = true)
        }
    }
}

@Composable
private fun Step(n: Int, title: String, done: Boolean, content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (done) Lime else SurfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (done) "✓" else "$n", style = MaterialTheme.typography.labelLarge, color = if (done) OnBlock else TextPrimary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            content()
        }
    }
}

/**
 * A sketch of Android's App info screen: the ⋮ menu ringed at the top right, and its open menu
 * with "Allow restricted settings" highlighted, so the rider knows what to look for.
 */
@Composable
private fun AppInfoPreview() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(ShapeMd)
            .background(SurfaceSunken)
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("←", color = TextSecondary, style = MaterialTheme.typography.titleMedium)
                Text("  App info", color = TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(30.dp).clip(CircleShape).border(2.dp, Lime, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⋮", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(34.dp).clip(ShapeSm).background(Lime))
                Text("Bumblebee", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            }
            repeat(2) {
                Box(Modifier.width(150.dp).height(8.dp).clip(ShapeSm).background(SurfaceRaised))
            }
        }
        // The open menu, dropping from the ⋮.
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .offset(y = 38.dp)
                .width(210.dp)
                .clip(ShapeSm)
                .background(Color(0xFF2E2E2E))
                .padding(vertical = 6.dp),
        ) {
            Text("Uninstall for all users", color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            Box(
                Modifier
                    .padding(horizontal = 6.dp)
                    .fillMaxWidth()
                    .clip(ShapeSm)
                    .border(2.dp, Lime, ShapeSm)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            ) {
                Text("Allow restricted settings", color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
