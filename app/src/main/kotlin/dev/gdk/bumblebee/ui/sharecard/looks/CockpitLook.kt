package dev.gdk.bumblebee.ui.sharecard.looks

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import dev.gdk.bumblebee.ride.ShareAccent
import dev.gdk.bumblebee.ride.ShareBackground
import dev.gdk.bumblebee.ride.ShareCardModel
import dev.gdk.bumblebee.ride.ShareMetric
import dev.gdk.bumblebee.ride.ShareMetricKind
import dev.gdk.bumblebee.ride.ShareShape
import dev.gdk.bumblebee.ui.sharecard.ShareCardRoute
import dev.gdk.bumblebee.ui.sharecard.ShareTextures
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * The bike's own dash: a REC line and odometer digits on top, the route in a cool accent,
 * a stats row, a max-speed dial and the speed trace along the bottom. Mockup C.
 */
object CockpitLook : CardLook {
    private const val MUTED = 0xFF7D8B99.toInt()
    private const val TRACK = 0xFF1D2A38.toInt()
    private const val SKY_FILL = 0xFF5EC8FF.toInt()

    /** Vertical bands, in px. Story stacks the dial under the stats; feed and square put it beside the route. */
    private class Frame(
        val stage: RectF,
        val statsBase: Float,
        val dialCx: Float,
        val dialCy: Float,
        val dialR: Float,
        val traceTop: Float,
        val traceBottom: Float,
    )

    private fun frame(ctx: LookContext): Frame {
        val u = ctx.u
        val w = ctx.layout.width
        val h = ctx.layout.height
        val headBottom = (if (ctx.card.highlight != null || !ctx.card.weatherLine.isNullOrBlank()) 88f else 74f) * u
        val traceBottom = h - 20f * u
        if (ctx.layout.shape == ShareShape.STORY) {
            val traceTop = traceBottom - 56f * u
            val dialCy = traceTop - 26f * u
            val dialR = 62f * u
            val statsBase = dialCy - dialR - 42f * u
            val stage = RectF(24f * u, headBottom, w - 24f * u, statsBase - 26f * u)
            return Frame(stage, statsBase, w / 2f, dialCy, dialR, traceTop, traceBottom)
        }
        val traceTop = traceBottom - (if (ctx.layout.shape == ShareShape.FEED) 44f else 34f) * u
        val statsBase = traceTop - 30f * u
        val middleBottom = statsBase - 28f * u
        val dialR = minOf(w * 0.17f, (middleBottom - headBottom - 16f * u) * 0.95f)
        val dialCx = w - 18f * u - dialR - 10f * u
        val dialCy = (headBottom + middleBottom) / 2f + dialR * 0.45f
        val stage = RectF(18f * u, headBottom, dialCx - dialR - 22f * u, middleBottom)
        return Frame(stage, statsBase, dialCx, dialCy, dialR, traceTop, traceBottom)
    }

    override fun stage(ctx: LookContext): RectF = frame(ctx).stage

