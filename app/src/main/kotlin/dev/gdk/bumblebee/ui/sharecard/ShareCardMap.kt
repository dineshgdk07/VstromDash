package dev.gdk.bumblebee.ui.sharecard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import dev.gdk.bumblebee.ride.TileCover
import dev.gdk.bumblebee.ride.TileId

object ShareCardMap {
    fun stitch(cover: TileCover, tiles: Map<TileId, Bitmap>): Bitmap? {
        if (cover.tiles().any { tiles[it] == null }) return null
        val mosaic = Bitmap.createBitmap(cover.cols * 256, cover.rows * 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mosaic)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        for (id in cover.tiles()) {
            val bmp = tiles[id] ?: return null
            val left = (id.x - cover.x0) * 256f
            val top = (id.y - cover.y0) * 256f
            canvas.drawBitmap(bmp, null, RectF(left, top, left + 256f, top + 256f), paint)
        }
        return mosaic
    }

    fun paint(): Paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = cinematic
    }

    /** Near-black / blue-gray. Labels become texture. No purple. */
    private val cinematic = ColorMatrixColorFilter(
        ColorMatrix().apply {
            setSaturation(0.07f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        0.24f, 0f, 0f, 0f, -28f,
                        0f, 0.25f, 0f, 0f, -26f,
                        0f, 0f, 0.28f, 0f, -22f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        0.82f, 0f, 0f, 0f, 8f,
                        0f, 0.82f, 0f, 0f, 8f,
                        0f, 0f, 0.82f, 0f, 8f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        },
    )

    fun dim(canvas: Canvas, width: Float, height: Float) {
        canvas.drawColor(Color.argb(62, 8, 8, 10))
        val header = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, 240f,
                intArrayOf(Color.argb(132, 8, 8, 10), Color.argb(48, 8, 8, 10), Color.TRANSPARENT),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width, (height * 0.16f).coerceAtMost(240f), header)
    }

    fun fade(canvas: Canvas, width: Float, height: Float) {
        val top = height * 0.55f
        val paint = Paint().apply {
            shader = LinearGradient(
                0f,
                top,
                0f,
                height,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(110, 8, 8, 10),
                    Color.argb(210, 8, 8, 10),
                    Color.rgb(8, 8, 10),
                ),
                floatArrayOf(0f, 0.32f, 0.68f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, top, width, height, paint)
    }
}
