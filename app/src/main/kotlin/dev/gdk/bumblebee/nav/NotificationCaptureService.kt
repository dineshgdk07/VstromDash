package dev.gdk.bumblebee.nav

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.gdk.bumblebee.DashApp

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
        MapsNavSource.update(GoogleMapsParser.parse(sbn), sbn.key)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == GoogleMapsParser.PKG_GOOGLE_MAPS) {
            MapsNavSource.clearIf(sbn.key)
        }
    }
}
