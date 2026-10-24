package dev.gdk.bumblebee.ui.sharecard.looks

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import dev.gdk.bumblebee.ride.ShareAccent
import dev.gdk.bumblebee.ride.ShareBackground
import dev.gdk.bumblebee.ride.ShareShape
import dev.gdk.bumblebee.ui.sharecard.ShareCardRoute
import dev.gdk.bumblebee.ui.sharecard.ShareTextures

/**
 * Warm glow on near-black: wordmark and date on top, the route glowing in the middle,
 * a big headline, and stats on a frosted panel. Mockup A.
 */
object NightGlowLook : CardLook {
    private const val BASE = 0xFF060608.toInt()
    private const val MID = 0xFF120F06.toInt()

    override fun stage(ctx: LookContext): RectF {
        val u = ctx.u
        return RectF(20f * u, 44f * u, ctx.layout.width - 20f * u, headlineTop(ctx) - 12f * u)
    }

    override fun drawOwnBackground(canvas: Canvas, ctx: LookContext) {
        val w = ctx.layout.width
        val h = ctx.layout.height
        canvas.drawColor(BASE)
        val stage = stage(ctx)
        val centre = routeCentre(ctx, stage) ?: PointF(w * 0.70f, h * 0.30f)
        val glow = Paint().apply {
            shader = RadialGradient(
                centre.x,
                centre.y,
                w * 1.2f,
                intArrayOf(mix(0xFF16140E.toInt(), ctx.accent, 0.11f), MID, BASE),
                floatArrayOf(0f, 0.35f, 0.75f),
                Shader.TileMode.CLAMP,
            ).apply {
                // The mockup's ellipse: 120% of the width across, 70% of the height down.
                setLocalMatrix(Matrix().apply { setScale(1f, (h * 0.7f) / (w * 1.2f), centre.x, centre.y) })
            }
        }
        canvas.drawRect(0f, 0f, w, h, glow)
        ShareTextures.contours(
            canvas,
            RectF(0f, stage.top, w, panel(ctx).top),
            withAlpha(ctx.accent, 22),
            spacing = 34f * ctx.u,
            strokeWidth = 0.8f * ctx.u,
            seed = 7L,
        )
        ShareTextures.grain(canvas, RectF(0f, 0f, w, h), 0.42f)
    }

