package dev.gdk.bumblebee.ride

import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class MonthSpend(val month: YearMonth, val fuel: Double, val service: Double) {
    val total: Double get() = fuel + service
}

/** What the bike costs to keep on the road. */
data class Ownership(
    val rupeesPerKm: Double?,
    val fuelPerKm: Double?,
    val servicePerKm: Double?,
    val fuelFraction: Double,
    val thisYear: Double,
    /** A year of spend at the current rate, or null with too little history. */
    val projectedYear: Double?,
    val months: List<MonthSpend>,
    val partial: Boolean,
)

/** One upcoming care item, on a date. */
data class CareDate(val title: String, val date: LocalDate, val overdue: Boolean, val fromPace: Boolean)

/** Where a part's wear is counted from. */
enum class PartSince { LOGGED, SET, PURCHASE, FIRST_READING }

/** A rider's fix for one part: when it was last replaced and, optionally, how long it lasts. */
data class PartBaseline(val odoKm: Int? = null, val date: LocalDate? = null, val life: Int? = null)

/** A wearing part: km or days used against a typical life. */
data class PartWear(
    val title: String,
    val used: Int,
    val life: Int,
    val days: Boolean,
    val since: PartSince,
    val sinceOdoKm: Int? = null,
    val sinceDate: LocalDate? = null,
) {
    val fraction: Float get() = if (life <= 0) 0f else used.toFloat() / life
    val left: Int get() = life - used

    /** True when the count starts from something real, not just the first reading in the app. */
    val logged: Boolean get() = since != PartSince.FIRST_READING
}

data class ChainLube(val kmSince: Int, val wetKm: Int, val due: Boolean, val message: String)

data class EfficiencyAlert(val latestKmPerL: Double, val usualKmPerL: Double, val drop: Double, val causes: List<String>)

data class HealthReason(val points: Int, val text: String)

data class BikeHealth(val score: Int, val label: String, val reasons: List<HealthReason>)

data class GarageYear(
    val year: Int,
    val km: Double,
    val fuel: Double,
    val service: Double,
    val services: Int,
    val fills: Int,
    val litres: Double,
    val bestKmPerL: Double?,
    val avgKmPerL: Double?,
) {
    val spent: Double get() = fuel + service
    val rupeesPerKm: Double? get() = if (km > 0 && spent > 0) spent / km else null
}

data class PlannedCost(val title: String, val date: LocalDate, val rupees: Double?)

data class CostPlan(val items: List<PlannedCost>, val fuelRupees: Double?, val horizonDays: Int) {
    val total: Double get() = items.sumOf { it.rupees ?: 0.0 } + (fuelRupees ?: 0.0)
    val unpriced: Int get() = items.count { it.rupees == null }
}

data class OdoStory(
    val firstOdoKm: Int?,
    val lastOdoKm: Int?,
    val since: LocalDate?,
    val loggedKm: Double,
    val months: List<MonthTotal>,
    val busiestMonth: Month?,
    val avgPerMonth: Double?,
)

enum class PassportKind { SERVICE, FUEL, DOCUMENT, MILESTONE, TRIP, START }

data class PassportEvent(
    val ts: Long,
    val kind: PassportKind,
    val title: String,
    val detail: String?,
    val rideId: Long? = null,
)

/** A finished ride, as the passport and chain nudge read it. */
data class GarageRide(
    val id: Long,
    val startTs: Long,
    val km: Double,
    val label: String,
    val odoEndKm: Int?,
    val weatherCode: Int?,
)

/** A plan as the Garage summaries read it. */
data class PlanState(
    val title: String,
    val kind: String,
    val band: CareBand,
    val kmLeft: Int?,
    val daysLeft: Int?,
    val lastDoneOdoKm: Int?,
    val lastDoneOn: LocalDate?,
    val intervalKm: Int?,
)

/** Pure numbers behind the Garage tab. */
object GarageInsights {
    private val RAIN = (51..67).toSet() + (80..82) + (95..99)

    fun isWet(code: Int?): Boolean = code != null && code in RAIN

