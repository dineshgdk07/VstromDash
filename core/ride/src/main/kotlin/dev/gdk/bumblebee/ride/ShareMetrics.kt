package dev.gdk.bumblebee.ride

import java.util.Locale

enum class ShareMetricKind {
    DISTANCE,
    MOVING,
    MAX_SPEED,
    AVG_SPEED,
    ELEVATION,
    FUEL_ECONOMY,
    DURATION,
}

data class ShareMetric(
    val kind: ShareMetricKind,
    val value: String,
    val heroValue: String,
    val unitLabel: String,
    val title: String,
    val detail: String,
)

object ShareMetrics {
    const val MAX_STATS = 3

    fun of(ride: RideInsightInput, climbM: Double? = null, tankKmPerL: Double? = null): List<ShareMetric> {
        val out = ArrayList<ShareMetric>(7)
        if (ride.km > 0) {
            val v = String.format(Locale.US, "%.1f", ride.km)
            out += ShareMetric(ShareMetricKind.DISTANCE, v, v, "KM", "Distance", "$v km")
        }
        if (ride.movingSec > 0) {
            val clock = clock(ride.movingSec)
            val hero = if (ride.movingSec >= 3600) clock else (ride.movingSec / 60).toString()
            out += ShareMetric(ShareMetricKind.MOVING, clock, hero, "MOVING", "Moving Time", clock)
        }
        if (ride.maxSpeedKmh > 0) {
            val v = ride.maxSpeedKmh.toString()
            out += ShareMetric(ShareMetricKind.MAX_SPEED, v, v, "KM/H", "Max Speed", "$v km/h")
        }
        if (ride.avgMovingSpeedKmh > 0) {
            val v = String.format(Locale.US, "%.0f", ride.avgMovingSpeedKmh)
            out += ShareMetric(ShareMetricKind.AVG_SPEED, v, v, "KM/H", "Average Speed", "$v km/h")
        }
        if (climbM != null && climbM >= 40.0) {
            val v = climbM.toInt().toString()
            out += ShareMetric(ShareMetricKind.ELEVATION, v, v, "M", "Elevation", "$v m")
        }
        if (tankKmPerL != null && tankKmPerL > 0) {
            val v = String.format(Locale.US, "%.1f", tankKmPerL)
            out += ShareMetric(ShareMetricKind.FUEL_ECONOMY, v, v, "KM/L", "Fuel Economy", "$v km/L")
        }
        val durationSec = ((ride.endTs - ride.startTs) / 1000L).toInt()
        if (durationSec >= 60 && durationSec != ride.movingSec) {
            val clock = clock(durationSec)
            val hero = if (durationSec >= 3600) clock else (durationSec / 60).toString()
            out += ShareMetric(ShareMetricKind.DURATION, clock, hero, "TIME", "Ride Duration", clock)
        }
        return out
    }

    fun defaultStats(available: List<ShareMetric>): List<ShareMetric> {
        val by = available.associateBy { it.kind }
        val out = ArrayList<ShareMetric>(MAX_STATS)
        by[ShareMetricKind.DISTANCE]?.let { out += it }
        by[ShareMetricKind.MOVING]?.let { if (out.size < MAX_STATS) out += it }
        third(available)?.let { if (out.size < MAX_STATS && out.none { s -> s.kind == it.kind }) out += it }
        return out.take(MAX_STATS)
    }

    fun defaultHero(available: List<ShareMetric>): ShareMetric? =
        available.firstOrNull { it.kind == ShareMetricKind.DISTANCE } ?: available.firstOrNull()

    fun select(available: List<ShareMetric>, kinds: List<ShareMetricKind>, limit: Int = MAX_STATS): List<ShareMetric> {
        val by = available.associateBy { it.kind }
        val seen = HashSet<ShareMetricKind>()
        val out = ArrayList<ShareMetric>(limit)
        for (kind in kinds) {
            if (!seen.add(kind)) continue
            val metric = by[kind] ?: continue
            out += metric
            if (out.size == limit) break
        }
        return out
    }

    fun hero(available: List<ShareMetric>, kind: ShareMetricKind?): ShareMetric? {
        if (kind == null) return null
        return available.firstOrNull { it.kind == kind }
    }

    fun clock(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "$m min"
    }

    private fun third(available: List<ShareMetric>): ShareMetric? {
        val by = available.associateBy { it.kind }
        by[ShareMetricKind.MAX_SPEED]?.takeIf { it.value.toIntOrNull() != null && it.value.toInt() >= 100 }?.let { return it }
        by[ShareMetricKind.ELEVATION]?.let { return it }
        by[ShareMetricKind.FUEL_ECONOMY]?.let { return it }
        by[ShareMetricKind.AVG_SPEED]?.let { return it }
        return null
    }
}
