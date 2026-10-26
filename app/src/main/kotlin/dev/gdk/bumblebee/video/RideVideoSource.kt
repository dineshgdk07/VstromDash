package dev.gdk.bumblebee.video

import android.content.Context
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.R
import dev.gdk.bumblebee.ride.FuelEventType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Gathers a saved ride into a [RideVideoInput]: track, breaks, refuels, name and places. */
object RideVideoSource {
    suspend fun load(app: DashApp, rideId: Long): RideVideoInput? {
        val ride = app.capture.rideById(rideId) ?: return null
        val track = app.capture.trackForRide(rideId)
        if (track.size < 2) return null
        val segments = app.capture.segmentsFor(rideId).sortedBy { it.startTs }
        // A break is the gap between one segment's end and the next one's start.
        val stops = segments.zipWithNext().mapNotNull { (a, b) ->
            val end = a.endTs ?: return@mapNotNull null
            val gapS = ((b.startTs - end) / 1000).toInt()
            if (gapS > 0) end to gapS else null
        }
        val refuels = app.capture.fuelEventsForRide(rideId).filter { it.type == FuelEventType.REFUEL }.map { it.ts }
        val zone = ZoneId.systemDefault()
        val title = ride.name?.takeIf { it.isNotBlank() }
            ?: ride.endPlace?.let { "To $it" }
            ?: DEFAULT_TITLE.format(Instant.ofEpochMilli(ride.startTs).atZone(zone))
        return RideVideoInput(
            track = track,
            stops = stops,
            refuelTs = refuels,
            title = title,
            fromPlace = ride.startPlace,
            toPlace = ride.endPlace,
            startTs = ride.startTs,
            movingSec = ride.movingSec,
            zone = zone,
            places = placesAlong(app, track),
        )
    }

    /**
     * Town names along the route from the phone's geocoder: about a dozen evenly spaced samples
     * between the hidden first and last stretches, keeping each change of name. Offline or
     * without a geocoder this is simply empty.
     */
    private suspend fun placesAlong(app: DashApp, track: List<dev.gdk.bumblebee.ride.GpsFix>): List<PlaceMark> {
        val namer = dev.gdk.bumblebee.ui.share.PlaceNamer.android(app)
        val cum = DoubleArray(track.size)
        for (i in 1 until track.size) {
            cum[i] = cum[i - 1] + dev.gdk.bumblebee.ride.Geo.haversineMeters(track[i - 1].lat, track[i - 1].lng, track[i].lat, track[i].lng)
        }
        val total = cum.last()
        val margin = dev.gdk.bumblebee.ride.SharePrivacy.ZONE_M + 300
        if (total < margin * 2 + 1_000) return emptyList()
        val out = mutableListOf<PlaceMark>()
        var j = 0
        for (k in 0 until SAMPLES) {
            val want = margin + (total - margin * 2) * k / (SAMPLES - 1)
            while (j < cum.lastIndex && cum[j] < want) j++
            val fix = track[j]
            val name = namer.locality(fix.lat, fix.lng) ?: continue
            if (out.lastOrNull()?.name != name) out += PlaceMark(fix.lat, fix.lng, name)
        }
        return out
    }

    private const val SAMPLES = 12

    fun fonts(context: Context) = VideoFonts(
        display = context.resources.getFont(R.font.anton_regular),
        label = context.resources.getFont(R.font.rajdhani_semibold),
        labelBold = context.resources.getFont(R.font.rajdhani_bold),
    )

    private val DEFAULT_TITLE = DateTimeFormatter.ofPattern("EEEE 'ride'", Locale.ENGLISH)
}
