package dev.gdk.bumblebee.ride

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object TrackInsights {
    const val STOP_KMH = 3
    const val MIN_STOP_MS = 20_000L
    const val ELEV_NOISE_M = 3.0
    const val TWIST_MIN_KM = 1.0

    fun lines(track: List<GpsFix>, ride: RideInsightInput): List<InsightLine> {
        if (track.size < 2) return emptyList()
        val out = ArrayList<InsightLine>(5)
        val stops = stopCount(track)
        if (stops >= 1) {
            out += InsightLine(
                InsightKind.INSIGHT,
                "Pulled over",
                if (stops == 1) "After you got going, you came to a real stop once (not just the start/park). That's a wait or a pull-over, not traffic crawl."
                else "After you got going, you came to a real stop $stops times. Those are pauses long enough to count — not just slowing for a junction.",
            )
        }
        elevationGainM(track)?.let { gain ->
            if (gain >= 15) {
                out += InsightLine(
                    InsightKind.FACT,
                    "Climb",
                    "The altitude climbed about ${gain.roundToInt()} m in total. Small GPS wobble is ignored, so this is hills, not noise.",
                )
            }
        }
        twistDegPerKm(track)?.let { deg ->
            if (deg >= 70) {
                out += InsightLine(
                    InsightKind.INSIGHT,
                    "Roads",
                    String.format("The nose of the bike kept turning — about %.0f° of heading change per km. Winding roads, not a straight blast.", deg),
                )
            }
        }
        peak(track)?.let { (fix, kmh) ->
            val into = ((fix.ts - ride.startTs) / 1000L).toInt().coerceAtLeast(0)
            out += InsightLine(
                InsightKind.FACT,
                "Top speed",
                "${kmh} km/h — the fastest GPS sample, ${clock(into)} after you started. Tap to put the cursor there on the map.",
                atTs = fix.ts,
            )
        }
        medianAccuracyM(track)?.let { acc ->
            out += InsightLine(
                InsightKind.FACT,
                "GPS",
                if (acc <= 8) "Phone GPS was tight (median ${acc.roundToInt()} m). The yellow line is trustworthy."
                else "Phone GPS was a bit loose (median ${acc.roundToInt()} m). Treat sharp corners on the map as approximate.",
            )
        }
        return out
    }

    fun stopCount(track: List<GpsFix>): Int {
        if (track.size < 4) return 0
        var i = 0
        while (i < track.size && kmh(track[i]) < STOP_KMH) i++
        var count = 0
        var stopStart: Long? = null
        while (i < track.size) {
            if (kmh(track[i]) < STOP_KMH) {
                if (stopStart == null) stopStart = track[i].ts
            } else {
                val start = stopStart
                if (start != null && track[i].ts - start >= MIN_STOP_MS) count++
                stopStart = null
            }
            i++
        }
        return count
    }

    fun elevationGainM(track: List<GpsFix>): Double? {
        val alts = track.mapNotNull { it.altitudeM }
        if (alts.size < 4) return null
        var gain = 0.0
        var last = alts.first()
        for (a in alts.drop(1)) {
            val d = a - last
            if (d >= ELEV_NOISE_M) gain += d
            if (abs(d) >= ELEV_NOISE_M) last = a
        }
        return gain
    }

    fun twistDegPerKm(track: List<GpsFix>): Double? {
        var dist = 0.0
        var turn = 0.0
        var prevHeading: Double? = null
        for (i in 1 until track.size) {
            val a = track[i - 1]
            val b = track[i]
            val d = Geo.haversineMeters(a.lat, a.lng, b.lat, b.lng)
            if (d < 8) continue
            dist += d
            val h = headingDeg(a, b)
            val prev = prevHeading
            if (prev != null) turn += abs(wrapDelta(prev, h))
            prevHeading = h
        }
        val km = dist / 1000.0
        if (km < TWIST_MIN_KM) return null
        return turn / km
    }

    fun peak(track: List<GpsFix>): Pair<GpsFix, Int>? {
        val best = track.maxByOrNull { kmh(it) } ?: return null
        val k = kmh(best).roundToInt()
        if (k < 8) return null
        return best to k
    }

    fun medianAccuracyM(track: List<GpsFix>): Float? {
        val acc = track.mapNotNull { it.accuracyM }.sorted()
        if (acc.isEmpty()) return null
        return acc[acc.size / 2]
    }

    fun kmh(fix: GpsFix): Float {
        val fromSpeed = fix.speedMps
        if (fromSpeed != null) return fromSpeed * 3.6f
        return 0f
    }

    fun inBand(kmh: Int, band: SpeedBand): Boolean = when (band) {
        SpeedBand.IDLE -> kmh < 3
        SpeedBand.B1_30 -> kmh in 3 until 30
        SpeedBand.B30_50 -> kmh in 30 until 50
        SpeedBand.B50_70 -> kmh in 50 until 70
        SpeedBand.B70_90 -> kmh in 70 until 90
        SpeedBand.B90_PLUS -> kmh >= 90
    }

    fun peakInBand(speeds: List<Pair<Long, Int>>, band: SpeedBand): Pair<Long, Int>? =
        speeds.filter { inBand(it.second, band) }.maxByOrNull { it.second }

    private fun headingDeg(a: GpsFix, b: GpsFix): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun wrapDelta(from: Double, to: Double): Double {
        var d = (to - from) % 360.0
        if (d > 180) d -= 360
        if (d < -180) d += 360
        return d
    }

    private fun clock(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
