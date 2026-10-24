package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareSwitchesCodecTest {
    @Test fun `round trips every field except photo`() {
        val s = ShareSwitches(
            ShareLook.POSTER,
            ShareShape.FEED,
            ShareBackground.MAP,
            ShareAccent.SKY,
            ShareMetricKind.MAX_SPEED,
            listOf(ShareMetricKind.DISTANCE, ShareMetricKind.MOVING),
            false,
            true,
            false,
        )
        assertEquals(s, ShareSwitchesCodec.decode(ShareSwitchesCodec.encode(s), ShareMetricKind.values().toList()))
    }

    @Test fun `photo is never remembered`() {
        val raw = ShareSwitchesCodec.encode(ShareSwitches(background = ShareBackground.PHOTO))
        assertEquals(ShareBackground.OWN, ShareSwitchesCodec.decode(raw, emptyList()).background)
    }

    @Test fun `garbage and null decode to defaults`() {
        assertEquals(ShareSwitches(), ShareSwitchesCodec.decode("%%%;look=NOPE", emptyList()))
        assertEquals(ShareSwitches(), ShareSwitchesCodec.decode(null, emptyList()))
    }

    @Test fun `stats this ride lacks are dropped`() {
        val raw = ShareSwitchesCodec.encode(
            ShareSwitches(stats = listOf(ShareMetricKind.FUEL_ECONOMY, ShareMetricKind.DISTANCE)),
        )
        assertEquals(listOf(ShareMetricKind.DISTANCE), ShareSwitchesCodec.decode(raw, listOf(ShareMetricKind.DISTANCE)).stats)
    }
}
