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

/**
 * The 3D bike's placement: [position] on the ground, [forward] along the road, and [leanDeg] into
 * the bend (positive leans left, as in a left turn).
 */
data class BikeFrame(val position: Vec3, val forward: Vec3, val leanDeg: Double) {
    /** The bike's axes in the world after leaning: forward, left and up. */
    fun axes(): Triple<Vec3, Vec3, Vec3> {
        val left = Vec3.UP.cross(forward).normalized().let { if (it.length < 0.5) Vec3(0.0, 1.0, 0.0) else it }
        val up = forward.cross(left).normalized()
        val r = Math.toRadians(leanDeg)
        val l = left * kotlin.math.cos(r) - up * kotlin.math.sin(r)
        val u = up * kotlin.math.cos(r) + left * kotlin.math.sin(r)
        return Triple(forward, l, u)
    }
}

/** A named place near the route, from a reverse geocode. */
data class PlaceMark(val lat: Double, val lng: Double, val name: String)

/** A town pinned in 3D at [anchor], where the route passes it at [distanceM]. */
data class PlaceLabel(val name: String, val distanceM: Double, val anchor: Vec3)

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
    /** Towns along the route, for 3D labels. */
    val places: List<PlaceMark> = emptyList(),
    /** This ride's number among all rides, for the tags. */
    val rideNumber: Int? = null,
    /** Like "24°C · Partly cloudy". */
    val weather: String? = null,
    /** Like "Sunrise ride". */
    val sunTag: String? = null,
    /** Fuel gauge readings: when, and how many bars. */
    val fuelBars: List<Pair<Long, Int>> = emptyList(),
    val photos: List<RidePhoto> = emptyList(),
)

/**
 * A ride turned into everything the renderer needs: terrain, the trimmed route, moments, the
 * clock, the camera, the light, and the numbers for the overlays. Built once per export; each
 * frame then only asks it questions.
 */
