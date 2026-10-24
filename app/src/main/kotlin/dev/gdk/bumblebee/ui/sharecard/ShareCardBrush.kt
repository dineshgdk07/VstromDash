package dev.gdk.bumblebee.ui.sharecard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import dev.gdk.bumblebee.R

/** Cached yellow brush-stroke PNG. Drawn behind the title, never generated. */
object ShareCardBrush {
    @Volatile
    private var cached: Bitmap? = null

    fun of(context: Context): Bitmap? {
        cached?.takeUnless { it.isRecycled }?.let { return it }
        return BitmapFactory.decodeResource(context.resources, R.drawable.share_card_brush)?.also {
            cached = it
        }
    }

    fun draw(canvas: Canvas, layout: ShareCardLayout, bitmap: Bitmap) {
        val targetW = if (layout.story) 420f else 340f
        val scale = targetW / bitmap.width
        val dw = bitmap.width * scale
        val dh = bitmap.height * scale
        val left = if (layout.story) -80f else -60f
        val top = layout.titleTop + if (layout.story) 88f else 32f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = 184
        }
        canvas.save()
        canvas.rotate(if (layout.story) -7f else -5f, left + dw * 0.20f, top + dh * 0.55f)
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + dw, top + dh), paint)
        canvas.restore()
    }
}
