package dev.gdk.bumblebee.ui.garage.documents

import dev.gdk.bumblebee.ride.CareBand
import dev.gdk.bumblebee.ride.GarageCare
import java.time.LocalDate

data class DocumentCountdown(
    val band: CareBand?,
    val days: Int?,
    val line: String,
)

/** Countdown and band for a paper. Blank expiry is not a due date. Bands match [GarageCare.documentInsight]. */
fun documentCountdown(
    iso: String?,
    today: LocalDate,
    remindDays: Int = 14,
): DocumentCountdown {
    if (iso.isNullOrBlank()) {
        return DocumentCountdown(band = null, days = null, line = "No expiry")
    }
    val insight = GarageCare.documentInsight(iso, today, "", remindDays)
    val days = insight.daysRemaining
    if (insight.band == CareBand.UNKNOWN || days == null) {
        return DocumentCountdown(CareBand.UNKNOWN, null, "Check the date")
    }
    val line = when {
        days < -1 -> "expired ${-days} days ago"
        days == -1 -> "expired 1 day ago"
        days == 0 -> "expires today"
        days == 1 -> "expires in 1 day"
        else -> "expires in $days days"
    }
    return DocumentCountdown(insight.band, days, line)
}
