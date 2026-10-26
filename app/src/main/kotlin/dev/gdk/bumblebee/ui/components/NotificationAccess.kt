package dev.gdk.bumblebee.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.gdk.bumblebee.ui.theme.Surface
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary

/**
 * True when Android may grey out notification access for this app as a "Restricted setting":
 * Android 13 and newer do that for apps installed from a downloaded file rather than a store,
 * until the rider allows it in App info.
 */
fun mayRestrictNotificationAccess(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

fun openNotificationAccess(context: Context) {
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}

fun openAppInfo(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
}

/**
 * What to do when the notification access switch is greyed out: Android blocks it for apps
 * installed from a downloaded APK until "Allow restricted settings" is turned on in App info.
 */
@Composable
fun NotificationAccessHelp(context: Context, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Turn on notification access", color = TextPrimary) },
        text = {
            Text(
                "Bumblebee reads only Google Maps' navigation notification, to show the turn arrows on the cluster.\n\n" +
                    "If the switch is greyed out or says \"Restricted setting\", Android is being careful with an app " +
                    "installed from a download:\n" +
                    "1. Tap App info below.\n" +
                    "2. Tap ⋮ at the top right, then Allow restricted settings (confirm with your PIN or fingerprint).\n" +
                    "3. Come back and tap Notification access, then turn on Bumblebee.",
                color = TextSecondary,
            )
        },
        confirmButton = {
            ChunkyButton(
                text = "Notification access",
                onClick = {
                    onDismiss()
                    openNotificationAccess(context)
                },
                style = ChunkyStyle.Lime,
                compact = true,
            )
        },
        dismissButton = {
            ChunkyButton(text = "App info", onClick = { openAppInfo(context) }, style = ChunkyStyle.Outline, compact = true)
        },
    )
}
