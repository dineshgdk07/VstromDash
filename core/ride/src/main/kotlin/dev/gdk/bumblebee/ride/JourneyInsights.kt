package dev.gdk.bumblebee.ride

import java.net.URLEncoder
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

enum class Tone { GOOD, WARN, BAD }

data class TripTip(val tone: Tone, val text: String)

/** One navigated trip, as the journey log keeps it. */
data class JourneyEntry(
    val startTs: Long,
    val endTs: Long?,
    val destination: String?,
    /** What Maps promised when guidance started. */
    val firstEtaTs: Long?,
    val firstTotalKm: Double?,
    val arrived: Boolean,
)

data class JourneyStats(val trips: Int, val arrived: Int, val avgBeatMin: Double?, val km: Double)

/** A place the rider has ridden to, from ride end places. */
data class VisitedPlace(val name: String, val visits: Int, val firstTs: Long, val lastTs: Long, val lat: Double?, val lng: Double?)

/** Pure trip maths for the Journey tab. */
object JourneyInsights {
    private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun clockLabel(t: LocalTime): String = t.format(clock).lowercase()

    /** Cluster ETA "0540PM" or "174000" → a time of day. */
    fun parseEta(raw: String?): LocalTime? {
        val t = raw?.trim() ?: return null
        val ampm = Regex("""^(\d{2})(\d{2})(AM|PM)$""").find(t.uppercase())
        if (ampm != null) {
            val (h, m, p) = ampm.destructured
            var hour = h.toInt() % 12
            if (p == "PM") hour += 12
            return runCatching { LocalTime.of(hour, m.toInt()) }.getOrNull()
        }
        val h24 = Regex("""^(\d{2})(\d{2})\d{2}$""").find(t) ?: return null
        val (h, m) = h24.destructured
        return runCatching { LocalTime.of(h.toInt(), m.toInt()) }.getOrNull()
    }

    /** The next moment that time of day happens: an ETA past midnight lands tomorrow. */
    fun arrivalAt(now: LocalDateTime, eta: LocalTime): LocalDateTime {
        val today = now.toLocalDate().atTime(eta)
        return if (today.isBefore(now.minusMinutes(5))) today.plusDays(1) else today
    }

    /** Cluster distance "23.4"/"K" or "0800"/"M" → km. */
    fun km(value: String?, unit: String?): Double? {
        val v = value?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: return null
        return if (unit.equals("K", ignoreCase = true)) v else v / 1000.0
    }

    /** Will the tank get there? [rangeKm] is what's left in it at the rider's own economy. */
    fun fuelCheck(remainingKm: Double?, rangeKm: Double?, pumps: List<String> = emptyList()): TripTip? {
        if (remainingKm == null || remainingKm <= 0 || rangeKm == null) return null
        val spare = rangeKm - remainingKm
        val near = pumps.take(2).takeIf { it.isNotEmpty() }?.joinToString(" or ", prefix = " ")?.let { " Your usual:$it." }.orEmpty()
        return when {
            spare >= 40 -> TripTip(Tone.GOOD, "Fuel's fine: about ${spare.roundToInt()} km to spare at arrival")
            spare >= 0 -> TripTip(Tone.WARN, "Tight on fuel: ${spare.roundToInt()} km spare. Top up on the way.$near")
            else -> TripTip(Tone.BAD, "Fill up within ${(rangeKm - 10).coerceAtLeast(0.0).roundToInt()} km. The tank won't reach.$near")
        }
    }

    /**
     * Things worth knowing about getting there: riding into the dark, rain around arrival, and a
     * break on long legs.
     */
    fun arrivalCheck(
        now: LocalDateTime,
        arrival: LocalDateTime?,
        sunset: LocalDateTime?,
        rainChanceAtArrival: Int?,
    ): List<TripTip> {
        if (arrival == null) return emptyList()
        val out = mutableListOf<TripTip>()
        if (sunset != null && arrival.isAfter(sunset) && now.isBefore(sunset)) {
            out += TripTip(Tone.WARN, "You'll arrive after dark: ${clockLabel(arrival.toLocalTime())}, sunset ${clockLabel(sunset.toLocalTime())}. Lights on, visor clear.")
        }
        if (rainChanceAtArrival != null && rainChanceAtArrival >= 50) {
            out += TripTip(Tone.WARN, "$rainChanceAtArrival% chance of rain around arrival. Pack the rain liner.")
        }
        val minutes = Duration.between(now, arrival).toMinutes()
        if (minutes >= 90) {
            val breakAt = now.plusMinutes(minutes / 2)
            out += TripTip(Tone.GOOD, "${minutes / 60} h ${minutes % 60} min to go. Plan a break around ${clockLabel(breakAt.toLocalTime())}.")
        }
        return out
    }

