package dev.gdk.bumblebee.ui.journey

internal fun prettyDistance(value: String, unit: String): String {
    val trimmed = value.trim().trimStart('0').ifBlank { "0" }
    val number = if (trimmed.startsWith('.')) "0$trimmed" else trimmed
    val suffix = when (unit.uppercase()) {
        "K" -> "km"
        "M" -> "m"
        else -> unit
    }
    return "$number $suffix"
}

internal fun prettyEta(raw: String): String {
    val t = raw.trim()
    if (t.length >= 6 && t.takeLast(2).all { it.isLetter() }) {
        val hh = t.take(2)
        val mm = t.drop(2).take(2)
        return "$hh:$mm ${t.takeLast(2).uppercase()}"
    }
    if (t.length >= 4 && t.all { it.isDigit() }) {
        return "${t.take(2)}:${t.drop(2).take(2)}"
    }
    return t.ifBlank { "—" }
}

internal fun clusterCue(maneuverId: Int): String = when (maneuverId) {
    46 -> "Cluster will hide the arrow"
    else -> "Cluster arrow $maneuverId"
}
