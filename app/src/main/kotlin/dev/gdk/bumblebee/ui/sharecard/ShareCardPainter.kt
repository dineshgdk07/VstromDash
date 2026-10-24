package dev.gdk.bumblebee.ui.sharecard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import dev.gdk.bumblebee.ride.ShareBackground
import dev.gdk.bumblebee.ride.ShareCardModel
import dev.gdk.bumblebee.ride.ShareLook
import dev.gdk.bumblebee.ride.TileId
import dev.gdk.bumblebee.ui.sharecard.looks.CardLook
import dev.gdk.bumblebee.ui.sharecard.looks.LookContext
import dev.gdk.bumblebee.ui.sharecard.looks.PlaceholderLook
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object ShareCardPainter {
    fun paint(
        context: Context,
        model: ShareCardModel,
        photo: Bitmap?,
        tiles: Map<TileId, Bitmap>?,
        sticker: Boolean = false,
    ): Bitmap {
        val layout = ShareCardLayout.forShape(model.shape)
        val portrait = layout.height > layout.width
        val cover = if (!sticker && model.needsTiles) {
            ShareCardMapTransform.posterCover(model.route, portrait)
        } else {
            null
        }
        val tilesComplete = cover != null && cover.tiles().all { tiles?.containsKey(it) == true }
        val card = if (model.needsTiles) model.withTiles(tilesComplete) else model
        val bitmap = Bitmap.createBitmap(layout.width.toInt(), layout.height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val look = lookFor(card.look)
        val ctx = LookContext(
            context = context,
            card = card,
            layout = layout,
            fonts = ShareText.fonts(context),
            accent = card.accent.argb,
            background = card.background,
        )
        if (sticker) {
            look.drawSticker(canvas, ctx)
            return bitmap
        }
        when (card.background) {
            ShareBackground.OWN -> look.drawOwnBackground(canvas, ctx)
            ShareBackground.MAP -> drawMap(canvas, card, cover, tiles, layout, portrait, look, ctx)
            ShareBackground.PHOTO -> {
                if (photo != null) drawCover(canvas, photo, layout.width, layout.height)
                else look.drawOwnBackground(canvas, ctx)
                look.drawScrim(canvas, ctx)
            }
            ShareBackground.TRANSPARENT -> Unit
        }
        look.draw(canvas, ctx)
        return bitmap
    }

    fun lookFor(look: ShareLook): CardLook = when (look) {
        ShareLook.NIGHT_GLOW, ShareLook.POSTER, ShareLook.COCKPIT, ShareLook.RIDE_PASS -> PlaceholderLook
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

    private fun drawMap(
        canvas: Canvas,
        card: ShareCardModel,
        cover: dev.gdk.bumblebee.ride.TileCover?,
        tiles: Map<TileId, Bitmap>?,
        layout: ShareCardLayout,
        portrait: Boolean,
        look: CardLook,
        ctx: LookContext,
    ) {
        val mosaic = if (cover != null && tiles != null) ShareCardMap.stitch(cover, tiles) else null
        if (mosaic != null && cover != null && card.route.size >= 2) {
            val frame = ShareCardMapTransform.ofRoute(card.route, cover, ShareCardRoute.stage(layout), portrait)
            canvas.save()
            canvas.concat(frame.matrix)
            canvas.drawBitmap(mosaic, 0f, 0f, ShareCardMap.paint())
            canvas.restore()
            ShareCardMap.dim(canvas, layout.width, layout.height)
            ShareCardMap.fade(canvas, layout.width, layout.height)
        } else {
            look.drawOwnBackground(canvas, ctx)
        }
        look.drawScrim(canvas, ctx)
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
