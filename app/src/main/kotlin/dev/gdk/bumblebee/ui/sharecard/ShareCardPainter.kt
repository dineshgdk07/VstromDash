package dev.gdk.bumblebee.ui.sharecard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import dev.gdk.bumblebee.ride.ShareBackground
import dev.gdk.bumblebee.ride.ShareCardModel
import dev.gdk.bumblebee.ride.ShareStyle
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object ShareCardPainter {
    fun paint(context: Context, model: ShareCardModel, photo: Bitmap?, tiles: Map<dev.gdk.bumblebee.ride.TileId, Bitmap>?): Bitmap {
        val fonts = ShareCardChrome.fonts(context)
        val layout = ShareCardLayout.forShape(model.shape)
        val cover = if (model.needsTiles) {
            ShareCardMapTransform.posterCover(model.route, layout.story)
        } else {
            null
        }
        val tilesComplete = cover != null && cover.tiles().all { tiles?.containsKey(it) == true }
        val card = if (model.needsTiles) model.withTiles(tilesComplete) else model
        val bitmap = Bitmap.createBitmap(layout.width.toInt(), layout.height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val portrait = layout.story
        if (card.background != ShareBackground.TRANSPARENT) {
            canvas.drawColor(Color.rgb(8, 8, 10))
        }

        if (card.background == ShareBackground.PHOTO && photo != null) {
            drawCover(canvas, photo, layout.width, layout.height)
            ShareCardMap.dim(canvas, layout)
            ShareCardMap.fade(canvas, layout)
        } else if (card.style == ShareStyle.MAP && cover != null && tiles != null) {
            val mosaic = ShareCardMap.stitch(cover, tiles)
            val frame = ShareCardMapTransform.ofRoute(card.route, cover, layout.mapStage, portrait)
            if (mosaic != null) {
                canvas.save()
                canvas.concat(frame.matrix)
                canvas.drawBitmap(mosaic, 0f, 0f, ShareCardMap.paint())
                canvas.restore()
            }
            ShareCardMap.dim(canvas, layout)
            ShareCardMap.fade(canvas, layout)
        }

        ShareCardChrome.hero(canvas, card, fonts, layout)

        when {
            card.style == ShareStyle.CORRIDOR && card.plot.size >= 2 -> {
                val frame = ShareCardMapTransform.ofPlot(card.plot, layout.mapStage, portrait)
                ShareCardRoute.drawFaintRoad(canvas, card.plot, frame.matrix)
                ShareCardRoute.drawPlot(canvas, card.plot, frame.matrix)
            }
            card.style == ShareStyle.MAP && cover != null && card.route.size >= 2 -> {
                val frame = ShareCardMapTransform.ofRoute(card.route, cover, layout.mapStage, portrait)
                ShareCardRoute.drawMap(canvas, card.route, cover, frame.matrix)
            }
            card.plot.size >= 2 -> {
                val frame = ShareCardMapTransform.ofPlot(card.plot, layout.mapStage, portrait)
                ShareCardRoute.drawPlot(canvas, card.plot, frame.matrix)
            }
        }

        if (card.plot.size >= 2) {
            ShareCardChrome.paceKey(canvas, fonts, layout)
        }
        ShareCardChrome.brackets(canvas, layout)
        ShareCardChrome.header(canvas, card, fonts, layout)
        ShareCardBrush.of(context)?.let { ShareCardBrush.draw(canvas, layout, it) }
        ShareCardChrome.story(canvas, card, fonts, layout)
        return bitmap
    }

    fun decodePhoto(context: Context, uri: Uri, reqW: Int, reqH: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()

    fun writePng(context: Context, bitmap: Bitmap, rideId: Long): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "ride-$rideId-card.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    private fun drawCover(canvas: Canvas, photo: Bitmap, width: Float, height: Float) {
        val scale = max(width / photo.width, height / photo.height)
        val dw = photo.width * scale
        val dh = photo.height * scale
        val left = (width - dw) / 2f
        val top = (height - dh) / 2f
        canvas.drawBitmap(photo, null, RectF(left, top, left + dw, top + dh), Paint(Paint.ANTI_ALIAS_FLAG))
    }

    private fun sampleSize(width: Int, height: Int, reqW: Int, reqH: Int): Int {
        var sample = 1
        while (width / sample > reqW * 2 && height / sample > reqH * 2) sample *= 2
        return sample
    }
}
