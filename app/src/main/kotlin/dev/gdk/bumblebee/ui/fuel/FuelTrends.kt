package dev.gdk.bumblebee.ui.fuel

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A pump the rider fills at, recognised from the fill comment. */
data class StationStats(
    val name: String,
    val fills: Int,
    val rupeesPerLitre: Double?,
    /** km/L of the tanks filled here: each is measured at the fill after it. */
    val kmPerL: Double?,
    val lastLabel: String,
)

data class PricePoint(val label: String, val rupeesPerLitre: Double)

/** Where the month's spend stands against the rider's budget. */
data class BudgetView(
    val budget: Double,
    val spent: Double,
    /** Month-end spend at this month's pace; only for the current month. */
    val projected: Double?,
) {
    val fraction: Float get() = (spent / budget).toFloat().coerceIn(0f, 1f)
    val over: Boolean get() = spent > budget
    val heading: Boolean get() = !over && (projected ?: 0.0) > budget
}

object FuelTrends {
    private val dayFmt = DateTimeFormatter.ofPattern("d MMM")

    private val brands = listOf(
        listOf("hindustan petroleum", "hpcl", "hp") to "HP",
        listOf("indian oil", "iocl", "ioc") to "Indian Oil",
        listOf("bharat petroleum", "bpcl", "bp") to "Bharat Petroleum",
        listOf("jio-bp", "jio bp", "jiobp", "reliance") to "Jio-bp",
        listOf("nayara", "essar") to "Nayara",
        listOf("shell") to "Shell",
    )
    private val generic = listOf("full", "tank", "top up", "topped", "litre", "liter", "partial", "short", "reserve", "half")

    /**
     * The station in a fill comment: its first part, with the brand spelled one way.
     * "hpcl whitefield, full tank" → "HP Whitefield". A comment that only talks about the fill gives null.
     */
    fun station(note: String?): String? {
        val head = note?.split(',', ';', '\n', '.')?.firstOrNull()?.trim()?.replace(Regex("\\s+"), " ") ?: return null
        if (head.isEmpty()) return null
        val lower = head.lowercase(Locale.ENGLISH)
        for ((aliases, brand) in brands) {
            val alias = aliases.firstOrNull { lower == it || lower.startsWith("$it ") } ?: continue
            val rest = head.substring(alias.length).trim()
            return (if (rest.isEmpty()) brand else "$brand ${titleCase(rest)}").take(MAX_NAME)
        }
        if (generic.any { it in lower }) return null
        if (head.split(' ').size > 4) return null
        return titleCase(head).take(MAX_NAME)
    }

    fun stations(entries: List<FuelLogEntry>, zone: ZoneId): List<StationStats> {
        val fills = entries.filter { it.isFill }.sortedBy { it.ts }
        val next = fills.zipWithNext().associate { (a, b) -> a.id to b }
        return fills.mapNotNull { fill -> station(fill.note)?.let { it to fill } }
            .groupBy({ it.first.lowercase(Locale.ENGLISH) }, { it })
            .map { (_, group) ->
                val visits = group.map { it.second }
                val priced = visits.filter { (it.litres ?: 0.0) > 0 && (it.rupees ?: 0.0) > 0 }
                val economy = visits.mapNotNull { next[it.id]?.tankKmPerL }
                StationStats(
                    name = group.last().first,
                    fills = visits.size,
                    rupeesPerLitre = priced.takeIf { it.isNotEmpty() }?.let { p -> p.sumOf { it.rupees!! } / p.sumOf { it.litres!! } },
                    kmPerL = economy.takeIf { it.isNotEmpty() }?.average(),
                    lastLabel = dayFmt.format(Instant.ofEpochMilli(visits.last().ts).atZone(zone)),
                )
            }
            .sortedWith(compareByDescending<StationStats> { it.fills }.thenBy { it.name })
    }

    /** Pump price at each priced fill, oldest first, the last [limit] of them. */
    fun prices(entries: List<FuelLogEntry>, zone: ZoneId, limit: Int = 12): List<PricePoint> =
        entries.filter { it.isFill && it.rupeesPerLitre != null }
            .sortedBy { it.ts }
            .takeLast(limit)
            .map { PricePoint(dayFmt.format(Instant.ofEpochMilli(it.ts).atZone(zone)), it.rupeesPerLitre!!) }

    /** Fills in a row, newest back, logged with both litres and price. */
    fun streak(entries: List<FuelLogEntry>): Int =
        entries.filter { it.isFill }.sortedByDescending { it.ts }.takeWhile { complete(it) }.size

    /** The newest fill when it is missing litres or price, so the page can ask for them. */
    fun incomplete(entries: List<FuelLogEntry>): FuelLogEntry? =
        entries.filter { it.isFill }.maxByOrNull { it.ts }?.takeUnless { complete(it) }

    fun budget(spent: Double?, budget: Double?, month: YearMonth, today: LocalDate): BudgetView? {
        if (budget == null || budget <= 0) return null
        val total = spent ?: 0.0
        val current = YearMonth.from(today) == month
        val projected = if (current && total > 0 && today.dayOfMonth >= 3) total / today.dayOfMonth * month.lengthOfMonth() else null
        return BudgetView(budget, total, projected)
    }

    private fun complete(entry: FuelLogEntry) = (entry.litres ?: 0.0) > 0 && (entry.rupees ?: 0.0) > 0

    private fun titleCase(text: String) = text.split(' ').joinToString(" ") { word ->
        if (word.length <= 3 && word.all { it.isUpperCase() || !it.isLetter() }) word
        else word.lowercase(Locale.ENGLISH).replaceFirstChar { it.titlecase(Locale.ENGLISH) }
    }

    private const val MAX_NAME = 28
}
