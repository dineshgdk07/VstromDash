package dev.gdk.bumblebee.ride

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ShareLook { NIGHT_GLOW, POSTER, COCKPIT, RIDE_PASS }

enum class ShareShape { STORY, FEED, SQUARE }

/** Kept until Task 11 so the v1 painter still compiles. Derived from the background. */
enum class ShareStyle { INK, CORRIDOR, MAP }

enum class ShareBackground { OWN, MAP, PHOTO, TRANSPARENT }

enum class ShareAccent(val argb: Int) {
    YELLOW(0xFFFAC816.toInt()),
    RED_ORANGE(0xFFFF5A36.toInt()),
    SKY(0xFF5EC8FF.toInt()),
    GREEN(0xFF7CF29A.toInt()),
    WHITE(0xFFF2F2F2.toInt()),
}

data class ShareSwitches(
    val look: ShareLook = ShareLook.NIGHT_GLOW,
    val shape: ShareShape = ShareShape.STORY,
    val background: ShareBackground = ShareBackground.OWN,
    val accent: ShareAccent = ShareAccent.YELLOW,
    val bigNumber: ShareMetricKind? = ShareMetricKind.DISTANCE,
    val stats: List<ShareMetricKind>? = null,
    val showPlaces: Boolean = true,
    val showHighlight: Boolean = true,
    val showWeather: Boolean = true,
)

/** Facts the caller looked up. All optional. */
data class ShareExtras(
    val rideName: String? = null,
    val startPlace: String? = null,
    val endPlace: String? = null,
    val rideNumber: Int? = null,
    val highlight: Highlight? = null,
    val weatherLine: String? = null,
    val odoEndKm: Int? = null,
    val gpsOnly: Boolean = false,
)

data class SharePlot(val x: Float, val y: Float, val pace: Float)

data class ShareCardModel(
    val shape: ShareShape,
    val style: ShareStyle,
    val background: ShareBackground,
    val sentence: String,
    val whenLabel: String,
    val dateText: String,
    val timeOfDay: String,
    val available: List<ShareMetric>,
    val hero: ShareMetric?,
    val stats: List<ShareMetric>,
    val plot: List<SharePlot>,
    val route: List<GpsFix>,
    val demo: Boolean,
    val needsTiles: Boolean,
    val look: ShareLook = ShareLook.NIGHT_GLOW,
    val accent: ShareAccent = ShareAccent.YELLOW,
    val headline: String = "",
    val fromCode: String? = null,
    val toCode: String? = null,
    val fromPlace: String? = null,
    val toPlace: String? = null,
    val rideNumberText: String? = null,
    val highlight: Highlight? = null,
    val weatherLine: String? = null,
    val elevation: List<Float> = emptyList(),
    val speedTrace: List<Float> = emptyList(),
    val odoKm: Int? = null,
    val stops: List<GpsFix> = emptyList(),
    val mapFallback: Boolean = false,
) {
    /** Missing map tiles stay on the same look, on the look's own background, and say so. */
    fun withTiles(complete: Boolean): ShareCardModel {
        if (!needsTiles || complete) return this
        return copy(
            style = ShareStyle.INK,
            background = ShareBackground.OWN,
            needsTiles = false,
            mapFallback = true,
        )
    }
}

