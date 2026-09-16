package dev.gdk.bumblebee.nav

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationCaptureService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            activeNotifications?.forEach { onNotificationPosted(it) }
        } catch (_: Throwable) { }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != GoogleMapsParser.PKG_GOOGLE_MAPS) return
        MapsNavSource.update(GoogleMapsParser.parse(sbn))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == GoogleMapsParser.PKG_GOOGLE_MAPS) MapsNavSource.clear()
    }
}
