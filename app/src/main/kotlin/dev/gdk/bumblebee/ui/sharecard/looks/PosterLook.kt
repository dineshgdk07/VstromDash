package dev.gdk.bumblebee.ui.sharecard.looks

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.text.TextPaint
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.ShareBackground
import dev.gdk.bumblebee.ride.ShareShape
import dev.gdk.bumblebee.ui.sharecard.RouteStyle
import dev.gdk.bumblebee.ui.sharecard.ShareCardLayout
import dev.gdk.bumblebee.ui.sharecard.ShareCardMapTransform
import dev.gdk.bumblebee.ui.sharecard.ShareCardRoute
import dev.gdk.bumblebee.ui.sharecard.ShareTextures
import dev.gdk.bumblebee.ui.sharecard.ShareText

object PosterLook : CardLook {
    private const val INK = 0xFF141414.toInt()

    override fun drawOwnBackground(canvas: Canvas, ctx: LookContext) {
        val paper = ShareTextures.paper(ctx.layout.width.toInt(), ctx.layout.height.toInt())
        canvas.drawBitmap(paper, 0f, 0f, null)
    }

    override fun drawScrim(canvas: Canvas, ctx: LookContext) {
        val top = ctx.layout.height * 0.45f
        val paint = Paint().apply {
            shader = android.graphics.LinearGradient(
                0f,
                top,
                0f,
                ctx.layout.height,
                Color.TRANSPARENT,
                Color.argb(170, 0, 0, 0),
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, top, ctx.layout.width, ctx.layout.height, paint)
    }

    override fun draw(canvas: Canvas, ctx: LookContext) {
        drawRoute(canvas, ctx)
        drawElevation(canvas, ctx)
        drawHero(canvas, ctx)
        drawHeadline(canvas, ctx)
        drawStamp(canvas, ctx)
        drawBand(canvas, ctx)
    }

    override fun drawSticker(canvas: Canvas, ctx: LookContext) {
        drawRoute(canvas, ctx)
        drawBand(canvas, ctx)
    }

    private fun drawRoute(canvas: Canvas, ctx: LookContext) {
        if (ctx.card.plot.size < 2 && ctx.card.route.size < 2) return
        val stage = ShareCardRoute.stage(ctx.layout)
        val points = ShareCardRoute.project(ctx.card, cover(ctx), stage)
        canvas.save()
        canvas.clipRect(routeClip(ctx.layout))
        ShareCardRoute.drawRoute(canvas, points, ink(ctx), RouteStyle.INK)
        if (points.isEmpty()) return
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = ink(ctx)
        }
        canvas.drawCircle(points.first().x, points.first().y, 11f, stroke)
        canvas.drawCircle(points.last().x, points.last().y, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ctx.accent })
        ShareCardRoute.drawStops(canvas, stopPoints(ctx, points), ctx.accent)
        canvas.restore()
    }

    private fun routeClip(layout: ShareCardLayout): RectF {
        val heroBase = if (layout.shape == ShareShape.STORY) 280f else layout.height * 0.18f
        val headlineBase = if (layout.shape == ShareShape.STORY) 1240f else layout.height * 0.78f
        val headlineSize = if (layout.shape == ShareShape.STORY) 72f else layout.width * 0.055f
        return RectF(0f, heroBase + 28f, layout.width, headlineBase - headlineSize - 28f)
    }

    private fun drawElevation(canvas: Canvas, ctx: LookContext) {
        val samples = ctx.card.elevation
        if (samples.size < 2) return
        val bandTop = ctx.layout.height * 0.93f
        val base = bandTop - 8f
        val amp = ctx.layout.height * 0.06f
        val path = Path()
        path.moveTo(0f, base)
        samples.forEachIndexed { i, v ->
            val x = ctx.layout.width * i / samples.lastIndex
            path.lineTo(x, base - amp * v.coerceIn(0f, 1f))
        }
        path.lineTo(ctx.layout.width, base)
        path.close()
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(50, Color.red(ink(ctx)), Color.green(ink(ctx)), Color.blue(ink(ctx))) })
    }

    private fun drawHero(canvas: Canvas, ctx: LookContext) {
        val hero = ctx.card.hero ?: return
        val layout = ctx.layout
        val size = if (layout.shape == ShareShape.STORY) layout.height * 0.11f else layout.height * 0.10f
        val value = text(size, ink(ctx), ctx.fonts.serif, shadowed(ctx))
        val fitted = ShareText.fit(value, hero.heroValue, layout.width * 0.72f, if (layout.shape == ShareShape.STORY) 48f else 40f)
        val baseline = if (layout.shape == ShareShape.STORY) 280f else layout.height * 0.18f
        canvas.drawText(fitted, 48f, baseline, value)
        val unit = text(size * 0.22f, ink(ctx), ctx.fonts.body, shadowed(ctx))
        canvas.drawText(hero.unitLabel, 48f + value.measureText(fitted) + 16f, baseline, unit)
    }

    private fun drawHeadline(canvas: Canvas, ctx: LookContext) {
        val layout = ctx.layout
        val paint = text(
            if (layout.shape == ShareShape.STORY) 72f else layout.width * 0.055f,
            ink(ctx),
            ctx.fonts.display,
            shadowed(ctx),
        )
        val min = if (layout.shape == ShareShape.STORY) 48f else 40f
        val headline = ShareText.fit(paint, ctx.card.headline, layout.width * 0.62f, min)
        val baseline = if (layout.shape == ShareShape.STORY) 1240f else layout.height * 0.78f
        canvas.drawText(headline, 48f, baseline, paint)
        val from = ctx.card.fromPlace
        val to = ctx.card.toPlace
        val places = if (!from.isNullOrBlank() && !to.isNullOrBlank()) "$from → $to" else null
        val line = listOfNotNull(places, ctx.card.dateText.takeIf { it.isNotBlank() }).joinToString(" · ")
        if (line.isBlank()) return
        val place = text(28f * layout.width / 1080f, ink(ctx), ctx.fonts.body, shadowed(ctx))
        val fitted = ShareText.fit(place, line, layout.width * 0.55f, 18f)
        val y = baseline + place.textSize + 18f
        canvas.drawText(fitted, layout.width - 48f - place.measureText(fitted), y, place)
        ctx.card.weatherLine?.let { weather ->
            val weatherPaint = text(26f * layout.width / 1080f, ink(ctx), ctx.fonts.body, shadowed(ctx))
            canvas.drawText(weather, 48f, y, weatherPaint)
        }
    }

    private fun drawStamp(canvas: Canvas, ctx: LookContext) {
        val badge = ctx.card.highlight ?: return
        val layout = ctx.layout
        val cx = layout.width * 0.78f
        val cy = if (layout.shape == ShareShape.STORY) 420f else layout.height * 0.28f
        canvas.save()
        canvas.rotate(-14f, cx, cy)
        val label = text(26f * layout.width / 1080f, Color.rgb(20, 16, 8), ctx.fonts.body, false)
        val text = badge.title
        val tw = label.measureText(text)
        val rect = RectF(cx - tw / 2f - 22f, cy - 28f, cx + tw / 2f + 22f, cy + 28f)
        canvas.drawRoundRect(rect, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ctx.accent })
        canvas.drawText(text, rect.left + 22f, cy + label.textSize * 0.32f, label)
        canvas.restore()
    }

    private fun drawBand(canvas: Canvas, ctx: LookContext) {
        val layout = ctx.layout
        val h = layout.height * 0.07f
        val top = layout.height - h
        canvas.drawRect(0f, top, layout.width, layout.height, Paint().apply { color = ctx.accent })
        val stats = ctx.card.stats.take(3)
        if (stats.isEmpty()) return
        val slot = layout.width / stats.size
        stats.forEachIndexed { i, stat ->
            val value = text(h * 0.38f, INK, ctx.fonts.body, false)
            val caption = text(h * 0.20f, INK, ctx.fonts.body, false)
            val fitted = ShareText.fit(value, stat.value, slot - 28f, 16f)
            val cap = ShareText.fit(caption, stat.title, slot - 28f, 12f)
            val x = slot * i + 24f
            canvas.drawText(fitted, x, top + h * 0.48f, value)
            canvas.drawText(cap, x, top + h * 0.82f, caption)
        }
    }

    private fun ink(ctx: LookContext): Int = when (ctx.background) {
        ShareBackground.MAP, ShareBackground.PHOTO -> Color.WHITE
        else -> INK
    }

    private fun shadowed(ctx: LookContext): Boolean =
        ctx.background == ShareBackground.MAP || ctx.background == ShareBackground.PHOTO

    private fun text(size: Float, color: Int, face: android.graphics.Typeface, shadow: Boolean) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = face
            if (shadow) setShadowLayer(10f, 0f, 3f, Color.argb(180, 0, 0, 0))
        }

    private fun stopPoints(ctx: LookContext, route: List<PointF>): List<PointF> {
        if (ctx.card.stops.isEmpty() || route.isEmpty() || ctx.card.route.isEmpty()) return emptyList()
        return ctx.card.stops.map { stop -> route[nearest(ctx.card.route, stop)] }
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

    private fun cover(ctx: LookContext) =
        if (ctx.background == ShareBackground.MAP && ctx.card.route.size >= 2) {
            ShareCardMapTransform.posterCover(ctx.card.route, ctx.layout.height > ctx.layout.width)
        } else {
            null
        }
}
