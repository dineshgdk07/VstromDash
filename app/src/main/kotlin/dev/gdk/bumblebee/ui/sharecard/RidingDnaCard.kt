package dev.gdk.bumblebee.ui.sharecard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** A rider's riding personality, as the share card shows it. */
data class DnaCard(
    val persona: String,
    val tagline: String,
    /** Trait name and strength, 0..1, drawn clockwise from the top. */
    val traits: List<Pair<String, Float>>,
    val signature: List<Pair<String, String>>,
)

/** A 4:5 poster: the persona, a radar "fingerprint" of the traits, and the signature numbers. */
object RidingDnaPainter {
    private const val W = 1080
    private const val H = 1350
    private const val ACCENT = 0xFFFAC816.toInt()
    private const val BASE = 0xFF0B0B0E.toInt()
    private const val MUTED = 0xFF9C9A92.toInt()

    fun paint(context: Context, card: DnaCard): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fonts = ShareText.fonts(context)
        c.drawColor(BASE)
        val cx = W / 2f
        val cy = 700f
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = RadialGradient(cx, cy, 700f, intArrayOf(0xFF2A2208.toInt(), BASE), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        })
        ShareTextures.grain(c, RectF(0f, 0f, W.toFloat(), H.toFloat()), 0.35f)

        val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f; color = MUTED; typeface = fonts.body; letterSpacing = 0.3f }
        center(c, "MY RIDING DNA", 120f, label)
        val persona = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 150f; color = ACCENT; typeface = fonts.display }
        val name = ShareText.fit(persona, card.persona.uppercase(Locale.ENGLISH), W - 120f, 80f)
        center(c, name, 270f, persona)
        val tag = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 44f; color = Color.argb(220, 255, 255, 255); typeface = fonts.body }
        center(c, ShareText.fit(tag, card.tagline, W - 160f, 28f), 340f, tag)

        radar(c, cx, cy, 250f, card.traits, fonts)

        var y = 1070f
        val key = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f; color = MUTED; typeface = fonts.body; letterSpacing = 0.2f }
        val value = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 46f; color = Color.WHITE; typeface = fonts.display }
        val cells = card.signature.take(4)
        val colW = (W - 160f) / 2f
        cells.forEachIndexed { i, (k, v) ->
            val x = 80f + (i % 2) * colW
            val rowY = y + (i / 2) * 110f
            c.drawText(k.uppercase(Locale.ENGLISH), x, rowY, key)
            c.drawText(ShareText.fit(value, v, colW - 30f, 28f), x, rowY + 52f, value)
        }
        val mark = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f; color = ACCENT; typeface = fonts.display; letterSpacing = 0.08f }
        center(c, "BUMBLEBEE", H - 50f, mark)
        return bmp
    }

    private fun radar(c: Canvas, cx: Float, cy: Float, r: Float, traits: List<Pair<String, Float>>, fonts: CardFonts) {
        val n = traits.size
        if (n < 3) return
        fun at(i: Int, scale: Float): Pair<Float, Float> {
            val a = -Math.PI / 2 + 2 * Math.PI * i / n
            return (cx + cos(a) * r * scale).toFloat() to (cy + sin(a) * r * scale).toFloat()
        }
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(60, 255, 255, 255) }
        for (ring in 1..4) {
            val p = Path()
            for (i in 0 until n) {
                val (x, y) = at(i, ring / 4f)
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            c.drawPath(p, grid)
        }
        for (i in 0 until n) {
            val (x, y) = at(i, 1f)
            c.drawLine(cx, cy, x, y, grid)
        }
        val shape = Path()
        traits.forEachIndexed { i, (_, v) ->
            val (x, y) = at(i, 0.12f + 0.88f * v.coerceIn(0f, 1f))
            if (i == 0) shape.moveTo(x, y) else shape.lineTo(x, y)
        }
        shape.close()
        c.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 250, 200, 22) })
        c.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeJoin = Paint.Join.ROUND
            color = ACCENT
            setShadowLayer(18f, 0f, 0f, Color.argb(160, 250, 160, 0))
        })
        traits.forEachIndexed { i, (_, v) ->
            val (x, y) = at(i, 0.12f + 0.88f * v.coerceIn(0f, 1f))
            c.drawCircle(x, y, 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        }
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 28f; color = Color.WHITE; typeface = fonts.body; letterSpacing = 0.12f }
        traits.forEachIndexed { i, (name, _) ->
            val (x, y) = at(i, 1.2f)
            val t = name.uppercase(Locale.ENGLISH)
            val w = text.measureText(t) - text.letterSpacing * text.textSize
            c.drawText(t, x - w / 2f, y + 10f, text)
        }
    }

    private fun center(c: Canvas, s: String, y: Float, p: TextPaint) {
        val w = p.measureText(s) - p.letterSpacing * p.textSize
        c.drawText(s, W / 2f - w / 2f, y, p)
    }
}
