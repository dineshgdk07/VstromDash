package dev.gdk.bumblebee.ui.sharecard

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Procedural textures. Small tiles repeat across the card, so nothing scales with card size. */
object ShareTextures {
    private const val GRAIN_TILE = 256
    private const val PAPER_TILE = 512

    private val grainTile: Bitmap by lazy { grainBitmap(GRAIN_TILE, seed = 7L) }
    private val paperTile: Bitmap by lazy { paperBitmap(PAPER_TILE, seed = 11L) }

    /** Film grain: light and dark specks over whatever is below. [strength] 0..1. */
    fun grain(canvas: Canvas, rect: RectF, strength: Float) {
        val paint = Paint().apply {
            shader = BitmapShader(grainTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            alpha = (255 * strength.coerceIn(0f, 1f)).roundToInt()
        }
        canvas.drawRect(rect, paint)
    }

    /** Cream paper with soft mottling and fine fibre. Opaque. */
    fun paper(canvas: Canvas, rect: RectF) {
        val paint = Paint().apply { shader = BitmapShader(paperTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
        canvas.drawRect(rect, paint)
    }

    /**
     * Topographic lines: a family of flowing curves that share one shape and drift apart slowly.
     * [color] carries the alpha the caller wants.
     */
    fun contours(canvas: Canvas, rect: RectF, color: Int, spacing: Float, strokeWidth: Float, seed: Long) {
        if (rect.width() <= 0f || rect.height() <= 0f || spacing <= 0f) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            this.color = color
        }
        val rnd = Random(seed)
        val f1 = 1.6f + rnd.nextFloat()
        val f2 = 3.4f + rnd.nextFloat() * 1.5f
        val p1 = rnd.nextFloat() * 6.28f
        val p2 = rnd.nextFloat() * 6.28f
        val amp = spacing * 1.6f
        val step = rect.width() / 64f
        val path = Path()
        var y = rect.top - amp
        var line = 0
        while (y < rect.bottom + amp) {
            path.reset()
            val drift = line * 0.22f
            var i = 0
            while (i <= 64) {
                val x = rect.left + i * step
                val t = (x - rect.left) / rect.width()
                val dy = amp * sin(t * f1 + p1 + drift) + amp * 0.35f * sin(t * f2 + p2 - drift * 0.6f)
                if (i == 0) path.moveTo(x, y + dy) else path.lineTo(x, y + dy)
                i++
            }
            canvas.drawPath(path, paint)
            y += spacing
            line++
        }
    }

    fun grid(canvas: Canvas, rect: RectF, step: Float, color: Int, strokeWidth: Float) {
        if (step <= 0f || rect.width() <= 0f || rect.height() <= 0f) return
        val paint = Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
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

    private fun grainBitmap(size: Int, seed: Long): Bitmap {
        val rnd = Random(seed)
        val pixels = IntArray(size * size) {
            val n = rnd.nextFloat() * 2f - 1f
            val a = (kotlin.math.abs(n) * 70f).roundToInt()
            if (n >= 0f) Color.argb(a, 255, 255, 255) else Color.argb(a, 0, 0, 0)
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }

    private fun paperBitmap(size: Int, seed: Long): Bitmap {
        val rnd = Random(seed)
        val mottle = TileNoise(size, cells = 8, seed = seed)
        val fibre = TileNoise(size, cells = 48, seed = seed + 1)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val m = mottle.at(x, y) - 0.5f
                val f = fibre.at(x, y) - 0.5f
                val speck = rnd.nextFloat() - 0.5f
                val d = m * 6f + f * 5f + speck * 6f
                pixels[y * size + x] = Color.rgb(
                    (244 + d).roundToInt().coerceIn(0, 255),
                    (236 + d).roundToInt().coerceIn(0, 255),
                    (216 + d * 1.1f).roundToInt().coerceIn(0, 255),
                )
            }
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }

    /** Value noise on a lattice that wraps, so the tile repeats without seams. 0..1. */
    private class TileNoise(private val size: Int, private val cells: Int, seed: Long) {
        private val lattice = Random(seed).let { r -> FloatArray(cells * cells) { r.nextFloat() } }

        fun at(x: Int, y: Int): Float {
            val gx = x.toFloat() * cells / size
            val gy = y.toFloat() * cells / size
            val x0 = floor(gx).toInt()
            val y0 = floor(gy).toInt()
            val tx = smooth(gx - x0)
            val ty = smooth(gy - y0)
            val a = v(x0, y0)
            val b = v(x0 + 1, y0)
            val c = v(x0, y0 + 1)
            val d = v(x0 + 1, y0 + 1)
            return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * ty
        }

        private fun v(x: Int, y: Int) = lattice[Math.floorMod(y, cells) * cells + Math.floorMod(x, cells)]

        private fun smooth(t: Float) = t * t * (3f - 2f * t)
    }
}
