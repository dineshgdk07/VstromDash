package dev.gdk.bumblebee.ui.sharecard

import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import dev.gdk.bumblebee.ride.ShareBackground
import dev.gdk.bumblebee.ride.ShareExtras
import dev.gdk.bumblebee.ride.ShareLook
import dev.gdk.bumblebee.ride.ShareShape
import org.junit.Assert.assertEquals
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
}
