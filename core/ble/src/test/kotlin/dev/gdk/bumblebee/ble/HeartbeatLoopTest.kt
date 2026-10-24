package dev.gdk.bumblebee.ble

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime
import java.util.Locale

class HeartbeatLoopTest {

    private fun loop(
        battery: Int,
        charging: Boolean = false,
        hour: Int = 14,
        minute: Int = 30,
        second: Int = 5,
    ) = HeartbeatLoop(
        phoneBatteryProvider = { battery to charging },
        signalBarsProvider = { 3 },
        smsPendingProvider = { false },
        callPendingProvider = { false },
        weatherProvider = { 1 to 0 },
        clock = { LocalTime.of(hour, minute, second) },
    )

    @Test
    fun `battery 80 maps to bucket 3`() {
        assertEquals("3", loop(80).buildFrame().batteryBucket)
    }

    @Test
    fun `battery 10 maps to bucket 0`() {
        assertEquals("0", loop(10).buildFrame().batteryBucket)
    }

    @Test
    fun `charging is Y`() {
        assertEquals("Y", loop(50, charging = true).buildFrame().charging)
    }

    @Test
    fun `afternoon clock is sent as 12-hour hhmmss`() {
        // 14:30:05 → 02:30:05
        assertEquals("023005", loop(80).buildFrame().timeHhmmss)
    }

    @Test
    fun `interval is one second`() {
        assertEquals(1_000L, HeartbeatLoop.HEARTBEAT_INTERVAL_MS)
    }

    @Test
    fun `time digits stay ASCII under a German locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("023005", loop(80).buildFrame().timeHhmmss)
        } finally {
            Locale.setDefault(previous)
        }
    }
}
