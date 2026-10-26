package dev.gdk.bumblebee.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoConfigTest {
    @Test fun `round trips through its text form`() {
        val c = VideoConfig(
            preset = VideoPreset.CUSTOM, shape = VideoShape.SQUARE, lengthS = 45, title = "Top Station; run=1",
            hud = false, cameraDistance = 1.4, exaggeration = 2.2, glow = 0.3, grain = false,
        )
        val back = VideoConfig.decode(c.encode())
        assertEquals(c.copy(title = "Top Station, run-1"), back)
    }

    @Test fun `bad or missing values keep defaults and ranges hold`() {
        val d = VideoConfig.decode("glow=99;camera=abc;exag=;shape=TALL;hud=maybe;length=500")
        assertEquals(2.0, d.glow, 0.0)
        assertEquals(1.0, d.cameraDistance, 0.0)
        assertNull(d.exaggeration)
        assertEquals(VideoShape.VERTICAL, d.shape)
        assertTrue(d.hud)
        assertEquals(VideoLength.MAX_S, d.lengthS)
        assertEquals(VideoConfig(), VideoConfig.decode(null))
    }

    @Test fun `presets keep size, length and title`() {
        val c = VideoConfig(shape = VideoShape.LANDSCAPE, lengthS = 30, title = "Ghats", hud = false)
        val m = c.withPreset(VideoPreset.MINIMAL)
        assertEquals(VideoPreset.MINIMAL, m.preset)
        assertEquals(VideoShape.LANDSCAPE, m.shape)
        assertEquals(30, m.lengthS)
        assertEquals("Ghats", m.title)
        assertTrue(!m.elevationStrip && !m.moments)
        val a = c.withPreset(VideoPreset.ADVENTURE)
        assertTrue(a.hud && a.exaggeration!! > 2)
    }
}
