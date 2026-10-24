package dev.gdk.bumblebee.ride

/** Hides the first and last 500 m of a ride so home is not on the share. */
object SharePrivacy {
    const val ZONE_M = 500.0

    fun livePointAllowed(distanceFromStartM: Double, zoneM: Double = ZONE_M): Boolean =
        distanceFromStartM >= zoneM

    fun retainAfterRide(
        distancesFromStartM: List<Double>,
        totalM: Double,
        zoneM: Double = ZONE_M,
    ): List<Double> {
        if (totalM < zoneM * 2) return emptyList()
        return distancesFromStartM.filter { it in zoneM..(totalM - zoneM) }
    }
}
