package dev.gdk.bumblebee.ride

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt

/** The numbers about one finished ride that the history views need. */
data class RideFact(
    val id: Long,
    val startTs: Long,
    val km: Double,
    val movingSec: Int,
    val maxSpeedKmh: Int,
    val climbM: Double?,
    val label: String,
)

data class MonthTotal(val month: YearMonth, val km: Double)

data class PersonalRecord(
    val title: String,
    val value: String,
    val detail: String,
    /** The ride to open, when the record is one ride. */
    val rideId: Long?,
    /** The day to show, when the record is a day or a span. */
    val date: LocalDate?,
)

data class GoalView(val goalKm: Double, val km: Double, val daysLeft: Int) {
    val fraction: Float get() = (km / goalKm).toFloat().coerceIn(0f, 1f)
    val done: Boolean get() = km >= goalKm
    /** Km a day still needed, counting today, or null when done or out of days. */
    val perDayKm: Double? get() = if (done || daysLeft <= 0) null else (goalKm - km) / daysLeft
}

/** A ~550 m square of the map. */
data class MapCell(val row: Int, val col: Int)

data class Territory(
    val cells: Set<MapCell>,
    val areaKm2: Double,
    /** The cell most rides start from. */
    val home: MapCell?,
    val farthestKm: Double?,
    /** New cells each ride uncovered, by ride id. */
    val newCells: Map<Long, Int>,
)

data class Badge(
    val title: String,
    val detail: String,
    val earnedOn: LocalDate?,
    /** Progress towards it, 0..1; 1 when earned. */
    val progress: Float,
) {
    val earned: Boolean get() = earnedOn != null
}

data class Challenge(val title: String, val detail: String, val progress: Double, val target: Double) {
    val done: Boolean get() = progress >= target
    val fraction: Float get() = (progress / target).toFloat().coerceIn(0f, 1f)
}

data class Trait(val name: String, val value: Float, val caption: String)

data class RidingDna(
    val persona: String,
    val tagline: String,
    val traits: List<Trait>,
    val signature: List<Pair<String, String>>,
)

object RideHistory {
    const val CELL_DEG = 0.005

    private fun date(ts: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
    private fun hour(ts: Long, zone: ZoneId): Int = Instant.ofEpochMilli(ts).atZone(zone).hour
    private fun km(v: Double) = String.format(Locale.US, "%,.0f km", v)

    /** Km in each of the last [count] months up to [month], oldest first. */
    fun monthTotals(rides: List<RideFact>, month: YearMonth, count: Int, zone: ZoneId): List<MonthTotal> {
        val byMonth = rides.groupBy { YearMonth.from(date(it.startTs, zone)) }.mapValues { (_, r) -> r.sumOf { it.km } }
        return (count - 1 downTo 0).map { back -> month.minusMonths(back.toLong()).let { MonthTotal(it, byMonth[it] ?: 0.0) } }
    }

    /** Where this month lands at its pace so far; null for past months or the first two days. */
    fun projectedKm(km: Double, month: YearMonth, today: LocalDate): Double? {
        if (YearMonth.from(today) != month || today.dayOfMonth < 3 || km <= 0) return null
        return km / today.dayOfMonth * month.lengthOfMonth()
    }

    fun yearHeat(rides: List<RideFact>, year: Int, zone: ZoneId): Map<LocalDate, Double> =
        rides.map { date(it.startTs, zone) to it.km }
            .filter { it.first.year == year }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.sum() }

    fun goal(goalKm: Double?, km: Double, month: YearMonth, today: LocalDate): GoalView? {
        if (goalKm == null || goalKm <= 0) return null
        val daysLeft = when {
            YearMonth.from(today) == month -> month.lengthOfMonth() - today.dayOfMonth + 1
            month.isAfter(YearMonth.from(today)) -> month.lengthOfMonth()
            else -> 0
        }
        return GoalView(goalKm, km, daysLeft)
    }

