package dev.gdk.bumblebee.ui.sharecard.looks

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import dev.gdk.bumblebee.ride.ShareBackground
import dev.gdk.bumblebee.ride.ShareCardModel
import dev.gdk.bumblebee.ride.ShareShape
import dev.gdk.bumblebee.ui.sharecard.CardFonts
import dev.gdk.bumblebee.ui.sharecard.RouteStyle
import dev.gdk.bumblebee.ui.sharecard.ShareCardLayout
import dev.gdk.bumblebee.ui.sharecard.ShareCardMapTransform
import dev.gdk.bumblebee.ui.sharecard.ShareCardRoute
import dev.gdk.bumblebee.ui.sharecard.ShareText

class LookContext(
    val context: Context,
    val card: ShareCardModel,
    val layout: ShareCardLayout,
    val fonts: CardFonts,
    val accent: Int,
    val background: ShareBackground,
)

interface CardLook {
    fun drawOwnBackground(canvas: Canvas, ctx: LookContext)
    fun drawScrim(canvas: Canvas, ctx: LookContext)
    fun draw(canvas: Canvas, ctx: LookContext)
    fun drawSticker(canvas: Canvas, ctx: LookContext)
}

/** Keeps the share screen painting until the four looks land. Route and headline only. */
object PlaceholderLook : CardLook {
    override fun drawOwnBackground(canvas: Canvas, ctx: LookContext) {
        canvas.drawColor(Color.rgb(8, 8, 10))
    }

    override fun drawScrim(canvas: Canvas, ctx: LookContext) {
        val top = ctx.layout.height * 0.55f
        val paint = Paint().apply { color = Color.argb(150, 0, 0, 0) }
        canvas.drawRect(0f, top, ctx.layout.width, ctx.layout.height, paint)
    }

    override fun draw(canvas: Canvas, ctx: LookContext) {
        drawRoute(canvas, ctx)
        val min = if (ctx.layout.shape == ShareShape.STORY) 48f else 40f
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = ctx.layout.width * 0.09f
            typeface = ctx.fonts.display
        }
        val headline = ShareText.fit(paint, ctx.card.headline, ctx.layout.width - 96f, min)
        canvas.drawText(headline, 48f, ctx.layout.height * 0.84f, paint)
    }

    override fun drawSticker(canvas: Canvas, ctx: LookContext) {
        drawRoute(canvas, ctx)
    }

    private fun drawRoute(canvas: Canvas, ctx: LookContext) {
        val stage = ShareCardRoute.stage(ctx.layout)
        val portrait = ctx.layout.height > ctx.layout.width
        val cover = if (ctx.background == ShareBackground.MAP) {
            ShareCardMapTransform.posterCover(ctx.card.route, portrait)
        } else {
            null
        }
        val points = ShareCardRoute.project(ctx.card, cover, stage)
        ShareCardRoute.drawRoute(canvas, points, ctx.accent, RouteStyle.GLOW)
    }
}
