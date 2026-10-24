package dev.gdk.bumblebee.ride

/**
 * Companion-voice copy for the Ride pillar. Facts (bike/GPS numbers) and Insights
 * (Ride Engine sentences) stay distinct per the insights catalog Part C / D-007.
 */
enum class InsightKind { FACT, INSIGHT }

data class InsightLine(
    val kind: InsightKind,
    val title: String,
    val body: String,
    val atTs: Long? = null,
)

data class RideInsightInput(
    val startTs: Long,
    val endTs: Long,
    val km: Double,
    val movingSec: Int,
    val idleSec: Int,
    val maxSpeedKmh: Int,
    val avgMovingSpeedKmh: Double,
    val odoStartKm: Int? = null,
    val odoEndKm: Int? = null,
    val histogram: SpeedHistogram? = null,
    val fuelBarsStart: Int? = null,
    val fuelBarsEnd: Int? = null,
)

object RideInsights {

    fun headline(
        ride: RideInsightInput,
        allTimeMaxKmh: Int,
        monthLongestKm: Double,
    ): InsightLine? {
        val lines = forRide(ride, allTimeMaxKmh, monthLongestKm)
        return lines.firstOrNull { it.kind == InsightKind.INSIGHT } ?: lines.firstOrNull()
    }

    /** At most three lines, notability first: PB, longest, pace, then facts. */
    fun forRide(
        ride: RideInsightInput,
        allTimeMaxKmh: Int = ride.maxSpeedKmh,
        monthLongestKm: Double = ride.km,
    ): List<InsightLine> {
        val lines = candidates(ride, allTimeMaxKmh, monthLongestKm)
        return lines.take(3)
    }

    /** Full debrief: ride facts plus what the GPS track can prove. Not capped. */
    fun debrief(
        ride: RideInsightInput,
        allTimeMaxKmh: Int = ride.maxSpeedKmh,
        monthLongestKm: Double = ride.km,
        track: List<GpsFix> = emptyList(),
    ): List<InsightLine> {
        val out = ArrayList<InsightLine>()
        out += candidates(ride, allTimeMaxKmh, monthLongestKm)
        clockSplit(ride)?.let { if (out.none { line -> line.title == it.title }) out += it }
        out += TrackInsights.lines(track, ride)
        if (out.none { it.title == "Distance" }) out += distanceFact(ride)
        return out.distinctBy { it.title }
    }

    private fun candidates(
        ride: RideInsightInput,
        allTimeMaxKmh: Int,
        monthLongestKm: Double,
    ): List<InsightLine> {
        val out = ArrayList<InsightLine>(6)
        pb(ride, allTimeMaxKmh)?.let { out += it }
        longest(ride, monthLongestKm)?.let { out += it }
        cruise(ride)?.let { out += it }
        breaks(ride)?.let { out += it }
        milestone(ride)?.let { out += it }
        if (out.none { it.title == "Distance" }) out += distanceFact(ride)
        return out
    }

    fun period(
        todayKm: Double,
        weekKm: Double,
        monthKm: Double,
        pbKmh: Int,
        todayMaxKmh: Int,
    ): List<InsightLine> {
        val lines = mutableListOf<InsightLine>()
        if (todayKm > 0) {
            lines += InsightLine(
                InsightKind.FACT,
                "Today",
                if (todayMaxKmh > 0) {
                    String.format("%.1f km · top speed %d km/h", todayKm, todayMaxKmh)
                } else {
                    String.format("%.1f km", todayKm)
                },
            )
        }
        if (weekKm > 0) {
            lines += InsightLine(InsightKind.FACT, "7 days", String.format("%.1f km", weekKm))
        }
        if (monthKm > 0) {
            lines += InsightLine(InsightKind.FACT, "This month", String.format("%.1f km", monthKm))
        }
        if (pbKmh > 0) {
            lines += InsightLine(InsightKind.INSIGHT, "All-time top speed", "$pbKmh km/h")
        }
        return lines
    }

    private fun pb(ride: RideInsightInput, allTimeMaxKmh: Int): InsightLine? {
        if (ride.maxSpeedKmh <= 0) return null
        if (ride.maxSpeedKmh < allTimeMaxKmh) return null
        return InsightLine(
            InsightKind.INSIGHT,
            "Personal best",
            "That's a new all-time top in this log: ${ride.maxSpeedKmh} km/h. Nothing we've saved was faster.",
        )
    }

    private fun longest(ride: RideInsightInput, monthLongestKm: Double): InsightLine? {
        if (ride.km < 5) return null
        if (ride.km + 0.05 < monthLongestKm) return null
        return InsightLine(
            InsightKind.INSIGHT,
            "Longest this month",
            String.format("This is the furthest you've gone in a single ride this month (%.1f km).", ride.km),
        )
    }

    private fun cruise(ride: RideInsightInput): InsightLine? {
        val h = ride.histogram ?: return null
        val moving = SpeedBand.entries.filter { it != SpeedBand.IDLE }.sumOf { h.secondsIn(it) }
        if (moving < 30) return null
        val band = SpeedBand.entries.filter { it != SpeedBand.IDLE }.maxBy { h.secondsIn(it) }
        val pct = (100.0 * h.secondsIn(band) / moving).toInt()
        if (pct < 40) return null
        val range = when (band) {
            SpeedBand.B1_30 -> "under 30 km/h"
            SpeedBand.B30_50 -> "30–50 km/h"
            SpeedBand.B50_70 -> "50–70 km/h"
            SpeedBand.B70_90 -> "70–90 km/h"
            SpeedBand.B90_PLUS -> "90 km/h and up"
            SpeedBand.IDLE -> return null
        }
        return InsightLine(
            InsightKind.INSIGHT,
            "Pace",
            "Most of the time you were actually moving, you sat $range ($pct% of rolling time). That's the shape of this ride, not the top-speed spike.",
        )
    }

    private fun breaks(ride: RideInsightInput): InsightLine? {
        val total = ride.movingSec + ride.idleSec
        if (total < 60) return null
        val pct = (100.0 * ride.idleSec / total).toInt()
        if (pct < 8) return null
        return InsightLine(
            InsightKind.INSIGHT,
            "Stops",
            "About $pct% of the session the bike wasn't moving — lights, waiting, or parked. Rolling time is the smaller number on purpose.",
        )
    }

    private fun milestone(ride: RideInsightInput): InsightLine? {
        val start = ride.odoStartKm ?: return null
        val end = ride.odoEndKm ?: return null
        if (end <= start) return null
        val crossed = ((start / 1_000) + 1) * 1_000
        if (end < crossed) return null
        return InsightLine(
            InsightKind.INSIGHT,
            "Odometer",
            "The V-Strom's odo ticked through ${"%,d".format(crossed)} km on this ride. That's a lifetime milestone, not a GPS estimate.",
        )
    }

    private fun distanceFact(ride: RideInsightInput): InsightLine =
        InsightLine(
            InsightKind.FACT,
            "Distance",
            String.format("%.1f km covered, %s actually moving. Distance is odo when the bike was linked, GPS path if it wasn't.", ride.km, clock(ride.movingSec)),
        )

    private fun clockSplit(ride: RideInsightInput): InsightLine? {
        val total = ride.movingSec + ride.idleSec
        if (total < 60) return null
        return InsightLine(
            InsightKind.FACT,
            "Clock",
            "The session lasted ${clock(total)}. You were rolling ${clock(ride.movingSec)} of that — the rest is idle on the clock, not extra distance.",
        )
    }

    private fun clock(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
