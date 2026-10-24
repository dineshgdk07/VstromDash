package dev.gdk.bumblebee.ui.share

import android.content.Context
import dev.gdk.bumblebee.ride.ShareMetricKind
import dev.gdk.bumblebee.ride.ShareSwitches
import dev.gdk.bumblebee.ride.ShareSwitchesCodec

/** Remembered share settings. Photo is never returned. */
class SharePrefs(context: Context) {
    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun load(available: List<ShareMetricKind>): ShareSwitches =
        ShareSwitchesCodec.decode(prefs.getString(KEY_SWITCHES, null), available)

    fun save(switches: ShareSwitches) {
        prefs.edit().putString(KEY_SWITCHES, ShareSwitchesCodec.encode(switches)).apply()
    }

    var promptEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROMPT, true)
        set(value) {
            prefs.edit().putBoolean(KEY_PROMPT, value).apply()
        }

    private companion object {
        const val NAME = "bumblebee_share"
        const val KEY_SWITCHES = "switches"
        const val KEY_PROMPT = "prompt"
    }
}
