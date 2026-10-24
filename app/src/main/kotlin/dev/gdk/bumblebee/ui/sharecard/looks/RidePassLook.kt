package dev.gdk.bumblebee.ui.sharecard.looks

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.text.TextPaint
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.ShareBackground
import dev.gdk.bumblebee.ride.ShareMetricKind
import dev.gdk.bumblebee.ride.ShareShape
import dev.gdk.bumblebee.ui.sharecard.RouteStyle
import dev.gdk.bumblebee.ui.sharecard.ShareCardLayout
import dev.gdk.bumblebee.ui.sharecard.ShareCardMapTransform
import dev.gdk.bumblebee.ui.sharecard.ShareCardRoute
import dev.gdk.bumblebee.ui.sharecard.ShareText

object RidePassLook : CardLook {
    private const val INK = 0xFF141414.toInt()
    private const val PAGE = 0xFF1B1B1F.toInt()

    override fun drawOwnBackground(canvas: Canvas, ctx: LookContext) {
        canvas.drawColor(PAGE)
    }

    override fun drawScrim(canvas: Canvas, ctx: LookContext) {
        canvas.drawColor(Color.argb(70, 0, 0, 0))
    }

    override fun draw(canvas: Canvas, ctx: LookContext) {
        val ticket = ticket(ctx.layout)
        val tear = ticket.top + ticket.height() * 0.62f
        drawTicket(canvas, ctx, ticket)
        drawHeader(canvas, ctx, ticket)
        drawTitle(canvas, ctx, ticket)
        drawRoute(canvas, ctx, ticket, tear)
        punch(canvas, ctx, ticket, tear)
        drawStub(canvas, ctx, ticket, tear)
        drawStamp(canvas, ctx, ticket)
    }

    override fun drawSticker(canvas: Canvas, ctx: LookContext) {
        val ticket = ticket(ctx.layout)
        val tear = ticket.top + ticket.height() * 0.62f
        drawRoute(canvas, ctx, ticket, tear)
        drawStub(canvas, ctx, ticket, tear)
    }

