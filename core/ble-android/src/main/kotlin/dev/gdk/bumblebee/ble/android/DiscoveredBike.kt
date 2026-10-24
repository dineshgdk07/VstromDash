package dev.gdk.bumblebee.ble.android

data class DiscoveredBike(
    val mac: String,
    val name: String?,
    val rssi: Int,
    val bonded: Boolean,
) {
    val looksLikeSuzuki: Boolean
        get() {
            val n = name
            if (n != null && n.startsWith("SBM", ignoreCase = true)) return true
            val parts = mac.split(":")
            if (parts.size < 3) return false
            val oui = parts.take(3).joinToString(":").uppercase()
            return oui == SUZUKI_OUI
        }

    companion object {
        const val SUZUKI_OUI = "74:B8:39"
    }
}
