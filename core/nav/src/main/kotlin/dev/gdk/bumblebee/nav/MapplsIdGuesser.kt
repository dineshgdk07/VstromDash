package dev.gdk.bumblebee.nav

/** Google Maps instruction text → Mappls maneuver ID, via [ManeuverText]. */
object MapplsIdGuesser {
    const val DEFAULT_MAPPLS_ID = 7

    fun fromText(instruction: String?): Int = ManeuverText.classify(instruction)?.mapplsId ?: DEFAULT_MAPPLS_ID
}
