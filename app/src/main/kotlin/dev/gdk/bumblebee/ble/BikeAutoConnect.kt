package dev.gdk.bumblebee.ble

import android.companion.CompanionDeviceManager
import android.content.Context
import android.os.Build
import dev.gdk.bumblebee.ble.android.BikePrefs
import java.time.LocalDate

object BikeAutoConnect {
    fun shouldStart(context: Context): Boolean {
        val prefs = BikePrefs(context)
        if (!prefs.autoConnect || prefs.bikeMac.isNullOrBlank()) return false
        return prefs.skipAutoConnectDate != LocalDate.now().toString()
    }

    /** Asks the system to wake us when an already-associated bike appears (Android 12+). */
    fun observe(context: Context) {
        if (!shouldStart(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val mac = BikePrefs(context).bikeMac ?: return
        val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return
        runCatching { cdm.startObservingDevicePresence(mac) }
    }
}
