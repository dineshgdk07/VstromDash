package dev.gdk.bumblebee.ui.sharecard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.RideInsightInput
import dev.gdk.bumblebee.ride.ShareCard
import dev.gdk.bumblebee.ride.ShareShape
import dev.gdk.bumblebee.ride.ShareStyle
import dev.gdk.bumblebee.ride.ShareSwitches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareCardRenderTest {
    @Test
    fun `story poster fills the canvas and rotates a wide route`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val track = eastbound(58_000.0)
        val card = ShareCard.of(
            ride = RideInsightInput(
                startTs = 21L * 3600_000,
                endTs = 4L * 3600_000,
                km = 58.0,
                movingSec = 53 * 60,
                idleSec = 0,
                maxSpeedKmh = 148,
                avgMovingSpeedKmh = 66.0,
            ),
            track = track,
            switches = ShareSwitches(shape = ShareShape.STORY, style = ShareStyle.MAP),
            climbM = 262.0,
            zone = ZoneOffset.UTC,
        )
        val cover = ShareCardMapTransform.posterCover(card.route, true)!!
        val layout = ShareCardLayout.forShape(ShareShape.STORY)
        val frame = ShareCardMapTransform.ofRoute(card.route, cover, layout.mapStage, true)
        assertTrue(kotlin.math.abs(frame.rotateDeg) >= 90f - 0.01f)
        val mapped = android.graphics.RectF()
        frame.matrix.mapRect(mapped, android.graphics.RectF(0f, 0f, frame.mosaicW, frame.mosaicH))
        assertTrue("map must cover left edge", mapped.left <= 1f)
        assertTrue("map must cover right edge", mapped.right >= layout.width - 1f)

        val tiles = cover.tiles().associateWith { fakeTile() }
        val bitmap = ShareCardPainter.paint(context, card, photo = null, tiles = tiles)
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
        val out = File("../screens/share-card-story-actual.png").canonicalFile
        out.parentFile?.mkdirs()
        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue(pixels.any { Color.alpha(it) > 200 && Color.red(it) + Color.green(it) + Color.blue(it) > 40 })
    }

    private fun fakeTile(): Bitmap {
        val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(150, 158, 172))
        val paint = Paint().apply {
            color = Color.rgb(62, 68, 78)
            strokeWidth = 3f
        }
        for (i in 0..8) {
            canvas.drawLine(0f, i * 32f, 256f, i * 32f, paint)
            canvas.drawLine(i * 32f, 0f, i * 32f, 256f, paint)
        }
        return bmp
    }

    /** Roughly [meters] of eastbound travel with a north wobble, plus 1 km privacy tails. */
    private fun eastbound(meters: Double): List<GpsFix> {
        val total = meters + 1_200.0
        val steps = 80
        return List(steps) { i ->
            val t = i / (steps - 1).toFloat()
            val eastM = total * t
            val northM = kotlin.math.sin(t * 12.0) * 900.0 + t * 400.0
            GpsFix(
                ts = i * 1_000L,
                lat = 12.97 + northM / 111_320.0,
                lng = 77.59 + eastM / (111_320.0 * kotlin.math.cos(Math.toRadians(12.97))),
                speedMps = 12f + 8f * t,
            )
        }
    }
}
