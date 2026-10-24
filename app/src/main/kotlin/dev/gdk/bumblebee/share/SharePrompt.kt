package dev.gdk.bumblebee.share

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.gdk.bumblebee.MainActivity
import dev.gdk.bumblebee.ride.Highlight

/** Ride-end prompt that opens the share screen for one ride. */
object SharePrompt {
    const val CHANNEL_ID = "ride_highlights"
    const val OPEN_PREFIX = "share:"

    fun notification(context: Context, rideId: Long, highlight: Highlight): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Ride highlights", NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
        }
        val open = PendingIntent.getActivity(
            context,
            rideId.toInt(),
            MainActivity.open(context, "$OPEN_PREFIX$rideId"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(highlight.detail)
            .setContentText("Share it?")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }

    fun parse(open: String?): Long? {
        if (open == null || !open.startsWith(OPEN_PREFIX)) return null
        return open.removePrefix(OPEN_PREFIX).toLongOrNull()
    }
}
