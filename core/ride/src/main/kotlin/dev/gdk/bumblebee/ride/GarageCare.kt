package dev.gdk.bumblebee.ride

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

enum class CareBand { OVERDUE, DUE_SOON, OK, UNKNOWN }

data class DocumentInsight(
    val band: CareBand,
    val label: String,
    val daysRemaining: Int?,
)

data class PlanInsight(
    val title: String,
    val band: CareBand,
    val label: String,
    val kmRemaining: Int?,
    val daysRemaining: Int?,
)

object GarageCare {

    fun documentInsight(
        expiryIso: String?,
        today: LocalDate,
        title: String,
        remindDays: Int = 14,
    ): DocumentInsight {
        if (expiryIso.isNullOrBlank()) {
            return DocumentInsight(CareBand.OK, "$title on file · no expiry", null)
        }
        val date = runCatching { LocalDate.parse(expiryIso.trim()) }.getOrNull()
            ?: return DocumentInsight(CareBand.UNKNOWN, "$title · use yyyy-MM-dd", null)
        val days = ChronoUnit.DAYS.between(today, date).toInt()
        return when {
            days < 0 -> DocumentInsight(CareBand.OVERDUE, "$title expired ${-days} days ago", days)
            days == 0 -> DocumentInsight(CareBand.OVERDUE, "$title expires today", 0)
            days <= remindDays -> DocumentInsight(CareBand.DUE_SOON, "$title renew in $days days", days)
            else -> DocumentInsight(CareBand.OK, "$title good for $days days", days)
        }
    }

    fun planInsight(
        title: String,
        currentOdoKm: Int?,
        lastDoneOdoKm: Int?,
        intervalKm: Int?,
        lastDoneOn: LocalDate?,
        intervalDays: Int?,
        dueOn: LocalDate?,
        today: LocalDate,
        kmPerDay: Double,
        remindKm: Int = 200,
        remindDays: Int = 14,
    ): PlanInsight {
        val kmLeft = if (intervalKm != null && intervalKm > 0 && lastDoneOdoKm != null && currentOdoKm != null) {
            lastDoneOdoKm + intervalKm - currentOdoKm
        } else {
            null
        }
        val fromRecurringDays = if (intervalDays != null && intervalDays > 0 && lastDoneOn != null) {
            ChronoUnit.DAYS.between(today, lastDoneOn.plusDays(intervalDays.toLong())).toInt()
        } else {
            null
        }
        val fromDate = dueOn?.let { ChronoUnit.DAYS.between(today, it).toInt() }
        val daysFromKm = if (kmLeft != null && kmPerDay > 0) ceil(kmLeft / kmPerDay).toInt() else null
        val daysLeft = listOfNotNull(fromRecurringDays, fromDate, daysFromKm).minOrNull()
        val band = when {
            (kmLeft != null && kmLeft <= 0) || (daysLeft != null && daysLeft <= 0) -> CareBand.OVERDUE
            (kmLeft != null && kmLeft <= remindKm) || (daysLeft != null && daysLeft <= remindDays) -> CareBand.DUE_SOON
            kmLeft == null && daysLeft == null -> CareBand.UNKNOWN
            else -> CareBand.OK
        }
        val label = buildString {
            append(title)
            append(" · ")
            append(
                when (band) {
                    CareBand.OVERDUE -> when {
                        kmLeft != null && kmLeft <= 0 -> "${-kmLeft} km overdue"
                        daysLeft != null && daysLeft <= 0 -> "due ${-daysLeft} days ago"
                        else -> "due now"
                    }
                    CareBand.DUE_SOON, CareBand.OK -> {
                        val bits = buildList {
                            kmLeft?.let { add("$it km") }
                            daysLeft?.let { add(if (it == 1) "1 day" else "$it days") }
                        }
                        if (bits.isEmpty()) "scheduled" else bits.joinToString(" · ")
                    }
                    CareBand.UNKNOWN -> "set km, a date, or last done"
                },
            )
        }
        return PlanInsight(title, band, label, kmLeft, daysLeft)
    }

    fun shouldRemind(
        band: CareBand,
        kmRemaining: Int?,
        daysRemaining: Int?,
        remindKm: Int,
        remindDays: Int,
    ): Boolean = when (band) {
        CareBand.OVERDUE, CareBand.DUE_SOON -> true
        CareBand.OK -> (kmRemaining != null && kmRemaining <= remindKm) ||
            (daysRemaining != null && daysRemaining <= remindDays)
        CareBand.UNKNOWN -> false
    }

    fun historyStory(item: String, odoKm: Int, rupees: Double?, shop: String?, kmSincePrev: Int?): String {
        val bits = mutableListOf<String>()
        bits += item.replaceFirstChar { it.uppercase() }
        shop?.takeIf { it.isNotBlank() }?.let { bits += "at $it" }
        bits += "%,d km".format(odoKm)
        kmSincePrev?.let { bits += "$it km since last" }
        rupees?.takeIf { it > 0 }?.let { bits += "₹${"%.0f".format(it)}" }
        return bits.joinToString(" · ")
    }
}
