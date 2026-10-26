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
        )
    }

    fun fonts(context: Context) = VideoFonts(
        display = context.resources.getFont(R.font.anton_regular),
        label = context.resources.getFont(R.font.rajdhani_semibold),
        labelBold = context.resources.getFont(R.font.rajdhani_bold),
    )

    private val DEFAULT_TITLE = DateTimeFormatter.ofPattern("EEEE 'ride'", Locale.ENGLISH)
}
