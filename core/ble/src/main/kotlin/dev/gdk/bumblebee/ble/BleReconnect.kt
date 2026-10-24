package dev.gdk.bumblebee.ble

/**
 * Rotation and other Activity recreations call [dev.gdk.bumblebee.ble.android.BleClient.connect]
 * again. Reconnecting the same MAC while already linked tears the GATT session down first.
 */
object BleReconnect {
    fun needed(state: ConnectionState, lastMac: String?, nextMac: String): Boolean {
        if (!macEquals(lastMac, nextMac)) return true
        return when (state) {
            ConnectionState.Ready,
            ConnectionState.Connecting,
            ConnectionState.Discovering,
            -> false
            else -> true
        }
    }

    private fun macEquals(a: String?, b: String): Boolean =
        a != null && a.equals(b, ignoreCase = true)
}
