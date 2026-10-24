package dev.gdk.bumblebee.ride

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

enum class RideEventKind { STOP, FUEL_DROP, REFUEL, MAX_SPEED }

data class RideEvent(
    val ts: Long,
    val kind: RideEventKind,
    val label: String,
    val durationSec: Int? = null,
)

/** A pause between two closed ride segments. [ts] is when the bike stopped. */
data class RideStop(val ts: Long, val durationSec: Int)

/** One segment's clock span. A null [endTs] is still open and is not a stop boundary. */
data class SegmentSpan(val startTs: Long, val endTs: Long?)

data class RideMoment(
    val ts: Long,
    val clock: LocalTime,
    val sinceStartSec: Int,
    val distanceKm: Double,
    val remainingKm: Double,
    val odoEstKm: Double?,
    val speedKmh: Int?,
    val speedSource: SpeedSource,
    val altitudeM: Double?,
    val fuelBars: Int?,
    val position: GpsFix?,
    val nearestEvent: RideEvent?,
)

/**
 * One scrubbable ride. Cumulative distance is computed once in [build]; [at] and [atDistance]
 * only binary-search that array.
 *
 * Fixes at most [INTERPOLATE_MAX_GAP_MS] apart are interpolated. A longer hole holds the last
 * fix until the next real one, so nothing is invented across a GPS gap and distance (and the
 * odo estimate) never goes backwards.
 */
