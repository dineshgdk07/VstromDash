package dev.gdk.bumblebee.ble

/** Lifecycle of our connection to the bike. */
sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Connecting : ConnectionState
    data object Discovering : ConnectionState
    data object Ready : ConnectionState
    data class Disconnected(val status: Int) : ConnectionState
    data class Failed(val reason: String) : ConnectionState
}
