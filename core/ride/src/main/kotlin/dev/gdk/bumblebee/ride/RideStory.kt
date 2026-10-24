package dev.gdk.bumblebee.ride

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Rider-facing paragraphs. Numbers stay, jargon does not. */
object RideStory {

    fun of(
        ride: RideInsightInput,
        track: List<GpsFix> = emptyList(),
        zone: ZoneId = ZoneOffset.systemDefault(),
    ): String {
        val parts = mutableListOf<String>()
        val whenWord = timeOfDay(ride.startTs, zone)
        val km = String.format("%.1f", ride.km)
        val moving = clock(ride.movingSec)
        parts += if (ride.km <= 0) {
            "This session started in the $whenWord but we didn't log usable distance."
        } else {
            "You headed out in the $whenWord and covered $km km, rolling for $moving."
        }

        paceSentence(ride)?.let { parts += it }

        val stops = TrackInsights.stopCount(track)
        val idlePct = idlePercent(ride)
        when {
            stops >= 2 -> parts += "You actually parked or waited $stops times after you got moving — not just crawling in traffic."
            stops == 1 -> parts += "There was one real stop after you rolled off."
            idlePct != null && idlePct >= 20 -> parts += "A lot of the clock ($idlePct%) was spent not moving — lights, waiting, or sitting with the engine on."
            idlePct != null && idlePct <= 5 && ride.movingSec >= 600 -> parts += "Almost no dead time. Once you left, you kept rolling."
        }

        TrackInsights.elevationGainM(track)?.takeIf { it >= 40 }?.let {
            parts += "The road climbed about ${it.toInt()} m — enough to feel in the legs, not GPS noise."
        }
        TrackInsights.twistDegPerKm(track)?.takeIf { it >= 90 }?.let {
            parts += "The heading kept changing. This wasn't a straight highway drone."
        }

        shapeSentence(ride)?.let { parts += it }
        return parts.joinToString(" ")
    }

    fun ofDay(title: String, rides: List<RideInsightInput>): String {
        if (rides.isEmpty()) return "$title — no rides logged."
        val km = rides.sumOf { it.km }
        val moving = rides.sumOf { it.movingSec }
        val top = rides.maxOf { it.maxSpeedKmh }
        val n = rides.size
        val howMany = when (n) {
            1 -> "one ride"
            2 -> "two rides"
            else -> "$n rides"
        }
        val longest = rides.maxBy { it.km }
        val bits = mutableListOf<String>()
        bits += "$title — $howMany, ${String.format("%.1f", km)} km on the day."
        bits += "You were moving ${clock(moving)}."
        if (n > 1) bits += "The longest was ${String.format("%.1f", longest.km)} km."
        if (top > 0) bits += "Fastest moment: $top km/h."
        val morning = rides.count { hour(it.startTs) in 5..11 }
        val evening = rides.count { hour(it.startTs) in 17..22 }
        when {
            morning == n && n > 0 -> bits += "All of it happened in the morning."
            evening == n && n > 0 -> bits += "All of it happened in the evening."
            morning > 0 && evening > 0 -> bits += "You split the day — morning and evening both."
        }
        return bits.joinToString(" ")
    }

    fun ofMonth(title: String, rides: List<RideInsightInput>): String {
        if (rides.isEmpty()) return "$title — no rides logged yet. The calendar fills in as you ride."
        val km = rides.sumOf { it.km }
        val moving = rides.sumOf { it.movingSec }
        val top = rides.maxOf { it.maxSpeedKmh }
        val n = rides.size
        val days = rides.map { Instant.ofEpochMilli(it.startTs).atZone(ZoneOffset.systemDefault()).toLocalDate() }.toSet().size
        val longest = rides.maxBy { it.km }
        val bits = mutableListOf<String>()
        bits += "$title — $n ${if (n == 1) "ride" else "rides"} across $days ${if (days == 1) "day" else "days"}, ${String.format("%.1f", km)} km."
        bits += "You were moving ${clock(moving)}."
        if (n > 1) bits += "The longest was ${String.format("%.1f", longest.km)} km."
        if (top > 0) bits += "Fastest moment: $top km/h."
        val morning = rides.count { hour(it.startTs) in 5..11 }
        val evening = rides.count { hour(it.startTs) in 17..22 }
        when {
            morning > evening && morning >= n / 2.0 -> bits += "Most of it started in the morning."
            evening > morning && evening >= n / 2.0 -> bits += "Most of it started in the evening."
            morning > 0 && evening > 0 -> bits += "Mornings and evenings both showed up."
        }
        return bits.joinToString(" ")
    }

    fun timeOfDay(ts: Long, zone: ZoneId = ZoneOffset.systemDefault()): String {
        val h = Instant.ofEpochMilli(ts).atZone(zone).hour
        return when (h) {
            in 0..5 -> "pre-dawn"
            in 6..11 -> "morning"
            in 12..15 -> "afternoon"
            in 16..18 -> "evening"
            else -> "night"
        }
    }

    private fun hour(ts: Long): Int =
        Instant.ofEpochMilli(ts).atZone(ZoneOffset.systemDefault()).hour

    private fun paceSentence(ride: RideInsightInput): String? {
        val h = ride.histogram ?: return avgFallback(ride)
        val moving = SpeedBand.entries.filter { it != SpeedBand.IDLE }.sumOf { h.secondsIn(it) }
        if (moving < 30) return avgFallback(ride)
        val band = SpeedBand.entries.filter { it != SpeedBand.IDLE }.maxBy { h.secondsIn(it) }
        val pct = (100.0 * h.secondsIn(band) / moving).toInt()
        if (pct < 35) return avgFallback(ride)
        val meaning = when (band) {
            SpeedBand.B1_30 -> "most of the moving time was under 30 km/h — city crawl, not open road"
            SpeedBand.B30_50 -> "you lived in 30–50 km/h, typical town and inner roads"
            SpeedBand.B50_70 -> "you spent most of the moving time in 50–70 km/h, the V-Strom's easy cruise"
            SpeedBand.B70_90 -> "a big slice was 70–90 km/h — highway or a fast ring road"
            SpeedBand.B90_PLUS -> "you spent real time at 90 km/h and up"
            SpeedBand.IDLE -> return avgFallback(ride)
        }
        return "Pace-wise, $meaning ($pct% of rolling time)."
    }

    private fun avgFallback(ride: RideInsightInput): String? {
        if (ride.avgMovingSpeedKmh <= 0) return null
        return String.format("Average while moving was %.0f km/h.", ride.avgMovingSpeedKmh)
    }

    private fun shapeSentence(ride: RideInsightInput): String? = when {
        ride.km < 4 -> "Short hop — errand or a start-stop around the block."
        ride.km < 12 && (idlePercent(ride) ?: 0) >= 15 -> "Reads like a city commute: distance is modest, waiting is not."
        ride.km >= 40 -> "That's a proper outing, not just getting from A to B."
        ride.maxSpeedKmh >= 100 -> "You opened it up at least once."
        else -> null
    }

    private fun idlePercent(ride: RideInsightInput): Int? {
        val total = ride.movingSec + ride.idleSec
        if (total < 60) return null
        return (100.0 * ride.idleSec / total).toInt()
    }

    private fun clock(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m} min"
    }
}
