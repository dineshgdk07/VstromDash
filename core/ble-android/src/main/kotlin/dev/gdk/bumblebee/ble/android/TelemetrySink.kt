package dev.gdk.bumblebee.ble.android

import dev.gdk.bumblebee.data.local.RideCaptureRepository
import dev.gdk.bumblebee.protocol.FRAME_LEN
import dev.gdk.bumblebee.protocol.TelemetryFrame
import dev.gdk.bumblebee.protocol.decodeFrame

/**
 * Bridges GATT notify bytes into [RideCaptureRepository].
 * [onReady] opens a ride so the first a537 is not lost.
 */
class TelemetrySink(private val repo: RideCaptureRepository) {

    suspend fun onReady(ts: Long) {
        if (repo.activeRideId == null) repo.startRide(ts)
    }

    suspend fun onNotify(bytes: ByteArray, ts: Long) {
        if (repo.activeRideId == null) return
        if (bytes.size != FRAME_LEN) return
        val frame = try {
            decodeFrame(bytes)
        } catch (_: Exception) {
            return
        }
        if (frame is TelemetryFrame) repo.onTelemetry(frame, ts)
    }

    suspend fun onDisconnected(ts: Long) {
        if (repo.activeRideId != null) repo.finishRide(ts)
    }
}
