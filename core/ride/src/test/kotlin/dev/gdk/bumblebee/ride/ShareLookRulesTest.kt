package dev.gdk.bumblebee.ride

import dev.gdk.bumblebee.ride.ShareMetricKind.AVG_SPEED
import dev.gdk.bumblebee.ride.ShareMetricKind.DISTANCE
import dev.gdk.bumblebee.ride.ShareMetricKind.ELEVATION
import dev.gdk.bumblebee.ride.ShareMetricKind.FUEL_ECONOMY
import dev.gdk.bumblebee.ride.ShareMetricKind.MAX_SPEED
import dev.gdk.bumblebee.ride.ShareMetricKind.MOVING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareLookRulesTest {
    private val available = ShareMetrics.of(
        RideInsightInput(0L, 9_000_000L, 86.4, 8_040, 600, 104, 38.7),
        climbM = 573.0,
        tankKmPerL = 38.2,
    )

    private fun choices(look: ShareLook, hero: ShareMetricKind? = DISTANCE) =
        ShareLookRules.choices(look, available, hero).map { it.kind }

    @Test fun `slots per look`() {
        assertEquals(3, ShareLookRules.statSlots(ShareLook.NIGHT_GLOW))
        assertEquals(3, ShareLookRules.statSlots(ShareLook.POSTER))
        assertEquals(3, ShareLookRules.statSlots(ShareLook.COCKPIT))
        assertEquals(1, ShareLookRules.statSlots(ShareLook.RIDE_PASS))
    }

    @Test fun `built-in metrics are not offered`() {
        assertFalse(DISTANCE in choices(ShareLook.POSTER))
        assertTrue(DISTANCE in choices(ShareLook.POSTER, hero = MAX_SPEED))
        assertFalse(MAX_SPEED in choices(ShareLook.POSTER, hero = MAX_SPEED))
        assertTrue(DISTANCE in choices(ShareLook.COCKPIT))
        assertFalse(MAX_SPEED in choices(ShareLook.COCKPIT))
        assertFalse(DISTANCE in choices(ShareLook.RIDE_PASS))
        assertFalse(MOVING in choices(ShareLook.RIDE_PASS))
    }

    @Test fun `big number only where the look has one`() {
        assertTrue(ShareLookRules.hasBigNumber(ShareLook.NIGHT_GLOW))
        assertTrue(ShareLookRules.hasBigNumber(ShareLook.POSTER))
        assertFalse(ShareLookRules.hasBigNumber(ShareLook.COCKPIT))
        assertFalse(ShareLookRules.hasBigNumber(ShareLook.RIDE_PASS))
    }

    @Test fun `cockpit fills all three slots by default`() {
        val shown = ShareLookRules.stats(ShareLook.COCKPIT, available, DISTANCE, null).map { it.kind }
        assertEquals(listOf(DISTANCE, MOVING, AVG_SPEED), shown)
    }

    @Test fun `tapping a shown stat removes it`() =
        assertEquals(listOf(MOVING, ELEVATION), ShareLookRules.toggle(ShareLook.POSTER, listOf(MOVING, MAX_SPEED, ELEVATION), listOf(MOVING, MAX_SPEED, ELEVATION), MAX_SPEED))

    @Test fun `tapping a hidden stat adds it while there is room`() =
        assertEquals(listOf(MOVING, ELEVATION, FUEL_ECONOMY), ShareLookRules.toggle(ShareLook.POSTER, listOf(MOVING, ELEVATION), listOf(MOVING, ELEVATION), FUEL_ECONOMY))

    @Test fun `a full look refuses another stat`() =
        assertNull(ShareLookRules.toggle(ShareLook.POSTER, listOf(MOVING, MAX_SPEED, ELEVATION), listOf(MOVING, MAX_SPEED, ELEVATION), FUEL_ECONOMY))

    @Test fun `a one-stat look swaps and keeps the rest of the order`() =
        assertEquals(listOf(FUEL_ECONOMY, MAX_SPEED, ELEVATION), ShareLookRules.toggle(ShareLook.RIDE_PASS, listOf(MAX_SPEED, ELEVATION), listOf(MAX_SPEED), FUEL_ECONOMY))

    @Test fun `a cockpit stat added on top of a hidden max speed still shows`() {
        val prefs = listOf(MAX_SPEED, MOVING, ELEVATION)
        val shown = ShareLookRules.stats(ShareLook.COCKPIT, available, DISTANCE, prefs).map { it.kind }
        assertEquals(listOf(MOVING, ELEVATION), shown)
        val next = ShareLookRules.toggle(ShareLook.COCKPIT, prefs, shown, DISTANCE)!!
        assertEquals(listOf(MOVING, ELEVATION, DISTANCE), ShareLookRules.stats(ShareLook.COCKPIT, available, DISTANCE, next).map { it.kind })
    }
}
