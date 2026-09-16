package dev.gdk.bumblebee.data.local

import dev.gdk.bumblebee.protocol.TelemetryFrame
import dev.gdk.bumblebee.ride.DemoRideSource
import dev.gdk.bumblebee.ride.GpsFix

sealed class CaptureTick {
    abstract val ts: Long
    data class Telemetry(override val ts: Long, val frame: TelemetryFrame) : CaptureTick()
    data class Gps(val fix: GpsFix) : CaptureTick() {
        override val ts: Long get() = fix.ts
    }
}

fun DemoRideSource.asTicks(): List<CaptureTick> {
    val tel = telemetry().map { CaptureTick.Telemetry(it.first, it.second) }
    val gps = gps().map { CaptureTick.Gps(it) }
    return (tel + gps).sortedWith(compareBy<CaptureTick> { it.ts }.thenBy { if (it is CaptureTick.Telemetry) 0 else 1 })
}

/**
 * Applies an ordered tick stream to [RideCaptureRepository].
 * Ride boundaries = first tick timestamp → [endTs] (or last tick ts).
 */
class RideSession(private val repo: RideCaptureRepository) {

    suspend fun run(
        ticks: List<CaptureTick>,
        endTs: Long? = null,
        onTick: (CaptureTick) -> Unit = {},
    ): RideEntity {
        require(ticks.isNotEmpty()) { "no ticks" }
        repo.startRide(ticks.first().ts)
        for (tick in ticks) {
            when (tick) {
                is CaptureTick.Telemetry -> repo.onTelemetry(tick.frame, tick.ts)
                is CaptureTick.Gps -> repo.onGps(tick.fix)
            }
            onTick(tick)
        }
        val finishAt = endTs ?: ticks.maxOf { it.ts }
        return repo.finishRide(finishAt)
    }
}