class RideVideoPlan private constructor(
    val input: RideVideoInput,
    val config: VideoConfig,
    val land: LayeredElevation,
    val exaggeration: Double,
    val path: RoutePath,
    val timeline: VideoTimeline,
    val director: CameraDirector,
    /** The sun at the ride's start. */
    val sun: Sun,
    val length: LengthRange,
    /** True when no elevation could be fetched and the terrain is flat. */
    val flat: Boolean,
    /** Named acts of the ride; empty when it was all one thing or chapters are off. */
    val chapters: List<Chapter>,
    /** Lakes and sea, or null when water is off or none was found. */
    val water: WaterLayer?,
    /** Towns pinned along the route; empty when labels are off. */
    val labels: List<PlaceLabel>,
    /** Every added photo and where it went, or why it couldn't. */
    val photoSpots: List<PhotoSpot>,
    private val alongTs: LongArray,
    private val alongD: DoubleArray,
    private val speedKmh: DoubleArray,
    val trimStartM: Double,
) {
    val totalS: Double get() = timeline.totalS
    val fps: Int get() = FPS
    val frameCount: Int get() = (timeline.totalS * fps).roundToInt()
    val shape: VideoShape get() = config.shape
    val title: String get() = config.title ?: input.title
    val routeKm: Double get() = path.length / 1000

    fun timeOf(frame: Int): Double = frame.toDouble() / fps

    private val sunLat = input.track.first().lat
    private val sunLng = input.track.first().lng

    /**
     * The sun at video time [t]: where it was when the rider was at that point of the ride, so a
     * ride into the evening darkens as it goes. Fixed at the start time when time of day is off.
     */
    fun sunAt(t: Double): Sun {
        if (!config.timeOfDay || alongTs.isEmpty()) return sun
        val cue = timeline.cue(t)
        val ts = when (cue.shot) {
            Shot.INTRO -> alongTs.first()
            Shot.FINISH -> alongTs.last()
            else -> alongTs[indexOf(cue.distanceM)]
        }
        return SunPosition.at(ts, sunLat, sunLng)
    }

    /** True when no ridge stands between [eye] and [p] (both in the scene's exaggerated metres). */
    fun canSee(eye: Vec3, p: Vec3): Boolean {
        for (i in 1 until 32) {
            val q = Vec3.lerp(eye, p, i / 32.0)
            if (land.heightAt(q.x, q.y) * exaggeration > q.z + 5) return false
        }
        return true
    }

    /** The colours at video time [t]. */
    fun lookAt(t: Double, accentArgb: Int) = VideoLook(sunAt(t), accentArgb)

    @Volatile private var lastCamera: Pair<Double, CameraPose>? = null

    /** The camera at [t]; the renderer and overlay ask for the same frame, so the last is kept. */
    fun camera(t: Double): CameraPose {
        lastCamera?.let { (lt, pose) -> if (lt == t) return pose }
        return director.shot(timeline, t).also { lastCamera = t to it }
    }

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

    /**
     * Where the 3D bike is at [d]: on the ground, pointing along the road (up and down hills too),
     * leaning into the bend as a rider would at the recorded speed, lean = atan(v² × curvature / g),
     * smoothed over a few dozen metres and held to 40 degrees.
     */
    fun bikeFrame(d: Double): BikeFrame {
        val p = path.at(d)
        val ground = Vec3(p.x, p.y, land.heightAt(p.x, p.y) * exaggeration)
        val a = path.at((d - 15).coerceAtLeast(0.0))
        val b = path.at((d + 15).coerceAtMost(path.length))
        val forward = (b - a).let { if (it.length < 1e-6) Vec3(0.0, 1.0, 0.0) else it.normalized() }
        var lean = 0.0
        var weight = 0.0
        // Wider Gaussian smoothing avoids lean snapping when sparse GPS points change heading.
        for (k in -4..4) {
            val offset = k * 12.0
            val c = (d + offset).coerceIn(0.0, path.length)
            val h1 = path.heading(c, 18.0, 0.0)
            val h2 = path.heading(c, 0.0, 18.0)
            val turn = kotlin.math.atan2(h1.x * h2.y - h1.y * h2.x, h1.dot(h2)) // radians, left positive
            val curvature = turn / 18.0
            val v = speedAt(c) / 3.6
            val w = kotlin.math.exp(-(k * k) / 8.0)
            lean += kotlin.math.atan(v * v * curvature / 9.81) * w
            weight += w
        }
        val rawLean = Math.toDegrees(lean / weight)
        lean = 36.0 * kotlin.math.tanh(rawLean / 36.0)
        return BikeFrame(ground, forward, lean)
    }

    /** Fuel bars on the gauge when the rider was at [d], or null without gauge readings. */
    fun fuelAt(d: Double): Int? {
        val bars = input.fuelBars
        if (bars.isEmpty() || alongTs.isEmpty()) return null
        val ts = alongTs[indexOf(d)]
        return bars.lastOrNull { it.first <= ts }?.second ?: bars.first().second
    }

    /** "RIDE #42 · 24°C · Partly cloudy · Sunrise ride", or null when tags are off or unknown. */
    val tagsLine: String?
        get() {
            if (!config.tags) return null
            return listOfNotNull(input.rideNumber?.let { "Ride #$it" }, input.weather, input.sunTag)
                .joinToString("  ·  ").ifBlank { null }
        }

    /** True when [id]'s photo made it into the video (placed, and not crowded out). */
    fun photoShown(id: String) = timeline.moments.any { it.photoId == id }

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
            if (!input.showPlaces || !config.places) return null
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

        /**
         * Pins each place where the route passes closest to it, as long as that's within 3 km and
         * not in the hidden first or last stretch (the route is already trimmed). One label per
         * name, and none closer than 8% of the route to another.
         */
        fun labels(places: List<PlaceMark>, path: RoutePath, land: Elevation): List<PlaceLabel> {
            val frame = land.frame
            val out = mutableListOf<PlaceLabel>()
            for (p in places.distinctBy { it.name }) {
                val at = Vec3(frame.x(p.lng), frame.y(p.lat), 0.0)
                var best = -1
                var bestD = Double.MAX_VALUE
                for (i in path.points.indices step 4) {
                    val d = (path.points[i] - at).flatLength
                    if (d < bestD) {
                        bestD = d
                        best = i
                    }
                }
                if (best < 0 || bestD > 3_000) continue
                val along = path.along[best]
                if (out.any { abs(it.distanceM - along) < path.length * 0.08 }) continue
                val ground = path.points[best]
                out += PlaceLabel(p.name, along, Vec3(ground.x, ground.y, ground.z))
            }
            return out.sortedBy { it.distanceM }
        }

        /** Shortest trimmed route worth a video. */
        const val MIN_ROUTE_M = 800.0

        const val FPS = 30

        /**
         * Builds the plan, or null when the ride is too short once the privacy trim is taken
         * off. [tile] fetches elevation tiles and may block; call this off the main thread.
         */
        fun build(
            input: RideVideoInput,
            config: VideoConfig,
            tile: (z: Int, x: Int, y: Int) -> IntArray?,
            /** OpenStreetMap map tiles, for lakes and rivers; null to use the elevation alone. */
            mapTile: ((z: Int, x: Int, y: Int) -> IntArray?)? = null,
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
            val ex = if (flat) 1.0 else config.exaggeration ?: Exaggeration.choose(surface.maxHeight - surface.minHeight, routeSpan)
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
            // The rider's photos: placed on the full ride, then moved onto the trimmed route.
            val photoSpots = PhotoPlacer.place(input.photos, input.track)
            val photoMoments = if (!config.photos) emptyList() else photoSpots.filterIsInstance<PhotoSpot.Placed>().mapNotNull { s ->
                val d = s.rideM - trimStart
                if (d < 0 || d > path.length) return@mapNotNull null
                val clock = s.ts?.let { CLOCK.format(Instant.ofEpochMilli(it).atZone(input.zone)) }
                VideoMoment(MomentKind.PHOTO, d, s.photo.caption ?: clock ?: "On the way", "", s.photo.id)
            }
            val candidates = Moments.candidates(samples, stops, refuels) + photoMoments

            val length = VideoLength.range((total) / 1000, candidates.size)
            val totalS = (config.lengthS ?: length.recommended).coerceIn(length.min, length.max).toDouble()
            val probe = VideoTimeline(totalS, path.length, emptyList())
            val moments = Moments.pick(candidates, path.length, probe.rideS)
            val twistWindow = max(600.0, path.length * 0.02)
            // The teaser is the most important moment.
            val teaser = if (config.coldOpen) moments.minByOrNull { it.kind.rank } else null
            val timeline = VideoTimeline(totalS, path.length, moments, teaser) { d -> path.twistiness(d, twistWindow) * 2 }
            val director = CameraDirector(path, land, ex, config.shape.aspect, CameraStyle.of(config))
            val chapters = if (config.chapters) {
                Chapters.plan(
                    path.length,
                    altitudeAt = { d -> if (flat) null else path.at(d).let { land.heightAt(it.x, it.y) } },
                    twistAt = { d -> path.twistiness(d, twistWindow) },
                    breaks = stops,
                )
            } else {
                emptyList()
            }
            val first = kept.first()
            val sun = SunPosition.at(input.startTs, first.lat, first.lng)
            return RideVideoPlan(
                input, config, land, ex, path, timeline, director, sun, length, flat, chapters,
                if (config.water && !flat) WaterLayer(land, mapTile).takeIf { it.any } else null,
                if (config.placeLabels) labels(input.places, path, land) else emptyList(),
                photoSpots,
                alongTs, alongD, speeds, trimStart,
            )
        }
    }
}
