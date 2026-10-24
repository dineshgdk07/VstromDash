package dev.gdk.bumblebee.ride

import java.util.Locale

object RideNames {
    const val MAX_LEN = 30

    /** Trim, collapse inner whitespace, cut to 30 characters. Blank becomes null. */
    fun clean(input: String?): String? {
        if (input == null) return null
        val collapsed = input.trim().replace(Regex("\\s+"), " ")
        if (collapsed.isEmpty()) return null
        return collapsed.take(MAX_LEN)
    }

    fun headline(name: String?, from: String?, to: String?, showPlaces: Boolean, timeOfDay: String): String {
        val named = name?.trim()?.takeIf { it.isNotEmpty() }
        if (named != null) return named
        if (showPlaces && !from.isNullOrBlank() && !to.isNullOrBlank()) return "$from → $to"
        return "$timeOfDay ride"
    }

    /** First three letters (A–Z after dropping non-letters), uppercased. */
    fun code(locality: String): String =
        locality.filter { it.isLetter() }.uppercase(Locale.ENGLISH).take(3)
}
