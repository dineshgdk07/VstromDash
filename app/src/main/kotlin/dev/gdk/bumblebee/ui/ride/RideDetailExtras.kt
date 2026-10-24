package dev.gdk.bumblebee.ui.ride

import dev.gdk.bumblebee.data.local.RideCaptureRepository
import dev.gdk.bumblebee.data.local.RideEntity
import dev.gdk.bumblebee.ride.FuelFill
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.RideAnalytics
import dev.gdk.bumblebee.ride.RideCost
import dev.gdk.bumblebee.ride.RideEventKind
import dev.gdk.bumblebee.ride.RideScore
import dev.gdk.bumblebee.ride.RideSummary
import dev.gdk.bumblebee.ride.RideTimeline
import java.time.Instant
import java.time.ZoneId

/** The ride detail's score, comparison and cost, shared by the Ride and Explorer tabs. */
internal data class DetailExtras(val score: RideScore?, val usual: List<String>, val cost: RideCost?)

/**
 * [rides] are the rider's finished rides to compare against. [routes] maps ride id to route
 * cluster; leave it empty to skip the same-route comparison.
 */
internal suspend fun detailExtras(
    capture: RideCaptureRepository,
    entity: RideEntity,
    track: List<GpsFix>,
    timeline: RideTimeline,
    litres: Double?,
    rupeesPerKm: Double?,
    rides: List<RideEntity>,
    routes: Map<Long, Int>,
): DetailExtras {
    val stops = timeline.events.count { it.kind == RideEventKind.STOP }
    // GPS speed at one fix a second shows hard changes; the cluster's samples are the fallback.
    val samples = track.mapNotNull { fix -> fix.speedMps?.let { fix.ts to (it * 3.6f).toInt() } }
        .ifEmpty { capture.speedsForRide(entity.id) }
    val score = if (entity.movingSec >= 120) RideAnalytics.score(entity.movingSec, entity.idleSec, stops, samples) else null
    val zone = ZoneId.systemDefault()
    fun RideEntity.summary(): RideSummary {
        val date = Instant.ofEpochMilli(startTs).atZone(zone)
        return RideSummary(id, startTs, distanceKm(), movingSec, avgMovingSpeedKmh, date.year * 100 + date.monthValue)
    }
    val others = rides.filter { !it.isDemo && it.mergedIntoId == null }
    val cluster = if (entity.isDemo) null else routes[entity.id]
    val mates = if (cluster == null) emptyList() else others.filter { routes[it.id] == cluster }
    val usual = if (entity.isDemo) emptyList() else RideAnalytics.usual(entity.summary(), others.map { it.summary() }, mates.map { it.summary() })
    val cost = RideAnalytics.cost(entity.distanceKm(), litres, recentPumpPrice(capture.fuelFills()), rupeesPerKm)
    return DetailExtras(score, usual, cost)
}

/** Pump price over the last three priced fills. */
internal fun recentPumpPrice(fills: List<FuelFill>): Double? {
    val priced = fills.filter { (it.litres ?: 0.0) > 0 && (it.rupees ?: 0.0) > 0 }.sortedBy { it.ts }.takeLast(3)
    if (priced.isEmpty()) return null
    return priced.sumOf { it.rupees!! } / priced.sumOf { it.litres!! }
}

/** Odometer distance when both ends are known, else GPS. */
private fun RideEntity.distanceKm(): Double {
    val s = odoStartKm
    val e = odoEndKm
    if (s != null && e != null) return (e - s).toDouble()
    return gpsDistanceM / 1000.0
}
