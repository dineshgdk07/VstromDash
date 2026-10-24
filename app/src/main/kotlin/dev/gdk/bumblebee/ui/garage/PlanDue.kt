package dev.gdk.bumblebee.ui.garage

import dev.gdk.bumblebee.ui.components.StatusChipTone
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

data class PlanDue(
    val usedLabel: String,
    val totalLabel: String,
    val fraction: Float,
    val tone: StatusChipTone,
    val caption: String,
)

/**
 * Picks the interval closer to due. A bare due date has a caption and no bar.
 * Riding pace is not part of this choice.
 */
fun planDue(
    currentOdoKm: Int?,
    lastDoneOdoKm: Int?,
    intervalKm: Int?,
    lastDoneOn: LocalDate?,
    intervalDays: Int?,
    dueOn: LocalDate?,
    today: LocalDate,
    remindKm: Int = 200,
    remindDays: Int = 14,
): PlanDue {
    val km = kmAxis(currentOdoKm, lastDoneOdoKm, intervalKm)
    val days = dayAxis(lastDoneOn, intervalDays, today)
    val chosen = listOfNotNull(km, days).maxByOrNull { it.used.toFloat() / it.total.toFloat() }
    if (chosen != null) {
        val window = if (chosen.km) remindKm else remindDays
        return PlanDue(
            usedLabel = grouped(chosen.used),
            totalLabel = if (chosen.km) "${grouped(chosen.total)} km" else "${grouped(chosen.total)} days",
            fraction = (chosen.used.toFloat() / chosen.total.toFloat()).coerceIn(0f, 1f),
            tone = toneFor(chosen.left, window),
            caption = if (chosen.km) kmCaption(chosen.left) else dayCaption(chosen.left),
        )
    }
    if (dueOn != null) {
        val left = ChronoUnit.DAYS.between(today, dueOn).toInt()
        return PlanDue("—", "—", 0f, toneFor(left, remindDays), dayCaption(left))
    }
    return PlanDue("—", "—", 0f, StatusChipTone.NoExpiry, "Set km or a date")
}

private data class DueAxis(val used: Int, val total: Int, val left: Int, val km: Boolean)

private fun kmAxis(currentOdoKm: Int?, lastDoneOdoKm: Int?, intervalKm: Int?): DueAxis? {
    if (intervalKm == null || intervalKm <= 0 || lastDoneOdoKm == null || currentOdoKm == null) return null
    val used = currentOdoKm - lastDoneOdoKm
    return DueAxis(used = used, total = intervalKm, left = intervalKm - used, km = true)
}

private fun dayAxis(lastDoneOn: LocalDate?, intervalDays: Int?, today: LocalDate): DueAxis? {
    if (intervalDays == null || intervalDays <= 0 || lastDoneOn == null) return null
    val used = ChronoUnit.DAYS.between(lastDoneOn, today).toInt()
    return DueAxis(used = used, total = intervalDays, left = intervalDays - used, km = false)
}

private fun toneFor(left: Int, window: Int): StatusChipTone = when {
    left <= 0 -> StatusChipTone.Overdue
    left <= window -> StatusChipTone.DueSoon
    else -> StatusChipTone.Ok
}

private fun kmCaption(left: Int): String = when {
    left > 1 -> "due in ${grouped(left)} km"
    left == 1 -> "due in 1 km"
    left == 0 -> "due now"
    left == -1 -> "1 km overdue"
    else -> "${grouped(-left)} km overdue"
}

private fun dayCaption(left: Int): String = when {
    left > 1 -> "due in $left days"
    left == 1 -> "due in 1 day"
    left == 0 -> "due today"
    left == -1 -> "due 1 day ago"
    else -> "due ${-left} days ago"
}

private fun grouped(value: Int): String = String.format(Locale.US, "%,d", value)
