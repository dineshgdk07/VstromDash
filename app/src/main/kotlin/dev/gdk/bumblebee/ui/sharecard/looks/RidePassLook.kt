package dev.gdk.bumblebee.ui.sharecard.looks

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.text.TextPaint
import dev.gdk.bumblebee.ride.ShareBackground
import dev.gdk.bumblebee.ride.ShareMetricKind
import dev.gdk.bumblebee.ride.ShareShape
import dev.gdk.bumblebee.ui.sharecard.ShareCardRoute
import dev.gdk.bumblebee.ui.sharecard.ShareTextures
import kotlin.random.Random

/**
 * The ride as a boarding pass: a ticket in the accent colour with from → to codes, the route,
 * a dashed tear line with notches and a stub of stats over a barcode. Mockup D.
 */
object RidePassLook : CardLook {
    private const val INK = 0xFF141414.toInt()
    private const val PAGE = 0xFF1B1B1F.toInt()

    override fun stage(ctx: LookContext): RectF {
        val u = ctx.u
        val t = ticket(ctx)
        val top = t.top + titleBottom(ctx) + 10f * u
        val bottom = tear(ctx) - (if (ctx.card.weatherLine.isNullOrBlank()) 14f else 26f) * u
        return RectF(t.left + 22f * u, top, t.right - 22f * u, bottom)
    }

    override fun drawOwnBackground(canvas: Canvas, ctx: LookContext) {
        canvas.drawColor(PAGE)
        ShareTextures.grain(canvas, RectF(0f, 0f, ctx.layout.width, ctx.layout.height), 0.3f)
    }

    override fun drawScrim(canvas: Canvas, ctx: LookContext) {
        canvas.drawColor(Color.argb(80, 0, 0, 0))
    }

    override fun draw(canvas: Canvas, ctx: LookContext) {
        drawTicket(canvas, ctx)
        drawHeader(canvas, ctx)
        drawTitle(canvas, ctx)
        drawRoute(canvas, ctx)
        drawStamp(canvas, ctx)
        drawWeather(canvas, ctx)
        drawTear(canvas, ctx)
        drawStub(canvas, ctx)
    }

    /** The ticket is the sticker. */
    override fun drawSticker(canvas: Canvas, ctx: LookContext) = draw(canvas, ctx)

    // ---- layout ----

    private fun ticket(ctx: LookContext): RectF {
        val m = 14f * ctx.u
        return RectF(m, m, ctx.layout.width - m, ctx.layout.height - m)
    }

    private fun compact(ctx: LookContext) = ctx.layout.shape == ShareShape.SQUARE

    /** Stub height below the tear line, in units. */
    private fun stubHeight(ctx: LookContext) = when (ctx.layout.shape) {
        ShareShape.STORY -> 150f
        ShareShape.FEED -> 122f
        ShareShape.SQUARE -> 78f
    }

    private fun tear(ctx: LookContext): Float = ticket(ctx).bottom - stubHeight(ctx) * ctx.u

    private fun codeSize(ctx: LookContext) = (if (compact(ctx)) 24f else 30f) * ctx.u

    /** Offsets from the ticket top, in px. */
    private fun headerBase(ctx: LookContext) = (if (compact(ctx)) 20f else 26f) * ctx.u
    private fun codeBase(ctx: LookContext) = headerBase(ctx) + (if (compact(ctx)) 46f else 60f) * ctx.u
    private fun titleBottom(ctx: LookContext) = codeBase(ctx) + (if (ctx.card.fromCode != null) 16f else 6f) * ctx.u

    // ---- ticket ----