    private fun drawTicket(canvas: Canvas, ctx: LookContext, ticket: RectF) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ctx.accent
            if (ctx.background == ShareBackground.MAP || ctx.background == ShareBackground.PHOTO) {
                setShadowLayer(28f, 0f, 14f, Color.argb(170, 0, 0, 0))
            }
        }
        canvas.drawRoundRect(ticket, 36f, 36f, paint)
    }

    private fun drawHeader(canvas: Canvas, ctx: LookContext, ticket: RectF) {
        val scale = ctx.layout.width / 1080f
        val label = text(28f * scale, INK, ctx.fonts.body)
        canvas.drawText("Ride pass", ticket.left + 36f, ticket.top + 64f * scale, label)
        val number = ctx.card.rideNumberText
        if (!number.isNullOrBlank()) {
            val num = text(28f * scale, INK, ctx.fonts.mono)
            canvas.drawText(number, ticket.right - 36f - num.measureText(number), ticket.top + 64f * scale, num)
        }
    }

    private fun drawTitle(canvas: Canvas, ctx: LookContext, ticket: RectF) {
        val from = ctx.card.fromCode
        val to = ctx.card.toCode
        val scale = ctx.layout.width / 1080f
        if (from != null && to != null) {
            val code = text(if (ctx.layout.shape == ShareShape.STORY) 92f else 72f * scale, INK, ctx.fonts.display)
            val place = text(24f * scale, INK, ctx.fonts.body)
            val y = if (ctx.layout.shape == ShareShape.STORY) 280f else ticket.top + ticket.height() * 0.16f
            val left = ticket.left + 36f
            val mid = ticket.centerX() + 12f
            canvas.drawText(ShareText.fit(code, from, ticket.width() * 0.40f, 40f), left, y, code)
            canvas.drawText(ctx.card.fromPlace.orEmpty(), left, y + place.textSize + 8f, place)
            val toFitted = ShareText.fit(code, to, ticket.width() * 0.40f, 40f)
            canvas.drawText(toFitted, mid, y, code)
            canvas.drawText(ctx.card.toPlace.orEmpty(), mid, y + place.textSize + 8f, place)
            return
        }
        val min = if (ctx.layout.shape == ShareShape.STORY) 48f else 40f
        val paint = text(if (ctx.layout.shape == ShareShape.STORY) 72f else 56f * scale, INK, ctx.fonts.display)
        val headline = ShareText.fit(paint, ctx.card.headline, ticket.width() - 80f, min)
        val y = if (ctx.layout.shape == ShareShape.STORY) 280f else ticket.top + ticket.height() * 0.16f
        canvas.drawText(headline, ticket.left + 36f, y, paint)
    }

    private fun drawRoute(canvas: Canvas, ctx: LookContext, ticket: RectF, tear: Float) {
        if (ctx.card.plot.size < 2 && ctx.card.route.size < 2) return
        val stage = RectF(ticket.left + 48f, ticket.top + ticket.height() * 0.28f, ticket.right - 48f, tear - 36f)
        if (stage.height() < 24f) return
        val points = ShareCardRoute.project(ctx.card, cover(ctx), stage)
        canvas.save()
        canvas.clipRect(stage)
        ShareCardRoute.drawRoute(canvas, points, INK, RouteStyle.INK)
        if (points.isNotEmpty()) {
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = INK
            }
            canvas.drawCircle(points.first().x, points.first().y, 8f, stroke)
            canvas.drawCircle(points.last().x, points.last().y, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK })
        }
        ShareCardRoute.drawStops(canvas, stopPoints(ctx, points), INK)
        canvas.restore()
    }

    private fun punch(canvas: Canvas, ctx: LookContext, ticket: RectF, tear: Float) {
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(14f, 12f), 0f)
        }
        canvas.drawLine(ticket.left + 28f, tear, ticket.right - 28f, tear, dash)
        val radius = 16f
        val clear = ctx.background != ShareBackground.OWN
        val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (clear) Color.TRANSPARENT else PAGE
            if (clear) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        canvas.drawCircle(ticket.left, tear, radius, hole)
        canvas.drawCircle(ticket.right, tear, radius, hole)
    }

    private fun drawStub(canvas: Canvas, ctx: LookContext, ticket: RectF, tear: Float) {
        val distance = ctx.card.available.firstOrNull { it.kind == ShareMetricKind.DISTANCE }
        val moving = ctx.card.available.firstOrNull { it.kind == ShareMetricKind.MOVING }
        val third = ctx.card.stats.firstOrNull { it.kind != ShareMetricKind.DISTANCE && it.kind != ShareMetricKind.MOVING }
        val cells = listOfNotNull(
            distance?.let { it.value to "Distance" },
            moving?.let { it.value to "Moving" },
            ctx.card.dateText.takeIf { it.isNotBlank() }?.let { it to "Date" },
            third?.let { it.value to it.title },
        )
        val top = tear + 36f
        val slot = ticket.width() / cells.size.coerceAtLeast(1)
        cells.forEachIndexed { i, (value, caption) ->
            val valuePaint = text(30f * ctx.layout.width / 1080f, INK, ctx.fonts.body)
            val cap = text(16f * ctx.layout.width / 1080f, INK, ctx.fonts.body)
            val x = ticket.left + 28f + slot * i
            canvas.drawText(ShareText.fit(valuePaint, value, slot - 24f, 14f), x, top + valuePaint.textSize, valuePaint)
            canvas.drawText(ShareText.fit(cap, caption, slot - 24f, 12f), x, top + valuePaint.textSize + cap.textSize + 6f, cap)
        }
        val weather = ctx.card.weatherLine
        if (!weather.isNullOrBlank()) {
            val line = text(20f * ctx.layout.width / 1080f, INK, ctx.fonts.body)
            canvas.drawText(
                ShareText.fit(line, weather, ticket.width() - 72f, 14f),
                ticket.left + 36f,
                ticket.bottom - 88f,
                line,
            )
        }
        barcode(canvas, ticket)
    }

    private fun barcode(canvas: Canvas, ticket: RectF) {
        val paint = Paint().apply { color = INK }
        var x = ticket.left + 36f
        val top = ticket.bottom - 64f
        val pattern = intArrayOf(2, 1, 3, 1, 1, 4, 2, 1, 2, 3, 1, 1, 2, 4, 1, 2, 1, 3)
        var i = 0
        while (x < ticket.right - 36f) {
            val w = pattern[i % pattern.size] * 3f
            canvas.drawRect(x, top, x + w, ticket.bottom - 28f, paint)
            x += w + 4f
            i++
        }
    }

    private fun drawStamp(canvas: Canvas, ctx: LookContext, ticket: RectF) {
        val badge = ctx.card.highlight ?: return
        val cx = ticket.right - 110f
        val cy = ticket.top + 150f
        canvas.save()
        canvas.rotate(-16f, cx, cy)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = INK
        }
        canvas.drawCircle(cx, cy, 54f, ring)
        val label = text(16f, INK, ctx.fonts.body)
        val fitted = ShareText.fit(label, badge.title, 90f, 12f)
        canvas.drawText(fitted, cx - label.measureText(fitted) / 2f, cy + label.textSize * 0.3f, label)
        canvas.restore()
    }

    private fun ticket(layout: ShareCardLayout): RectF {
        val inset = layout.width * 0.04f
        return RectF(inset, layout.height * 0.045f, layout.width - inset, layout.height * 0.955f)
    }

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
