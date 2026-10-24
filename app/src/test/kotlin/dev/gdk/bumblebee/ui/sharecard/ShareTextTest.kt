package dev.gdk.bumblebee.ui.sharecard

import android.text.TextPaint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareTextTest {
    @Test fun `long wide name shrinks then ellipsizes within width`() {
        val p = TextPaint().apply { textSize = 120f }
        val out = ShareText.fit(p, "W".repeat(30), maxWidth = 900f, minSize = 48f)
        assertTrue(p.measureText(out) <= 900f)
        assertEquals(48f, p.textSize)
        assertTrue(out.endsWith("…"))
    }

    @Test fun `short text keeps its size`() {
        val p = TextPaint().apply { textSize = 120f }
        assertEquals("NANDI", ShareText.fit(p, "NANDI", 900f, 48f))
        assertEquals(120f, p.textSize)
    }
}
