package dev.gdk.bumblebee.ui.sharecard

import android.graphics.RectF
import dev.gdk.bumblebee.ride.ShareShape

/** Positions in a 1080-wide design space. Story is 1920 tall. Square is its own stack. */
data class ShareCardLayout(
    val width: Float,
    val height: Float,
    val mapStage: RectF,
    val titleTop: Float,
    val gradientTop: Float,
    val sentenceTop: Float,
    val statsTop: Float,
) {
    val story: Boolean get() = height > 1400f

    companion object {
        fun forShape(shape: ShareShape): ShareCardLayout = if (shape == ShareShape.STORY) {
            ShareCardLayout(
                width = 1080f,
                height = 1920f,
                mapStage = RectF(0f, 0f, 1080f, 1290f),
                titleTop = 1240f,
                gradientTop = 1000f,
                sentenceTop = 1504f,
                statsTop = 1592f,
            )
        } else {
            ShareCardLayout(
                width = 1080f,
                height = 1080f,
                mapStage = RectF(0f, 0f, 1080f, 760f),
                titleTop = 628f,
                gradientTop = 500f,
                sentenceTop = 804f,
                statsTop = 900f,
            )
        }
    }
}
