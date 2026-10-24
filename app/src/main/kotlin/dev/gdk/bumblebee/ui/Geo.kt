package dev.gdk.bumblebee.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.gdk.bumblebee.ride.GpsFix

fun openParkedMaps(context: Context, parked: GpsFix) {
    val uri = Uri.parse("geo:${parked.lat},${parked.lng}?q=${parked.lat},${parked.lng}(Bike)")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

fun shareParkedLocation(context: Context, parked: GpsFix) {
    val text = "Bike last parked: ${parked.lat}, ${parked.lng}\nhttps://maps.google.com/?q=${parked.lat},${parked.lng}"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Share bike location")) }
}
