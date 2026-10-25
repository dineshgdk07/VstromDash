package dev.gdk.bumblebee.dev

import android.content.Context

/**
 * Tunables most riders never touch, behind Settings → Advanced. Every value is clamped on read so
 * a bad edit can't break riding.
 */
class AdvancedPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Clock style for the cluster's arrival time. */
    var clock24h: Boolean
        get() = prefs.getBoolean(KEY_CLOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_CLOCK, value).apply()

    /** Seconds the crash alarm counts down before texting contacts. */
    var crashCountdownS: Int
        get() = prefs.getInt(KEY_COUNTDOWN, 30).coerceIn(COUNTDOWN_CHOICES.first(), COUNTDOWN_CHOICES.last())
        set(value) = prefs.edit().putInt(KEY_COUNTDOWN, value).apply()

    /** How far off the route line counts as off route, in metres. */
    var offRouteM: Int
        get() = prefs.getInt(KEY_OFF_M, 60).coerceIn(OFF_ROUTE_CHOICES.first(), OFF_ROUTE_CHOICES.last())
        set(value) = prefs.edit().putInt(KEY_OFF_M, value).apply()

    /** How long the rider must stay off route before a re-route, in seconds. */
    var rerouteDelayS: Int
        get() = prefs.getInt(KEY_REROUTE_S, 8).coerceIn(REROUTE_CHOICES.first(), REROUTE_CHOICES.last())
        set(value) = prefs.edit().putInt(KEY_REROUTE_S, value).apply()

    /** How long a Maps step stays on the cluster after the notification goes quiet, in seconds. */
    var mapsStaleS: Int
        get() = prefs.getInt(KEY_STALE_S, 60).coerceIn(STALE_CHOICES.first(), STALE_CHOICES.last())
        set(value) = prefs.edit().putInt(KEY_STALE_S, value).apply()

    /** Map tile disk cache limit, in MB. */
    var tileCacheMb: Int
        get() = prefs.getInt(KEY_TILES_MB, 200).coerceIn(TILE_CHOICES.first(), TILE_CHOICES.last())
        set(value) = prefs.edit().putInt(KEY_TILES_MB, value).apply()

    /** Developer section shown, after seven taps on the version line. */
    var devUnlocked: Boolean
        get() = prefs.getBoolean(KEY_DEV, false)
        set(value) = prefs.edit().putBoolean(KEY_DEV, value).apply()

    /** Keep raw BLE frames in the developer log; off by default because it's chatty. */
    var logFrames: Boolean
        get() = prefs.getBoolean(KEY_FRAMES, false)
        set(value) = prefs.edit().putBoolean(KEY_FRAMES, value).apply()

    /** Every tunable at once, as a stable value for the settings screen. */
    data class Values(
        val clock24h: Boolean,
        val crashCountdownS: Int,
        val offRouteM: Int,
        val rerouteDelayS: Int,
        val mapsStaleS: Int,
        val tileCacheMb: Int,
    )

    fun snapshot() = Values(clock24h, crashCountdownS, offRouteM, rerouteDelayS, mapsStaleS, tileCacheMb)

    fun resetTunables() {
        prefs.edit()
            .remove(KEY_CLOCK).remove(KEY_COUNTDOWN).remove(KEY_OFF_M)
            .remove(KEY_REROUTE_S).remove(KEY_STALE_S).remove(KEY_TILES_MB)
            .apply()
    }

    companion object {
        private const val NAME = "advanced"
        private const val KEY_CLOCK = "clock24h"
        private const val KEY_COUNTDOWN = "crash_countdown_s"
        private const val KEY_OFF_M = "off_route_m"
        private const val KEY_REROUTE_S = "reroute_delay_s"
        private const val KEY_STALE_S = "maps_stale_s"
        private const val KEY_TILES_MB = "tile_cache_mb"
        private const val KEY_DEV = "dev_unlocked"
        private const val KEY_FRAMES = "log_frames"

        val COUNTDOWN_CHOICES = listOf(15, 20, 30, 45, 60)
        val OFF_ROUTE_CHOICES = listOf(30, 45, 60, 90, 120)
        val REROUTE_CHOICES = listOf(4, 8, 12, 20)
        val STALE_CHOICES = listOf(30, 60, 120, 300)
        val TILE_CHOICES = listOf(50, 100, 200, 500)
    }
}
