package dev.gdk.bumblebee.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class NavPipelineTest {

    @Test
    fun `continue straight is cluster byte 8`() {
        val mappls = MapplsIdGuesser.fromText("Continue straight")
        assertEquals(7, mappls)
        assertEquals(8, ManeuverMap.mapplsIdToClusterByte(mappls))
    }

    @Test
    fun `head north is straight ahead`() {
        val mappls = MapplsIdGuesser.fromText("Head north on MG Road")
        assertEquals(7, mappls)
        assertEquals(8, ManeuverMap.mapplsIdToClusterByte(mappls))
    }

    @Test
    fun `turn left maps to cluster 1`() {
        val mappls = MapplsIdGuesser.fromText("In 200 m turn left")
        assertEquals(0, mappls)
        assertEquals(1, ManeuverMap.mapplsIdToClusterByte(mappls))
    }

    @Test
    fun `destination beats head catch-all and shows the arrival flag`() {
        val mappls = MapplsIdGuesser.fromText("Head to your destination")
        assertEquals(8, mappls)
        assertEquals(9, ManeuverMap.mapplsIdToClusterByte(mappls))
    }

    @Test
    fun `slight right wins over right`() {
        assertEquals(4, MapplsIdGuesser.fromText("Slight right onto Ring Road"))
        assertEquals(3, MapplsIdGuesser.fromText("Turn right onto Ring Road"))
    }

    @Test
    fun `comma decimals miles and long unit names`() {
        assertEquals("01.5" to "K", normalizeDistance("1,5 km"))
        assertEquals("01.6" to "K", normalizeDistance("1 mi"))
        assertEquals("01.2" to "K", normalizeDistance("1.2 kilometers"))
        assertEquals("0200" to "M", normalizeDistance("200 meters"))
    }

    @Test
    fun `hud distance does not strip leading zeros into a bare dot`() {
        assertEquals("0.5 km", formatHudDistance("00.5", "K"))
        assertEquals("1.2 km", formatHudDistance("01.2", "K"))
        assertEquals("250 m", formatHudDistance("0250", "M"))
    }

    @Test
    fun `normalize 200 m and 1_2 km`() {
        assertEquals("0200" to "M", normalizeDistance("In 200 m turn left"))
        assertEquals("01.2" to "K", normalizeDistance("1.2 km"))
    }

    @Test
    fun `eta from clock`() {
        assertEquals("0432PM", normalizeEta("5 min · 1.2 km · 4:32 PM", twelveHour = true))
    }

    @Test
    fun `eta from minutes uses injected now`() {
        val now = LocalTime.of(10, 0)
        assertEquals("100500", normalizeEta("5 min", twelveHour = false, now = now))
    }

    @Test
    fun `toNavFrame sets status 1`() {
        val parsed = ParsedNavData(1, "0200", "M", "0432PM", "01.2", "K", "turn left")
        val f = parsed.toNavFrame()
        assertEquals("1", f.status)
        assertEquals(1, f.maneuverId)
    }

    @Test
    fun `a non-nav Maps post does not clear the active turn`() {
        val turn = ParsedNavData(1, "0200", "M", "0432PM", "01.2", "K", "turn left")
        MapsNavSource.update(turn, key = "nav-key")
        MapsNavSource.update(null, key = "traffic-alert")
        assertEquals(1, MapsNavSource.frame.value?.maneuverId)
        assertEquals("nav-key", MapsNavSource.activeKey)
        MapsNavSource.clear()
    }

    @Test
    fun `removing the active nav key clears the turn`() {
        val turn = ParsedNavData(1, "0200", "M", "0432PM", "01.2", "K", "turn left")
        MapsNavSource.update(turn, key = "nav-key")
        MapsNavSource.update(null, key = "nav-key")
        assertNull(MapsNavSource.frame.value)
        assertNull(MapsNavSource.activeKey)
    }
}
