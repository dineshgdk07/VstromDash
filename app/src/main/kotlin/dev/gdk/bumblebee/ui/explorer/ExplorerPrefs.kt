package dev.gdk.bumblebee.ui.explorer

import android.content.Context

/** Explorer settings that live on this phone only. */
class ExplorerPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Monthly riding goal in km, or null when unset. */
    var monthlyGoalKm: Double?
        get() = prefs.getFloat(KEY_GOAL, 0f).takeIf { it > 0f }?.toDouble()
        set(value) {
            prefs.edit().apply {
                if (value == null || value <= 0) remove(KEY_GOAL) else putFloat(KEY_GOAL, value.toFloat())
            }.apply()
        }

    private companion object {
        const val NAME = "bumblebee_explorer"
        const val KEY_GOAL = "monthly_goal_km"
    }
}
