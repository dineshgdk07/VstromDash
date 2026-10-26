package dev.gdk.bumblebee.video

import dev.gdk.bumblebee.ride.Geo
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.SharePrivacy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Everything about a ride the video needs, gathered by the app. */
data class RideVideoInput(
    val track: List<GpsFix>,
    /** When the bike stopped, and for how long, for breaks between segments. */
    val stops: List<Pair<Long, Int>> = emptyList(),
    val refuelTs: List<Long> = emptyList(),
    val title: String,
    val fromPlace: String? = null,
    val toPlace: String? = null,
    val startTs: Long,
    val movingSec: Int,
    val zone: ZoneId = ZoneId.systemDefault(),
    val showPlaces: Boolean = true,
)

/** What the rider chose on the setup screen. */
data class VideoSettings(
    val shape: VideoShape = VideoShape.VERTICAL,
    /** Null for the recommended length. */
    val lengthS: Int? = null,
    val fps: Int = 30,
)

/**
 * A ride turned into everything the renderer needs: terrain, the trimmed route, moments, the
 * clock, the camera, the light, and the numbers for the overlays. Built once per export; each
 * frame then only asks it questions.
 */
class RideVideoPlan private constructor(
    val input: RideVideoInput,
    val settings: VideoSettings,
    val land: LayeredElevation,
    val exaggeration: Double,
    val path: RoutePath,
    val timeline: VideoTimeline,
    val director: CameraDirector,
    val sun: Sun,
    val length: LengthRange,
    /** True when no elevation could be fetched and the terrain is flat. */
    val flat: Boolean,
    private val alongTs: LongArray,
    private val alongD: DoubleArray,
    private val speedKmh: DoubleArray,
    val trimStartM: Double,
) {
    val totalS: Double get() = timeline.totalS
    val frameCount: Int get() = (timeline.totalS * settings.fps).roundToInt()
    val routeKm: Double get() = path.length / 1000

    fun timeOf(frame: Int): Double = frame.toDouble() / settings.fps

    fun camera(t: Double): CameraPose = director.shot(timeline, t)

    /** True height in metres on the route at [d]. */
    fun altitudeAt(d: Double): Double = path.at(d).let { land.heightAt(it.x, it.y) }

    /** Recorded speed near [d], smoothed. */
    fun speedAt(d: Double): Int {
        if (alongD.isEmpty()) return 0
        val i = indexOf(d)
        val lo = max(0, i - 3)
        val hi = min(alongD.lastIndex, i + 3)
        var sum = 0.0
        for (k in lo..hi) sum += speedKmh[k]
        return (sum / (hi - lo + 1)).roundToInt()
    }

    /** Time of day when the rider was at [d]. */
    fun clockAt(d: Double): String {
        if (alongD.isEmpty()) return ""
        val ts = alongTs[indexOf(d)]
        return CLOCK.format(Instant.ofEpochMilli(ts).atZone(input.zone))
    }

    /** Distance ridden at [d] on the trimmed route, counted from the real start. */
    fun odoKm(d: Double): Double = (d + trimStartM) / 1000

    private fun indexOf(d: Double): Int {
        var lo = 0
        var hi = alongD.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (alongD[mid] <= d) lo = mid else hi = mid - 1
        }
        return lo
    }

    val dateLine: String
        get() {
            val at = Instant.ofEpochMilli(input.startTs).atZone(input.zone)
            val part = when (at.hour) {
                in 4..11 -> "morning"
                in 12..16 -> "afternoon"
                in 17..19 -> "evening"
                else -> "night"
            }
            return "${DATE.format(at)} · $part"
        }

    val placesLine: String?
        get() {
            if (!input.showPlaces) return null
            val a = input.fromPlace
            val b = input.toPlace
            return when {
                a != null && b != null && a != b -> "$a  —  $b"
                a != null -> a
                b != null -> b
                else -> null
            }
        }

    /** The closing card's numbers: value and label. */
    fun stats(): List<Pair<String, String>> {
        val climb = climbM()
        val high = (0..200).maxOf { altitudeAt(path.length * it / 200) }
        val h = input.movingSec / 3600
        val m = input.movingSec % 3600 / 60
        val km = (path.length + trimStartM * 2) / 1000
        return listOf(
            String.format(Locale.ENGLISH, "%.1f", km) to "KM",
            String.format(Locale.ENGLISH, "%d:%02d", h, m) to "MOVING",
            String.format(Locale.ENGLISH, "%,d", climb.roundToInt()) to "M CLIMBED",
            String.format(Locale.ENGLISH, "%,d", high.roundToInt()) to "M HIGHEST",
        )
    }

    /** Total climb from terrain heights every 50 m, ignoring rises under 3 m (noise). */
    fun climbM(): Double {
        val n = max(2, (path.length / 50).toInt())
        var total = 0.0
        var base = altitudeAt(0.0)
        for (i in 1..n) {
            val a = altitudeAt(path.length * i / n)
            if (a > base + 3) {
                total += a - base
                base = a
            } else if (a < base) {
                base = a
            }
        }
        return total
    }

    companion object {
        private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        private val DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)

        /** A GPS hole longer than this, and wider than [GAP_M], is drawn dashed. */
        const val GAP_MS = 30_000L
        const val GAP_M = 200.0

        /** Shortest trimmed route worth a video. */
        const val MIN_ROUTE_M = 800.0

        /**
         * Builds the plan, or null when the ride is too short once the privacy trim is taken
         * off. [tile] fetches elevation tiles and may block; call this off the main thread.
         */
        fun build(
            input: RideVideoInput,
            settings: VideoSettings,
            tile: (z: Int, x: Int, y: Int) -> IntArray?,
        ): RideVideoPlan? {
            val fixes = input.track.filter { (it.accuracyM ?: 0f) <= 50f }
            if (fixes.size < 2) return null
            val cum = DoubleArray(fixes.size)
            for (i in 1 until fixes.size) {
                val a = fixes[i - 1]
                val b = fixes[i]
                cum[i] = cum[i - 1] + Geo.haversineMeters(a.lat, a.lng, b.lat, b.lng)
            }
            val total = cum.last()
            val zone = SharePrivacy.ZONE_M
            if (total - zone * 2 < MIN_ROUTE_M) return null
            val keep = fixes.indices.filter { cum[it] in zone..(total - zone) }
            if (keep.size < 2) return null
            val kept = keep.map { fixes[it] }
            val trimStart = cum[keep.first()]
            val gapAfter = (0 until kept.lastIndex).filter { i ->
                val a = kept[i]
                val b = kept[i + 1]
                b.ts - a.ts > GAP_MS && Geo.haversineMeters(a.lat, a.lng, b.lat, b.lng) > GAP_M
            }.toSet()

            val latLng = kept.map { it.lat to it.lng }
            val routeBounds = LatLngBounds.of(latLng)!!.padded(0.25, 0.04)
            val wide = LatLngBounds.aroundRoute(latLng, margin = 0.9)!!
            var land = LayeredElevation.forRoute(routeBounds, wide, tile = tile)
            val baseMissing = land.base.missing.toDouble() / (land.base.cols * land.base.rows)
            val flat = baseMissing > 0.5
            if (flat) land = LayeredElevation(ElevationGrid.flat(wide, cells = 128), null)
            val surface = land.detail ?: land.base
            val routeSpan = max(surface.width, surface.height)
            val ex = if (flat) 1.0 else Exaggeration.choose(surface.maxHeight - surface.minHeight, routeSpan)
            val path = RoutePath.build(latLng, land, ex, gapAfter = gapAfter)

            // Distance along the trimmed route for each kept fix, for speeds and the clock.
            val alongD = DoubleArray(kept.size) { cum[keep[it]] - trimStart }
            val alongTs = LongArray(kept.size) { kept[it].ts }
            val speeds = DoubleArray(kept.size) { i ->
                kept[i].speedMps?.toDouble()?.times(3.6) ?: run {
                    val j = max(1, i)
                    val dt = (alongTs[j] - alongTs[j - 1]) / 1000.0
                    if (dt <= 0) 0.0 else (alongD[j] - alongD[j - 1]) / dt * 3.6
                }
            }
            fun distanceAtTs(ts: Long): Double? {
                if (ts < alongTs.first() || ts > alongTs.last()) return null
                var best = 0
                var bestDt = Long.MAX_VALUE
                for (i in alongTs.indices) {
                    val dt = abs(alongTs[i] - ts)
                    if (dt < bestDt) {
                        bestDt = dt
                        best = i
                    }
                }
                return alongD[best]
            }

            val step = max(25.0, path.length / 2000)
            val samples = (0..(path.length / step).toInt()).map { k ->
                val d = k * step
                val p = path.at(d)
                val i = run {
                    var lo = 0
                    var hi = alongD.lastIndex
                    while (lo < hi) {
                        val mid = (lo + hi + 1) / 2
                        if (alongD[mid] <= d) lo = mid else hi = mid - 1
                    }
                    lo
                }
                TrackSample(d, if (flat) null else land.heightAt(p.x, p.y), speeds[i])
            }
            val stops = input.stops.mapNotNull { (ts, dur) -> distanceAtTs(ts)?.let { StopMark(it, dur) } }
            val refuels = input.refuelTs.mapNotNull { distanceAtTs(it) }
            val candidates = Moments.candidates(samples, stops, refuels)

            val length = VideoLength.range((total) / 1000, candidates.size)
            val totalS = (settings.lengthS ?: length.recommended).coerceIn(length.min, length.max).toDouble()
            val probe = VideoTimeline(totalS, path.length, emptyList())
            val moments = Moments.pick(candidates, path.length, probe.rideS)
            val twistWindow = max(600.0, path.length * 0.02)
            val timeline = VideoTimeline(totalS, path.length, moments) { d -> path.twistiness(d, twistWindow) * 2 }
            val director = CameraDirector(path, land, ex, settings.shape.aspect)
            val first = kept.first()
            val sun = SunPosition.at(input.startTs, first.lat, first.lng)
            return RideVideoPlan(
                input, settings, land, ex, path, timeline, director, sun, length, flat,
                alongTs, alongD, speeds, trimStart,
            )
        }
    }
}
