package dev.gdk.bumblebee.ble.android

import android.content.Context

/** Persisted pairing: bike MAC and rider name for a536 identity. */
class BikePrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var bikeMac: String?
        get() = prefs.getString(KEY_MAC, null)
        set(value) {
            prefs.edit().putString(KEY_MAC, value).apply()
        }

    var riderName: String
        get() = prefs.getString(KEY_NAME, DEFAULT_NAME) ?: DEFAULT_NAME
        set(value) {
            prefs.edit().putString(KEY_NAME, value).apply()
        }

    companion object {
        private const val PREFS = "bumblebee_ble"
        private const val KEY_MAC = "bike_mac"
        private const val KEY_NAME = "rider_name"
        const val DEFAULT_NAME = "RIDER"
    }
}
