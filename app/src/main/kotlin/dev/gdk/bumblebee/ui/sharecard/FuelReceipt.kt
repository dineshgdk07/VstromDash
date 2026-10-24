package dev.gdk.bumblebee.ui.sharecard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import java.util.Locale
import kotlin.random.Random

/** One pump visit, as printed on the receipt. Nulls leave their line off. */
data class FuelReceipt(
    val id: Long,
    /** "SUN 25 OCT 2026". */
    val date: String,
    /** "06:12". */
    val time: String,
    val station: String?,
    val odoKm: Int,
    val litres: Double?,
    val rupees: Double?,
    val rupeesPerLitre: Double?,
    val kmSinceLast: Int?,
    val kmPerL: Double?,
    val barsBefore: Int,
    val barsAfter: Int,
)

/** A thermal-paper receipt for a fill, on a dark 4:5 card. */
object FuelReceiptPainter {
    private const val W = 1080
    private const val H = 1350
    private const val PAPER = 0xFFF6F3EA.toInt()
    private const val INK = 0xFF1E1E1E.toInt()
    private const val MARKER = 0xFFFAC816.toInt()

    fun paint(context: Context, r: FuelReceipt): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fonts = ShareText.fonts(context)
        c.drawColor(0xFF1B1B1F.toInt())
        ShareTextures.grain(c, RectF(0f, 0f, W.toFloat(), H.toFloat()), 0.3f)

