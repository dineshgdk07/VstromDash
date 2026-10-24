package dev.gdk.bumblebee.ui.sharecard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import java.util.Locale

/** One visit on the service history card. */
data class HistoryLine(val date: String, val item: String, val detail: String)

/** The bike's care record, for a buyer or a friend. */
data class ServiceHistoryCard(
    val bike: String,
    val odoKm: Int?,
    val since: String?,
    val services: Int,
    val careRupees: Double,
    val lines: List<HistoryLine>,
)

/** One number on the year card, with its change from last year. */
data class YearStat(val label: String, val value: String, val delta: String?, val good: Boolean?)

data class GarageYearCard(val year: Int, val headline: String, val stats: List<YearStat>)

private const val W = 1080
private const val H = 1350
private const val ACCENT = 0xFFFAC816.toInt()
private const val LIME = 0xFFA6FF6A.toInt()
private const val CORAL = 0xFFE17A57.toInt()
private const val BASE = 0xFF0E0E11.toInt()
private const val MUTED = 0xFF9C9A92.toInt()

private fun text(size: Float, color: Int, face: android.graphics.Typeface, spacing: Float = 0f) =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = face
        letterSpacing = spacing
    }

private fun backdrop(c: Canvas, glow: Int) {
    c.drawColor(BASE)
    c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, H.toFloat(), intArrayOf(glow, BASE, BASE), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    })
    ShareTextures.grain(c, RectF(0f, 0f, W.toFloat(), H.toFloat()), 0.3f)
}

private fun widthOf(p: TextPaint, s: String) = p.measureText(s) - p.letterSpacing * p.textSize

/** A dark 4:5 card: the bike, its odometer, and a timeline of its recent care. */
object ServiceHistoryPainter {
    fun paint(context: Context, card: ServiceHistoryCard): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fonts = ShareText.fonts(context)
        backdrop(c, 0xFF1F2A12.toInt())
        val left = 90f

        c.drawText("SERVICE HISTORY", left, 130f, text(30f, LIME, fonts.body, 0.3f))
        val name = text(96f, Color.WHITE, fonts.display)
        c.drawText(ShareText.fit(name, card.bike.uppercase(Locale.ENGLISH), W - 180f, 60f), left, 235f, name)

        val big = text(130f, ACCENT, fonts.display)
        val odo = card.odoKm?.let { "%,d".format(Locale.ENGLISH, it) } ?: "—"
        c.drawText(odo, left, 400f, big)
        c.drawText("KM", left + widthOf(big, odo) + 18f, 400f, text(40f, MUTED, fonts.body, 0.2f))

        val key = text(24f, MUTED, fonts.body, 0.2f)
        val value = text(44f, Color.WHITE, fonts.display)
        val cells = listOfNotNull(
            "SERVICES" to "${card.services}",
            ("ON CARE" to "₹%,.0f".format(Locale.ENGLISH, card.careRupees)).takeIf { card.careRupees > 0 },
            card.since?.let { "LOGGED SINCE" to it },
        )
        val colW = (W - 2 * left) / 3f
        cells.forEachIndexed { i, (k, v) ->
            val x = left + i * colW
            value.textSize = 44f
            c.drawText(k, x, 480f, key)
            c.drawText(ShareText.fit(value, v, colW - 20f, 28f), x, 532f, value)
        }
        c.drawRect(left, 580f, W - left, 582f, Paint().apply { color = Color.argb(50, 255, 255, 255) })

        val lines = card.lines.take(7)
        if (lines.isEmpty()) {
            c.drawText("No service logged yet", left, 680f, text(40f, MUTED, fonts.body))
        }
        val rowH = 82f
        val top = 650f
        val dotX = left + 10f
        if (lines.size > 1) {
            c.drawRect(dotX - 2f, top, dotX + 2f, top + (lines.size - 1) * rowH, Paint().apply { color = Color.argb(90, 166, 255, 106) })
        }
        val date = text(24f, MUTED, fonts.body, 0.12f)
        val item = text(36f, Color.WHITE, fonts.body)
        val detail = text(26f, MUTED, fonts.body)
        lines.forEachIndexed { i, l ->
            val y = top + i * rowH
            item.textSize = 36f
            detail.textSize = 26f
            c.drawCircle(dotX, y, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (i == 0) LIME else Color.argb(255, 60, 70, 50) })
            c.drawCircle(dotX, y, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BASE })
            c.drawText(l.date.uppercase(Locale.ENGLISH), left + 50f, y - 14f, date)
            val d = ShareText.fit(detail, l.detail, 330f, 20f)
            val dw = widthOf(detail, d)
            c.drawText(d, W - left - dw, y + 26f, detail)
            val itemText = ShareText.fit(item, l.item, W - 2 * left - 50f - dw - 30f, 24f)
            c.drawText(itemText, left + 50f, y + 26f, item)
        }
        if (card.services > lines.size) {
            c.drawText("+ ${card.services - lines.size} earlier", left + 50f, top + lines.size * rowH + 10f, text(26f, MUTED, fonts.body))
        }
        val mark = text(34f, ACCENT, fonts.display, 0.08f)
        c.drawText("BUMBLEBEE", W / 2f - widthOf(mark, "BUMBLEBEE") / 2f, H - 50f, mark)
        return bmp
    }
}

/** A dark 4:5 card: the garage year in six numbers, each against last year. */
object GarageYearPainter {
    fun paint(context: Context, card: GarageYearCard): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fonts = ShareText.fonts(context)
        backdrop(c, 0xFF2A2208.toInt())
        val left = 90f

        c.drawText("MY GARAGE YEAR", left, 130f, text(30f, ACCENT, fonts.body, 0.3f))
        val year = text(300f, Color.WHITE, fonts.display)
        c.drawText("${card.year}", left - 10f, 425f, year)
        val head = text(40f, Color.argb(220, 255, 255, 255), fonts.body)
        c.drawText(ShareText.fit(head, card.headline, W - 2 * left, 26f), left, 500f, head)

        val stats = card.stats.take(6)
        val colW = (W - 2 * left) / 2f
        val rowH = 220f
        val top = 590f
        val key = text(26f, MUTED, fonts.body, 0.2f)
        val value = text(76f, Color.WHITE, fonts.display)
        val delta = text(28f, MUTED, fonts.body)
        stats.forEachIndexed { i, s ->
            val x = left + (i % 2) * colW
            val y = top + (i / 2) * rowH
            value.textSize = 76f
            c.drawRoundRect(RectF(x, y, x + colW - 24f, y + rowH - 24f), 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(22, 255, 255, 255) })
            c.drawText(s.label.uppercase(Locale.ENGLISH), x + 28f, y + 52f, key)
            c.drawText(ShareText.fit(value, s.value, colW - 80f, 40f), x + 28f, y + 132f, value)
            s.delta?.let { d ->
                delta.color = when (s.good) {
                    true -> LIME
                    false -> CORAL
                    null -> MUTED
                }
                c.drawText(d, x + 28f, y + 176f, delta)
            }
        }
        val mark = text(34f, ACCENT, fonts.display, 0.08f)
        c.drawText("BUMBLEBEE", W / 2f - widthOf(mark, "BUMBLEBEE") / 2f, H - 50f, mark)
        return bmp
    }
}
