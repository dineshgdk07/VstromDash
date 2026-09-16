package dev.gdk.bumblebee.ride

enum class SpeedBand { IDLE, B1_30, B30_50, B50_70, B70_90, B90_PLUS }

/** Seconds spent per speed band. Bands: idle <3, [3,30), [30,50), [50,70), [70,90), >=90 km/h. */
class SpeedHistogram {
    private val seconds = IntArray(SpeedBand.entries.size)

    fun add(speedKmh: Int, durationSec: Int) {
        val band = when {
            speedKmh < 3 -> SpeedBand.IDLE
            speedKmh < 30 -> SpeedBand.B1_30
            speedKmh < 50 -> SpeedBand.B30_50
            speedKmh < 70 -> SpeedBand.B50_70
            speedKmh < 90 -> SpeedBand.B70_90
            else -> SpeedBand.B90_PLUS
        }
        seconds[band.ordinal] += durationSec
    }

    fun secondsIn(band: SpeedBand): Int = seconds[band.ordinal]

    /** Stable JSON: {"IDLE":70,"B1_30":30,...} — hand-rolled to avoid a JSON dependency. */
    fun toJson(): String = SpeedBand.entries.joinToString(
        separator = ",", prefix = "{", postfix = "}",
    ) { "\"${it.name}\":${seconds[it.ordinal]}" }

    companion object {
        fun fromJson(json: String): SpeedHistogram {
            val h = SpeedHistogram()
            Regex("\"([A-Z0-9_]+)\":(\\d+)").findAll(json).forEach { m ->
                val band = SpeedBand.entries.firstOrNull { it.name == m.groupValues[1] } ?: return@forEach
                h.seconds[band.ordinal] = m.groupValues[2].toInt()
            }
            return h
        }
    }
}