        // Lay out once to measure, then size the paper to fit and centre it.
        val height = body(null, r, fonts, RectF(170f, 0f, 910f, 0f))
        val top = (H - height) / 2f
        val paper = RectF(170f, top, 910f, top + height)
        val outline = torn(paper)
        c.save()
        c.rotate(-2f, W / 2f, H / 2f)
        c.drawPath(outline, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PAPER
            setShadowLayer(36f, 0f, 18f, Color.argb(170, 0, 0, 0))
        })
        c.save()
        c.clipPath(outline)
        ShareTextures.grain(c, paper, 0.18f)
        c.restore()
        body(c, r, fonts, paper)
        c.restore()
        return bmp
    }

    /** Draws the receipt onto [paper] when [c] is set. Returns the paper height it needs. */
    private fun body(c: Canvas?, r: FuelReceipt, fonts: CardFonts, paper: RectF): Float {
        val left = paper.left + 56f
        val right = paper.right - 56f
        var y = paper.top + 120f
        val mono = { size: Float -> TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; color = INK; typeface = fonts.mono } }
        c?.let { center(it, "BUMBLEBEE", y, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 76f; color = INK; typeface = fonts.display; letterSpacing = 0.06f }, paper) }
        y += 46f
        c?.let { center(it, "RIDE FUEL RECEIPT", y, mono(26f).apply { letterSpacing = 0.18f }, paper) }
        y += 52f
        c?.let { rule(it, left, right, y) }
        y += 56f
        c?.let { row(it, "DATE", "${r.date}  ${r.time}".trim(), left, right, y, mono(28f)) }
        r.station?.let { name ->
            y += 46f
            c?.let { row(it, "STATION", name.uppercase(Locale.ENGLISH), left, right, y, mono(28f)) }
        }
        y += 46f
        c?.let { row(it, "ODO", "%,d KM".format(r.odoKm), left, right, y, mono(28f)) }
        y += 40f
        c?.let { rule(it, left, right, y) }
        if (r.litres != null || r.rupeesPerLitre != null) {
            y += 60f
            r.litres?.let { litres ->
                c?.let { row(it, "PETROL", "%.2f L".format(litres), left, right, y, mono(32f)) }
                y += 50f
            }
            r.rupeesPerLitre?.let { rate ->
                c?.let { row(it, "RATE", "₹%.2f/L".format(rate), left, right, y, mono(32f)) }
                y += 50f
            }
            y -= 6f
            c?.let { rule(it, left, right, y) }
        }
        y += 80f
        if (c != null) {
            val total = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 76f; color = INK; typeface = fonts.display }
            val totalText = r.rupees?.let { "₹%,.0f".format(it) } ?: "—"
            val tw = total.measureText(totalText)
            // A highlighter swipe across the total.
            if (r.rupees != null) {
                c.drawRoundRect(RectF(right - tw - 18f, y - 58f, right + 12f, y + 10f), 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MARKER })
            }
            c.drawText("TOTAL", left, y, mono(34f))
            c.drawText(totalText, right - tw, y, total)
        }
        y += 44f
        c?.let { rule(it, left, right, y) }
        y += 56f
        r.kmSinceLast?.let { km ->
            c?.let { row(it, "SINCE LAST FILL", "$km KM", left, right, y, mono(28f)) }
            y += 46f
        }
        r.kmPerL?.let { kmPerL ->
            c?.let { row(it, "LAST TANK", "%.1f KM/L".format(kmPerL), left, right, y, mono(28f)) }
            y += 46f
        }
        c?.let {
            it.drawText("GAUGE", left, y, mono(28f))
            gauge(it, right, y, r.barsBefore, r.barsAfter)
        }
        y += 40f
        c?.let { rule(it, left, right, y) }
        y += 64f
        c?.let { center(it, "THANK YOU · RIDE SAFE", y, mono(26f).apply { letterSpacing = 0.14f }, paper) }
        y += 50f
        c?.let { barcode(it, RectF(left + 40f, y, right - 40f, y + 74f), r.id) }
        y += 114f
        c?.let { center(it, "NO. %06d".format(r.id), y, mono(22f).apply { letterSpacing = 0.2f }, paper) }
        y += 56f
        return y - paper.top
    }

    /** Paper with a sawtooth tear along the top and bottom. */
    private fun torn(rect: RectF): Path {
        val tooth = 18f
        val p = Path()
        p.moveTo(rect.left, rect.top + tooth)
        var x = rect.left
        var up = true
        while (x < rect.right) {
            x = minOf(x + tooth, rect.right)
            p.lineTo(x, if (up) rect.top else rect.top + tooth)
            up = !up
        }
        p.lineTo(rect.right, rect.bottom - tooth)
        x = rect.right
        up = true
        while (x > rect.left) {
            x = maxOf(x - tooth, rect.left)
            p.lineTo(x, if (up) rect.bottom else rect.bottom - tooth)
            up = !up
        }
        p.close()
        return p
    }

    private fun row(c: Canvas, label: String, value: String, left: Float, right: Float, y: Float, paint: TextPaint) {
        c.drawText(label, left, y, paint)
        val room = right - left - paint.measureText(label) - 24f
        val shown = ShareText.fit(paint, value, room, paint.textSize * 0.7f)
        c.drawText(shown, right - paint.measureText(shown), y, paint)
    }

    private fun center(c: Canvas, text: String, y: Float, paint: TextPaint, paper: RectF) {
        val w = paint.measureText(text) - paint.letterSpacing * paint.textSize
        c.drawText(text, paper.centerX() - w / 2f, y, paint)
    }

    private fun rule(c: Canvas, left: Float, right: Float, y: Float) {
        c.drawLine(left, y, right, y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 30, 30, 30)
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(12f, 9f), 0f)
        })
    }

    /** Six cells; bars that were left are hollow, bars added are filled. */
    private fun gauge(c: Canvas, right: Float, baseline: Float, before: Int, after: Int) {
        val cell = 30f
        val gap = 8f
        var x = right - 6 * cell - 5 * gap
        val top = baseline - 26f
        for (bar in 1..6) {
            val r = RectF(x, top, x + cell, top + 28f)
            when {
                bar <= before.coerceIn(0, 6) -> c.drawRect(r, Paint().apply { color = Color.argb(110, 30, 30, 30) })
                bar <= after.coerceIn(0, 6) -> c.drawRect(r, Paint().apply { color = INK })
                else -> c.drawRect(r, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = INK })
            }
            x += cell + gap
        }
    }

    private fun barcode(c: Canvas, rect: RectF, seed: Long) {
        val rnd = Random(seed)
        val unit = rect.width() / 150f
        val paint = Paint().apply { color = INK }
        var x = rect.left
        while (x < rect.right) {
            val bar = (1 + rnd.nextInt(3)) * unit
            c.drawRect(x, rect.top, minOf(x + bar, rect.right), rect.bottom, paint)
            x += bar + (1 + rnd.nextInt(2)) * unit
        }
    }
}
