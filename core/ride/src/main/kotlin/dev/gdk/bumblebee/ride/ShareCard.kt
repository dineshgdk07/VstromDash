package dev.gdk.bumblebee.ride

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ShareShape { STORY, SQUARE }

enum class ShareStyle { INK, CORRIDOR, MAP }

enum class ShareBackground { BLACK, PHOTO, TRANSPARENT }

data class ShareSwitches(
    val shape: ShareShape = ShareShape.STORY,
    val style: ShareStyle = ShareStyle.INK,
    val background: ShareBackground = ShareBackground.BLACK,
    val bigNumber: ShareMetricKind? = ShareMetricKind.DISTANCE,
    val stats: List<ShareMetricKind>? = null,
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
    val bikeName: String,
    val available: List<ShareMetric>,
    val hero: ShareMetric?,
    val stats: List<ShareMetric>,
    val plot: List<SharePlot>,
    val route: List<GpsFix>,
    val demo: Boolean,
    val needsTiles: Boolean,
) {
    /** Missing map tiles become a corridor stroke on black. */
    fun withTiles(complete: Boolean): ShareCardModel {
        if (!needsTiles || complete) return this
        return copy(style = ShareStyle.CORRIDOR, background = ShareBackground.BLACK, needsTiles = false)
    }
}

object ShareCard {
    fun of(
        ride: RideInsightInput,
        track: List<GpsFix> = emptyList(),
        switches: ShareSwitches = ShareSwitches(),
        climbM: Double? = null,
        tankKmPerL: Double? = null,
        bikeName: String = "",
        demo: Boolean = false,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ShareCardModel {
        val route = trim(track)
        val drawable = route.size >= 2
        var style = switches.style
        var background = switches.background
        if (style == ShareStyle.MAP && !drawable) {
            style = ShareStyle.INK
            background = ShareBackground.BLACK
        } else if (style == ShareStyle.MAP) {
            background = ShareBackground.BLACK
        }
        val needsTiles = style == ShareStyle.MAP && drawable
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
        return ShareCardModel(
            shape = switches.shape,
            style = style,
            background = background,
            sentence = firstSentence(RideStory.of(ride, track, zone)),
            whenLabel = whenLabel(ride.startTs, zone),
            dateText = dateText(ride.startTs, zone),
            timeOfDay = RideStory.timeOfDay(ride.startTs, zone),
            bikeName = bikeName.trim(),
            available = available,
            hero = hero,
            stats = stats,
            plot = plot(route),
            route = route,
            demo = demo,
            needsTiles = needsTiles,
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
