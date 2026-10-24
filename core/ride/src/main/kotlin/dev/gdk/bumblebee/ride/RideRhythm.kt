package dev.gdk.bumblebee.ride

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.max

data class RideRecord(
    val id: Long,
    val startTs: Long,
    val endTs: Long? = null,
    val km: Double,
    val movingSec: Int = 0,
    val maxSpeedKmh: Int = 0,
)

data class StreakInfo(val current: Int, val longest: Int)

data class ServiceEta(
    val daysAway: Int,
    val fromPace: Boolean,
    val overdue: Boolean,
) {
    fun label(): String = when {
        overdue || daysAway <= 0 -> "due now"
        daysAway == 1 -> "~1 day"
        else -> "~$daysAway days"
    }
}

data class WrappedRecap(
    val year: Int,
    val totalKm: Double,
    val rideCount: Int,
    val movingSec: Int,
    val longestKm: Double,
    val topSpeedKmh: Int,
    val busiestWeekday: String?,
    val bestTankKmPerL: Double?,
    val worstTankKmPerL: Double?,
    val litresEst: Double?,
    val litresFromFills: Boolean,
    val spendRupees: Double?,
    val spendPartial: Boolean,
    val longestStreak: Int,
)

object RideRhythm {

    /** Monday=0 … Sunday=6. */
    fun weekdayKm(rides: List<RideRecord>, zone: ZoneId = ZoneId.systemDefault()): List<Double> {
        val out = DoubleArray(7)
        for (r in rides) {
            val dow = Instant.ofEpochMilli(r.startTs).atZone(zone).toLocalDate().dayOfWeek.value
            out[dow - 1] += max(0.0, r.km)
        }
        return out.toList()
    }

    /** Morning 5–11, afternoon 12–16, evening 17–20, night else. */
    fun timeOfDayKm(rides: List<RideRecord>, zone: ZoneId = ZoneId.systemDefault()): List<Double> {
        val out = DoubleArray(4)
        for (r in rides) {
            val hour = Instant.ofEpochMilli(r.startTs).atZone(zone).hour
            val idx = when (hour) {
                in 5..11 -> 0
                in 12..16 -> 1
                in 17..20 -> 2
                else -> 3
            }
            out[idx] += max(0.0, r.km)
        }
        return out.toList()
    }

    fun streak(
        rides: List<RideRecord>,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): StreakInfo {
        if (rides.isEmpty()) return StreakInfo(0, 0)
        val days = rides.map { Instant.ofEpochMilli(it.startTs).atZone(zone).toLocalDate() }.toSortedSet()
        var current = 0
        var d = today
        if (d !in days) d = d.minusDays(1)
        while (d in days) {
            current++
            d = d.minusDays(1)
        }
        var longest = 0
        var run = 0
        var prev: LocalDate? = null
        for (day in days) {
            run = if (prev != null && day == prev.plusDays(1)) run + 1 else 1
            longest = maxOf(longest, run)
            prev = day
        }
        return StreakInfo(current, longest)
    }

    fun paceKmPerDay(
        rides: List<RideRecord>,
        windowDays: Long = 30,
        now: Long = System.currentTimeMillis(),
    ): Double {
        if (windowDays <= 0) return 0.0
        val cutoff = now - windowDays * 86_400_000L
        val km = rides.filter { it.startTs >= cutoff }.sumOf { max(0.0, it.km) }
        return km / windowDays.toDouble()
    }

    fun serviceEta(kmRemaining: Int?, kmPerDay: Double): ServiceEta? {
        if (kmRemaining == null) return null
        if (kmRemaining <= 0) return ServiceEta(daysAway = 0, fromPace = true, overdue = true)
        if (kmPerDay <= 0.0 || kmPerDay.isNaN() || kmPerDay.isInfinite()) return null
        val days = ceil(kmRemaining / kmPerDay).toInt().coerceAtLeast(0)
        return ServiceEta(daysAway = days, fromPace = true, overdue = false)
    }

    fun wrapped(
        year: Int,
        rides: List<RideRecord>,
        fills: List<FuelFill>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): WrappedRecap? {
        val windowed = rides.filter {
            Instant.ofEpochMilli(it.startTs).atZone(zone).year == year && it.km > 0
        }
        if (windowed.isEmpty()) return null
        val tanks = FuelEconomics.perTankKmPerL(
            fills.filter { Instant.ofEpochMilli(it.ts).atZone(zone).year == year },
        ).map { it.second }
        val yearFills = fills.filter { Instant.ofEpochMilli(it.ts).atZone(zone).year == year }
        val tankAvg = if (tanks.isEmpty()) null else tanks.average()
        val km = windowed.sumOf { it.km }
        val litres = FuelEconomics.litresBurnt(km, tankAvg, null)
        val priced = yearFills.mapNotNull { it.rupees }.filter { it > 0 }
        val spend = if (priced.isEmpty()) null else priced.sum()
        val weekday = weekdayKm(windowed, zone)
        val names = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val busyIdx = weekday.indices.maxByOrNull { weekday[it] }
        val busy = if (busyIdx != null && weekday[busyIdx] > 0) names[busyIdx] else null
        return WrappedRecap(
            year = year,
            totalKm = km,
            rideCount = windowed.size,
            movingSec = windowed.sumOf { it.movingSec },
            longestKm = windowed.maxOf { it.km },
            topSpeedKmh = windowed.maxOf { it.maxSpeedKmh },
            busiestWeekday = busy,
            bestTankKmPerL = tanks.maxOrNull(),
            worstTankKmPerL = tanks.minOrNull(),
            litresEst = litres?.first,
            litresFromFills = litres?.second == true,
            spendRupees = spend,
            spendPartial = spend != null && priced.size < yearFills.size,
            longestStreak = streak(windowed, zone).longest,
        )
    }
}
