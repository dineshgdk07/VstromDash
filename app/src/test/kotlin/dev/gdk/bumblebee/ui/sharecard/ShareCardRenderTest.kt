package dev.gdk.bumblebee.ui.sharecard

import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import dev.gdk.bumblebee.ride.RideInsightInput
import dev.gdk.bumblebee.ride.ShareBackground
import dev.gdk.bumblebee.ride.ShareCard
import dev.gdk.bumblebee.ride.ShareShape
import dev.gdk.bumblebee.ride.ShareSwitches
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneOffset

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

    private fun card(shape: ShareShape = ShareShape.STORY, background: ShareBackground = ShareBackground.OWN) =
        ShareCard.of(
            RideInsightInput(
                startTs = 18L * 3_600_000,
                endTs = 20L * 3_600_000,
                km = 12.0,
                movingSec = 600,
                idleSec = 0,
                maxSpeedKmh = 72,
                avgMovingSpeedKmh = 40.0,
            ),
            switches = ShareSwitches(shape = shape, background = background),
            zone = ZoneOffset.UTC,
        )
}
