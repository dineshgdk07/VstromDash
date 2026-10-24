package dev.gdk.bumblebee.ui.share

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class PlaceNamerTest {
    @Test fun `place namer swallows geocoder failures`() = runTest {
        assertNull(PlaceNamer { _, _ -> throw IOException("offline") }.locality(12.9, 77.6))
        assertNull(PlaceNamer { _, _ -> "  " }.locality(12.9, 77.6))
    }
}
