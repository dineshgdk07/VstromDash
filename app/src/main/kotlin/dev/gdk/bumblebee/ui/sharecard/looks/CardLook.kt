package dev.gdk.bumblebee.ui.sharecard.looks

import android.content.Context
import android.graphics.Canvas
import dev.gdk.bumblebee.ride.ShareBackground
import dev.gdk.bumblebee.ride.ShareCardModel
import dev.gdk.bumblebee.ui.sharecard.CardFonts
import dev.gdk.bumblebee.ui.sharecard.ShareCardLayout

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
