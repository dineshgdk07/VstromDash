package dev.gdk.bumblebee.ui.garage

import android.content.Context
import dev.gdk.bumblebee.ride.PartBaseline
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

    /** When and at what odometer the bike was bought; original parts count from here. */
    var purchase: PartBaseline?
        get() = decode(prefs.getString(KEY_PURCHASE, null))
        set(value) {
            prefs.edit().apply { if (value == null) remove(KEY_PURCHASE) else putString(KEY_PURCHASE, encode(value)) }.apply()
        }

    /** The rider's own "last replaced" and life for each part, by part name. */
    fun partBaselines(titles: List<String>): Map<String, PartBaseline> =
        titles.mapNotNull { t -> decode(prefs.getString(KEY_PART + t, null))?.let { t to it } }.toMap()

    fun setPartBaseline(title: String, value: PartBaseline?) {
        prefs.edit().apply { if (value == null) remove(KEY_PART + title) else putString(KEY_PART + title, encode(value)) }.apply()
    }

    private fun encode(b: PartBaseline) = listOf(b.odoKm?.toString().orEmpty(), b.date?.toString().orEmpty(), b.life?.toString().orEmpty()).joinToString("|")

    private fun decode(raw: String?): PartBaseline? {
        val bits = raw?.split('|') ?: return null
        if (bits.size < 3) return null
        return PartBaseline(
            odoKm = bits[0].toIntOrNull(),
            date = bits[1].takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            life = bits[2].toIntOrNull(),
        )
    }

    private companion object {
        const val NAME = "bumblebee_garage"
        const val KEY_CHECKS = "pre_ride_checks"
        const val KEEP = 120
        const val KEY_PURCHASE = "purchase"
        const val KEY_PART = "part_"
    }
}
