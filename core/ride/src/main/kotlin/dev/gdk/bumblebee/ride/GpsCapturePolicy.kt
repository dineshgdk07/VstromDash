package dev.gdk.bumblebee.ride

/**
 * High-accuracy GPS is only justified while a ride needs a track.
 * The bike link counts, and so does a phone-only ride that is still open
 * (including the short grace after the link drops). A few minutes with no
 * movement steps down to a coarse request so a parked bike does not hold
 * the GNSS chip at one fix per second.
 */
enum class GpsPace { Tracking, Parked }

object GpsCapturePolicy {
    const val STATIONARY_AFTER_MS = 3 * 60 * 1000L
    const val MOVING_SPEED_MPS = 1.5f
    const val MOVING_SPEED_KMH = 5
    const val TRACKING_INTERVAL_MS = 1_000L
    const val PARKED_INTERVAL_MS = 60_000L
    const val PARKED_MIN_INTERVAL_MS = 30_000L
    const val PARKED_MIN_DISTANCE_M = 50f

    fun shouldCapture(bikeLinked: Boolean, gpsOnlyRide: Boolean): Boolean =
        bikeLinked || gpsOnlyRide

    fun isMoving(speedMps: Float?, bikeSpeedKmh: Int?): Boolean =
        (speedMps != null && speedMps >= MOVING_SPEED_MPS) ||
            (bikeSpeedKmh != null && bikeSpeedKmh >= MOVING_SPEED_KMH)

    fun pace(stationaryForMs: Long): GpsPace =
        if (stationaryForMs >= STATIONARY_AFTER_MS) GpsPace.Parked else GpsPace.Tracking
}
