package dev.gdk.bumblebee.ui.sharecard.looks

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.Highlight
import dev.gdk.bumblebee.ride.HighlightKind
import dev.gdk.bumblebee.ride.ShareBackground
import dev.gdk.bumblebee.ride.ShareExtras
import dev.gdk.bumblebee.ride.ShareLook
import dev.gdk.bumblebee.ride.ShareShape
import dev.gdk.bumblebee.ui.sharecard.ShareCardPainter
import dev.gdk.bumblebee.ui.sharecard.bandHasInk
import dev.gdk.bumblebee.ui.sharecard.card
import dev.gdk.bumblebee.ui.sharecard.solid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NightGlowTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun `renders every shape and background without throwing`() =
        ShareShape.values().forEach { shape ->
            ShareBackground.values().forEach { bg ->
                ShareCardPainter.paint(
                    ctx,
                    card(look = ShareLook.NIGHT_GLOW, shape = shape, background = bg),
                    photo = solid(Color.WHITE),
                    tiles = null,
                )
            }
        }

    @Test fun `no route still draws headline area`() {
        val bmp = ShareCardPainter.paint(ctx, card(look = ShareLook.NIGHT_GLOW, track = emptyList()), null, null)
        assertTrue(bandHasInk(bmp, HEADLINE_BAND))
    }

    @Test fun `own background is opaque`() =
        assertEquals(255, Color.alpha(ShareCardPainter.paint(ctx, card(look = ShareLook.NIGHT_GLOW), null, null).getPixel(2, 2)))

    @Test fun `writes a preview png per shape when asked`() {
        if (System.getProperty("share.preview") != "true") return
        val dir = File("build/share-preview").apply { mkdirs() }
        val sample = card(
            look = ShareLook.NIGHT_GLOW,
            track = previewTrack(),
            extras = ShareExtras(
                rideName = "Nandi dusk",
                startPlace = "Bengaluru",
                endPlace = "Nandi Hills",
                highlight = Highlight(HighlightKind.TOP_SPEED, "New top speed", "New top speed: 96 km/h"),
                weatherLine = "22°C · Clear Sky · evening ride",
            ),
        )
        ShareShape.values().forEach { shape ->
            val bmp = ShareCardPainter.paint(ctx, sample.copy(shape = shape), null, null)
            FileOutputStream(File(dir, "night-glow-${shape.name.lowercase()}.png")).use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    private fun previewTrack(): List<GpsFix> {
        val start = 18L * 3_600_000
        return List(24) { i ->
            val t = i / 23.0
            GpsFix(
                ts = start + (t * 3_600_000).toLong(),
                lat = 12.97 + t * 0.08,
                lng = 77.59 + t * 0.04,
                altitudeM = 900 + t * 280 + if (i % 3 == 0) 20 else 0,
                speedMps = 8f + i,
            )
        }
    }

    companion object {
        /** Story headline band. Night Glow sets the baseline near y = 1460. */
        val HEADLINE_BAND = Rect(60, 1320, 1020, 1500)
    }
}
