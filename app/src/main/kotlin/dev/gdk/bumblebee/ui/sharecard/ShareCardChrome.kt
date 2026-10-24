package dev.gdk.bumblebee.ui.sharecard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import dev.gdk.bumblebee.R
import dev.gdk.bumblebee.ride.ShareBackground
import dev.gdk.bumblebee.ride.ShareCardModel
import dev.gdk.bumblebee.ride.ShareMetric

object ShareCardChrome {
    private const val YELLOW = 0xFFFAC816.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val MUTED = 0xFF8A8E96.toInt()

    fun fonts(context: Context) = CardFonts(
        body = ResourcesCompat.getFont(context, R.font.rajdhani_semibold) ?: Typeface.DEFAULT_BOLD,
        display = ResourcesCompat.getFont(context, R.font.anton_regular) ?: Typeface.DEFAULT_BOLD,
        script = ResourcesCompat.getFont(context, R.font.permanent_marker_regular) ?: Typeface.DEFAULT_BOLD,
    )

    fun paceKey(canvas: Canvas, fonts: CardFonts, layout: ShareCardLayout) {
        val y = 152f
        val label = text(13f, Color.argb(88, 176, 180, 188), fonts.body).apply { letterSpacing = 0.18f }
        val right = layout.width - 52f
        canvas.drawText("SLOW", right - 196f, y, label)
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 3f
        }
        val x0 = right - 148f
        val x1 = right - 58f
        for (i in 0 until 8) {
            val t = i / 7f
            bar.color = Color.argb(120, 255, (100 + 105 * t).toInt(), (20 * t).toInt())
            canvas.drawLine(x0 + (x1 - x0) * i / 8f, y - 4f, x0 + (x1 - x0) * (i + 1) / 8f - 3f, y - 4f, bar)
        }
        canvas.drawText("FAST", x1 + 8f, y, text(13f, Color.argb(120, 250, 200, 22), fonts.body).apply { letterSpacing = 0.16f })
    }

    fun hero(canvas: Canvas, card: ShareCardModel, fonts: CardFonts, layout: ShareCardLayout) {
        val stat = card.hero ?: return
        val paint = text(if (layout.story) 420f else 280f, Color.argb(22, 250, 200, 22), fonts.display).apply {
            textAlign = Paint.Align.CENTER
        }
        val y = layout.mapStage.centerY() + paint.textSize * 0.32f
        canvas.drawText(stat.heroValue, layout.width * 0.52f, y, paint)
    }

    fun brackets(canvas: Canvas, layout: ShareCardLayout) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(48, 250, 200, 22)
        }
        val arm = 20f
        val inset = 32f
        val box = RectF(inset, 122f, layout.width - inset, layout.height - inset)
        val corners = arrayOf(
            floatArrayOf(box.left, box.top, 1f, 1f),
            floatArrayOf(box.right, box.top, -1f, 1f),
            floatArrayOf(box.left, box.bottom, 1f, -1f),
            floatArrayOf(box.right, box.bottom, -1f, -1f),
        )
        corners.forEach { (x, y, dx, dy) ->
            canvas.drawLine(x, y, x + arm * dx, y, paint)
            canvas.drawLine(x, y, x, y + arm * dy, paint)
        }
    }

    fun header(canvas: Canvas, card: ShareCardModel, fonts: CardFonts, layout: ShareCardLayout) {
        val brand = text(38f, YELLOW, fonts.display).apply { letterSpacing = 0.08f }
        canvas.drawText("BUMBLEBEE", 48f, 86f, brand)
        val sub = text(18f, MUTED, fonts.body).apply { letterSpacing = 0.28f }
        canvas.drawText("RIDE", 50f, 114f, sub)
        var subX = 50f + sub.measureText("RIDE") + 16f
        if (card.demo) {
            val demo = text(18f, YELLOW, fonts.body).apply { letterSpacing = 0.2f }
            canvas.drawText("DEMO", subX, 114f, demo)
        } else if (card.bikeName.isNotBlank()) {
            canvas.drawText(card.bikeName.uppercase(), subX, 114f, sub)
        }
        val date = text(22f, WHITE, fonts.body).apply {
            textAlign = Paint.Align.RIGHT
            letterSpacing = 0.08f
        }
        canvas.drawText(card.dateText, layout.width - 48f, 74f, date)
        val whenWord = card.timeOfDay.uppercase()
        val whenPaint = text(20f, YELLOW, fonts.body).apply {
            textAlign = Paint.Align.RIGHT
            letterSpacing = 0.16f
        }
        canvas.drawText(whenWord, layout.width - 48f, 108f, whenPaint)
    }

    fun story(canvas: Canvas, card: ShareCardModel, fonts: CardFonts, layout: ShareCardLayout) {
        val onPhoto = card.background == ShareBackground.PHOTO
        val word = card.timeOfDay.uppercase()
        val title = text(if (layout.story) 122f else 78f, WHITE, fonts.display)
        while (title.measureText(word) > layout.width - 96f && title.textSize > 64f) {
            title.textSize -= 4f
        }
        val ride = text(if (layout.story) 96f else 66f, YELLOW, fonts.script)
        val body = text(if (layout.story) 32f else 24f, Color.rgb(216, 216, 220), fonts.body)
        body.isFakeBoldText = false
        if (onPhoto) {
            title.setShadowLayer(10f, 0f, 2f, Color.BLACK)
            ride.setShadowLayer(10f, 0f, 2f, Color.BLACK)
            body.setShadowLayer(8f, 0f, 2f, Color.BLACK)
        }
        val titleY = layout.titleTop + if (layout.story) 108f else 68f
        val rideY = layout.titleTop + if (layout.story) 198f else 128f
        canvas.drawText(word, 52f, titleY, title)
        canvas.drawText("RIDE", 48f, rideY, ride)
        val copy = StaticLayout.Builder.obtain(card.sentence, 0, card.sentence.length, body, (layout.width - 104f).toInt())
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.12f)
            .build()
        canvas.save()
        canvas.translate(52f, layout.sentenceTop)
        copy.draw(canvas)
        canvas.restore()
        stats(canvas, card.stats, fonts, layout, layout.statsTop, onPhoto)
    }

    private fun stats(
        canvas: Canvas,
        stats: List<ShareMetric>,
        fonts: CardFonts,
        layout: ShareCardLayout,
        top: Float,
        onPhoto: Boolean,
    ) {
        if (stats.isEmpty()) return
        val value = text(if (layout.story) 86f else 54f, WHITE, fonts.display)
        val caption = text(17f, MUTED, fonts.body).apply { letterSpacing = 0.22f }
        if (onPhoto) {
            value.setShadowLayer(8f, 0f, 2f, Color.BLACK)
            caption.setShadowLayer(6f, 0f, 2f, Color.BLACK)
        }
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(64, 250, 200, 22)
            strokeWidth = 2f
        }
        val faces = stats.map { Face(it.value, it.unitLabel) }
        val slot = (layout.width - 96f) / faces.size
        faces.forEachIndexed { i, face ->
            val cx = 48f + slot * i + slot / 2f
            val vw = value.measureText(face.value)
            canvas.drawText(face.value, cx - vw / 2f, top + 78f, value)
            val cw = caption.measureText(face.caption)
            canvas.drawText(face.caption, cx - cw / 2f, top + 118f, caption)
            if (i < faces.lastIndex) {
                val x = 48f + slot * (i + 1)
                canvas.drawLine(x, top + 12f, x, top + 118f, rule)
            }
        }
    }

    private fun text(size: Float, color: Int, face: Typeface) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = face
    }

    private data class Face(val value: String, val caption: String)
}

data class CardFonts(val body: Typeface, val display: Typeface, val script: Typeface)