    override fun drawOwnBackground(canvas: Canvas, ctx: LookContext) {
        val w = ctx.layout.width
        val h = ctx.layout.height
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h, 0xFF0B1016.toInt(), 0xFF05070A.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, paint)
        ShareTextures.grid(canvas, RectF(0f, 0f, w, h), 18f * ctx.u, Color.argb(26, 0x50, 0xA0, 0xDC), 0.5f * ctx.u)
    }

    override fun drawScrim(canvas: Canvas, ctx: LookContext) {
        val h = ctx.layout.height
        val w = ctx.layout.width
        canvas.drawRect(0f, 0f, w, h, fill(Color.argb(70, 5, 7, 10)))
        val top = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, 100f * ctx.u, Color.argb(190, 5, 7, 10), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, 100f * ctx.u, top)
        val from = frame(ctx).statsBase - 40f * ctx.u
        val bottom = Paint().apply {
            shader = LinearGradient(0f, from, 0f, h, Color.TRANSPARENT, Color.argb(225, 5, 7, 10), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, from, w, h, bottom)
    }

    override fun draw(canvas: Canvas, ctx: LookContext) {
        drawBacking(canvas, ctx)
        drawHead(canvas, ctx)
        drawOdometer(canvas, ctx)
        drawRoute(canvas, ctx)
        drawStats(canvas, ctx)
        drawDial(canvas, ctx)
        drawTraces(canvas, ctx)
    }

    override fun drawSticker(canvas: Canvas, ctx: LookContext) {
        drawBacking(canvas, ctx)
        drawRoute(canvas, ctx)
        drawStats(canvas, ctx)
        drawDial(canvas, ctx)
        drawTraces(canvas, ctx)
    }

    /** On Transparent the muted labels need a dark panel behind the gauges. */
    private fun drawBacking(canvas: Canvas, ctx: LookContext) {
        if (ctx.background != ShareBackground.TRANSPARENT) return
        val u = ctx.u
        val f = frame(ctx)
        val top = minOf(f.statsBase - 30f * u, f.dialCy - f.dialR - 18f * u)
        val panel = RectF(6f * u, top, ctx.layout.width - 6f * u, f.traceBottom + 18f * u)
        canvas.drawRoundRect(panel, 12f * u, 12f * u, fill(Color.argb(170, 5, 7, 10)))
    }

    // ---- top ----

    private fun drawHead(canvas: Canvas, ctx: LookContext) {
        val u = ctx.u
        val left = 14f * u
        val rec = textPaint(11f * u, dialColor(ctx), ctx.fonts.mono)
        canvas.drawText("● REC", left, 22f * u, rec)
        val stamp = listOf(ctx.card.shortDate(), ctx.card.startClock).filter { it.isNotBlank() }.joinToString(" ")
        val clock = textPaint(10f * u, MUTED, ctx.fonts.mono)
        val afterRec = left + rec.measureText("● REC") + 6f * u
        val room = odometerLeft(ctx) - afterRec - 8f * u
        canvas.fitted(stamp, afterRec, 22f * u, clock, room, 6f * u)
        val headlineRoom = odometerLeft(ctx) - left - 10f * u
        val headline = textPaint(24f * u, Color.WHITE, ctx.fonts.display)
        canvas.fitted(ctx.card.headline.upper(), left, 54f * u, headline, headlineRoom, 14f * u)
        var y = 70f * u
        ctx.card.highlight?.let {
            val tag = textPaint(9.5f * u, dialColor(ctx), ctx.fonts.mono)
            canvas.fitted("▲ " + it.title.upper(), left, y, tag, ctx.layout.width - 28f * u, 6f * u)
            y += 13f * u
        }
        ctx.card.weatherLine?.takeIf { it.isNotBlank() }?.let {
            canvas.fitted(it, left, y, textPaint(9.5f * u, MUTED, ctx.fonts.mono), ctx.layout.width - 28f * u, 6f * u)
        }
    }

    private fun odometerDigits(ctx: LookContext): String? = ctx.card.odoKm?.toString()?.padStart(5, '0')?.takeLast(6)

    private fun odometerLeft(ctx: LookContext): Float {
        val digits = odometerDigits(ctx) ?: return ctx.layout.width - 14f * ctx.u
        return ctx.layout.width - 14f * ctx.u - digits.length * 18f * ctx.u + 2f * ctx.u
    }

    private fun drawOdometer(canvas: Canvas, ctx: LookContext) {
        val digits = odometerDigits(ctx) ?: return
        val u = ctx.u
        val right = ctx.layout.width - 14f * u
        canvas.fitted("ODO", right, 18f * u, ctx.label(MUTED), 40f * u, 6f * u, Paint.Align.RIGHT)
        val glyph = textPaint(13f * u, 0xFFE8EDF2.toInt(), ctx.fonts.mono)
        var left = odometerLeft(ctx)
        digits.forEach { ch ->
            val cell = RectF(left, 23f * u, left + 16f * u, 44f * u)
            canvas.drawRoundRect(cell, 1.5f * u, 1.5f * u, fill(0xFF111821.toInt()))
            canvas.drawRoundRect(cell, 1.5f * u, 1.5f * u, stroke(0.6f * u, 0xFF2C3D52.toInt()))
            val s = ch.toString()
            canvas.drawText(s, cell.centerX() - glyph.measureText(s) / 2f, cell.centerY() + glyph.textSize * 0.36f, glyph)
            left += 18f * u
        }
    }

    // ---- middle ----

    private fun drawRoute(canvas: Canvas, ctx: LookContext) {
        if (!ctx.card.hasRoute()) return
        val u = ctx.u
        val points = ctx.routePoints(stage(ctx))
        if (points.size < 2) return
        val colour = routeColor(ctx)
        val glow = Paint(stroke(7f * u, withAlpha(colour, 50))).apply {
            maskFilter = android.graphics.BlurMaskFilter(5f * u, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        val path = ShareCardRoute.path(points)
        canvas.drawPath(path, glow)
        canvas.drawPath(path, stroke(3f * u, colour))
        ShareCardRoute.drawStops(canvas, ctx.stopPoints(points), dialColor(ctx), Color.WHITE, 3.5f * u)
        canvas.drawCircle(points.first().x, points.first().y, 4f * u, fill(Color.WHITE))
        canvas.drawCircle(points.last().x, points.last().y, 4f * u, fill(dialColor(ctx)))
    }

    /** The rider's three stats. Max speed lives on the dial; fuel only with bike data. */
    private fun rowStats(ctx: LookContext): List<ShareMetric> =
        ctx.card.stats.filter { ctx.card.odoKm != null || it.kind != ShareMetricKind.FUEL_ECONOMY }.take(3)

    private fun drawStats(canvas: Canvas, ctx: LookContext) {
        val stats = rowStats(ctx)
        if (stats.isEmpty()) return
        val u = ctx.u
        val f = frame(ctx)
        val left = 14f * u
        val right = ctx.layout.width - 14f * u
        val slot = (right - left) / stats.size
        stats.forEachIndexed { i, stat ->
            val align = when {
                stats.size == 1 || i == 0 -> Paint.Align.LEFT
                i == stats.lastIndex -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            val x = when (align) {
                Paint.Align.LEFT -> left
                Paint.Align.RIGHT -> right
                Paint.Align.CENTER -> (left + right) / 2f
            }
            val value = textPaint(19f * u, 0xFFE8EDF2.toInt(), ctx.fonts.mono)
            canvas.fitted(stat.value, x, f.statsBase, value, slot - 8f * u, 10f * u, align)
            canvas.fitted(shortCaption(stat).upper(), x, f.statsBase + 13f * u, ctx.label(MUTED), slot - 8f * u, 6f * u, align)
        }
    }

    private fun drawDial(canvas: Canvas, ctx: LookContext) {
        val metric = dialMetric(ctx.card) ?: return
        val u = ctx.u
        val f = frame(ctx)
        val r = f.dialR
        val oval = RectF(f.dialCx - r, f.dialCy - r, f.dialCx + r, f.dialCy + r)
        val width = r * 0.16f
        val speed = metric.value.toFloatOrNull() ?: 0f
        val max = maxOf(140f, ceil(speed / 20f) * 20f)
        val ticks = stroke(0.6f * u, withAlpha(MUTED, 150)).apply { strokeCap = Paint.Cap.BUTT }
        var v = 0f
        while (v <= max) {
            val a = Math.toRadians((180.0 + 180.0 * v / max))
            val inner = r + width * 0.9f
            val outer = inner + if (v % 40f == 0f) width * 0.8f else width * 0.45f
            canvas.drawLine(
                f.dialCx + (cos(a) * inner).toFloat(), f.dialCy + (sin(a) * inner).toFloat(),
                f.dialCx + (cos(a) * outer).toFloat(), f.dialCy + (sin(a) * outer).toFloat(),
                ticks,
            )
            v += 20f
        }
        canvas.drawArc(oval, 180f, 180f, false, stroke(width, TRACK).apply { strokeCap = Paint.Cap.BUTT })
        val sweep = 180f * (speed / max).coerceIn(0f, 1f)
        if (sweep > 0f) canvas.drawArc(oval, 180f, sweep, false, stroke(width, dialColor(ctx)))
        val value = textPaint(r * 0.55f, Color.WHITE, ctx.fonts.display)
        canvas.fitted(metric.value, f.dialCx, f.dialCy - r * 0.17f, value, r * 1.4f, 14f * u, Paint.Align.CENTER)
        canvas.fitted("MAX KM/H", f.dialCx, f.dialCy + r * 0.10f, ctx.label(MUTED, size = 9f * r / (62f * u)), r * 1.6f, 6f * u, Paint.Align.CENTER)
    }

    // ---- bottom ----

    private fun drawTraces(canvas: Canvas, ctx: LookContext) {
        val speed = smooth(ctx.card.speedTrace, 36)
        val elevation = smooth(ctx.card.elevation, 64)
        if (speed.size < 2 && elevation.size < 2) return
        val u = ctx.u
        val f = frame(ctx)
        val left = 14f * u
        val right = ctx.layout.width - 14f * u
        if (elevation.size >= 2) {
            val area = Path()
            area.moveTo(left, f.traceBottom)
            elevation.forEachIndexed { i, v -> area.lineTo(left + (right - left) * i / elevation.lastIndex, f.traceBottom - (f.traceBottom - f.traceTop) * 0.7f * v) }
            area.lineTo(right, f.traceBottom)
            area.close()
            canvas.drawPath(area, fill(withAlpha(SKY_FILL, 26)))
        }
        if (speed.size >= 2) {
            val line = Path()
            speed.forEachIndexed { i, v ->
                val x = left + (right - left) * i / speed.lastIndex
                val y = f.traceBottom - (f.traceBottom - f.traceTop) * v
                if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            canvas.drawPath(line, stroke(1.5f * u, withAlpha(dialColor(ctx), 230)))
        }
        val label = ctx.label(MUTED, size = 8f)
        val y = f.traceBottom + 12f * u
        if (speed.size >= 2) canvas.drawText("SPEED OVER RIDE", left, y, label)
        if (elevation.size >= 2) canvas.fitted("ELEVATION", right, y, ctx.label(withAlpha(SKY_FILL, 170), size = 8f), 80f * u, 6f * u, Paint.Align.RIGHT)
    }

    /** Bucket means, rescaled to 0..1, so the strip reads as a trace instead of noise. */
    private fun smooth(samples: List<Float>, buckets: Int): List<Float> {
        if (samples.size < 2) return samples
        val n = minOf(buckets, samples.size)
        var means = List(n) { b ->
            val from = b * samples.size / n
            val to = ((b + 1) * samples.size / n).coerceAtLeast(from + 1)
            samples.subList(from, to).average().toFloat()
        }
        repeat(2) {
            means = means.indices.map { i -> (means[maxOf(0, i - 1)] + means[i] * 2f + means[minOf(means.lastIndex, i + 1)]) / 4f }
        }
        val lo = means.min()
        val hi = means.max()
        if (hi - lo < 1e-4f) return means.map { 0.5f }
        return means.map { 0.08f + 0.92f * (it - lo) / (hi - lo) }
    }

    // ---- colour ----

    private fun dialMetric(card: ShareCardModel): ShareMetric? =
        card.available.firstOrNull { it.kind == ShareMetricKind.MAX_SPEED }

    private fun routeColor(ctx: LookContext): Int =
        if (ctx.card.accent == ShareAccent.YELLOW) ShareAccent.SKY.argb else ctx.accent

    private fun dialColor(ctx: LookContext): Int = ctx.accent
}
