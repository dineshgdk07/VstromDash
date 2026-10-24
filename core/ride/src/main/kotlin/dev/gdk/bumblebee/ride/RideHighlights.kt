package dev.gdk.bumblebee.ride

/** Badge kinds. [RideHighlights.of] chooses one in Task 2. */
enum class HighlightKind { CLUB_300, CLUB_200, CLUB_100, LONGEST, TOP_SPEED, BIGGEST_CLIMB, LONGEST_SADDLE }

data class Highlight(val kind: HighlightKind, val title: String, val detail: String)
