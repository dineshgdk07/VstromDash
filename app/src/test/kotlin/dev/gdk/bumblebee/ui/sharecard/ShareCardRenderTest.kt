package dev.gdk.bumblebee.ui.sharecard

import android.graphics.Color
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import dev.gdk.bumblebee.ride.ShareBackground
import dev.gdk.bumblebee.ride.ShareExtras
import dev.gdk.bumblebee.ride.ShareLook
import dev.gdk.bumblebee.ride.SharePlot
import dev.gdk.bumblebee.ride.ShareShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareCardRenderTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun `every shape renders at its exact size`() =
        ShareShape.values().forEach { shape ->
            val bmp = ShareCardPainter.paint(ctx, card(shape = shape), null, null)
            val l = ShareCardLayout.forShape(shape)
            assertEquals(l.width.toInt(), bmp.width)
            assertEquals(l.height.toInt(), bmp.height)
        }

    @Test fun `transparent leaves corners clear`() =
        assertEquals(
            0,
            Color.alpha(
                ShareCardPainter.paint(ctx, card(background = ShareBackground.TRANSPARENT), null, null).getPixel(2, 2),
            ),
        )

    @Test fun `every look shape and background survives a long name and an empty track`() {
        val photo = solid(Color.WHITE)
        val name = "W".repeat(30)
        ShareLook.values().forEach { look ->
            ShareShape.values().forEach { shape ->
                ShareBackground.values().forEach { background ->
                    ShareCardPainter.paint(ctx, card(look, shape, background, extras = ShareExtras(rideName = name)), photo, null)
                    ShareCardPainter.paint(ctx, card(look, shape, background, track = emptyList()), null, null)
                }
            }
        }
    }

    @Test fun `a tall bare route stays inside its stage`() {
        val plot = listOf(SharePlot(0.45f, 0f, 0f), SharePlot(0.55f, 1f, 0f))
        val stage = RectF(48f, 200f, 1032f, 900f)
        val pts = floatArrayOf(450f, 0f, 550f, 1000f)
        ShareCardMapTransform.ofPlot(plot, stage, portrait = true).matrix.mapPoints(pts)
        assertTrue("top ${pts[1]}", pts[1] >= stage.top)
        assertTrue("bottom ${pts[3]}", pts[3] <= stage.bottom)
    }
}
