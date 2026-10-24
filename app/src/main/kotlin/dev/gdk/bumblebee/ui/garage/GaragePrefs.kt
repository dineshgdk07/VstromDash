package dev.gdk.bumblebee.ui.garage

import android.content.Context
import java.time.LocalDate

/** Garage settings that live on this phone only. */
class GaragePrefs(context: Context) {
    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Days the pre-ride check was done, newest kept. */
    val checkDays: Set<LocalDate>
        get() = prefs.getStringSet(KEY_CHECKS, emptySet()).orEmpty()
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()

    fun markChecked(day: LocalDate) {
        val kept = (checkDays + day).sortedDescending().take(KEEP).map { it.toString() }.toSet()
        prefs.edit().putStringSet(KEY_CHECKS, kept).apply()
    }

    private companion object {
        const val NAME = "bumblebee_garage"
        const val KEY_CHECKS = "pre_ride_checks"
        const val KEEP = 120
    }
}
