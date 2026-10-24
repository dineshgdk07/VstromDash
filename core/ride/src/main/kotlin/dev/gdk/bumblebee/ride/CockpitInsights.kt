package dev.gdk.bumblebee.ride

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** The next round odometer number, and the one just crossed while it is still news. */
data class OdoMilestone(
    val target: Int,
    val kmToGo: Int,
    /** How far through the current block of [OdoMilestone.target] the bike is, 0..1. */
    val fraction: Float,
    /** The round number crossed in the last few km, to celebrate. */
    val justCrossed: Int?,
)

/** A finished ride the cockpit can look back to. */
data class PastRide(val id: Long, val startTs: Long, val km: Double, val label: String)

data class OnThisDay(val rideId: Long, val title: String, val detail: String)

object CockpitInsights {
    private val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    /** WMO codes for clear to overcast skies. Anything wetter gets no suggestion. */
    private val FAIR = 0..3

    fun milestone(odoKm: Int, step: Int = 1_000, celebrateKm: Int = 50): OdoMilestone {
        val into = odoKm % step
        val target = odoKm - into + step
        return OdoMilestone(
            target = target,
            kmToGo = target - odoKm,
            fraction = into.toFloat() / step,
            justCrossed = (odoKm - into).takeIf { it >= step && into < celebrateKm },
        )
    }

    /**
     * One line nudging a ride when the weather is fair: golden hour before sunset, an early
     * morning, a hot afternoon better ridden later, or a weekend day. Null otherwise.
     */
    fun rideSuggestion(code: Int?, tempC: Double?, now: ZonedDateTime, sun: SunDay?): String? {
        if (code == null || code !in FAIR) return null
        val sky = if (code <= 1) "clear" else "dry"
        val temp = tempC?.let { "${it.roundToInt()}°C" }
        val nowTs = now.toInstant().toEpochMilli()
        val sunset = sun?.sunsetTs
        val sunrise = sun?.sunriseTs
        val zone = now.zone
        fun at(ts: Long) = clock.format(Instant.ofEpochMilli(ts).atZone(zone))
        if (sunset != null) {
            val toSunset = (sunset - nowTs) / 60_000L
            if (toSunset in 15..120) {
                return "Sunset at ${at(sunset)}" + (temp?.let { ", $it and $sky" } ?: ", $sky") + ". Golden-hour loop?"
            }
        }
        if (sunrise != null) {
            val sinceSunrise = (nowTs - sunrise) / 60_000L
            if (sinceSunrise in -30..120) {
                return (temp?.let { "$it and $sky" } ?: sky.replaceFirstChar { it.uppercase() }) + " at sunrise. Early roads are empty."
            }
        }
        val daytime = sunrise != null && sunset != null && nowTs in sunrise..sunset
        if (daytime && tempC != null && tempC >= 34.0) {
            return "Hot at $temp. Cooler after sunset at ${at(sunset!!)}."
        }
        val weekend = now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY
        if (daytime && weekend && now.hour < 14) {
            return (temp?.let { "$it and $sky" } ?: sky.replaceFirstChar { it.uppercase() }) + ". Good day for a long one."
        }
        return null
    }

    /**
     * A ride from this date in an earlier year, the most recent such year first, else from this
     * date last month.
     */
    fun onThisDay(rides: List<PastRide>, today: LocalDate, zone: ZoneId): OnThisDay? {
        val byDate = rides.groupBy { Instant.ofEpochMilli(it.startTs).atZone(zone).toLocalDate() }
        fun longestOn(date: LocalDate) = byDate[date]?.maxByOrNull { it.km }
        val yearly = (1..10).firstNotNullOfOrNull { years ->
            longestOn(today.minusYears(years.toLong()))?.let { years to it }
        }
        if (yearly != null) {
            val (years, ride) = yearly
            return OnThisDay(ride.id, if (years == 1) "A year ago today" else "$years years ago today", detail(ride))
        }
        val lastMonth = today.minusMonths(1)
        if (lastMonth.dayOfMonth == today.dayOfMonth) {
            longestOn(lastMonth)?.let { return OnThisDay(it.id, "A month ago today", detail(it)) }
        }
        return null
    }

    /** "Right here", "350 m away" or "1.2 km away". */
    fun distanceLabel(meters: Double): String = when {
        meters < 50 -> "Right here"
        meters < 1_000 -> "${((meters / 10).roundToInt() * 10)} m away"
        meters < 100_000 -> String.format(Locale.US, "%.1f km away", meters / 1_000)
        else -> String.format(Locale.US, "%,d km away", (meters / 1_000).roundToInt())
    }

    private fun detail(ride: PastRide) = ride.label + " · " + String.format(Locale.US, "%.0f km", ride.km)
}
