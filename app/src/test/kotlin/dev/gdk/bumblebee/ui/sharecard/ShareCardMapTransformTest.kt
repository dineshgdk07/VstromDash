package dev.gdk.bumblebee.ui.sharecard

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareCardMapTransformTest {
    @Test
    fun `story rotates a wide route onto the vertical axis`() {
        assertEquals(90f, ShareCardMapTransform.rotateDegrees(400f, 80f, startOnMinSide = true, portrait = true))
        assertEquals(-90f, ShareCardMapTransform.rotateDegrees(400f, 80f, startOnMinSide = false, portrait = true))
    }

    @Test
    fun `a tall route on story stays unrotated`() {
        assertEquals(0f, ShareCardMapTransform.rotateDegrees(80f, 400f, startOnMinSide = true, portrait = true))
    }

    @Test
    fun `a square-ish route is not forced to rotate`() {
        assertEquals(0f, ShareCardMapTransform.rotateDegrees(200f, 190f, startOnMinSide = true, portrait = true))
    }
}
