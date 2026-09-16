package dev.gdk.bumblebee.ble

import dev.gdk.bumblebee.protocol.HeartbeatFrame
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * Builds a fresh a533 HeartbeatFrame each second from current phone state + weather.
 * Owner collects the produced frames and feeds them into [FrameWriter].
 */
class HeartbeatLoop(
    private val phoneBatteryProvider: () -> Pair<Int, Boolean>, // (percent, isCharging)
    private val signalBarsProvider: () -> Int,                  // 0..3 cell bars
    private val smsPendingProvider: () -> Boolean,
    private val callPendingProvider: () -> Boolean,
    private val weatherProvider: () -> Pair<Int, Int>,          // (suzukiCode, tempByte)
    private val clock: () -> LocalTime = { LocalTime.now() },
) {
    suspend fun run(emit: suspend (HeartbeatFrame) -> Unit) {
        while (true) {
            emit(buildFrame())
            delay(HEARTBEAT_INTERVAL_MS)
        }
    }

    fun buildFrame(): HeartbeatFrame {
        val (battery, charging) = phoneBatteryProvider()
        val bucket = when {
            battery >= 75 -> "3"
            battery >= 50 -> "2"
            battery >= 25 -> "1"
            else -> "0"
        }
        val now = clock()
        val hour12 = ((now.hour + 11) % 12) + 1
        val time = "%02d%02d%02d".format(hour12, now.minute, now.second)
        val (weatherCode, tempByte) = weatherProvider()
        val sigBars = signalBarsProvider().coerceIn(0, 3).toString()

        return HeartbeatFrame(
            batteryBucket = bucket,
            charging = if (charging) "Y" else "N",
            speedStr = "",
            signalStatus = sigBars,
            timeHhmmss = time,
            smsPending = if (smsPendingProvider()) "Y" else "N",
            callPending = if (callPendingProvider()) "Y" else "N",
            weather = weatherCode,
            tempFPlus115 = tempByte,
            tailConst = 0x01,
        )
    }

    companion object {
        const val HEARTBEAT_INTERVAL_MS: Long = 1_000L
    }
}