    private fun drawTicket(canvas: Canvas, ctx: LookContext) {
        val u = ctx.u
        val t = ticket(ctx)
        val body = fill(ctx.accent).apply {
            if (ctx.background != ShareBackground.OWN) setShadowLayer(9f * u, 0f, 3.5f * u, Color.argb(170, 0, 0, 0))
        }
        canvas.drawRoundRect(t, 14f * u, 14f * u, body)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(t, 14f * u, 14f * u, Path.Direction.CW) })
        ShareTextures.grain(canvas, t, 0.22f)
        canvas.restore()
    }

    private fun drawHeader(canvas: Canvas, ctx: LookContext) {
        val u = ctx.u
        val t = ticket(ctx)
        val y = t.top + headerBase(ctx)
        val label = ctx.label(INK, size = 9f)
        canvas.drawText("RIDE PASS", t.left + 16f * u, y, label)
        ctx.card.rideNumberText?.takeIf { it.isNotBlank() }?.let {
            canvas.fitted(it.upper(), t.right - 16f * u, y, label, t.width() * 0.4f, 6f * u, Paint.Align.RIGHT)
        }
    }

    private fun drawTitle(canvas: Canvas, ctx: LookContext) {
        val u = ctx.u
        val t = ticket(ctx)
        val left = t.left + 16f * u
        val right = t.right - 16f * u
        val base = t.top + codeBase(ctx)
        val from = ctx.card.fromCode
        val to = ctx.card.toCode
        val label = ctx.label(INK, size = 9f)
        val code = textPaint(codeSize(ctx), INK, ctx.fonts.display)
        if (from == null || to == null) {
            canvas.drawText(ctx.card.shortDate().upper(), left, base - codeSize(ctx) - 6f * u, label)
            canvas.fitted(ctx.card.headline.upper(), left, base, code, t.width() - 32f * u, 16f * u)
            return
        }
        val labelBase = base - codeSize(ctx) - 6f * u
        canvas.drawText("FROM", left, labelBase, label)
        canvas.fitted("TO", right, labelBase, label, 40f * u, 6f * u, Paint.Align.RIGHT)
        val fromW = canvas.fitted(from, left, base, code, t.width() * 0.36f, 16f * u)
        val toW = canvas.fitted(to, right, base, textPaint(codeSize(ctx), INK, ctx.fonts.display), t.width() * 0.36f, 16f * u, Paint.Align.RIGHT)
        val place = textPaint(11f * u, INK, ctx.fonts.body)
        val placeBase = base + 13f * u
        canvas.fitted(ctx.card.fromPlace.orEmpty(), left, placeBase, place, t.width() * 0.44f, 7f * u)
        canvas.fitted(ctx.card.toPlace.orEmpty(), right, placeBase, textPaint(11f * u, INK, ctx.fonts.body), t.width() * 0.44f, 7f * u, Paint.Align.RIGHT)
        connector(canvas, ctx, left + fromW + 10f * u, right - toW - 10f * u, base - codeSize(ctx) * 0.36f)
    }

    /** "──🏍──": a rule with a small motorcycle in the middle. */
    private fun connector(canvas: Canvas, ctx: LookContext, from: Float, to: Float, y: Float) {
        val u = ctx.u
        if (to - from < 40f * u) return
        val cx = (from + to) / 2f
        val half = 11f * u
        val line = stroke(1.2f * u, INK).apply { strokeCap = Paint.Cap.BUTT }
        canvas.drawLine(from, y, cx - half - 4f * u, y, line)
        canvas.drawLine(cx + half + 4f * u, y, to, y, line)
        val s = u * 0.95f
        val wheel = stroke(1.6f * s, INK)
        canvas.drawCircle(cx - 7f * s, y + 3f * s, 3.4f * s, wheel)
        canvas.drawCircle(cx + 7.5f * s, y + 3f * s, 3.4f * s, wheel)
        val body = Path().apply {
            moveTo(cx - 7f * s, y + 3f * s)
            lineTo(cx - 3f * s, y - 2.5f * s)
            lineTo(cx + 3f * s, y - 2.5f * s)
            lineTo(cx + 7.5f * s, y + 3f * s)
            moveTo(cx - 5f * s, y - 2.5f * s)
            lineTo(cx + 1f * s, y - 4.5f * s)
            moveTo(cx + 3f * s, y - 2.5f * s)
            lineTo(cx + 5f * s, y - 6.5f * s)
            lineTo(cx + 7.5f * s, y - 6.5f * s)
        }
        canvas.drawPath(body, stroke(1.6f * s, INK))
        canvas.drawRoundRect(RectF(cx - 3.5f * s, y - 5f * s, cx + 3.5f * s, y - 1f * s), 1.5f * s, 1.5f * s, fill(INK))
    }

    private fun drawRoute(canvas: Canvas, ctx: LookContext) {
        if (!ctx.card.hasRoute()) return
        val u = ctx.u
        val stage = stage(ctx)
        if (stage.height() < 24f * u) return
        // Keep the route clear of the stamp's corner.
        if (ctx.card.highlight != null) stage.right -= stampRadius(ctx) * 2f + 8f * u
        val points = ctx.routePoints(stage)
        if (points.size < 2) return
        canvas.drawPath(ShareCardRoute.path(points), stroke(4f * u, INK))
        ShareCardRoute.drawStops(canvas, ctx.stopPoints(points), INK, ctx.accent, 4f * u)
        val first = points.first()
        val last = points.last()
        canvas.drawCircle(first.x, first.y, 5f * u, fill(INK))
        canvas.drawCircle(last.x, last.y, 5f * u, fill(Color.WHITE))
        canvas.drawCircle(last.x, last.y, 5f * u, stroke(2f * u, INK))
    }

    /** A round rubber stamp in the route's top-right corner. */
    private fun drawStamp(canvas: Canvas, ctx: LookContext) {
        val badge = ctx.card.highlight ?: return
        val u = ctx.u
        val stage = stage(ctx)
        val r = stampRadius(ctx)
        val cx = stage.right - r
        val cy = stage.top + r + 2f * u
        val ink = withAlpha(INK, 215)
        canvas.save()
        canvas.rotate(-14f, cx, cy)
        canvas.drawCircle(cx, cy, r, fill(withAlpha(ctx.accent, 235)))
        canvas.drawCircle(cx, cy, r, stroke(1.6f * u, ink))
        canvas.drawCircle(cx, cy, r - 3.5f * u, stroke(0.6f * u, ink))
        val words = badge.title.upper().split(' ')
        val lines = if (words.size <= 3) words else listOf(words.dropLast(words.size / 2).joinToString(" "), words.takeLast(words.size / 2).joinToString(" "))
        val text = ctx.label(ink, size = if (compact(ctx)) 6.5f else 8f).apply { letterSpacing = 0.12f }
        val lineH = text.textSize * 1.2f
        var y = cy - lineH * (lines.size - 1) / 2f + text.textSize * 0.35f
        lines.forEach {
            canvas.fitted(it, cx, y, text, r * 1.6f, 5f * u, Paint.Align.CENTER)
            y += lineH
        }
        canvas.restore()
    }

    private fun stampRadius(ctx: LookContext) = (if (compact(ctx)) 22f else 30f) * ctx.u

    private fun drawWeather(canvas: Canvas, ctx: LookContext) {
        val weather = ctx.card.weatherLine?.takeIf { it.isNotBlank() } ?: return
        val u = ctx.u
        val t = ticket(ctx)
        canvas.fitted(weather.upper(), t.left + 16f * u, tear(ctx) - 11f * u, ctx.label(INK, size = 8f), t.width() - 32f * u, 6f * u)
    }

    private fun drawTear(canvas: Canvas, ctx: LookContext) {
        val u = ctx.u
        val t = ticket(ctx)
        val y = tear(ctx)
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(INK, 110)
            strokeWidth = 1f * u
            pathEffect = DashPathEffect(floatArrayOf(5f * u, 3.5f * u), 0f)
        }
        canvas.drawLine(t.left + 14f * u, y, t.right - 14f * u, y, dash)
        val radius = 11f * u
        val clear = ctx.background != ShareBackground.OWN
        val hole = fill(if (clear) Color.TRANSPARENT else PAGE).apply {
            if (clear) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        canvas.drawCircle(t.left, y, radius, hole)
        canvas.drawCircle(t.right, y, radius, hole)
    }

    private fun drawStub(canvas: Canvas, ctx: LookContext) {
        val u = ctx.u
        val t = ticket(ctx)
        val card = ctx.card
        val distance = card.available.firstOrNull { it.kind == ShareMetricKind.DISTANCE }
        val moving = card.available.firstOrNull { it.kind == ShareMetricKind.MOVING }
        val extra = card.stats.firstOrNull { it.kind != ShareMetricKind.DISTANCE && it.kind != ShareMetricKind.MOVING }
        val big = listOfNotNull(
            distance?.let { "Distance" to it.detail },
            moving?.let { "Moving" to it.value },
        )
        val small = listOfNotNull(
            card.shortDate().takeIf { it.isNotBlank() }?.let { "Date" to it },
            extra?.let { it.title to it.detail },
        )
        val left = t.left + 16f * u
        val right = t.right - 16f * u
        val top = tear(ctx)
        val label = ctx.label(INK, size = 8.5f)
        fun cell(i: Int, count: Int, caption: String, value: String, labelY: Float, valueY: Float, paint: TextPaint) {
            val align = if (count > 1 && i == count - 1) Paint.Align.RIGHT else Paint.Align.LEFT
            val x = when {
                align == Paint.Align.RIGHT -> right
                count > 2 -> left + (right - left) * i / count
                else -> left
            }
            val room = (right - left) / count - 6f * u
            canvas.fitted(caption.upper(), x, labelY, label, room, 6f * u, align)
            canvas.fitted(value, x, valueY, paint, room, 9f * u, align)
        }
        val barcodeTop: Float
        if (compact(ctx)) {
            val all = big + small
            all.forEachIndexed { i, (c, v) ->
                cell(i, all.size, c, v.upper(), top + 20f * u, top + 38f * u, textPaint(15f * u, INK, ctx.fonts.display))
            }
            barcodeTop = top + 48f * u
        } else {
            val s = stubHeight(ctx) / 150f
            big.forEachIndexed { i, (c, v) ->
                cell(i, big.size, c, v, top + 26f * u * s, top + 50f * u * s, textPaint(22f * u * s, INK, ctx.fonts.display))
            }
            small.forEachIndexed { i, (c, v) ->
                cell(i, small.size, c, v.upper(), top + 72f * u * s, top + 90f * u * s, textPaint(15f * u * s, INK, ctx.fonts.body))
            }
            barcodeTop = top + 104f * u * s
        }
        barcode(canvas, RectF(left, barcodeTop, right, t.bottom - 16f * u), card.rideNumberText.orEmpty() + card.dateText)
    }

    /** Decorative bars, the same for the same ride. */
    private fun barcode(canvas: Canvas, rect: RectF, seed: String) {
        if (rect.height() < 4f) return
        val rnd = Random(seed.hashCode())
        val unit = rect.width() / 180f
        val paint = fill(INK)
        var x = rect.left
        while (x < rect.right) {
            val bar = (1 + rnd.nextInt(3)) * unit
            val gap = (1 + rnd.nextInt(2)) * unit
            canvas.drawRect(x, rect.top, minOf(x + bar, rect.right), rect.bottom, paint)
            x += bar + gap
        }
    }
}