    fun records(rides: List<RideFact>, zone: ZoneId): List<PersonalRecord> {
        if (rides.isEmpty()) return emptyList()
        val out = ArrayList<PersonalRecord>()
        val dayFmt = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        fun on(r: RideFact) = "${r.label} · ${dayFmt.format(date(r.startTs, zone))}"
        rides.maxByOrNull { it.km }?.let { out += PersonalRecord("Longest ride", km(it.km), on(it), it.id, null) }
        rides.filter { it.maxSpeedKmh > 0 }.maxByOrNull { it.maxSpeedKmh }?.let {
            out += PersonalRecord("Top speed", "${it.maxSpeedKmh} km/h", on(it), it.id, null)
        }
        rides.filter { (it.climbM ?: 0.0) >= 40 }.maxByOrNull { it.climbM!! }?.let {
            out += PersonalRecord("Biggest climb", "${it.climbM!!.roundToInt()} m", on(it), it.id, null)
        }
        rides.maxByOrNull { it.movingSec }?.let {
            out += PersonalRecord("Longest in the saddle", ShareMetrics.clock(it.movingSec), on(it), it.id, null)
        }
        val byDay = rides.groupBy { date(it.startTs, zone) }
        byDay.maxByOrNull { (_, r) -> r.sumOf { it.km } }?.let { (day, r) ->
            out += PersonalRecord("Biggest day", km(r.sumOf { it.km }), "${r.size} ride${if (r.size == 1) "" else "s"} · ${dayFmt.format(day)}", null, day)
        }
        rides.groupBy { val d = date(it.startTs, zone); d.get(IsoFields.WEEK_BASED_YEAR) * 100 + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) }
            .maxByOrNull { (_, r) -> r.sumOf { it.km } }
            ?.let { (_, r) ->
                val monday = date(r.minOf { it.startTs }, zone).with(DayOfWeek.MONDAY)
                out += PersonalRecord("Best week", km(r.sumOf { it.km }), "Week of ${dayFmt.format(monday)}", null, monday)
            }
        rides.groupBy { YearMonth.from(date(it.startTs, zone)) }
            .maxByOrNull { (_, r) -> r.sumOf { it.km } }
            ?.let { (m, r) ->
                val name = m.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " ${m.year}"
                out += PersonalRecord("Best month", km(r.sumOf { it.km }), "$name · ${r.size} rides", null, m.atDay(1))
            }
        val streak = longestStreak(byDay.keys)
        if (streak.second >= 2) {
            out += PersonalRecord("Longest streak", "${streak.second} days", "From ${dayFmt.format(streak.first)}", null, streak.first)
        }
        return out
    }

    /** First day and length of the longest run of consecutive riding days. */
    fun longestStreak(days: Collection<LocalDate>): Pair<LocalDate?, Int> {
        val sorted = days.toSortedSet()
        var best: LocalDate? = null
        var bestLen = 0
        var start: LocalDate? = null
        var len = 0
        var prev: LocalDate? = null
        for (d in sorted) {
            if (prev != null && d == prev.plusDays(1)) len++ else {
                start = d
                len = 1
            }
            if (len > bestLen) {
                bestLen = len
                best = start
            }
            prev = d
        }
        return best to bestLen
    }

    fun cellOf(lat: Double, lng: Double): MapCell =
        MapCell(kotlin.math.floor(lat / CELL_DEG).toInt(), kotlin.math.floor(lng / CELL_DEG).toInt())

    fun cellCenter(cell: MapCell): Pair<Double, Double> = (cell.row + 0.5) * CELL_DEG to (cell.col + 0.5) * CELL_DEG

    /**
     * Every cell the rider has ridden through. [tracks] are by ride id; rides are walked oldest
     * first so each ride's new cells can be counted.
     */
    fun territory(rides: List<RideFact>, tracks: Map<Long, List<GpsFix>>): Territory {
        val seen = HashSet<MapCell>()
        val newCells = HashMap<Long, Int>()
        val starts = HashMap<MapCell, Int>()
        for (ride in rides.sortedBy { it.startTs }) {
            val track = tracks[ride.id].orEmpty()
            if (track.isEmpty()) continue
            val first = track.first()
            starts.merge(cellOf(first.lat, first.lng), 1, Int::plus)
            var fresh = 0
            for (fix in track) if (seen.add(cellOf(fix.lat, fix.lng))) fresh++
            newCells[ride.id] = fresh
        }
        val home = starts.maxByOrNull { it.value }?.key
        val area = seen.sumOf { cell ->
            val lat = cellCenter(cell).first
            val side = CELL_DEG * 111.195
            side * side * cos(Math.toRadians(lat))
        }
        val farthest = home?.let { h ->
            val (hLat, hLng) = cellCenter(h)
            seen.maxOfOrNull { c -> cellCenter(c).let { (lat, lng) -> Geo.haversineMeters(hLat, hLng, lat, lng) } }?.div(1_000)
        }
        return Territory(seen, area, home, farthest, newCells)
    }

    /** A ride found new ground when at least a quarter of it, and four cells, were never ridden before. */
    fun foundNewGround(ride: RideFact, territory: Territory, tracks: Map<Long, List<GpsFix>>): Boolean {
        val fresh = territory.newCells[ride.id] ?: return false
        val total = tracks[ride.id]?.map { cellOf(it.lat, it.lng) }?.toSet()?.size ?: return false
        return fresh >= 4 && fresh >= total / 4
    }

    fun badges(
        rides: List<RideFact>,
        territory: Territory?,
        mostRunsOnOneRoute: Int,
        zone: ZoneId,
    ): List<Badge> {
        val ordered = rides.sortedBy { it.startTs }
        fun day(r: RideFact?) = r?.let { date(it.startTs, zone) }
        fun firstWhere(test: (RideFact) -> Boolean) = ordered.firstOrNull(test)
        fun nth(n: Int, test: (RideFact) -> Boolean) = ordered.filter(test).getOrNull(n - 1)
        fun progress(count: Int, of: Int) = (count.toFloat() / of).coerceIn(0f, 1f)

        val out = ArrayList<Badge>()
        out += Badge("First ride", "Every story starts somewhere", day(ordered.firstOrNull()), if (ordered.isEmpty()) 0f else 1f)
        val longest = ordered.maxOfOrNull { it.km } ?: 0.0
        out += Badge("Century", "A 100 km ride", day(firstWhere { it.km >= 100 }), (longest / 100).toFloat().coerceIn(0f, 1f))
        out += Badge("Double century", "A 200 km ride", day(firstWhere { it.km >= 200 }), (longest / 200).toFloat().coerceIn(0f, 1f))
        val top = ordered.maxOfOrNull { it.maxSpeedKmh } ?: 0
        out += Badge("Ton up", "Touch 100 km/h", day(firstWhere { it.maxSpeedKmh >= 100 }), (top / 100f).coerceIn(0f, 1f))
        val climb = ordered.maxOfOrNull { it.climbM ?: 0.0 } ?: 0.0
        out += Badge("Hill climber", "Climb 500 m in one ride", day(firstWhere { (it.climbM ?: 0.0) >= 500 }), (climb / 500).toFloat().coerceIn(0f, 1f))
        val early = { r: RideFact -> hour(r.startTs, zone) < 7 }
        out += Badge("Dawn patrol", "5 rides started before 7 am", day(nth(5, early)), progress(ordered.count(early), 5))
        val late = { r: RideFact -> hour(r.startTs, zone) >= 20 }
        out += Badge("Night rider", "5 rides started after 8 pm", day(nth(5, late)), progress(ordered.count(late), 5))
        val months = ordered.groupBy { YearMonth.from(date(it.startTs, zone)) }
        val bigMonth = months.entries.sortedBy { it.key }.firstOrNull { (_, r) -> r.sumOf { it.km } >= 1_000 }
        val bestMonth = months.values.maxOfOrNull { r -> r.sumOf { it.km } } ?: 0.0
        out += Badge("Thousand-km month", "1,000 km in one month", bigMonth?.key?.atEndOfMonth(), (bestMonth / 1_000).toFloat().coerceIn(0f, 1f))
        var running = 0.0
        val tenK = ordered.firstOrNull { running += it.km; running >= 10_000 }
        out += Badge("10,000 km", "10,000 km logged in the app", day(tenK), (ordered.sumOf { it.km } / 10_000).toFloat().coerceIn(0f, 1f))
        val streak = longestStreak(ordered.map { date(it.startTs, zone) })
        out += Badge(
            "Week streak",
            "Ride 7 days in a row",
            if (streak.second >= 7) streak.first?.plusDays(6) else null,
            progress(streak.second, 7),
        )
        out += Badge(
            "Regular",
            "10 runs on one route",
            if (mostRunsOnOneRoute >= 10) day(ordered.lastOrNull()) else null,
            progress(mostRunsOnOneRoute, 10),
        )
        val area = territory?.areaKm2 ?: 0.0
        out += Badge("Explorer", "Ride through 100 km² of map", if (area >= 100) day(ordered.lastOrNull()) else null, (area / 100).toFloat().coerceIn(0f, 1f))
        return out.sortedWith(compareByDescending<Badge> { it.earned }.thenByDescending { it.progress })
    }

    /** This month's challenges, set from the rider's own history so each is a stretch, not a chore. */
    fun challenges(
        rides: List<RideFact>,
        month: YearMonth,
        newGround: (RideFact) -> Boolean,
        zone: ZoneId,
    ): List<Challenge> {
        if (rides.size < 3) return emptyList()
        val inMonth = rides.filter { YearMonth.from(date(it.startTs, zone)) == month }
        val before = rides.filter { YearMonth.from(date(it.startTs, zone)) < month }
        val out = ArrayList<Challenge>()
        val record = before.maxOfOrNull { it.km }
        if (record != null && record >= 10) {
            out += Challenge("Beat your longest ride", "Go past ${km(record)} in one ride", inMonth.maxOfOrNull { it.km } ?: 0.0, record + 1)
        }
        val lastMonthDays = before.filter { YearMonth.from(date(it.startTs, zone)) == month.minusMonths(1) }
            .map { date(it.startTs, zone) }.toSet().size
        val days = (lastMonthDays + 2).coerceIn(4, 20)
        out += Challenge("Ride on $days days", "Last month you rode on $lastMonthDays", inMonth.map { date(it.startTs, zone) }.toSet().size.toDouble(), days.toDouble())
        out += Challenge("Two sunrise starts", "Head out before 7 am, twice", inMonth.count { hour(it.startTs, zone) < 7 }.toDouble(), 2.0)
        out += Challenge("Find new roads", "Two rides where a quarter of the road is new to you", inMonth.count(newGround).toDouble(), 2.0)
        return out
    }

    /** A riding personality from habits; null until there are five rides to read. */
    fun dna(rides: List<RideFact>, newGround: (RideFact) -> Boolean, zone: ZoneId): RidingDna? {
        if (rides.size < 5) return null
        val n = rides.size.toFloat()
        val earlyShare = rides.count { hour(it.startTs, zone) < 9 } / n
        val nightShare = rides.count { hour(it.startTs, zone) >= 19 } / n
        val weekendShare = rides.count { date(it.startTs, zone).dayOfWeek.value >= 6 } / n
        val avgKm = rides.sumOf { it.km } / n
        val newShare = rides.count(newGround) / n
        val climbs = rides.mapNotNull { it.climbM }
        val avgClimb = if (climbs.isEmpty()) 0.0 else climbs.average()
        val avgTop = rides.sumOf { it.maxSpeedKmh } / n
        val (persona, tagline) = when {
            earlyShare >= 0.45f -> "Dawn Patrol" to "Out before the city wakes."
            nightShare >= 0.35f -> "Night Owl" to "The roads are yours after dark."
            avgKm >= 60 -> "Mile Muncher" to "Every ride turns into a journey."
            newShare >= 0.4f -> "Trailblazer" to "Never the same road twice."
            avgClimb >= 150 -> "Hill Hunter" to "If it climbs, you're on it."
            weekendShare >= 0.6f -> "Weekend Warrior" to "Weekdays are for waiting."
            else -> "All-Rounder" to "Any road, any hour."
        }
        val traits = listOf(
            Trait("Early bird", earlyShare, "${(earlyShare * 100).roundToInt()}% start before 9 am"),
            Trait("Night rider", nightShare, "${(nightShare * 100).roundToInt()}% start after 7 pm"),
            Trait("Weekender", weekendShare, "${(weekendShare * 100).roundToInt()}% on weekends"),
            Trait("Distance", (avgKm / 100).toFloat().coerceIn(0f, 1f), String.format(Locale.US, "%.0f km a ride", avgKm)),
            Trait("Explorer", newShare, "${(newShare * 100).roundToInt()}% find new roads"),
            Trait("Pace", (avgTop / 120).toFloat().coerceIn(0f, 1f), String.format(Locale.US, "%.0f km/h top on average", avgTop)),
        )
        val favDay = rides.groupBy { date(it.startTs, zone).dayOfWeek }.maxByOrNull { (_, r) -> r.sumOf { it.km } }!!.key
        val favHour = rides.groupBy { hour(it.startTs, zone) / 3 }.maxByOrNull { it.value.size }!!.key * 3
        val signature = listOf(
            "Total" to km(rides.sumOf { it.km }),
            "Rides" to rides.size.toString(),
            "Favourite day" to favDay.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
            "Favourite hour" to String.format(Locale.US, "%02d:00–%02d:00", favHour, favHour + 3),
            "Longest" to km(rides.maxOf { it.km }),
        )
        return RidingDna(persona, tagline, traits, signature)
    }
}