object ShareCard {
    fun of(
        ride: RideInsightInput,
        track: List<GpsFix> = emptyList(),
        switches: ShareSwitches = ShareSwitches(),
        climbM: Double? = null,
        tankKmPerL: Double? = null,
        extras: ShareExtras = ShareExtras(),
        demo: Boolean = false,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ShareCardModel {
        val route = trim(track)
        val drawable = route.size >= 2
        var background = switches.background
        if (background == ShareBackground.MAP && !drawable) {
            background = ShareBackground.OWN
        }
        val needsTiles = background == ShareBackground.MAP && drawable
        val style = if (background == ShareBackground.MAP) ShareStyle.MAP else ShareStyle.INK
        val available = ShareMetrics.of(ride, climbM, tankKmPerL)
        val stats = if (switches.stats == null) {
            ShareMetrics.defaultStats(available)
        } else {
            ShareMetrics.select(available, switches.stats)
        }
        val hero = if (switches.bigNumber == ShareMetricKind.DISTANCE && available.none { it.kind == ShareMetricKind.DISTANCE }) {
            ShareMetrics.defaultHero(available)
        } else {
            ShareMetrics.hero(available, switches.bigNumber)
        }
        val timeOfDay = RideStory.timeOfDay(ride.startTs, zone)
        val fromPlace = extras.startPlace?.takeIf { switches.showPlaces && it.isNotBlank() }
        val toPlace = extras.endPlace?.takeIf { switches.showPlaces && it.isNotBlank() }
        return ShareCardModel(
            shape = switches.shape,
            style = style,
            background = background,
            sentence = firstSentence(RideStory.of(ride, track, zone)),
            whenLabel = whenLabel(ride.startTs, zone),
            dateText = dateText(ride.startTs, zone),
            timeOfDay = timeOfDay,
            available = available,
            hero = hero,
            stats = stats,
            plot = plot(route),
            route = route,
            demo = demo,
            needsTiles = needsTiles,
            look = switches.look,
            accent = switches.accent,
            headline = RideNames.headline(RideNames.clean(extras.rideName), fromPlace, toPlace, switches.showPlaces, timeOfDay),
            fromCode = fromPlace?.let(RideNames::code),
            toCode = toPlace?.let(RideNames::code),
            fromPlace = fromPlace,
            toPlace = toPlace,
            rideNumberText = extras.rideNumber?.let { String.format(Locale.US, "No. %04d", it) },
            highlight = extras.highlight.takeIf { switches.showHighlight },
            weatherLine = extras.weatherLine.takeIf { switches.showWeather },
            elevation = emptyList(),
            speedTrace = emptyList(),
            odoKm = extras.odoEndKm.takeUnless { extras.gpsOnly },
            stops = emptyList(),
            mapFallback = false,
        )
    }

    private fun trim(track: List<GpsFix>): List<GpsFix> {
        if (track.size < 2) return emptyList()
        val cum = cumulative(track)
        val kept = SharePrivacy.retainAfterRide(cum, cum.last())
        if (kept.size < 2) return emptyList()
        var next = 0
        val out = ArrayList<GpsFix>(kept.size)
        for (i in track.indices) {
            if (next < kept.size && cum[i] == kept[next]) {
                out += track[i]
                next++
            }
        }
        return if (out.size >= 2) out else emptyList()
    }

    private fun cumulative(track: List<GpsFix>): List<Double> {
        val out = DoubleArray(track.size)
        for (i in 1 until track.size) {
            val a = track[i - 1]
            val b = track[i]
            out[i] = out[i - 1] + Geo.haversineMeters(a.lat, a.lng, b.lat, b.lng)
        }
        return out.toList()
    }

    private fun plot(route: List<GpsFix>): List<SharePlot> {
        if (route.size < 2) return emptyList()
        val speeds = route.map { paceSpeed(it) }
        val peak = speeds.max().coerceAtLeast(0.001f)
        val paced = route.zip(speeds) { fix, speed -> fix to (speed / peak).coerceIn(0f, 1f) }
        val sampled = TrackPlot.stride(paced)
        val points = TrackPlot.normalize(sampled.map { it.first })
        return points.zip(sampled.map { it.second }) { point, pace ->
            SharePlot(point.x, point.y, pace)
        }
    }

    private fun paceSpeed(fix: GpsFix): Float = (fix.speedMps ?: 0f).coerceAtLeast(0f)

    private fun firstSentence(text: String): String {
        val end = text.indexOf(". ")
        return if (end < 0) text.trim() else text.substring(0, end + 1).trim()
    }

    private fun dateText(startTs: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
            .format(Instant.ofEpochMilli(startTs).atZone(zone))
            .uppercase(Locale.ENGLISH)

    private fun whenLabel(startTs: Long, zone: ZoneId): String {
        val day = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
            .format(Instant.ofEpochMilli(startTs).atZone(zone))
        return "$day · ${RideStory.timeOfDay(startTs, zone)}"
    }
}
