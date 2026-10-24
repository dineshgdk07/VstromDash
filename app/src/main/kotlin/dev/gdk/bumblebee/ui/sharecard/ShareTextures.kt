package dev.gdk.bumblebee.ui.sharecard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.sin
import kotlin.random.Random

object ShareTextures {
    private val grainCache = HashMap<String, Bitmap>()
    private val paperCache = HashMap<String, Bitmap>()

    /** Cached procedural noise. The same size and seed returns the same bitmap. */
    fun grain(width: Int, height: Int, seed: Long = 7L): Bitmap {
        val key = "$width×$height×$seed"
        grainCache[key]?.let { return it }
        val bmp = noise(width, height, seed, base = 0, alpha = 255)
        grainCache[key] = bmp
        return bmp
    }

    fun paper(width: Int, height: Int): Bitmap {
        val key = "$width×$height"
        paperCache[key]?.let { return it }
        val bmp = noise(width, height, seed = 11L, base = Color.rgb(244, 236, 216), alpha = 255)
        paperCache[key] = bmp
        return bmp
    }

    /** Offset sine bands. [color] should already carry the alpha the caller wants. */
    fun contours(canvas: Canvas, rect: RectF, color: Int, seed: Long) {
        if (rect.width() <= 0f || rect.height() <= 0f) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
            this.color = color
        }
        val rnd = Random(seed)
        val path = Path()
        var y = rect.top + 24f
        var band = 0
        while (y < rect.bottom) {
            path.reset()
            val amp = 6f + (band % 4) * 3f
            val phase = rnd.nextFloat() * 6.2f
            var x = rect.left
            path.moveTo(x, y)
            while (x <= rect.right) {
                path.lineTo(x, y + amp * sin(x / 72f + phase + band * 0.4f).toFloat())
                x += 10f
            }
            canvas.drawPath(path, paint)
            y += 26f + (band % 3) * 4f
            band++
        }
    }

    fun grid(canvas: Canvas, rect: RectF, step: Float, color: Int) {
        if (step <= 0f || rect.width() <= 0f || rect.height() <= 0f) return
        val paint = Paint().apply {
            this.color = color
            strokeWidth = 1f
        }
        var x = rect.left
        while (x <= rect.right) {
            canvas.drawLine(x, rect.top, x, rect.bottom, paint)
            x += step
        }
        var y = rect.top
        while (y <= rect.bottom) {
            canvas.drawLine(rect.left, y, rect.right, y, paint)
            y += step
        }
    }

    private fun noise(width: Int, height: Int, seed: Long, base: Int, alpha: Int): Bitmap {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val rnd = Random(seed)
        val pixels = IntArray(w * h)
        val br = Color.red(base)
        val bg = Color.green(base)
        val bb = Color.blue(base)
        for (i in pixels.indices) {
            val n = rnd.nextInt(48) - 24
            pixels[i] = Color.argb(
                alpha,
                (br + n).coerceIn(0, 255),
                (bg + n).coerceIn(0, 255),
                (bb + n).coerceIn(0, 255),
            )
        }
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        return bmp
    }
}
