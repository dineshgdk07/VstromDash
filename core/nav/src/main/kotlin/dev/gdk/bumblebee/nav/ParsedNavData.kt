package dev.gdk.bumblebee.nav

import dev.gdk.bumblebee.protocol.NavFrame
import dev.gdk.bumblebee.protocol.rootFormat
import java.time.LocalTime
import java.util.Locale

data class ParsedNavData(
    val maneuverId: Int,
    val distNext: String,
    val distNextUnit: String,
    val eta: String,
    val distTotal: String,
    val distTotalUnit: String,
    val streetName: String?,
)

fun ParsedNavData.toNavFrame(): NavFrame = NavFrame(
    maneuverId = maneuverId,
    distNext = distNext,
    distNextUnit = distNextUnit,
    eta = eta,
    distTotal = distTotal,
    distTotalUnit = distTotalUnit,
    status = "1",
    continueFlag = "1",
)

private val DISTANCE_REGEX = Regex(
    """(\d+(?:[.,]\d+)?)\s*(kilometers?|km|miles?|mi|meters?|m|feet?|ft)\b""",
    RegexOption.IGNORE_CASE,
)

fun normalizeDistance(input: String?): Pair<String, String> {
    if (input.isNullOrBlank()) return "0000" to "M"
    val match = DISTANCE_REGEX.find(input) ?: return "0000" to "M"
    val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return "0000" to "M"
    val unitRaw = match.groupValues[2].lowercase(Locale.ROOT)
    val meters = when {
        unitRaw.startsWith("km") || unitRaw.startsWith("kilometer") -> amount * 1000.0
        unitRaw.startsWith("mi") || unitRaw.startsWith("mile") -> amount * 1609.34
        unitRaw.startsWith("ft") || unitRaw.startsWith("feet") || unitRaw == "foot" -> amount * 0.3048
        else -> amount
    }
    return formatClusterDistance(meters)
}

fun formatClusterDistance(meters: Double): Pair<String, String> {
    val m = meters.coerceAtLeast(0.0)
    return if (m >= 1000.0) {
        val km = m / 1000.0
        val encoded = if (km < 100.0) {
            rootFormat("%04.1f", km.coerceAtMost(99.9))
        } else {
            rootFormat("%04d", km.toInt().coerceAtMost(9999))
        }
        encoded to "K"
    } else {
        rootFormat("%04d", m.toInt().coerceAtMost(9999)) to "M"
    }
}

/** Rider-facing distance from a 4-char cluster field. */
fun formatHudDistance(dist: String, unit: String): String {
    val u = unit.uppercase(Locale.ROOT)
    if (u == "M") {
        val meters = dist.trim().toIntOrNull() ?: dist.trimStart('0').ifBlank { "0" }.toIntOrNull() ?: 0
        return "$meters m"
    }
    val km = dist.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
    return if (km < 10.0) rootFormat("%.1f km", km) else rootFormat("%.0f km", km)
}

private val CLOCK_REGEX = Regex(
    """\b(\d{1,2}):(\d{2})\s*(am|pm)?\b""",
    RegexOption.IGNORE_CASE,
)

private val MINUTES_REGEX = Regex("""\b(\d+)\s*min\b""", RegexOption.IGNORE_CASE)

fun normalizeEta(remainingText: String?, twelveHour: Boolean, now: LocalTime = LocalTime.now()): String {
    val clockMatch = remainingText?.let { CLOCK_REGEX.find(it) }
    val (hour24, minute) = if (clockMatch != null) {
        val rawHour = clockMatch.groupValues[1].toInt()
        val minutes = clockMatch.groupValues[2].toInt()
        val ampm = clockMatch.groupValues[3].lowercase(Locale.ROOT)
        val h24 = when (ampm) {
            "am" -> if (rawHour == 12) 0 else rawHour
            "pm" -> if (rawHour == 12) 12 else rawHour + 12
            else -> rawHour
        }
        h24 to minutes
    } else {
        val minMatch = remainingText?.let { MINUTES_REGEX.find(it) }
        val addMin = minMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val t = now.plusMinutes(addMin.toLong())
        t.hour to t.minute
    }
    return if (twelveHour) {
        val hour12 = ((hour24 + 11) % 12) + 1
        val ampm = if (hour24 < 12) "AM" else "PM"
        rootFormat("%02d%02d%s", hour12, minute, ampm)
    } else {
        rootFormat("%02d%02d00", hour24, minute)
    }
}

fun extractTotalDistance(timeRaw: String?): Pair<String, String> {
    if (timeRaw.isNullOrBlank()) return "0000" to "M"
    val distRegex = Regex("""(\d+(?:[.,]\d+)?)\s*(kilometers?|km|miles?|mi|meters?|m|feet?|ft)\b""", RegexOption.IGNORE_CASE)
    val match = distRegex.find(timeRaw) ?: return "0000" to "M"
    return normalizeDistance(match.value)
}
