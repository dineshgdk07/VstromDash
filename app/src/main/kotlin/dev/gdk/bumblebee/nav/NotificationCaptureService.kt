package dev.gdk.bumblebee.nav

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.dev.AdvancedPrefs

class NotificationCaptureService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            activeNotifications?.forEach { onNotificationPosted(it) }
        } catch (_: Throwable) { }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != GoogleMapsParser.PKG_GOOGLE_MAPS) return
        val app = applicationContext as? DashApp ?: return
        if (!app.prefs.mapsNavToCluster) return
        // The app is navigating itself; Maps' notification would fight it for the cluster.
        if (dev.gdk.bumblebee.journey.NavSession.active) return
        MapsNavSource.update(GoogleMapsParser.parse(this, sbn), sbn.key, AdvancedPrefs(this).mapsStaleS * 1000L)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == GoogleMapsParser.PKG_GOOGLE_MAPS && !dev.gdk.bumblebee.journey.NavSession.active) {
            MapsNavSource.clearIf(sbn.key)
        }
    }
}
