package dev.gdk.bumblebee.ui.sharecard.looks

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.ShareBackground
import dev.gdk.bumblebee.ride.ShareCardModel
import dev.gdk.bumblebee.ride.ShareMetric
import dev.gdk.bumblebee.ride.ShareMetricKind
import dev.gdk.bumblebee.ui.sharecard.ShareCardMapTransform
import dev.gdk.bumblebee.ui.sharecard.ShareCardRoute
import dev.gdk.bumblebee.ui.sharecard.ShareText
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Shared sizing and drawing for the looks. Sizes are in mockup units: the mockups are 270 wide,
 * so one unit is 4 px on a 1080 card (`docs/superpowers/specs/mockups/share-card-v2-styles.html`).
 */
internal val LookContext.u: Float get() = layout.width / 270f

/** Card height in units: 480 story, 337.5 feed, 270 square. */
internal val LookContext.heightU: Float get() = layout.height / u

internal val LookContext.onImage: Boolean
    get() = background == ShareBackground.MAP || background == ShareBackground.PHOTO

internal fun textPaint(size: Float, color: Int, face: Typeface, spacing: Float = 0f) =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = face
        letterSpacing = spacing
    }

/** Small uppercase tracked label, the mockups' `.lbl`. */
internal fun LookContext.label(color: Int, size: Float = 9f, face: Typeface = fonts.body) =
    textPaint(size * u, color, face, 0.22f)

internal fun String.upper(): String = uppercase(Locale.ENGLISH)

/** Draws [text] fitted to [maxWidth]. Returns the drawn width. */
internal fun Canvas.fitted(
    text: String,
    x: Float,
    y: Float,
    paint: TextPaint,
    maxWidth: Float,
    minSize: Float,
    align: Paint.Align = Paint.Align.LEFT,
): Float {
    val shown = ShareText.fit(paint, text, maxWidth, minSize)
    val w = paint.measureText(shown) - trailing(paint)
    val left = when (align) {
        Paint.Align.LEFT -> x
        Paint.Align.CENTER -> x - w / 2f
        Paint.Align.RIGHT -> x - w
    }
    drawText(shown, left, y, paint)
    return w
}

/** Letter spacing also lands after the last glyph. Right and centred text drops it. */
private fun trailing(paint: Paint): Float = paint.letterSpacing * paint.textSize

internal fun withAlpha(color: Int, alpha: Int): Int =
    Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

internal fun mix(a: Int, b: Int, t: Float): Int {
    fun ch(x: Int, y: Int) = (x + (y - x) * t).roundToInt().coerceIn(0, 255)
    return Color.argb(
        ch(Color.alpha(a), Color.alpha(b)),
        ch(Color.red(a), Color.red(b)),
        ch(Color.green(a), Color.green(b)),
        ch(Color.blue(a), Color.blue(b)),
    )
}

internal fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

internal fun stroke(width: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = width
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    this.color = color
}

internal fun ShareCardModel.hasRoute(): Boolean = plot.size >= 2 || route.size >= 2

/** Route points on [stage]. On a Map background they follow the map fit, so the line sits on the roads. */
internal fun LookContext.routePoints(stage: RectF): List<PointF> {
    // Any cover gives the same projection; the fit depends only on the route's shape.
    val cover = if (background == ShareBackground.MAP && card.route.size >= 2) {
        ShareCardMapTransform.cardCover(card.route, stage, layout.width, layout.height, layout.height > layout.width)
    } else {
        null
    }
    return ShareCardRoute.project(card, cover, stage)
}

/**
 * Day-trip stops, snapped to the drawn route. The drawn points can be a sample of the route,
 * so a stop maps by its position along the route.
 */
internal fun LookContext.stopPoints(points: List<PointF>): List<PointF> {
    if (card.stops.isEmpty() || points.isEmpty() || card.route.size < 2) return emptyList()
    return card.stops.map { stop ->
        val along = nearest(card.route, stop).toFloat() / card.route.lastIndex
        points[(along * points.lastIndex).roundToInt().coerceIn(0, points.lastIndex)]
    }
}

private fun nearest(route: List<GpsFix>, stop: GpsFix): Int {
    var best = 0
    var bestD = Double.MAX_VALUE
    route.forEachIndexed { i, fix ->
        val dLat = fix.lat - stop.lat
        val dLng = fix.lng - stop.lng
        val d = dLat * dLat + dLng * dLng
        if (d < bestD) {
            bestD = d
            best = i
        }
    }
    return best
}

/** Units of the hero: "KILOMETRES" for distance, the metric's own unit otherwise. */
internal fun heroUnit(card: ShareCardModel): String {
    val hero = card.hero ?: return ""
    return when (hero.kind) {
        ShareMetricKind.DISTANCE -> "Kilometres"
        ShareMetricKind.MAX_SPEED -> "km/h top speed"
        ShareMetricKind.AVG_SPEED -> "km/h average"
        ShareMetricKind.ELEVATION -> "metres climbed"
        ShareMetricKind.FUEL_ECONOMY -> "km per litre"
        ShareMetricKind.MOVING -> if (hero.heroValue.contains('h')) "moving" else "minutes moving"
        ShareMetricKind.DURATION -> if (hero.heroValue.contains('h')) "on the road" else "minutes on the road"
    }
}

/** Short stat caption for tight rows: "Max Speed" → "max km/h". */
internal fun shortCaption(metric: ShareMetric): String = when (metric.kind) {
    ShareMetricKind.DISTANCE -> "km"
    ShareMetricKind.MOVING -> "moving"
    ShareMetricKind.MAX_SPEED -> "max km/h"
    ShareMetricKind.AVG_SPEED -> "avg km/h"
    ShareMetricKind.ELEVATION -> "m climb"
    ShareMetricKind.FUEL_ECONOMY -> "km/l"
    ShareMetricKind.DURATION -> "total"
}

/** "SUN" from "SUN, 25 OCT 2026". */
internal fun ShareCardModel.weekday(): String = dateText.substringBefore(',').trim()

/** "SUN 25 OCT" from "SUN, 25 OCT 2026". */
internal fun ShareCardModel.shortDate(): String {
    val parts = dateText.replace(",", "").split(' ').filter { it.isNotBlank() }
    return if (parts.size >= 3) parts.take(3).joinToString(" ") else dateText
}