    private fun dateOf(ts: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()

    // ---- Cost of ownership ----

    fun monthlySpend(
        fills: List<FuelFill>,
        services: List<ServiceSpend>,
        until: YearMonth,
        count: Int,
        zone: ZoneId,
    ): List<MonthSpend> {
        val fuel = fills.filter { (it.rupees ?: 0.0) > 0 }.groupBy { YearMonth.from(dateOf(it.ts, zone)) }
        val svc = services.filter { (it.rupees ?: 0.0) > 0 }.groupBy { YearMonth.from(dateOf(it.ts, zone)) }
        return (count - 1 downTo 0).map { back ->
            val m = until.minusMonths(back.toLong())
            MonthSpend(m, fuel[m].orEmpty().sumOf { it.rupees ?: 0.0 }, svc[m].orEmpty().sumOf { it.rupees ?: 0.0 })
        }
    }

    fun ownership(
        fills: List<FuelFill>,
        services: List<ServiceSpend>,
        fallbackKm: Int?,
        today: LocalDate,
        zone: ZoneId,
    ): Ownership? {
        val running = RunningCostAnalytics.cost(fills, services, fallbackKm)
        val dated = fills.filter { (it.rupees ?: 0.0) > 0 }.map { it.ts to it.rupees!! } +
            services.filter { (it.rupees ?: 0.0) > 0 }.map { it.ts to it.rupees!! }
        if (running == null && dated.isEmpty()) return null
        val year = dated.filter { dateOf(it.first, zone).year == today.year }.sumOf { it.second }
        val first = dated.minOfOrNull { it.first }?.let { dateOf(it, zone) }
        val projected = first?.let {
            val days = ChronoUnit.DAYS.between(it, today).toInt() + 1
            when {
                days < 30 -> null
                days >= 365 -> dated.filter { d -> !dateOf(d.first, zone).isBefore(today.minusDays(364)) }.sumOf { d -> d.second }
                else -> dated.sumOf { d -> d.second } / days * 365
            }
        }
        return Ownership(
            rupeesPerKm = running?.rupeesPerKm,
            fuelPerKm = running?.let { it.fuelRupees / it.distanceKm },
            servicePerKm = running?.let { it.serviceRupees / it.distanceKm },
            fuelFraction = running?.fuelFraction ?: 1.0,
            thisYear = year,
            projectedYear = projected,
            months = monthlySpend(fills, services, YearMonth.from(today), 6, zone),
            partial = running?.let { it.fuelFillsPriced < it.fuelFillsTotal || it.servicesPriced < it.servicesTotal } ?: false,
        )
    }

    // ---- Service forecast ----

    /** "around 12 Nov" for a pace estimate, "on 12 Nov" for a fixed date. */
    fun forecastLabel(date: LocalDate, fromPace: Boolean, today: LocalDate): String {
        val fmt = if (date.year == today.year) "d MMM" else "d MMM yyyy"
        val d = date.format(DateTimeFormatter.ofPattern(fmt, Locale.ENGLISH))
        return when {
            !date.isAfter(today) -> "due now"
            fromPace -> "around $d"
            else -> "on $d"
        }
    }

    /** When each plan falls due. Pace decides km plans; the nearer of km and date wins. */
    fun careDates(plans: List<PlanState>, kmPerDay: Double, today: LocalDate): List<CareDate> = plans.mapNotNull { p ->
        val fromKm = p.kmLeft?.let { left ->
            when {
                left <= 0 -> 0
                kmPerDay > 0 -> kotlin.math.ceil(left / kmPerDay).toInt()
                else -> null
            }
        }
        val byDays = p.daysLeft
        val days = listOfNotNull(fromKm, byDays).minOrNull() ?: return@mapNotNull null
        val pace = fromKm != null && (byDays == null || fromKm <= byDays) && (p.kmLeft ?: 0) > 0
        CareDate(p.title, today.plusDays(days.coerceAtLeast(0).toLong()), days <= 0, pace)
    }.sortedBy { it.date }

    // ---- Parts life ----

    private data class PartSpec(val title: String, val lifeKm: Int?, val lifeDays: Int?, val matches: (String) -> Boolean)

    private fun tyre(t: String) = (t.contains("tyre") || t.contains("tire")) &&
        !t.contains("pressure") && !t.contains("puncture") && !t.contains("air")

    private val PARTS = listOf(
        PartSpec("Chain & sprockets", 20_000, null) { t ->
            t.contains("sprocket") || (t.contains("chain") && (t.contains("kit") || t.contains("replace") || t.contains("new") || t.contains("set")))
        },
        PartSpec("Front tyre", 25_000, null) { t -> tyre(t) && !t.contains("rear") && !t.contains("back") },
        PartSpec("Rear tyre", 18_000, null) { t -> tyre(t) && !t.contains("front") },
        PartSpec("Brake pads", 15_000, null) { t -> t.contains("pad") },
        PartSpec("Spark plug", 10_000, null) { t -> t.contains("plug") },
        PartSpec("Air filter", 12_000, null) { t -> t.contains("air filter") },
        PartSpec("Battery", null, 3 * 365) { t -> t.contains("battery") && !t.contains("charg") },
    )

    /** Part names, in the order the Parts tab lists them. */
    val PART_TITLES: List<String> get() = PARTS.map { it.title }

    /** "Engine oil, Air filter + spark plug" → its separate jobs. */
    fun workItems(item: String): List<String> =
        item.split(',', ';', '+', '&', '/', '·', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    private fun matchesPart(spec: PartSpec, item: String): Boolean =
        workItems(item.lowercase(Locale.ENGLISH)).any(spec.matches)

    /**
     * Wear for each part, counted from the latest of: a logged replacement, the rider's own
     * "last replaced" fix, the purchase (original parts), or the first reading in the app.
     */
    fun partsLife(
        services: List<ServiceSpend>,
        currentOdoKm: Int?,
        firstOdoKm: Int?,
        firstTs: Long?,
        today: LocalDate,
        zone: ZoneId,
        baselines: Map<String, PartBaseline> = emptyMap(),
        purchase: PartBaseline? = null,
    ): List<PartWear> {
        val newest = services.sortedByDescending { it.ts }
        return PARTS.mapNotNull { spec ->
            val log = newest.firstOrNull { matchesPart(spec, it.item) }
            val set = baselines[spec.title]
            val life = set?.life?.takeIf { it > 0 } ?: spec.lifeKm ?: spec.lifeDays!!
            if (spec.lifeDays != null) {
                val candidates = listOfNotNull(
                    log?.let { PartSince.LOGGED to dateOf(it.ts, zone) },
                    set?.date?.let { PartSince.SET to it },
                )
                val (since, from) = candidates.maxByOrNull { it.second }
                    ?: purchase?.date?.let { PartSince.PURCHASE to it }
                    ?: firstTs?.let { PartSince.FIRST_READING to dateOf(it, zone) }
                    ?: return@mapNotNull null
                val used = ChronoUnit.DAYS.between(from, today).toInt().coerceAtLeast(0)
                PartWear(spec.title, used, life, days = true, since = since, sinceDate = from)
            } else {
                val odo = currentOdoKm ?: return@mapNotNull null
                val candidates = listOfNotNull(
                    log?.let { Triple(PartSince.LOGGED, it.odometerKm, dateOf(it.ts, zone)) },
                    set?.let { b -> b.odoKm?.let { Triple(PartSince.SET, it, b.date) } },
                )
                val (since, from, date) = candidates.maxByOrNull { it.second }
                    ?: purchase?.let { b -> b.odoKm?.let { Triple(PartSince.PURCHASE, it, b.date) } }
                    ?: firstOdoKm?.let { Triple(PartSince.FIRST_READING, it, firstTs?.let { ts -> dateOf(ts, zone) }) }
                    ?: return@mapNotNull null
                PartWear(spec.title, (odo - from).coerceAtLeast(0), life, days = false, since = since, sinceOdoKm = from, sinceDate = date)
            }
        }
    }

    // ---- Chain lube ----

    /**
     * Km since the chain was last lubed, and how much of it was wet. Rain washes lube off, so a
     * wet ride makes it due early.
     */
    fun chainLube(
        lastLubeOdoKm: Int?,
        lastLubeTs: Long?,
        currentOdoKm: Int?,
        rides: List<GarageRide>,
        intervalKm: Int,
        zone: ZoneId,
    ): ChainLube? {
        if (lastLubeOdoKm == null || currentOdoKm == null) return null
        val since = (currentOdoKm - lastLubeOdoKm).coerceAtLeast(0)
        val after = rides.filter { lastLubeTs == null || it.startTs > lastLubeTs }
        val wet = after.filter { isWet(it.weatherCode) }
        val wetKm = wet.sumOf { it.km }.roundToInt()
        val limit = if (intervalKm > 0) intervalKm else 500
        val lastWet = wet.maxByOrNull { it.startTs }
        return when {
            wetKm >= 15 && lastWet != null -> {
                val day = dateOf(lastWet.startTs, zone).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                ChainLube(since, wetKm, true, "Rode $wetKm km wet since $day. Lube the chain")
            }
            since >= limit -> ChainLube(since, wetKm, true, "Chain lube due · ${"%,d".format(since)} km since last")
            else -> ChainLube(since, wetKm, false, "Chain lubed ${"%,d".format(since)} km ago")
        }
    }

    // ---- Efficiency detective ----

    /**
     * Flags a tank that ran well below the rider's usual km/l and lists likely reasons. Rides
     * inside the tank's window are compared with the rest.
     */
    fun efficiency(
        fills: List<FuelFill>,
        rides: List<RideFact>,
        oilOverdue: Boolean,
        chainDue: Boolean,
        minDrop: Double = 0.08,
    ): EfficiencyAlert? {
        val spans = FuelEconomics.tankSpans(fills)
        val good = spans.filter { !it.outlier }
        if (good.size < 4) return null
        val latest = spans.last()
        if (latest.outlier) return null
        val usual = median(good.dropLast(1).map { it.kmPerL })
        val drop = (usual - latest.kmPerL) / usual
        if (drop < minDrop) return null

        val asc = fills.sortedBy { it.ts }
        val close = asc.indexOfFirst { it.id == latest.closingFillId }
        val open = (close - 1 downTo 0).firstOrNull { asc[it].isFullTank } ?: return null
        val from = asc[open].ts
        val to = asc[close].ts
        val inside = rides.filter { it.startTs in from..to }
        val outside = rides.filter { it.startTs !in from..to }
        val causes = mutableListOf<String>()
        if (oilOverdue) causes += "Engine oil is overdue"
        val speedIn = avgSpeed(inside)
        val speedOut = avgSpeed(outside)
        if (speedIn != null && speedOut != null) {
            if (speedIn < speedOut * 0.8) {
                causes += "More stop-go riding: ${speedIn.roundToInt()} km/h average vs your usual ${speedOut.roundToInt()}"
            } else if (speedIn > speedOut * 1.2 && speedIn > 55) {
                causes += "Faster riding: ${speedIn.roundToInt()} km/h average vs your usual ${speedOut.roundToInt()}"
            }
        }
        val climbIn = climbPerKm(inside)
        val climbOut = climbPerKm(outside)
        if (climbIn != null && climbOut != null && climbIn > climbOut * 1.5 && climbIn >= 8) {
            causes += "Hillier roads: ${climbIn.roundToInt()} m climb per km vs ${climbOut.roundToInt()}"
        }
        if (chainDue) causes += "The chain is due for lube"
        causes += "Check tyre pressure"
        return EfficiencyAlert(latest.kmPerL, usual, drop, causes)
    }

    private fun avgSpeed(rides: List<RideFact>): Double? {
        val sec = rides.sumOf { it.movingSec }
        if (sec < 600) return null
        return rides.sumOf { it.km } / (sec / 3600.0)
    }

    private fun climbPerKm(rides: List<RideFact>): Double? {
        val withClimb = rides.filter { it.climbM != null }
        val km = withClimb.sumOf { it.km }
        if (km < 20) return null
        return withClimb.sumOf { it.climbM!! } / km
    }

    private fun median(values: List<Double>): Double {
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2.0
    }

    // ---- Health score ----

    fun health(
        plans: List<PlanState>,
        docs: List<Pair<String, CareBand>>,
        parts: List<PartWear>,
        efficiency: EfficiencyAlert?,
        chain: ChainLube?,
    ): BikeHealth {
        val reasons = mutableListOf<HealthReason>()
        plans.filter { it.kind != "CHAIN" || chain == null }.forEach { p ->
            when (p.band) {
                CareBand.OVERDUE -> reasons += HealthReason(12, "${p.title} is overdue")
                CareBand.DUE_SOON -> reasons += HealthReason(4, "${p.title} is due soon")
                else -> Unit
            }
        }
        docs.forEach { (title, band) ->
            when (band) {
                CareBand.OVERDUE -> reasons += HealthReason(15, "$title has expired")
                CareBand.DUE_SOON -> reasons += HealthReason(5, "$title expires soon")
                else -> Unit
            }
        }
        parts.filter { it.logged }.forEach { p ->
            when {
                p.fraction >= 1f -> reasons += HealthReason(10, "${p.title} is past its usual life")
                p.fraction >= 0.9f -> reasons += HealthReason(5, "${p.title} is near the end of its life")
            }
        }
        efficiency?.let {
            reasons += HealthReason((it.drop * 50).roundToInt().coerceIn(4, 12), "Last tank was ${(it.drop * 100).roundToInt()}% below your usual km/l")
        }
        if (chain?.due == true) reasons += HealthReason(5, "Chain needs lube")
        val sorted = reasons.sortedByDescending { it.points }
        val score = (100 - sorted.sumOf { it.points }).coerceIn(0, 100)
        val label = when {
            score >= 85 -> "Great shape"
            score >= 65 -> "Needs a little care"
            else -> "Needs attention"
        }
        return BikeHealth(score, label, sorted)
    }

    // ---- Pre-ride check ----

    /** Riding days in a row, newest first, that had a check. Today counts once checked. */
    fun checkStreak(checks: Set<LocalDate>, rideDays: Set<LocalDate>, today: LocalDate): Int {
        val days = (rideDays.filter { !it.isAfter(today) } + listOfNotNull(today.takeIf { it in checks }))
            .toSortedSet().reversed()
        var n = 0
        for (d in days) {
            if (d in checks) n++ else if (d != today) break
        }
        return n
    }

    // ---- Year in review ----

    fun year(year: Int, fills: List<FuelFill>, services: List<ServiceSpend>, rides: List<RideFact>, zone: ZoneId): GarageYear {
        val f = fills.filter { dateOf(it.ts, zone).year == year }
        val s = services.filter { dateOf(it.ts, zone).year == year }
        val ids = f.map { it.id }.toSet()
        val spans = FuelEconomics.tankSpans(fills).filter { !it.outlier && it.closingFillId in ids }
        return GarageYear(
            year = year,
            km = rides.filter { dateOf(it.startTs, zone).year == year }.sumOf { it.km },
            fuel = f.sumOf { it.rupees ?: 0.0 },
            service = s.sumOf { it.rupees ?: 0.0 },
            services = s.size,
            fills = f.size,
            litres = f.sumOf { it.litres ?: 0.0 },
            bestKmPerL = spans.maxOfOrNull { it.kmPerL },
            avgKmPerL = spans.takeIf { it.isNotEmpty() }?.let { sp -> sp.sumOf { it.km } / sp.sumOf { it.litres } },
        )
    }

    // ---- Spare cost planner ----

    private fun kindMatches(kind: String, item: String): Boolean {
        val t = item.lowercase(Locale.ENGLISH)
        return when (kind) {
            "OIL" -> t.contains("oil")
            "CHAIN" -> t.contains("chain")
            "BRAKES" -> t.contains("brake") || t.contains("pad")
            "TYRES" -> tyre(t)
            "BATTERY" -> t.contains("battery")
            "SERVICE" -> t.contains("service")
            else -> false
        }
    }

    /** Care falling due in the window, priced from the rider's own past bills, plus fuel at their pace. */
    fun costPlan(
        plans: List<PlanState>,
        services: List<ServiceSpend>,
        kmPerDay: Double,
        kmPerL: Double?,
        rupeesPerLitre: Double?,
        today: LocalDate,
        horizonDays: Int = 90,
    ): CostPlan {
        val end = today.plusDays(horizonDays.toLong())
        val dates = careDates(plans, kmPerDay, today).filter { !it.date.isAfter(end) }
        val items = dates.map { d ->
            val plan = plans.first { it.title == d.title }
            val past = services.filter {
                (it.rupees ?: 0.0) > 0 && (it.item.equals(plan.title, ignoreCase = true) || kindMatches(plan.kind, it.item))
            }.map { it.rupees!! }
            PlannedCost(d.title, d.date, past.takeIf { it.isNotEmpty() }?.let(::median))
        }
        val fuel = if (kmPerDay > 0 && kmPerL != null && kmPerL > 0 && rupeesPerLitre != null && rupeesPerLitre > 0) {
            kmPerDay * horizonDays / kmPerL * rupeesPerLitre
        } else {
            null
        }
        return CostPlan(items, fuel, horizonDays)
    }

    // ---- Odometer story ----

    fun odoStory(
        rides: List<RideFact>,
        firstOdoKm: Int?,
        lastOdoKm: Int?,
        today: LocalDate,
        zone: ZoneId,
    ): OdoStory {
        val since = rides.minOfOrNull { it.startTs }?.let { dateOf(it, zone) }
        val byMonth = rides.groupBy { dateOf(it.startTs, zone).month }.mapValues { (_, r) -> r.sumOf { it.km } }
        val busiest = byMonth.maxByOrNull { it.value }?.takeIf { byMonth.size >= 3 }?.key
        val total = rides.sumOf { it.km }
        val monthsSpan = since?.let { ChronoUnit.MONTHS.between(YearMonth.from(it), YearMonth.from(today)).toInt() + 1 }
        return OdoStory(
            firstOdoKm = firstOdoKm,
            lastOdoKm = lastOdoKm,
            since = since,
            loggedKm = total,
            months = RideHistory.monthTotals(rides, YearMonth.from(today), 12, zone),
            busiestMonth = busiest,
            avgPerMonth = monthsSpan?.takeIf { it >= 2 }?.let { total / it },
        )
    }

    // ---- Bike passport ----

    fun passport(
        services: List<ServiceSpend>,
        fills: List<FuelFill>,
        docs: List<Pair<String, Long>>,
        rides: List<GarageRide>,
        zone: ZoneId,
        milestoneKm: Int = 5_000,
        bigTripKm: Double = 150.0,
    ): List<PassportEvent> {
        val out = mutableListOf<PassportEvent>()
        services.forEach { s ->
            val bits = listOfNotNull(
                "%,d km".format(s.odometerKm),
                s.shop?.takeIf { it.isNotBlank() },
                s.rupees?.takeIf { it > 0 }?.let { "₹%,.0f".format(it) },
            )
            out += PassportEvent(s.ts, PassportKind.SERVICE, s.item.replaceFirstChar { it.uppercase() }, bits.joinToString(" · "))
        }
        fills.groupBy { YearMonth.from(dateOf(it.ts, zone)) }.forEach { (_, group) ->
            val litres = group.sumOf { it.litres ?: 0.0 }
            val rupees = group.sumOf { it.rupees ?: 0.0 }
            val bits = listOfNotNull(
                litres.takeIf { it > 0 }?.let { "%.1f L".format(Locale.ENGLISH, it) },
                rupees.takeIf { it > 0 }?.let { "₹%,.0f".format(it) },
            )
            val n = group.size
            out += PassportEvent(group.maxOf { it.ts }, PassportKind.FUEL, if (n == 1) "1 fill" else "$n fills", bits.joinToString(" · ").ifBlank { null })
        }
        docs.forEach { (title, ts) -> out += PassportEvent(ts, PassportKind.DOCUMENT, "$title on file", null) }
        val asc = rides.sortedBy { it.startTs }
        var prevOdo: Int? = null
        asc.forEach { r ->
            val odo = r.odoEndKm
            if (odo != null) {
                val p = prevOdo
                if (p != null && odo / milestoneKm > p / milestoneKm) {
                    val mark = odo / milestoneKm * milestoneKm
                    out += PassportEvent(r.startTs, PassportKind.MILESTONE, "Crossed ${"%,d".format(mark)} km", r.label, r.id)
                }
                prevOdo = odo
            }
            if (r.km >= bigTripKm) {
                out += PassportEvent(r.startTs, PassportKind.TRIP, r.label, "%,.0f km in one go".format(r.km), r.id)
            }
        }
        asc.firstOrNull()?.let { out += PassportEvent(it.startTs - 1, PassportKind.START, "First ride logged", it.label, it.id) }
        return out.sortedByDescending { it.ts }
    }

    /** Percent change, signed, for "vs last year" captions. Null when there is nothing to compare. */
    fun change(now: Double?, before: Double?): Int? {
        if (now == null || before == null || before == 0.0) return null
        val pct = ((now - before) / abs(before) * 100).roundToInt()
        return pct
    }
}
