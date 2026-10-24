package dev.gdk.bumblebee.ui.share

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Locality from a point. Failures and blank results are no place name. */
class PlaceNamer(
    private val geocode: suspend (Double, Double) -> String?,
) {
    suspend fun locality(lat: Double, lng: Double): String? = try {
        geocode(lat, lng)?.trim()?.ifBlank { null }
    } catch (_: Exception) {
        null
    }

    companion object {
        fun android(context: Context): PlaceNamer = PlaceNamer { lat, lng ->
            withContext(Dispatchers.IO) {
                val geo = Geocoder(context)
                @Suppress("DEPRECATION")
                val address = geo.getFromLocation(lat, lng, 1)?.firstOrNull()
                address?.locality?.takeIf { it.isNotBlank() } ?: address?.subAdminArea
            }
        }
    }
}
