package dev.gdk.bumblebee.ui.sharecard

import kotlin.math.max

/**
 * How the rider framed their photo behind a card: [zoom] past the fill-the-card size (1 = just
 * covers it), and [x], [y] from -1 to 1 for how far it's slid toward each edge of the spare
 * picture (0 is centred). Kept as fractions so the same framing fits every card size.
 */
data class PhotoFrame(val zoom: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    /**
     * Where the photo lands on a [width]×[height] card: left, top, right, bottom. It always
     * covers the card, so no edge ever shows.
     */
    fun rect(photoW: Int, photoH: Int, width: Float, height: Float): FloatArray {
        val scale = max(width / photoW, height / photoH) * zoom
        val dw = photoW * scale
        val dh = photoH * scale
        val left = (width - dw) / 2f - x * (dw - width) / 2f
        val top = (height - dh) / 2f - y * (dh - height) / 2f
        return floatArrayOf(left, top, left + dw, top + dh)
    }

    /**
     * This frame after a pinch by [zoomBy] and a drag of [dx], [dy] card pixels. A drag moves
     * the photo with the finger; the photo stays covering the card.
     */
    fun moved(photoW: Int, photoH: Int, width: Float, height: Float, zoomBy: Float, dx: Float, dy: Float): PhotoFrame {
        val z = (zoom * zoomBy).coerceIn(1f, MAX_ZOOM)
        val scale = max(width / photoW, height / photoH) * z
        val spareX = (photoW * scale - width) / 2f
        val spareY = (photoH * scale - height) / 2f
        val nx = if (spareX < 0.5f) 0f else (x - dx / spareX).coerceIn(-1f, 1f)
        val ny = if (spareY < 0.5f) 0f else (y - dy / spareY).coerceIn(-1f, 1f)
        return PhotoFrame(z, nx, ny)
    }

    companion object {
        const val MAX_ZOOM = 4f
    }
}
