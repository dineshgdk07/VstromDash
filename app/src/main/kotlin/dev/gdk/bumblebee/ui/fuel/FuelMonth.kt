package dev.gdk.bumblebee.ui.fuel

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** What the month's pump visits add up to. Nulls mean the fills lacked the numbers. */
data class FuelMonthInsights(
    val fills: Int = 0,
    val drops: Int = 0,
    val spend: Double? = null,
    /** Some fills had no price, so [spend] undercounts. */
    val spendPartial: Boolean = false,
    /** Spend against the month before, in percent. */
    val spendVsPrevPct: Int? = null,
    val litres: Double? = null,
    val rupeesPerLitre: Double? = null,
    /** Latest pump price against the fill before it, in percent. */
    val priceChangePct: Int? = null,
    val km: Int? = null,
    val kmPerL: Double? = null,
    val rupeesPerKm: Double? = null,
    val bestKmPerL: Double? = null,
    val bestLabel: String? = null,
)

/** Calendar marks for one day. */
data class DayMarks(val fills: Int, val drops: Int)

object FuelMonth {
    private val dayFmt = DateTimeFormatter.ofPattern("d MMM")

    fun dateOf(entry: FuelLogEntry, zone: ZoneId): LocalDate = Instant.ofEpochMilli(entry.ts).atZone(zone).toLocalDate()

    /** The month's entries, newest first, narrowed to one day when [selected] is set. */
    fun shown(entries: List<FuelLogEntry>, month: YearMonth, selected: String?, zone: ZoneId): List<FuelLogEntry> =
        entries.filter { entry ->
            val date = dateOf(entry, zone)
            YearMonth.from(date) == month && (selected == null || date.toString() == selected)
        }

    fun marks(entries: List<FuelLogEntry>, month: YearMonth, zone: ZoneId): Map<String, DayMarks> =
        entries.filter { YearMonth.from(dateOf(it, zone)) == month }
            .groupBy { dateOf(it, zone).toString() }
            .mapValues { (_, day) -> DayMarks(fills = day.count { it.isFill }, drops = day.count { !it.isFill }) }

    fun insights(entries: List<FuelLogEntry>, month: YearMonth, zone: ZoneId): FuelMonthInsights {
        val inMonth = entries.filter { YearMonth.from(dateOf(it, zone)) == month }
        val fills = inMonth.filter { it.isFill }
        val priced = fills.mapNotNull { it.rupees?.takeIf { r -> r > 0 } }
        val spend = priced.takeIf { it.isNotEmpty() }?.sum()
        val prevSpend = entries
            .filter { it.isFill && YearMonth.from(dateOf(it, zone)) == month.minusMonths(1) }
            .mapNotNull { it.rupees?.takeIf { r -> r > 0 } }
            .sum()
        val litres = fills.mapNotNull { it.litres?.takeIf { l -> l > 0 } }.takeIf { it.isNotEmpty() }?.sum()
        val bothPrice = fills.filter { (it.litres ?: 0.0) > 0 && (it.rupees ?: 0.0) > 0 }
        val rupeesPerLitre = bothPrice.takeIf { it.isNotEmpty() }?.let { f -> f.sumOf { it.rupees!! } / f.sumOf { it.litres!! } }
        val km = fills.mapNotNull { it.kmSincePrevFill }.takeIf { it.isNotEmpty() }?.sum()
        val economy = fills.filter { it.kmSincePrevFill != null && (it.litres ?: 0.0) > 0 }
        val kmPerL = economy.takeIf { it.isNotEmpty() }?.let { f -> f.sumOf { it.kmSincePrevFill!!.toDouble() } / f.sumOf { it.litres!! } }
        val costed = fills.filter { it.kmSincePrevFill != null && (it.rupees ?: 0.0) > 0 }
        val rupeesPerKm = costed.takeIf { it.isNotEmpty() }?.let { f -> f.sumOf { it.rupees!! } / f.sumOf { it.kmSincePrevFill!!.toDouble() } }
        val best = fills.filter { it.tankKmPerL != null }.maxByOrNull { it.tankKmPerL!! }
        return FuelMonthInsights(
            fills = fills.size,
            drops = inMonth.size - fills.size,
            spend = spend,
            spendPartial = spend != null && priced.size < fills.size,
            spendVsPrevPct = if (spend != null && prevSpend > 0) ((spend - prevSpend) / prevSpend * 100).roundToInt() else null,
            litres = litres,
            rupeesPerLitre = rupeesPerLitre,
            priceChangePct = priceChange(entries, fills),
            km = km,
            kmPerL = kmPerL,
            rupeesPerKm = rupeesPerKm,
            bestKmPerL = best?.tankKmPerL,
            bestLabel = best?.let { dayFmt.format(dateOf(it, zone)) },
        )
    }

    /** The month's latest pump price against the priced fill before it, which may be in an earlier month. */
    private fun priceChange(all: List<FuelLogEntry>, monthFills: List<FuelLogEntry>): Int? {
        val latest = monthFills.filter { it.rupeesPerLitre != null }.maxByOrNull { it.ts } ?: return null
        val before = all.filter { it.isFill && it.rupeesPerLitre != null && it.ts < latest.ts }.maxByOrNull { it.ts } ?: return null
        val prev = before.rupeesPerLitre!!
        if (prev <= 0) return null
        return ((latest.rupeesPerLitre!! - prev) / prev * 100).roundToInt()
    }
}
