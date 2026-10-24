package dev.gdk.bumblebee.ui.sharecard

import dev.gdk.bumblebee.ride.ShareShape

/** Pixel size of a share card. Looks place type and the route from width and height. */
data class ShareCardLayout(
    val width: Float,
    val height: Float,
) {
    val shape: ShareShape
        get() = when {
            height >= 1600f -> ShareShape.STORY
            height >= 1200f -> ShareShape.FEED
            else -> ShareShape.SQUARE
        }

    companion object {
        fun forShape(shape: ShareShape): ShareCardLayout = when (shape) {
            ShareShape.STORY -> ShareCardLayout(1080f, 1920f)
            ShareShape.FEED -> ShareCardLayout(1080f, 1350f)
            ShareShape.SQUARE -> ShareCardLayout(1080f, 1080f)
        }
    }
}
