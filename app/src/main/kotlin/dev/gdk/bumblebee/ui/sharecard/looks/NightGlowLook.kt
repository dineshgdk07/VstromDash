package dev.gdk.bumblebee.ui.sharecard.looks

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
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
import kotlin.math.hypot
import kotlin.math.roundToInt

object NightGlowLook : CardLook {
    override fun drawOwnBackground(canvas: Canvas, ctx: LookContext) {
        val w = ctx.layout.width
        val h = ctx.layout.height
        canvas.drawColor(0xFF060608.toInt())
        val warm = mix(0xFF2A2208.toInt(), ctx.accent, 0.20f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w * 0.70f,
                h * 0.30f,
                hypot(w, h),
                intArrayOf(warm, 0xFF120F06.toInt(), 0xFF060608.toInt()),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h, paint)
        ShareTextures.contours(
            canvas,
            RectF(0f, 0f, w, h),
            Color.argb(20, Color.red(ctx.accent), Color.green(ctx.accent), Color.blue(ctx.accent)),
            seed = 7L,
        )
        val grain = ShareTextures.grain(w.toInt(), h.toInt())
        canvas.drawBitmap(grain, 0f, 0f, Paint().apply { alpha = 46 })
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
                Color.argb(210, 0, 0, 0),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, top, ctx.layout.width, ctx.layout.height, paint)
    }

    override fun draw(canvas: Canvas, ctx: LookContext) {
        drawRoute(canvas, ctx)
        drawElevation(canvas, ctx)
        drawType(canvas, ctx)
        drawStats(canvas, ctx)
    }

    override fun drawSticker(canvas: Canvas, ctx: LookContext) {
        drawRoute(canvas, ctx)
        drawStats(canvas, ctx)
    }

    private fun drawRoute(canvas: Canvas, ctx: LookContext) {
        if (ctx.card.plot.size < 2 && ctx.card.route.size < 2) return
        val stage = ShareCardRoute.stage(ctx.layout)
        val points = ShareCardRoute.project(ctx.card, cover(ctx), stage)
        canvas.save()
        canvas.clipRect(0f, 0f, ctx.layout.width, typeTop(ctx.layout))
        ShareCardRoute.drawRoute(canvas, points, ctx.accent, RouteStyle.GLOW)
        markers(canvas, points, ctx.accent)
        ShareCardRoute.drawStops(canvas, stopPoints(ctx, points), ctx.accent)
        canvas.restore()
    }

    /** Keeps the glow off the headline. The map fit can place an end below the stage. */
    private fun typeTop(layout: ShareCardLayout): Float =
        if (layout.shape == ShareShape.STORY) 1280f else layout.height * 0.64f

    private fun drawElevation(canvas: Canvas, ctx: LookContext) {
        val samples = ctx.card.elevation
        if (samples.size < 2) return
        val stage = ShareCardRoute.stage(ctx.layout)
        val floor = typeTop(ctx.layout).coerceAtMost(stage.bottom)
        if (stage.top >= floor) return
        val path = Path()
        val amp = (floor - stage.top) * 0.16f
        path.moveTo(stage.left, floor)
        samples.forEachIndexed { i, v ->
            val x = stage.left + stage.width() * i / (samples.lastIndex)
            val y = floor - amp * v.coerceIn(0f, 1f)
            path.lineTo(x, y)
        }
        path.lineTo(stage.right, floor)
        path.close()
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(48, Color.red(ctx.accent), Color.green(ctx.accent), Color.blue(ctx.accent))
            },
        )
    }

    private fun drawType(canvas: Canvas, ctx: LookContext) {
        val layout = ctx.layout
        val min = if (layout.shape == ShareShape.STORY) 48f else 40f
        val headlinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = if (layout.shape == ShareShape.STORY) 108f else layout.width * 0.085f
            typeface = ctx.fonts.display
        }
        val headline = ShareText.fit(headlinePaint, ctx.card.headline, layout.width - 96f, min)
        val baseline = if (layout.shape == ShareShape.STORY) 1460f else layout.height * 0.76f
        ctx.card.highlight?.let { badge ->
            val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(12, 10, 6)
                textSize = 28f * layout.width / 1080f
                typeface = ctx.fonts.body
            }
            val text = badge.title
            val pad = 16f
            val tw = label.measureText(text)
            val pillBottom = baseline - headlinePaint.textSize * 0.92f - 14f
            val top = pillBottom - label.textSize - pad
            val pill = RectF(48f, top, 48f + tw + pad * 2, pillBottom)
            canvas.drawRoundRect(pill, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ctx.accent })
            canvas.drawText(text, pill.left + pad, pill.bottom - pad * 0.65f, label)
        }
        canvas.drawText(headline, 48f, baseline, headlinePaint)
        val weather = ctx.card.weatherLine
        if (!weather.isNullOrBlank()) {
            val muted = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(190, 196, 190, 176)
                textSize = 32f * layout.width / 1080f
                typeface = ctx.fonts.body
            }
            canvas.drawText(weather, 48f, baseline + muted.textSize + 16f, muted)
        }
    }

    private fun drawStats(canvas: Canvas, ctx: LookContext) {
        val layout = ctx.layout
        val hero = ctx.card.hero
        val stats = ctx.card.stats.take(3)
        if (hero == null && stats.isEmpty()) return
        val h = layout.height * 0.145f
        val panel = RectF(36f, layout.height - h - 32f, layout.width - 36f, layout.height - 32f)
        canvas.drawRoundRect(panel, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(15, 255, 255, 255) })
        canvas.drawRoundRect(
            panel,
            28f,
            28f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.argb(64, Color.red(ctx.accent), Color.green(ctx.accent), Color.blue(ctx.accent))
            },
        )
        data class Cell(val value: String, val caption: String, val hero: Boolean)
        val cells = buildList {
            if (hero != null) add(Cell(hero.heroValue, hero.unitLabel, true))
            stats.forEach { add(Cell(it.value, it.title, false)) }
        }
        val weight = cells.sumOf { if (it.hero) 1.6 else 1.0 }.toFloat()
        var x = panel.left + 24f
        val usable = panel.width() - 48f
        cells.forEach { cell ->
            val slot = usable * (if (cell.hero) 1.6f else 1f) / weight
            val value = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = panel.height() * if (cell.hero) 0.40f else 0.26f
                typeface = if (cell.hero) ctx.fonts.display else ctx.fonts.body
            }
            val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(180, 220, 216, 200)
                textSize = panel.height() * 0.13f
                typeface = ctx.fonts.body
            }
            val fitted = ShareText.fit(value, cell.value, slot - 16f, panel.height() * 0.16f)
            val cap = ShareText.fit(captionPaint, cell.caption, slot - 16f, 16f)
            val baseline = panel.centerY() + value.textSize * 0.12f
            canvas.drawText(fitted, x, baseline, value)
            canvas.drawText(cap, x, baseline + captionPaint.textSize + 8f, captionPaint)
            x += slot
        }
    }

    private fun markers(canvas: Canvas, points: List<PointF>, accent: Int) {
        if (points.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        canvas.drawCircle(points.first().x, points.first().y, 12f, paint)
        paint.color = accent
        canvas.drawCircle(points.last().x, points.last().y, 12f, paint)
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

    private fun mix(base: Int, accent: Int, amount: Float): Int {
        fun ch(a: Int, b: Int) = (a + (b - a) * amount).roundToInt().coerceIn(0, 255)
        return Color.rgb(
            ch(Color.red(base), Color.red(accent)),
            ch(Color.green(base), Color.green(accent)),
            ch(Color.blue(base), Color.blue(accent)),
        )
    }
}