    fun shareText(eta: LocalTime?, remainingKm: Double?, name: String?): String = buildString {
        append("On my way")
        eta?.let { append(", ETA ").append(clockLabel(it)) }
        remainingKm?.let { append(", ").append(if (it < 10) "%.1f".format(Locale.ENGLISH, it) else it.roundToInt().toString()).append(" km to go") }
        append('.')
        name?.takeIf { it.isNotBlank() }?.let { append(" — ").append(it) }
    }

    /** Minutes the ETA moved since it was shared; positive is later. */
    fun etaSlip(shared: LocalTime?, now: LocalTime?): Int? {
        if (shared == null || now == null) return null
        var d = Duration.between(shared, now).toMinutes().toInt()
        if (d < -12 * 60) d += 24 * 60
        if (d > 12 * 60) d -= 24 * 60
        return d
    }

    /** How the rider does against Maps: positive [JourneyStats.avgBeatMin] means earlier than promised. */
    fun stats(entries: List<JourneyEntry>): JourneyStats {
        val done = entries.filter { it.arrived && it.endTs != null && it.firstEtaTs != null }
        val beat = done.map { (it.firstEtaTs!! - it.endTs!!) / 60_000.0 }
        return JourneyStats(
            trips = entries.size,
            arrived = entries.count { it.arrived },
            avgBeatMin = beat.takeIf { it.size >= 2 }?.average(),
            km = entries.mapNotNull { it.firstTotalKm }.sum(),
        )
    }

    /** Places ridden to, most visited first. Names come from the ride's end place. */
    fun places(rides: List<Triple<String?, Long, Pair<Double, Double>?>>): List<VisitedPlace> =
        rides.filter { !it.first.isNullOrBlank() }
            .groupBy { it.first!!.trim() }
            .map { (name, visits) ->
                val last = visits.maxBy { it.second }
                VisitedPlace(name, visits.size, visits.minOf { it.second }, last.second, last.third?.first, last.third?.second)
            }
            .sortedWith(compareByDescending<VisitedPlace> { it.visits }.thenByDescending { it.lastTs })

    /** True when the newest ride ended somewhere it never ended before. */
    fun firstVisit(places: List<VisitedPlace>, latestName: String?): VisitedPlace? =
        places.firstOrNull { it.name == latestName?.trim() && it.visits == 1 }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** A Google Maps link that starts two-wheeler navigation to [destination]. */
    fun navigateUri(destination: String): String = "google.navigation:q=${enc(destination)}&mode=l"

    /**
     * A Google Maps directions link that follows a past ride: its start, up to [maxStops] points
     * along it, and its end, in two-wheeler mode.
     */
    fun routeUrl(track: List<Pair<Double, Double>>, maxStops: Int = 8): String? {
        if (track.size < 2) return null
        fun p(x: Pair<Double, Double>) = "%.5f,%.5f".format(Locale.ROOT, x.first, x.second)
        val inner = track.subList(1, track.size - 1)
        val step = (inner.size / (maxStops + 1)).coerceAtLeast(1)
        val stops = inner.indices.filter { it % step == step - 1 }.take(maxStops).map { inner[it] }
        return buildString {
            append("https://www.google.com/maps/dir/?api=1")
            append("&origin=").append(enc(p(track.first())))
            append("&destination=").append(enc(p(track.last())))
            if (stops.isNotEmpty()) append("&waypoints=").append(enc(stops.joinToString("|") { p(it) }))
            append("&travelmode=two-wheeler&dir_action=navigate")
        }
    }
}
