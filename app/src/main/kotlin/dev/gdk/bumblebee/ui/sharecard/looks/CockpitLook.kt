package dev.gdk.bumblebee.ui.sharecard.looks

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.ShareAccent
import dev.gdk.bumblebee.ride.ShareBackground
import dev.gdk.bumblebee.ride.ShareCardModel
import dev.gdk.bumblebee.ride.ShareMetric
import dev.gdk.bumblebee.ride.ShareMetricKind
import dev.gdk.bumblebee.ride.ShareShape
import dev.gdk.bumblebee.ui.sharecard.RouteStyle
import dev.gdk.bumblebee.ui.sharecard.ShareCardLayout
import dev.gdk.bumblebee.ui.sharecard.ShareCardMapTransform
import dev.gdk.bumblebee.ui.sharecard.ShareCardRoute
import dev.gdk.bumblebee.ui.sharecard.ShareTextures
import dev.gdk.bumblebee.ui.sharecard.ShareText

object CockpitLook : CardLook {
    override fun drawOwnBackground(canvas: Canvas, ctx: LookContext) {
        val w = ctx.layout.width
        val h = ctx.layout.height
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h, 0xFF0B1016.toInt(), 0xFF05070A.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, paint)
        val scale = w / 1080f
        ShareTextures.grid(canvas, RectF(0f, 0f, w, h), 18f * scale, Color.argb(18, 0x50, 0xA0, 0xDC))
    }

    override fun drawScrim(canvas: Canvas, ctx: LookContext) {
        val top = ctx.layout.height * 0.40f
        val paint = Paint().apply {
            shader = LinearGradient(0f, top, 0f, ctx.layout.height, Color.TRANSPARENT, Color.argb(200, 0, 0, 0), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, top, ctx.layout.width, ctx.layout.height, paint)
    }

    override fun draw(canvas: Canvas, ctx: LookContext) {
        drawRoute(canvas, ctx)
        drawRec(canvas, ctx)
        drawHeadline(canvas, ctx)
        drawDial(canvas, ctx)
        drawOdometer(canvas, ctx)
        drawStats(canvas, ctx)
        drawTraces(canvas, ctx)
    }

    override fun drawSticker(canvas: Canvas, ctx: LookContext) {
        drawRoute(canvas, ctx)
        drawDial(canvas, ctx)
        drawOdometer(canvas, ctx)
        drawStats(canvas, ctx)
        drawTraces(canvas, ctx)
    }

    private fun drawRoute(canvas: Canvas, ctx: LookContext) {
        if (ctx.card.plot.size < 2 && ctx.card.route.size < 2) return
        val stage = ShareCardRoute.stage(ctx.layout)
        val points = ShareCardRoute.project(ctx.card, cover(ctx), stage)
        val odo = odometerRect(ctx.layout)
        val dial = dialFrame(ctx.layout)
        canvas.save()
        canvas.clipRect(0f, headlineBaseline(ctx.layout) + 72f, ctx.layout.width, odo.top - 20f)
        val hole = Path().apply { addOval(dial, Path.Direction.CW) }
        canvas.clipOutPath(hole)
        ShareCardRoute.drawRoute(canvas, points, routeColor(ctx), RouteStyle.THIN)
        markers(canvas, points)
        ShareCardRoute.drawStops(canvas, stopPoints(ctx, points), dialColor(ctx))
        canvas.restore()
    }

    private fun drawRec(canvas: Canvas, ctx: LookContext) {
        val badge = ctx.card.highlight ?: return
        val scale = ctx.layout.width / 1080f
        val tag = text(28f * scale, ctx.accent, ctx.fonts.mono)
        canvas.drawText("● REC", 48f, 58f * scale, tag)
        val title = text(24f * scale, Color.argb(210, 230, 236, 242), ctx.fonts.mono)
        val fitted = ShareText.fit(title, badge.title, ctx.layout.width - 220f, 16f)
        canvas.drawText(fitted, 48f + tag.measureText("● REC") + 16f, 58f * scale, title)
    }

    private fun drawHeadline(canvas: Canvas, ctx: LookContext) {
        val layout = ctx.layout
        val min = if (layout.shape == ShareShape.STORY) 48f else 40f
        val paint = text(if (layout.shape == ShareShape.STORY) 64f else layout.width * 0.055f, Color.WHITE, ctx.fonts.display)
        val headline = ShareText.fit(paint, ctx.card.headline, layout.width - 96f, min)
        val baseline = headlineBaseline(layout)
        canvas.drawText(headline, 48f, baseline, paint)
        val weather = ctx.card.weatherLine
        if (!weather.isNullOrBlank()) {
            val muted = text(26f * layout.width / 1080f, Color.argb(170, 170, 186, 198), ctx.fonts.mono)
            canvas.drawText(weather, 48f, baseline + muted.textSize + 12f, muted)
        }
    }

    private fun drawDial(canvas: Canvas, ctx: LookContext) {
        val metric = dialMetric(ctx.card) ?: return
        val layout = ctx.layout
        val oval = dialFrame(layout)
        val cx = oval.centerX()
        val cy = oval.centerY()
        val radius = oval.width() / 2f
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 16f * layout.width / 1080f
            strokeCap = Paint.Cap.ROUND
            color = Color.argb(36, Color.red(dialColor(ctx)), Color.green(dialColor(ctx)), Color.blue(dialColor(ctx)))
        }
        canvas.drawArc(oval, 150f, 240f, false, track)
        val speed = metric.value.toFloatOrNull() ?: 0f
        val sweep = 240f * (speed / 160f).coerceIn(0f, 1f)
        track.color = dialColor(ctx)
        canvas.drawArc(oval, 150f, sweep, false, track)
        val value = text(radius * 0.55f, Color.WHITE, ctx.fonts.mono)
        val shown = ShareText.fit(value, metric.value, radius * 1.4f, 28f)
        canvas.drawText(shown, cx - value.measureText(shown) / 2f, cy + value.textSize * 0.15f, value)
        val unit = text(radius * 0.16f, Color.argb(190, 200, 214, 224), ctx.fonts.mono)
        canvas.drawText(metric.unitLabel, cx - unit.measureText(metric.unitLabel) / 2f, cy + value.textSize * 0.55f, unit)
    }

    private fun drawOdometer(canvas: Canvas, ctx: LookContext) {
        val km = ctx.card.odoKm ?: return
        val rect = odometerRect(ctx.layout)
        val digits = km.toString().padStart(6, '0').takeLast(6)
        val gap = 8f
        val box = (rect.width() - gap * (digits.length - 1)) / digits.length
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = dialColor(ctx)
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(28, 255, 255, 255) }
        val glyph = text(box * 0.62f, Color.WHITE, ctx.fonts.mono)
        digits.forEachIndexed { i, ch ->
            val left = rect.left + i * (box + gap)
            val cell = RectF(left, rect.top, left + box, rect.bottom)
            canvas.drawRoundRect(cell, 8f, 8f, fill)
            canvas.drawRoundRect(cell, 8f, 8f, stroke)
            val s = ch.toString()
            canvas.drawText(s, cell.centerX() - glyph.measureText(s) / 2f, cell.centerY() + glyph.textSize * 0.32f, glyph)
        }
    }

    private fun drawStats(canvas: Canvas, ctx: LookContext) {
        val stats = ctx.card.stats
            .filter { ctx.card.odoKm != null || it.kind != ShareMetricKind.FUEL_ECONOMY }
            .take(3)
        if (stats.isEmpty()) return
        val odo = odometerRect(ctx.layout)
        val top = odo.bottom + 28f
        val slot = ctx.layout.width / stats.size
        stats.forEachIndexed { i, stat ->
            val value = text(36f * ctx.layout.width / 1080f, Color.WHITE, ctx.fonts.mono)
            val caption = text(18f * ctx.layout.width / 1080f, Color.argb(170, 180, 196, 206), ctx.fonts.mono)
            val x = slot * i + 36f
            val fitted = ShareText.fit(value, stat.value, slot - 48f, 16f)
            val cap = ShareText.fit(caption, stat.title, slot - 48f, 12f)
            canvas.drawText(fitted, x, top + value.textSize, value)
            canvas.drawText(cap, x, top + value.textSize + caption.textSize + 8f, caption)
        }
    }

    private fun drawTraces(canvas: Canvas, ctx: LookContext) {
        val layout = ctx.layout
        val bottom = layout.height - 36f
        val top = bottom - layout.height * 0.09f
        val elevation = ctx.card.elevation
        val speed = ctx.card.speedTrace
        val split = if (elevation.size >= 2 && speed.size >= 2) layout.width * 0.38f else layout.width - 48f
        if (elevation.size >= 2) {
            trace(canvas, elevation, 48f, if (speed.size >= 2) split - 16f else layout.width - 48f, top, bottom, Color.argb(180, 0x7C, 0xF2, 0x9A))
        }
        if (speed.size >= 2) {
            val left = if (elevation.size >= 2) split + 16f else 48f
            trace(canvas, speed, left, layout.width - 48f, top, bottom, dialColor(ctx))
        }
    }

    private fun trace(canvas: Canvas, samples: List<Float>, left: Float, right: Float, top: Float, bottom: Float, color: Int) {
        if (right - left < 8f || samples.size < 2) return
        val path = Path()
        samples.forEachIndexed { i, v ->
            val x = left + (right - left) * i / samples.lastIndex
            val y = bottom - (bottom - top) * v.coerceIn(0f, 1f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                strokeJoin = Paint.Join.ROUND
                this.color = color
            },
        )
    }

    private fun markers(canvas: Canvas, points: List<PointF>) {
        if (points.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(points.first().x, points.first().y, 7f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(points.last().x, points.last().y, 7f, paint)
    }

    private fun dialMetric(card: ShareCardModel): ShareMetric? =
        if (card.hero?.kind == ShareMetricKind.MAX_SPEED) card.hero
        else card.available.firstOrNull { it.kind == ShareMetricKind.MAX_SPEED }

    private fun routeColor(ctx: LookContext): Int =
        if (ctx.card.accent == ShareAccent.YELLOW) ShareAccent.SKY.argb else ctx.accent

    private fun dialColor(ctx: LookContext): Int =
        if (ctx.card.accent == ShareAccent.YELLOW) ShareAccent.YELLOW.argb else ctx.accent

    private fun dialFrame(layout: ShareCardLayout): RectF {
        val odo = odometerRect(layout)
        val cx = layout.width * 0.50f
        val cy = (headlineBaseline(layout) + 80f + odo.top) / 2f
        val radius = minOf(layout.width * 0.22f, (odo.top - cy) * 0.85f).coerceAtLeast(48f)
        return RectF(cx - radius, cy - radius, cx + radius, cy + radius)
    }

    private fun headlineBaseline(layout: ShareCardLayout): Float =
        if (layout.shape == ShareShape.STORY) 160f else layout.height * 0.12f

    /** Story boxes sit in the rect the GPS-only test samples. */
    private fun odometerRect(layout: ShareCardLayout): RectF =
        if (layout.shape == ShareShape.STORY) RectF(280f, 1000f, 800f, 1090f)
        else RectF(layout.width * 0.22f, layout.height * 0.58f, layout.width * 0.78f, layout.height * 0.58f + 84f)

    private fun text(size: Float, color: Int, face: android.graphics.Typeface) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = face
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
