package dev.gdk.bumblebee.ble

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class SuzukiGattTest {
    @Test
    fun `vendor service and chars match REDLINE`() {
        assertEquals(UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"), SuzukiGatt.SERVICE_UUID)
        assertEquals(UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb"), SuzukiGatt.WRITE_CHAR_UUID)
        assertEquals(UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb"), SuzukiGatt.NOTIFY_CHAR_UUID)
    }
}
