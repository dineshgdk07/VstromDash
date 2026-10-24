package dev.gdk.bumblebee.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleReconnectTest {

    @Test
    fun `same mac while ready does not reconnect`() {
        assertFalse(BleReconnect.needed(ConnectionState.Ready, "AA:BB:CC:DD:EE:FF", "AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `same mac while connecting or discovering is a no-op`() {
        assertFalse(BleReconnect.needed(ConnectionState.Connecting, "aa:bb:cc:dd:ee:ff", "AA:BB:CC:DD:EE:FF"))
        assertFalse(BleReconnect.needed(ConnectionState.Discovering, "AA:BB:CC:DD:EE:FF", "AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `a different bike reconnects`() {
        assertTrue(BleReconnect.needed(ConnectionState.Ready, "AA:BB:CC:DD:EE:FF", "11:22:33:44:55:66"))
    }

    @Test
    fun `idle failed or disconnected reconnects the saved bike`() {
        assertTrue(BleReconnect.needed(ConnectionState.Idle, "AA:BB:CC:DD:EE:FF", "AA:BB:CC:DD:EE:FF"))
        assertTrue(BleReconnect.needed(ConnectionState.Failed("x"), "AA:BB:CC:DD:EE:FF", "AA:BB:CC:DD:EE:FF"))
        assertTrue(BleReconnect.needed(ConnectionState.Disconnected(0), "AA:BB:CC:DD:EE:FF", "AA:BB:CC:DD:EE:FF"))
    }
}
