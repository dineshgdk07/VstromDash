package dev.gdk.bumblebee.ble

import java.util.UUID

/**
 * Suzuki bike BLE GATT surface (REDLINE Phase 1).
 * Service 0xFFF0, write 0xFFF1 (phone→bike), notify 0xFFF2 (bike→phone).
 */
object SuzukiGatt {
    val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val WRITE_CHAR_UUID: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
    val NOTIFY_CHAR_UUID: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
    val CCC_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
