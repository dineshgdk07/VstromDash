package dev.gdk.bumblebee.video

import dev.gdk.bumblebee.ride.Geo
import dev.gdk.bumblebee.ride.GpsFix
import kotlin.math.abs

/**
 * A photo the rider added, with what its file said: where it was taken (if the camera recorded a
 * location) and when. [manualM] is a spot the rider chose on the route, in metres from the start
 * of the ride, which overrides both.
 */
data class RidePhoto(
    val id: String,
    val takenTs: Long? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val manualM: Double? = null,
    val caption: String? = null,
)

/** How a photo got its place on the route. */
enum class PhotoPlacing(val label: String) {
    LOCATION("By where it was taken"),
    TIME("By when it was taken"),
    MANUAL("Set by you"),
}

/** Where a photo goes, or why it can't go anywhere. */
sealed interface PhotoSpot {
    val photo: RidePhoto

    /** [rideM] is metres from the ride's real start, as the rider thinks of it. */
    data class Placed(override val photo: RidePhoto, val rideM: Double, val ts: Long?, val how: PhotoPlacing) : PhotoSpot

    data class Hidden(override val photo: RidePhoto, val rideM: Double) : PhotoSpot

    data class Unplaced(override val photo: RidePhoto, val reason: String) : PhotoSpot
}

/**
 * Puts photos on the ride. A photo's location wins when it's within [NEAR_M] of the track; else
 * its time, when it falls inside the ride (a few minutes either side allowed); else the rider
 * places it. Photos that land in the hidden first or last stretch stay out, so home isn't shown.
 */
object PhotoPlacer {
    const val NEAR_M = 500.0
    const val SLACK_MS = 5 * 60_000L

    fun place(photos: List<RidePhoto>, track: List<GpsFix>, hideM: Double = dev.gdk.bumblebee.ride.SharePrivacy.ZONE_M): List<PhotoSpot> {
        if (track.size < 2) return photos.map { PhotoSpot.Unplaced(it, "This ride has no GPS track") }
        val cum = DoubleArray(track.size)
        for (i in 1 until track.size) {
            cum[i] = cum[i - 1] + Geo.haversineMeters(track[i - 1].lat, track[i - 1].lng, track[i].lat, track[i].lng)
        }
        val total = cum.last()
        fun spot(p: RidePhoto, m: Double, ts: Long?, how: PhotoPlacing): PhotoSpot =
            if (m < hideM || m > total - hideM) PhotoSpot.Hidden(p, m) else PhotoSpot.Placed(p, m, ts, how)
        return photos.map { p ->
            p.manualM?.let { return@map spot(p, it.coerceIn(0.0, total), null, PhotoPlacing.MANUAL) }
            if (p.lat != null && p.lng != null) {
                var best = -1
                var bestD = Double.MAX_VALUE
                for (i in track.indices) {
                    val d = Geo.haversineMeters(p.lat, p.lng, track[i].lat, track[i].lng)
                    if (d < bestD) {
                        bestD = d
                        best = i
                    }
                }
                if (bestD <= NEAR_M) return@map spot(p, cum[best], track[best].ts, PhotoPlacing.LOCATION)
            }
            val ts = p.takenTs
            if (ts != null && ts in (track.first().ts - SLACK_MS)..(track.last().ts + SLACK_MS)) {
                var best = 0
                for (i in track.indices) if (abs(track[i].ts - ts) < abs(track[best].ts - ts)) best = i
                return@map spot(p, cum[best], ts, PhotoPlacing.TIME)
            }
            PhotoSpot.Unplaced(
                p,
                when {
                    ts == null && p.lat == null -> "The photo has no time or location in it"
                    ts != null && ts < track.first().ts -> "Taken ${gap(track.first().ts - ts)} before the ride"
                    ts != null -> "Taken ${gap(ts - track.last().ts)} after the ride"
                    else -> "Taken away from the route"
                },
            )
        }
    }

    private fun gap(ms: Long): String {
        val min = ms / 60_000
        return when {
            min < 90 -> "$min min"
            min < 48 * 60 -> "${min / 60} h"
            else -> "${min / (24 * 60)} days"
        }
    }
}
