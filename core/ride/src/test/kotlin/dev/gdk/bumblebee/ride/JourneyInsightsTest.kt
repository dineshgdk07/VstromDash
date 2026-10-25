package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class JourneyInsightsTest {
    private val now = LocalDateTime.of(2026, 10, 25, 17, 0)

    @Test fun `cluster etas and distances parse`() {
        assertEquals(LocalTime.of(17, 40), JourneyInsights.parseEta("0540PM"))
        assertEquals(LocalTime.of(0, 15), JourneyInsights.parseEta("1215AM"))
        assertEquals(LocalTime.of(17, 40), JourneyInsights.parseEta("174000"))
        assertNull(JourneyInsights.parseEta("soon"))
        assertEquals(23.4, JourneyInsights.km("23.4", "K")!!, 1e-9)
        assertEquals(0.8, JourneyInsights.km("0800", "M")!!, 1e-9)
    }

    @Test fun `an eta past midnight is tomorrow`() {
        val late = LocalDateTime.of(2026, 10, 25, 23, 30)
        assertEquals(LocalDateTime.of(2026, 10, 26, 0, 20), JourneyInsights.arrivalAt(late, LocalTime.of(0, 20)))
        assertEquals(LocalDateTime.of(2026, 10, 25, 17, 40), JourneyInsights.arrivalAt(now, LocalTime.of(17, 40)))
    }

    @Test fun `fuel check says how it stands`() {
        assertEquals(Tone.GOOD, JourneyInsights.fuelCheck(60.0, 150.0)!!.tone)
        assertEquals("Tight on fuel: 10 km spare. Top up on the way. Your usual: HP Hebbal.", JourneyInsights.fuelCheck(140.0, 150.0, listOf("HP Hebbal"))!!.text)
        val bad = JourneyInsights.fuelCheck(200.0, 120.0)!!
        assertEquals(Tone.BAD, bad.tone)
        assertTrue(bad.text.startsWith("Fill up within 110 km"))
        assertNull(JourneyInsights.fuelCheck(50.0, null))
    }

    @Test fun `arrival check warns about dark, rain and long legs`() {
        val tips = JourneyInsights.arrivalCheck(now, now.plusMinutes(100), now.plusMinutes(70), 60)
        assertEquals(3, tips.size)
        assertEquals("You'll arrive after dark: 6:40 pm, sunset 6:10 pm. Lights on, visor clear.", tips[0].text)
        assertTrue(tips[2].text.startsWith("1 h 40 min to go. Plan a break around 5:50 pm"))
        assertTrue(JourneyInsights.arrivalCheck(now, now.plusMinutes(20), now.plusMinutes(70), 10).isEmpty())
    }

    @Test fun `eta share and slip`() {
        assertEquals("On my way, ETA 5:40 pm, 23 km to go. — Dinesh", JourneyInsights.shareText(LocalTime.of(17, 40), 23.4, "Dinesh"))
        assertEquals(8, JourneyInsights.etaSlip(LocalTime.of(17, 40), LocalTime.of(17, 48)))
        assertEquals(10, JourneyInsights.etaSlip(LocalTime.of(23, 55), LocalTime.of(0, 5)))
    }

    @Test fun `stats compare arrivals with maps`() {
        val h = 3_600_000L
        val entries = listOf(
            JourneyEntry(0, h, "A", h + 6 * 60_000, 30.0, true),
            JourneyEntry(0, h, "B", h + 2 * 60_000, 20.0, true),
            JourneyEntry(0, null, "C", h, 10.0, false),
        )
        val s = JourneyInsights.stats(entries)
        assertEquals(3, s.trips)
        assertEquals(2, s.arrived)
        assertEquals(4.0, s.avgBeatMin!!, 1e-9)
        assertEquals(60.0, s.km, 1e-9)
    }

    @Test fun `places group by end place`() {
        val places = JourneyInsights.places(
            listOf(
                Triple("Nandi Hills", 1L, 13.37 to 77.68),
                Triple("Nandi Hills", 5L, 13.37 to 77.68),
                Triple("Hosur", 3L, null),
                Triple(null, 4L, null),
            ),
        )
        assertEquals(listOf("Nandi Hills", "Hosur"), places.map { it.name })
        assertEquals(2, places[0].visits)
        assertEquals("Hosur", JourneyInsights.firstVisit(places, "Hosur")!!.name)
        assertNull(JourneyInsights.firstVisit(places, "Nandi Hills"))
    }

    @Test fun `routes and destinations become maps links`() {
        assertEquals("google.navigation:q=Nandi+Hills&mode=l", JourneyInsights.navigateUri("Nandi Hills"))
        val track = (0..20).map { 12.9 + it * 0.01 to 77.5 }
        val url = JourneyInsights.routeUrl(track, maxStops = 3)!!
        assertTrue(url.startsWith("https://www.google.com/maps/dir/?api=1&origin=12.90000%2C77.50000"))
        assertTrue(url.contains("&destination=13.10000%2C77.50000"))
        assertEquals(3, url.substringAfter("&waypoints=").substringBefore("&").split("%7C").size)
        assertTrue(url.endsWith("&travelmode=two-wheeler&dir_action=navigate"))
        assertNull(JourneyInsights.routeUrl(track.take(1)))
    }
}
