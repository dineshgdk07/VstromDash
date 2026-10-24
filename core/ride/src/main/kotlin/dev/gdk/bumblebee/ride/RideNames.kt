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

    /**
     * Airport-style three-letter code. The first letter, then consonants: "Bengaluru" → BNG.
     * A multi-word name ends on its last word's initial: "Nandi Hills" → NDH.
     * Short or vowel-heavy names fill up with their remaining letters: "Goa" → GOA.
     */
    fun code(locality: String): String {
        val words = locality.split(Regex("[^\\p{L}]+"))
            .map { it.uppercase(Locale.ENGLISH) }
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return ""
        val first = words.first()
        val out = StringBuilder().append(first[0])
        val want = if (words.size > 1) 2 else 3
        for (ch in first.drop(1)) {
            if (out.length >= want) break
            if (ch !in VOWELS && ch != out.last()) out.append(ch)
        }
        if (words.size > 1) out.append(words.last()[0])
        for (ch in words.joinToString("").drop(1)) {
            if (out.length >= 3) break
            if (out.indexOf(ch.toString(), 1) < 0) out.append(ch)
        }
        return out.toString().take(3)
    }

    private const val VOWELS = "AEIOUY"
}
