package dev.gdk.bumblebee.ble

import android.companion.CompanionDeviceService
import android.os.Build
import androidx.annotation.RequiresApi

/** Wakes the bike link when Android sees the associated cluster. */
@RequiresApi(Build.VERSION_CODES.S)
class BikePresenceService : CompanionDeviceService() {
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onDeviceAppeared(address: String) {
        if (!BikeAutoConnect.shouldStart(this)) return
        BikeBridgeService.connect(this, address)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onDeviceDisappeared(address: String) = Unit
}
