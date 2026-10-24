package dev.gdk.bumblebee.ui.sharecard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.abs
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.RideInsightInput
import dev.gdk.bumblebee.ride.ShareBackground
import dev.gdk.bumblebee.ride.ShareCard
import dev.gdk.bumblebee.ride.ShareCardModel
import dev.gdk.bumblebee.ride.ShareExtras
import dev.gdk.bumblebee.ride.ShareLook
import dev.gdk.bumblebee.ride.ShareShape
import dev.gdk.bumblebee.ride.ShareSwitches
import java.time.ZoneOffset

fun card(
    look: ShareLook = ShareLook.NIGHT_GLOW,
    shape: ShareShape = ShareShape.STORY,
    background: ShareBackground = ShareBackground.OWN,
    track: List<GpsFix> = emptyList(),
    extras: ShareExtras = ShareExtras(),
    climbM: Double? = null,
): ShareCardModel = ShareCard.of(
    RideInsightInput(
        startTs = 18L * 3_600_000,
        endTs = 20L * 3_600_000,
        km = 42.0,
        movingSec = 5_400,
        idleSec = 120,
        maxSpeedKmh = 96,
        avgMovingSpeedKmh = 48.0,
    ),
    track = track,
    switches = ShareSwitches(look = look, shape = shape, background = background),
    extras = extras,
    climbM = climbM,
    zone = ZoneOffset.UTC,
)

fun solid(color: Int): Bitmap =
    Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { Canvas(it).drawColor(color) }

/** True when the band contains pixels that are not the card's corner colour. */
fun bandHasInk(bmp: Bitmap, band: Rect): Boolean {
    val bg = bmp.getPixel(2, 2)
    var y = band.top
    while (y < band.bottom) {
        var x = band.left
        while (x < band.right) {
            val c = bmp.getPixel(x.coerceIn(0, bmp.width - 1), y.coerceIn(0, bmp.height - 1))
            val delta = abs(Color.alpha(c) - Color.alpha(bg)) +
                abs(Color.red(c) - Color.red(bg)) +
                abs(Color.green(c) - Color.green(bg)) +
                abs(Color.blue(c) - Color.blue(bg))
            if (delta > 140) return true
            x += 4
        }
        y += 4
    }
    return false
}
