package dev.gdk.bumblebee.ui.ride.replay

import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.RideTimeline
import dev.gdk.bumblebee.ride.SpeedBand
import dev.gdk.bumblebee.ride.SpeedHistogram
import dev.gdk.bumblebee.ride.TrackPlot
import kotlin.math.floor

/** One downsampled point, with speed, altitude and fuel taken from the full-resolution timeline. */
data class ReplayFrame(
    val ts: Long,
    val lat: Double,
    val lng: Double,
    val distanceKm: Double,
    val speedKmh: Int?,
    val altitudeM: Double?,
    val fuelBars: Int?,
    val band: SpeedBand?,
)

data class ReplayPlot(
    val frames: List<ReplayFrame>,
    val totalKm: Double,
)

/**
 * Built once per ride. Drawing uses at most [maxPoints] positions; each position's moment
 * comes from [RideTimeline.at], so scrubbing never rebuilds this list.
 */
fun replayPlot(timeline: RideTimeline, track: List<GpsFix>, maxPoints: Int = 240): ReplayPlot {
    val drawn = TrackPlot.downsample(track, maxPoints)
    val frames = drawn.map { fix ->
        val moment = timeline.at(fix.ts)
        ReplayFrame(
            ts = fix.ts,
            lat = fix.lat,
            lng = fix.lng,
            distanceKm = moment.distanceKm,
            speedKmh = moment.speedKmh,
            altitudeM = moment.altitudeM,
            fuelBars = moment.fuelBars,
            band = moment.speedKmh?.let { SpeedHistogram.bandFor(it) },
        )
    }
    val end = timeline.at(timeline.endTs)
    return ReplayPlot(frames, end.distanceKm + end.remainingKm)
}

/** Frames where the ride crosses the next whole 5 km. */
fun kmTickFrames(frames: List<ReplayFrame>): List<ReplayFrame> {
    var next = 5.0
    val ticks = mutableListOf<ReplayFrame>()
    for (frame in frames) {
        if (frame.distanceKm + 1e-6 < next) continue
        ticks += frame
        next = (floor(frame.distanceKm / 5.0) + 1.0) * 5.0
    }
    return ticks
}
