package dev.gdk.bumblebee.ride

import org.junit.Assert.assertTrue
import org.junit.Test

class GpxPrivacyTest {
    /** A straight 5 km north, a fix every ~11 m. */
    private val track = (0..450).map { i -> GpsFix(1_000L * i, 10.0 + i * 0.0001, 76.9, accuracyM = 5f) }

    @Test fun `the first and last 500 m are left out`() {
        val kept = GpxExport.withoutEnds(track)
        val first = kept.first()
        val last = kept.last()
        assertTrue(Geo.haversineMeters(track.first().lat, track.first().lng, first.lat, first.lng) >= 500)
        assertTrue(Geo.haversineMeters(track.last().lat, track.last().lng, last.lat, last.lng) >= 500)
        assertTrue(kept.size in 340..365)
    }

    @Test fun `a ride under a kilometre has nothing to share privately`() {
        assertTrue(GpxExport.withoutEnds(track.take(80)).isEmpty())
    }
}
