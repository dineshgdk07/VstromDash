package dev.gdk.bumblebee.gps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dev.gdk.bumblebee.ride.GpsCapturePolicy
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.GpsPace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class PhoneGps(private val context: Context) {

    private val pace = MutableStateFlow(GpsPace.Tracking)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** [GpsPace.Tracking] is one-second GNSS. [GpsPace.Parked] is a coarse, infrequent fix. */
    fun setPace(next: GpsPace) {
        pace.value = next
    }

    /** The phone's last known position, without switching GPS on. Null without permission or a fix. */
    @SuppressLint("MissingPermission")
    suspend fun lastKnown(): GpsFix? {
        if (!hasPermission()) return null
        return suspendCancellableCoroutine { cont ->
            runCatching {
                LocationServices.getFusedLocationProviderClient(context).lastLocation
                    .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc?.toFix()) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }
    }

    fun fixes(): Flow<GpsFix> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        val fusedOk = runCatching { listenFused(this) { trySend(it) } }.getOrNull()
        if (fusedOk != null) {
            awaitClose { fusedOk() }
            return@callbackFlow
        }
        val stop = listenGpsProvider(this) { trySend(it) }
        if (stop == null) {
            close()
            return@callbackFlow
        }
        awaitClose { stop() }
    }

    private fun listenFused(scope: CoroutineScope, emit: (GpsFix) -> Unit): () -> Unit {
        val fused = LocationServices.getFusedLocationProviderClient(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("ACCESS_FINE_LOCATION")
        }
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                emit(loc.toFix())
            }
        }
        val stopped = AtomicBoolean(false)
        val generation = AtomicInteger(0)
        val initial = pace.value
        fused.requestLocationUpdates(requestFor(initial), callback, Looper.getMainLooper())
        var applied = initial
        val job = scope.launch {
            pace.collect { next ->
                if (next == applied || stopped.get()) return@collect
                applied = next
                val ticket = generation.incrementAndGet()
                fused.removeLocationUpdates(callback).addOnCompleteListener {
                    if (stopped.get() || ticket != generation.get()) return@addOnCompleteListener
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return@addOnCompleteListener
                    }
                    runCatching {
                        fused.requestLocationUpdates(requestFor(next), callback, Looper.getMainLooper())
                    }
                }
            }
        }
        return {
            stopped.set(true)
            job.cancel()
            runCatching { fused.removeLocationUpdates(callback) }
        }
    }

    private fun listenGpsProvider(scope: CoroutineScope, emit: (GpsFix) -> Unit): (() -> Unit)? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val listener = LocationListener { loc -> emit(loc.toFix()) }
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> LocationManager.GPS_PROVIDER
        }
        val initial = pace.value
        val (interval, distance) = limits(initial)
        return try {
            lm.requestLocationUpdates(provider, interval, distance, listener, Looper.getMainLooper())
            val stopped = AtomicBoolean(false)
            var applied = initial
            val job = scope.launch {
                pace.collect { next ->
                    if (next == applied || stopped.get()) return@collect
                    applied = next
                    val (nextInterval, nextDistance) = limits(next)
                    runCatching { lm.removeUpdates(listener) }
                    runCatching {
                        lm.requestLocationUpdates(
                            provider, nextInterval, nextDistance, listener, Looper.getMainLooper(),
                        )
                    }
                }
            }
            ({
                stopped.set(true)
                job.cancel()
                runCatching { lm.removeUpdates(listener) }
            })
        } catch (_: SecurityException) {
            null
        }
    }

    private fun requestFor(next: GpsPace): LocationRequest = when (next) {
        GpsPace.Tracking -> LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            GpsCapturePolicy.TRACKING_INTERVAL_MS,
        )
            .setMinUpdateIntervalMillis(GpsCapturePolicy.TRACKING_INTERVAL_MS)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()
        GpsPace.Parked -> LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            GpsCapturePolicy.PARKED_INTERVAL_MS,
        )
            .setMinUpdateIntervalMillis(GpsCapturePolicy.PARKED_MIN_INTERVAL_MS)
            .setMinUpdateDistanceMeters(GpsCapturePolicy.PARKED_MIN_DISTANCE_M)
            .setWaitForAccurateLocation(false)
            .build()
    }

    private fun limits(next: GpsPace): Pair<Long, Float> = when (next) {
        GpsPace.Tracking -> GpsCapturePolicy.TRACKING_INTERVAL_MS to 0f
        GpsPace.Parked -> GpsCapturePolicy.PARKED_INTERVAL_MS to GpsCapturePolicy.PARKED_MIN_DISTANCE_M
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