class RideTimeline private constructor(
    val startTs: Long,
    val endTs: Long,
    private val zone: ZoneId,
    private val gpsOnly: Boolean,
    private val odoStartKm: Int?,
    private val odoFactor: Double,
    private val samples: List<FixSample>,
    private val telemetry: List<TelemetrySample>,
    val events: List<RideEvent>,
) {
    private val totalM: Double = samples.lastOrNull()?.distanceM ?: 0.0

    fun at(ts: Long): RideMoment {
        val clamped = ts.coerceIn(startTs, endTs)
        val pose = poseAt(clamped)
        val (speed, source) = speedAt(clamped, pose.speedMps)
        val distanceKm = pose.distanceM / 1000.0
        val totalKm = totalM / 1000.0
        return RideMoment(
            ts = clamped,
            clock = Instant.ofEpochMilli(clamped).atZone(zone).toLocalTime(),
            sinceStartSec = ((clamped - startTs) / 1000L).toInt().coerceAtLeast(0),
            distanceKm = distanceKm,
            remainingKm = (totalKm - distanceKm).coerceAtLeast(0.0),
            odoEstKm = odoAt(distanceKm),
            speedKmh = speed,
            speedSource = source,
            altitudeM = pose.altitudeM,
            fuelBars = fuelAt(clamped),
            position = pose.toFix(clamped),
            nearestEvent = nearestEvent(clamped),
        )
    }

    fun atDistance(km: Double): RideMoment {
        if (samples.isEmpty() || totalM <= 0.0) return at(startTs)
        val targetM = (km.coerceAtLeast(0.0) * 1000.0).coerceAtMost(totalM)
        var lo = 0
        var hi = samples.lastIndex
        var idx = samples.size
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (samples[mid].distanceM >= targetM - 1e-6) {
                idx = mid
                hi = mid - 1
            } else {
                lo = mid + 1
            }
        }
        if (idx <= 0) return at(samples.first().ts)
        if (idx >= samples.size) return at(endTs)
        val right = samples[idx]
        val left = samples[idx - 1]
        if (targetM <= left.distanceM + 1e-6) return at(left.ts)
        if (right.gapFromPrev) return at(right.ts)
        val span = right.distanceM - left.distanceM
        if (span <= 1e-6) return at(left.ts)
        val frac = ((targetM - left.distanceM) / span).coerceIn(0.0, 1.0)
        val whenTs = left.ts + ((right.ts - left.ts) * frac).toLong()
        return at(whenTs)
    }

    private fun odoAt(distanceKm: Double): Double? {
        val start = odoStartKm ?: return null
        if (gpsOnly) return null
        return start + distanceKm * odoFactor
    }

    private fun poseAt(ts: Long): FixSample {
        if (samples.isEmpty() || ts < samples.first().ts) return FixSample.EMPTY
        val idx = floorIndex(ts)
        val left = samples[idx]
        if (idx == samples.lastIndex || left.ts == ts) return left
        val right = samples[idx + 1]
        if (right.gapFromPrev) return left
        val span = (right.ts - left.ts).toDouble()
        val frac = if (span <= 0.0) 0.0 else ((ts - left.ts) / span).coerceIn(0.0, 1.0)
        return left.lerpToward(right, frac)
    }

    private fun speedAt(ts: Long, gpsMps: Double?): Pair<Int?, SpeedSource> {
        val bike = nearestTelemetry(ts)
        if (bike != null) return bike.speedKmh to SpeedSource.BLE
        if (gpsMps != null) return (gpsMps * 3.6).roundToInt() to SpeedSource.GPS
        return null to SpeedSource.NONE
    }

    private fun nearestTelemetry(ts: Long): TelemetrySample? {
        if (telemetry.isEmpty()) return null
        val idx = telemetryFloor(ts)
        val earlier = telemetry.getOrNull(idx)
        val later = telemetry.getOrNull(idx + 1)
        return listOfNotNull(earlier, later)
            .filter { abs(it.ts - ts) <= SPEED_MATCH_MS }
            .minWithOrNull(compareBy({ abs(it.ts - ts) }, { it.ts }))
    }

    private fun fuelAt(ts: Long): Int? {
        val idx = telemetryFloor(ts)
        if (idx < 0) return null
        return telemetry[idx].fuelBars
    }

    private fun nearestEvent(ts: Long): RideEvent? =
        events
            .filter { abs(it.ts - ts) <= EVENT_MATCH_MS }
            .minWithOrNull(compareBy({ abs(it.ts - ts) }, { it.ts }, { it.kind.ordinal }))

    private fun floorIndex(ts: Long): Int {
        var lo = 0
        var hi = samples.lastIndex
        var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (samples[mid].ts <= ts) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }

    private fun telemetryFloor(ts: Long): Int {
        var lo = 0
        var hi = telemetry.lastIndex
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (telemetry[mid].ts <= ts) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }

    private data class FixSample(
        val ts: Long,
        val lat: Double,
        val lng: Double,
        val altitudeM: Double?,
        val bearingDeg: Float?,
        val speedMps: Double?,
        val accuracyM: Float?,
        val distanceM: Double,
        val gapFromPrev: Boolean,
        val positioned: Boolean,
    ) {
        fun toFix(atTs: Long): GpsFix? {
            if (!positioned) return null
            return GpsFix(
                ts = atTs,
                lat = lat,
                lng = lng,
                altitudeM = altitudeM,
                bearingDeg = bearingDeg,
                speedMps = speedMps?.toFloat(),
                accuracyM = accuracyM,
            )
        }

        fun lerpToward(right: FixSample, frac: Double): FixSample = FixSample(
            ts = ts,
            lat = lat + (right.lat - lat) * frac,
            lng = lng + (right.lng - lng) * frac,
            altitudeM = blend(altitudeM, right.altitudeM, frac),
            bearingDeg = bearingDeg,
            speedMps = blend(speedMps, right.speedMps, frac),
            accuracyM = accuracyM,
            distanceM = distanceM + (right.distanceM - distanceM) * frac,
            gapFromPrev = false,
            positioned = true,
        )

        companion object {
            val EMPTY = FixSample(
                ts = 0L,
                lat = 0.0,
                lng = 0.0,
                altitudeM = null,
                bearingDeg = null,
                speedMps = null,
                accuracyM = null,
                distanceM = 0.0,
                gapFromPrev = false,
                positioned = false,
            )
        }
    }

    companion object {
        const val SPEED_MATCH_MS = 6_000L
        const val EVENT_MATCH_MS = 30_000L
        const val INTERPOLATE_MAX_GAP_MS = 5_000L
        const val STOP_MIN_GAP_MS = 60_000L

        fun empty(): RideTimeline = build()

        fun odoGpsFactor(odoStartKm: Int?, odoEndKm: Int?, gpsDistanceM: Double): Double {
            val start = odoStartKm ?: return 1.0
            val end = odoEndKm ?: return 1.0
            val delta = (end - start).toDouble()
            val gpsKm = gpsDistanceM / 1000.0
            if (delta <= 0.0 || gpsKm <= 0.0) return 1.0
            return delta / gpsKm
        }

        fun stopsBetween(segments: List<SegmentSpan>): List<RideStop> {
            val closed = segments.mapNotNull { span ->
                val end = span.endTs ?: return@mapNotNull null
                span.startTs to end
            }.sortedBy { it.first }
            if (closed.size < 2) return emptyList()
            return (0 until closed.lastIndex).mapNotNull { i ->
                val gapMs = closed[i + 1].first - closed[i].second
                if (gapMs < STOP_MIN_GAP_MS) return@mapNotNull null
                RideStop(ts = closed[i].second, durationSec = (gapMs / 1000L).toInt())
            }
        }

        fun fromRide(
            startTs: Long,
            endTs: Long,
            gpsOnly: Boolean,
            odoStartKm: Int?,
            odoEndKm: Int?,
            gpsDistanceM: Double,
            track: List<GpsFix>,
            telemetry: List<TelemetrySample>,
            fuelEvents: List<FuelEvent>,
            segments: List<SegmentSpan>,
            zone: ZoneId = ZoneId.systemDefault(),
        ): RideTimeline = build(
            track = track,
            telemetry = telemetry,
            fuelEvents = fuelEvents,
            stops = stopsBetween(segments),
            odoStartKm = odoStartKm,
            odoGpsFactor = odoGpsFactor(odoStartKm, odoEndKm, gpsDistanceM),
            gpsOnly = gpsOnly,
            startTs = startTs,
            endTs = endTs,
            zone = zone,
        )

        fun build(
            track: List<GpsFix> = emptyList(),
            telemetry: List<TelemetrySample> = emptyList(),
            fuelEvents: List<FuelEvent> = emptyList(),
            stops: List<RideStop> = emptyList(),
            odoStartKm: Int? = null,
            odoGpsFactor: Double = 1.0,
            gpsOnly: Boolean = false,
            startTs: Long = 0L,
            endTs: Long = startTs,
            zone: ZoneId = ZoneId.systemDefault(),
        ): RideTimeline {
            val rideEnd = maxOf(startTs, endTs)
            val samples = compress(track)
            val tel = telemetry.sortedBy { it.ts }
            val shell = RideTimeline(
                startTs = startTs,
                endTs = rideEnd,
                zone = zone,
                gpsOnly = gpsOnly,
                odoStartKm = odoStartKm,
                odoFactor = odoGpsFactor.coerceAtLeast(0.0),
                samples = samples,
                telemetry = tel,
                events = emptyList(),
            )
            return RideTimeline(
                startTs = startTs,
                endTs = rideEnd,
                zone = zone,
                gpsOnly = gpsOnly,
                odoStartKm = odoStartKm,
                odoFactor = odoGpsFactor.coerceAtLeast(0.0),
                samples = samples,
                telemetry = tel,
                events = shell.assembleEvents(stops, fuelEvents),
            )
        }

        private fun compress(track: List<GpsFix>): List<FixSample> {
            val ordered = track
                .sortedBy { it.ts }
                .fold(mutableListOf<GpsFix>()) { acc, fix ->
                    if (acc.lastOrNull()?.ts == fix.ts) acc[acc.lastIndex] = fix else acc += fix
                    acc
                }
            var distance = 0.0
            return ordered.mapIndexed { index, fix ->
                val prev = ordered.getOrNull(index - 1)
                if (prev != null) {
                    distance += Geo.haversineMeters(prev.lat, prev.lng, fix.lat, fix.lng)
                }
                FixSample(
                    ts = fix.ts,
                    lat = fix.lat,
                    lng = fix.lng,
                    altitudeM = fix.altitudeM,
                    bearingDeg = fix.bearingDeg,
                    speedMps = fix.speedMps?.toDouble(),
                    accuracyM = fix.accuracyM,
                    distanceM = distance,
                    gapFromPrev = prev != null && fix.ts - prev.ts > INTERPOLATE_MAX_GAP_MS,
                    positioned = true,
                )
            }
        }
    }

    private fun assembleEvents(stops: List<RideStop>, fuelEvents: List<FuelEvent>): List<RideEvent> {
        val listed = ArrayList<RideEvent>(stops.size + fuelEvents.size + 1)
        for (stop in stops) {
            listed += RideEvent(stop.ts, RideEventKind.STOP, "Stop", stop.durationSec)
        }
        for (event in fuelEvents) {
            val refill = event.type == FuelEventType.REFUEL
            listed += RideEvent(
                ts = event.ts,
                kind = if (refill) RideEventKind.REFUEL else RideEventKind.FUEL_DROP,
                label = if (refill) "Refuel" else "Fuel drop",
            )
        }
        maxSpeedEvent()?.let { listed += it }
        listed.sortWith(compareBy({ it.ts }, { it.kind.ordinal }))
        return listed
    }

    private fun maxSpeedEvent(): RideEvent? {
        var bestSpeed: Int? = null
        var bestTs = Long.MAX_VALUE
        fun consider(ts: Long, speed: Int) {
            val best = bestSpeed
            if (best == null || speed > best || (speed == best && ts < bestTs)) {
                bestSpeed = speed
                bestTs = ts
            }
        }
        for (sample in telemetry) {
            if (sample.ts in startTs..endTs) consider(sample.ts, sample.speedKmh)
        }
        for (point in samples) {
            if (point.ts !in startTs..endTs) continue
            val (speed, source) = speedAt(point.ts, point.speedMps)
            if (speed != null && source == SpeedSource.GPS) consider(point.ts, speed)
        }
        val at = bestTs.takeIf { bestSpeed != null } ?: return null
        return RideEvent(at, RideEventKind.MAX_SPEED, "Max speed")
    }
}

private fun blend(left: Double?, right: Double?, frac: Double): Double? {
    if (left == null) return null
    if (right == null) return left
    return left + (right - left) * frac
}
