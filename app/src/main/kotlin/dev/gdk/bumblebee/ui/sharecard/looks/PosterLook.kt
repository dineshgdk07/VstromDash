package dev.gdk.bumblebee.ui.sharecard.looks

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import dev.gdk.bumblebee.ride.ShareBackground
import dev.gdk.bumblebee.ride.ShareShape
import dev.gdk.bumblebee.ui.sharecard.ShareCardRoute
import dev.gdk.bumblebee.ui.sharecard.ShareTextures

/**
 * Print poster: cream paper, the route as a bold ink line, one huge serif number
 * and an accent band of stats along the bottom. Mockup B.
 */
object PosterLook : CardLook {
    private const val INK = 0xFF141414.toInt()

    override fun stage(ctx: LookContext): RectF {
        val u = ctx.u
        return RectF(24f * u, 24f * u, ctx.layout.width - 24f * u, stack(ctx).headlineTop - 14f * u)
    }

    override fun drawOwnBackground(canvas: Canvas, ctx: LookContext) {
        ShareTextures.paper(canvas, RectF(0f, 0f, ctx.layout.width, ctx.layout.height))
    }

    override fun drawScrim(canvas: Canvas, ctx: LookContext) {
        val top = stack(ctx).headlineTop - 40f * ctx.u
        val paint = Paint().apply {
            shader = LinearGradient(0f, top, 0f, ctx.layout.height, Color.TRANSPARENT, Color.argb(200, 0, 0, 0), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, top, ctx.layout.width, ctx.layout.height, paint)
    }

    override fun draw(canvas: Canvas, ctx: LookContext) {
        drawRoute(canvas, ctx)
        drawElevation(canvas, ctx)
        drawStack(canvas, ctx)
        drawHero(canvas, ctx)
        drawBand(canvas, ctx)
    }

    override fun drawSticker(canvas: Canvas, ctx: LookContext) {
        drawRoute(canvas, ctx)
        drawBand(canvas, ctx)
    }

    // ---- layout, bottom up: band, unit, number, stamp, info lines, headline, route ----

    private class Stack(
        val bandTop: Float,
        val unitBase: Float,
        val numberBase: Float,
        val numberSize: Float,
        val stampY: Float?,
        val lines: List<Pair<String, Float>>,
        val headlineBase: Float,
        val headlineSize: Float,
    ) {
        val headlineTop get() = headlineBase - headlineSize * 0.9f
    }

    private fun stack(ctx: LookContext): Stack {
        val u = ctx.u
        val story = ctx.layout.shape == ShareShape.STORY
        val bandH = when (ctx.layout.shape) {
            ShareShape.STORY -> 56f
            ShareShape.FEED -> 48f
            ShareShape.SQUARE -> 40f
        } * u
        val bandTop = ctx.layout.height - bandH
        val hero = ctx.card.hero
        val unitBase = bandTop - 12f * u
        val numberSize = when (ctx.layout.shape) {
            ShareShape.STORY -> 118f
            ShareShape.FEED -> 92f
            ShareShape.SQUARE -> 64f
        } * u
        val numberBase = unitBase - 22f * u
        var cursor = if (hero != null) numberBase - numberSize * 0.74f else bandTop - 6f * u
        val stampY = if (ctx.card.highlight != null) {
            cursor -= 16f * u
            cursor.also { cursor -= 18f * u }
        } else {
            cursor -= 6f * u
            null
        }
        val card = ctx.card
        val places = if (!card.fromPlace.isNullOrBlank() && !card.toPlace.isNullOrBlank()) "${card.fromPlace} → ${card.toPlace}" else null
        val wanted = listOfNotNull(
            places?.takeIf { it != card.headline },
            card.dateText.takeIf { it.isNotBlank() },
            card.weatherLine?.takeIf { it.isNotBlank() && story },
        ).take(if (ctx.layout.shape == ShareShape.SQUARE) 1 else 3)
        val lines = ArrayList<Pair<String, Float>>()
        wanted.asReversed().forEach { text ->
            lines.add(0, text to cursor)
            cursor -= 12f * u
        }
        val headlineSize = (if (story) 26f else 20f) * u
        val headlineBase = cursor - 6f * u
        return Stack(bandTop, unitBase, numberBase, numberSize, stampY, lines, headlineBase, headlineSize)
    }

    // ---- drawing ----

    private fun drawRoute(canvas: Canvas, ctx: LookContext) {
        if (!ctx.card.hasRoute()) return
        val u = ctx.u
        val points = ctx.routePoints(stage(ctx))
        if (points.size < 2) return
        val ink = ink(ctx)
        val line = stroke(6f * u, ink).apply { if (ctx.onImage) setShadowLayer(8f * u, 0f, 1f * u, Color.argb(150, 0, 0, 0)) }
        canvas.drawPath(ShareCardRoute.path(points), line)
        ShareCardRoute.drawStops(canvas, ctx.stopPoints(points), ctx.accent, ink, 4.5f * u)
        val first = points.first()
        val last = points.last()
        canvas.drawCircle(first.x, first.y, 7f * u, fill(paperOr(ctx)))
        canvas.drawCircle(first.x, first.y, 7f * u, stroke(3f * u, ink))
        canvas.drawCircle(last.x, last.y, 7f * u, fill(ctx.accent))
        canvas.drawCircle(last.x, last.y, 7f * u, stroke(3f * u, ink))
    }

    private fun drawElevation(canvas: Canvas, ctx: LookContext) {
        val samples = ctx.card.elevation
        if (samples.size < 2) return
        val base = stack(ctx).bandTop
        val amp = 26f * ctx.u
        val w = ctx.layout.width
        val path = Path()
        path.moveTo(0f, base)
        samples.forEachIndexed { i, v -> path.lineTo(w * i / samples.lastIndex, base - amp * v.coerceIn(0f, 1f)) }
        path.lineTo(w, base)
        path.close()
        canvas.drawPath(path, fill(withAlpha(ink(ctx), if (ctx.onImage) 40 else 18)))
    }

    private fun drawStack(canvas: Canvas, ctx: LookContext) {
        val u = ctx.u
        val s = stack(ctx)
        val right = ctx.layout.width - 16f * u
        val maxWidth = ctx.layout.width - 40f * u
        val headline = textPaint(s.headlineSize, ink(ctx), ctx.fonts.display).apply { shadow(ctx) }
        canvas.fitted(ctx.card.headline.upper(), right, s.headlineBase, headline, maxWidth, 14f * u, Paint.Align.RIGHT)
        val label = ctx.label(ink(ctx)).apply { shadow(ctx) }
        s.lines.forEach { (text, y) -> canvas.fitted(text.upper(), right, y, label, maxWidth, 6f * u, Paint.Align.RIGHT) }
        val badge = ctx.card.highlight ?: return
        val y = s.stampY ?: return
        val text = ctx.label(INK, size = 9f).apply { letterSpacing = 0.16f }
        val title = badge.title.upper()
        val tw = text.measureText(title) - text.letterSpacing * text.textSize
        val box = RectF(right - tw - 16f * u, y - 9f * u, right, y + 7f * u)
        canvas.save()
        canvas.rotate(-4f, box.centerX(), box.centerY())
        canvas.drawRect(box, fill(ctx.accent))
        canvas.drawRect(box, stroke(1.5f * u, INK).apply { strokeJoin = Paint.Join.MITER; strokeCap = Paint.Cap.BUTT })
        canvas.drawText(title, box.left + 8f * u, box.bottom - 5f * u, text)
        canvas.restore()
    }

    private fun drawHero(canvas: Canvas, ctx: LookContext) {
        val hero = ctx.card.hero ?: return
        val u = ctx.u
        val s = stack(ctx)
        val left = 14f * u
        val maxWidth = ctx.layout.width - 28f * u
        val dot = hero.heroValue.indexOf('.')
        val whole = if (dot > 0) hero.heroValue.substring(0, dot) else hero.heroValue
        val fraction = if (dot > 0) hero.heroValue.substring(dot) else ""
        var size = s.numberSize
        fun big() = textPaint(size, ink(ctx), ctx.fonts.serif, -0.04f).apply { shadow(ctx) }
        fun small() = textPaint(size * 0.5f, ink(ctx), ctx.fonts.serif, -0.02f).apply { shadow(ctx) }
        while (size > 40f * u && big().measureText(whole) + small().measureText(fraction) > maxWidth) size -= 2f
        val bigPaint = big()
        canvas.drawText(whole, left, s.numberBase, bigPaint)
        if (fraction.isNotEmpty()) canvas.drawText(fraction, left + bigPaint.measureText(whole), s.numberBase, small())
        val unit = textPaint(16f * u, ink(ctx), ctx.fonts.body, 0.2f).apply { shadow(ctx) }
        canvas.fitted(heroUnit(ctx.card).upper(), left + 4f * u, s.unitBase, unit, maxWidth, 8f * u)
    }

    private fun drawBand(canvas: Canvas, ctx: LookContext) {
        val u = ctx.u
        val top = stack(ctx).bandTop
        canvas.drawRect(0f, top, ctx.layout.width, ctx.layout.height, fill(ctx.accent))
        val stats = ctx.card.stats.take(3)
        if (stats.isEmpty()) return
        val slot = ctx.layout.width / stats.size
        val base = (top + ctx.layout.height) / 2f + 5f * u
        stats.forEachIndexed { i, stat ->
            val value = textPaint(16f * u, INK, ctx.fonts.display)
            val caption = textPaint(11f * u, INK, ctx.fonts.body, 0.12f)
            val cap = shortCaption(stat).upper()
            val room = slot - 12f * u
            while (value.measureText(stat.value) + 4f * u + caption.measureText(cap) > room && value.textSize > 9f * u) {
                value.textSize -= 1f
                caption.textSize = value.textSize * 0.68f
            }
            val total = value.measureText(stat.value) + 4f * u + caption.measureText(cap) - caption.letterSpacing * caption.textSize
            val x = slot * i + (slot - total) / 2f
            canvas.drawText(stat.value, x, base, value)
            canvas.drawText(cap, x + value.measureText(stat.value) + 4f * u, base, caption)
        }
    }

    // ---- colour ----

    private fun ink(ctx: LookContext): Int = if (ctx.onImage) Color.WHITE else INK

    /** Start marker fill: the paper, or ink-on-white's opposite on an image. */
    private fun paperOr(ctx: LookContext): Int = if (ctx.onImage) INK else 0xFFF4ECD8.toInt()

    private fun Paint.shadow(ctx: LookContext) {
        if (ctx.onImage) setShadowLayer(2.5f * ctx.u, 0f, 0.75f * ctx.u, Color.argb(170, 0, 0, 0))
    }
}
