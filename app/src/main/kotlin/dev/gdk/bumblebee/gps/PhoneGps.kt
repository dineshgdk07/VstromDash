package dev.gdk.bumblebee.gps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import dev.gdk.bumblebee.ride.GpsFix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class PhoneGps(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun fixes(): Flow<GpsFix> = callbackFlow {
        val lm = context.getSystemService(LocationManager::class.java)
        var listener: LocationListener? = null
        if (hasPermission() && lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            listener = LocationListener { loc -> trySend(loc.toFix()) }
            try {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1_000L,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
            } catch (_: SecurityException) {
                listener = null
                close()
            }
        } else {
            close()
        }
        awaitClose {
            if (listener != null && lm != null) {
                runCatching { lm.removeUpdates(listener) }
            }
        }
    }

    private fun Location.toFix(): GpsFix = GpsFix(
        ts = time,
        lat = latitude,
        lng = longitude,
        altitudeM = altitude.takeIf { hasAltitude() },
        bearingDeg = bearing.takeIf { hasBearing() },
        speedMps = speed.takeIf { hasSpeed() },
        accuracyM = accuracy.takeIf { hasAccuracy() },
    )
}
