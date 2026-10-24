package dev.gdk.bumblebee.ui.fuel

import android.content.Context

/** Fuel log settings that live on this phone only. */
class FuelPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Monthly fuel budget in rupees, or null when unset. */
    var monthlyBudget: Double?
        get() = prefs.getFloat(KEY_BUDGET, 0f).takeIf { it > 0f }?.toDouble()
        set(value) {
            prefs.edit().apply {
                if (value == null || value <= 0) remove(KEY_BUDGET) else putFloat(KEY_BUDGET, value.toFloat())
            }.apply()
        }

    private companion object {
        const val NAME = "bumblebee_fuel"
        const val KEY_BUDGET = "monthly_budget"
    }
}