    override fun drawScrim(canvas: Canvas, ctx: LookContext) {
        val h = ctx.layout.height
        val w = ctx.layout.width
        val top = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, 70f * ctx.u, Color.argb(150, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, 70f * ctx.u, top)
        val from = headlineTop(ctx) - 40f * ctx.u
        val bottom = Paint().apply {
            shader = LinearGradient(0f, from, 0f, h, Color.TRANSPARENT, Color.argb(215, 0, 0, 0), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, from, w, h, bottom)
    }

    override fun draw(canvas: Canvas, ctx: LookContext) {
        drawTopBar(canvas, ctx)
        drawRoute(canvas, ctx)
        drawType(canvas, ctx)
        drawPanel(canvas, ctx)
    }

    override fun drawSticker(canvas: Canvas, ctx: LookContext) {
        drawRoute(canvas, ctx)
        drawPanel(canvas, ctx)
    }

    // ---- layout, bottom up ----

    private fun panelHeight(ctx: LookContext) = if (ctx.layout.shape == ShareShape.SQUARE) 58f else 64f

    private fun panel(ctx: LookContext): RectF {
        val u = ctx.u
        val bottom = ctx.layout.height - 14f * u
        return RectF(14f * u, bottom - panelHeight(ctx) * u, ctx.layout.width - 14f * u, bottom)
    }

    private fun headlineSize(ctx: LookContext) = when (ctx.layout.shape) {
        ShareShape.STORY -> 40f
        ShareShape.FEED -> 34f
        ShareShape.SQUARE -> 28f
    } * ctx.u

    private fun sublineBase(ctx: LookContext) = panel(ctx).top - (if (ctx.layout.shape == ShareShape.STORY) 30f else 20f) * ctx.u

    private fun headlineBase(ctx: LookContext) = sublineBase(ctx) - 18f * ctx.u

    private fun headlineTop(ctx: LookContext): Float {
        val top = headlineBase(ctx) - headlineSize(ctx) * 0.9f
        return if (ctx.card.highlight != null) top - 22f * ctx.u else top
    }

    // ---- drawing ----

    private fun drawTopBar(canvas: Canvas, ctx: LookContext) {
        val u = ctx.u
        val base = 27f * u
        val mark = textPaint(14f * u, ctx.accent, ctx.fonts.display, 0.08f)
        canvas.drawText("BUMBLEBEE", 16f * u, base, mark)
        val stamp = listOf(ctx.card.weekday(), ctx.card.startClock).filter { it.isNotBlank() }.joinToString(" · ")
        canvas.fitted(stamp.upper(), ctx.layout.width - 16f * u, base - 1f * u, ctx.label(muted(ctx)), ctx.layout.width * 0.5f, 6f * u, Paint.Align.RIGHT)
    }

    private fun drawRoute(canvas: Canvas, ctx: LookContext) {
        if (!ctx.card.hasRoute()) return
        val u = ctx.u
        val points = ctx.routePoints(stage(ctx))
        if (points.size < 2) return
        val path = ShareCardRoute.path(points)
        val (start, end, glow) = routeColours(ctx.card.accent, ctx.accent)
        val halo = Paint(stroke(10f * u, glow)).apply {
            alpha = 200
            maskFilter = BlurMaskFilter(8.5f * u, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(path, halo)
        val first = points.first()
        val last = points.last()
        val line = stroke(4f * u, start).apply {
            shader = LinearGradient(first.x, first.y, last.x, last.y, start, end, Shader.TileMode.CLAMP)
        }
        canvas.drawPath(path, line)
        ShareCardRoute.drawStops(canvas, ctx.stopPoints(points), ctx.accent, Color.WHITE, 4f * u)
        canvas.drawCircle(first.x, first.y, 5f * u, fill(Color.WHITE))
        canvas.drawCircle(last.x, last.y, 6f * u, fill(ctx.accent))
        canvas.drawCircle(last.x, last.y, 6f * u, stroke(2f * u, Color.WHITE))
    }

    private fun drawType(canvas: Canvas, ctx: LookContext) {
        val u = ctx.u
        val left = 16f * u
        val maxWidth = ctx.layout.width - 32f * u
        val headline = textPaint(headlineSize(ctx), Color.WHITE, ctx.fonts.display).apply {
            if (ctx.background != ShareBackground.OWN) setShadowLayer(8f * u, 0f, 1f * u, Color.argb(140, 0, 0, 0))
        }
        val base = headlineBase(ctx)
        canvas.fitted(ctx.card.headline.upper(), left, base, headline, maxWidth, 22f * u)
        ctx.card.highlight?.let { badge ->
            val label = ctx.label(0xFF141008.toInt(), size = 9f, face = ctx.fonts.body)
            val text = badge.title.upper()
            val w = label.measureText(text) - label.letterSpacing * label.textSize
            val bottom = base - headline.textSize * 0.9f - 8f * u
            val pill = RectF(left, bottom - 16f * u, left + w + 16f * u, bottom)
            canvas.drawRoundRect(pill, 8f * u, 8f * u, fill(ctx.accent))
            canvas.drawText(text, pill.left + 8f * u, pill.bottom - 5f * u, label)
        }
        val sub = subline(ctx) ?: return
        val paint = textPaint(13f * u, muted(ctx), ctx.fonts.body).apply {
            if (ctx.background != ShareBackground.OWN) setShadowLayer(6f * u, 0f, 0.5f * u, Color.argb(160, 0, 0, 0))
        }
        canvas.fitted(sub, left, sublineBase(ctx), paint, maxWidth, 9f * u)
    }

    private fun subline(ctx: LookContext): String? {
        val card = ctx.card
        val places = if (!card.fromPlace.isNullOrBlank() && !card.toPlace.isNullOrBlank()) "${card.fromPlace} → ${card.toPlace}" else null
        val parts = listOfNotNull(places?.takeIf { it != card.headline }, card.weatherLine?.takeIf { it.isNotBlank() })
        return parts.joinToString("  ·  ").ifBlank { card.dateText.takeIf { it.isNotBlank() } }
    }

    private fun drawPanel(canvas: Canvas, ctx: LookContext) {
        val card = ctx.card
        val cells = buildList {
            card.hero?.let { add(it.heroValue to it.unitLabel) }
            card.stats.forEach { add(it.value to shortCaption(it)) }
        }.take(4)
        if (cells.isEmpty()) return
        val u = ctx.u
        val panel = panel(ctx)
        val radius = 12f * u
        val glass = if (ctx.background == ShareBackground.OWN) Color.argb(16, 255, 255, 255) else Color.argb(120, 10, 10, 12)
        canvas.drawRoundRect(panel, radius, radius, fill(glass))
        canvas.drawRoundRect(panel, radius, radius, stroke(0.75f * u, withAlpha(ctx.accent, 72)))
        val inner = panel.width() - 32f * u
        var valueSize = 22f * u
        val labels = cells.map { it.second.upper() }
        val labelPaint = ctx.label(muted(ctx))
        fun widths(size: Float): List<Float> {
            val v = textPaint(size, Color.WHITE, ctx.fonts.display)
            return cells.mapIndexed { i, (value, _) ->
                maxOf(v.measureText(value), labelPaint.measureText(labels[i]) - labelPaint.letterSpacing * labelPaint.textSize)
            }
        }
        var w = widths(valueSize)
        val minGap = 10f * u
        while (w.sum() + minGap * (cells.size - 1) > inner && valueSize > 12f * u) {
            valueSize -= 1f
            w = widths(valueSize)
        }
        val gap = if (cells.size > 1) (inner - w.sum()) / (cells.size - 1) else 0f
        val value = textPaint(valueSize, Color.WHITE, ctx.fonts.display)
        val valueBase = panel.top + panel.height() * 0.56f
        val labelBase = valueBase + 13f * u
        var x = panel.left + 16f * u
        cells.forEachIndexed { i, (v, _) ->
            canvas.drawText(v, x, valueBase, value)
            canvas.fitted(labels[i], x, labelBase, labelPaint, w[i] + gap * 0.8f, 6f * u)
            x += w[i] + gap
        }
    }

    // ---- colour ----

    /** Gradient start, end and glow. Bumblebee yellow uses the mockup's amber-to-cream. */
    private fun routeColours(accent: ShareAccent, argb: Int): Triple<Int, Int, Int> =
        if (accent == ShareAccent.YELLOW) {
            Triple(0xFFFF6A00.toInt(), 0xFFFFE680.toInt(), 0xFFFF8A00.toInt())
        } else {
            Triple(argb, mix(argb, Color.WHITE, 0.6f), argb)
        }

    /** The mockup's muted gold, tinted by the accent. */
    private fun muted(ctx: LookContext): Int =
        if (ctx.card.accent == ShareAccent.YELLOW) 0xFFBBA86A.toInt() else mix(0xFFB8B4A8.toInt(), ctx.accent, 0.3f)

    private fun routeCentre(ctx: LookContext, stage: RectF): PointF? {
        if (!ctx.card.hasRoute()) return null
        val points = ctx.routePoints(stage)
        if (points.isEmpty()) return null
        return PointF(points.sumOf { it.x.toDouble() }.toFloat() / points.size, points.sumOf { it.y.toDouble() }.toFloat() / points.size)
    }
}
