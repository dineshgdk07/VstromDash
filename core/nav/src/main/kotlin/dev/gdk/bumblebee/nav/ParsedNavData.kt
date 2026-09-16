package dev.gdk.bumblebee.nav

import dev.gdk.bumblebee.protocol.NavFrame
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
    """(\d+(?:[.,]\d+)?)\s*(km|mi|ft|m)\b""",
    RegexOption.IGNORE_CASE,
)

fun normalizeDistance(input: String?): Pair<String, String> {
    if (input.isNullOrBlank()) return "0000" to "M"
    val match = DISTANCE_REGEX.find(input) ?: return "0000" to "M"
    val numberRaw = match.groupValues[1].replace(',', '.')
    val unitRaw = match.groupValues[2].lowercase(Locale.ROOT)
    val unit = when (unitRaw) {
        "km", "mi" -> "K"
        else -> "M"
    }
    val numberStr: String = when {
        !numberRaw.contains('.') -> {
            val n = numberRaw.toIntOrNull() ?: 0
            "%04d".format(n.coerceIn(0, 9999))
        }
        else -> {
            val parts = numberRaw.split('.')
            val whole = parts[0].toIntOrNull() ?: 0
            val frac = parts.getOrNull(1)?.firstOrNull()?.toString() ?: "0"
            val candidate = "%02d.%s".format(whole.coerceAtMost(99), frac)
            if (candidate.length == 4) candidate else "%04d".format(whole.coerceIn(0, 9999))
        }
    }
    return numberStr to unit
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
        "%02d%02d%s".format(hour12, minute, ampm)
    } else {
        "%02d%02d00".format(hour24, minute)
    }
}

fun extractTotalDistance(timeRaw: String?): Pair<String, String> {
    if (timeRaw.isNullOrBlank()) return "0000" to "M"
    val distRegex = Regex("""(\d+(?:[.,]\d+)?)\s*(km|m|mi|ft)""", RegexOption.IGNORE_CASE)
    val match = distRegex.find(timeRaw) ?: return "0000" to "M"
    return normalizeDistance(match.value)
}
