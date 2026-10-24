package dev.gdk.bumblebee.ride

import kotlin.math.roundToInt

enum class SpeedSource { GPS, BLE, NONE }

data class ResolvedSpeed(val speedKmh: Int, val source: SpeedSource)

object SpeedResolver {
    const val MAX_GPS_AGE_MS = 5_000L
    const val MAX_ACCURACY_M = 25f
    const val STANDSTILL_KMH = 3

    fun resolve(gps: GpsFix?, nowMs: Long, bleSpeedKmh: Int?): ResolvedSpeed {
        if (gpsUsable(gps, nowMs)) {
            val kmh = ((gps!!.speedMps!!) * 3.6f).roundToInt()
            return ResolvedSpeed(clampStandstill(kmh), SpeedSource.GPS)
        }
        if (bleSpeedKmh != null) {
            return ResolvedSpeed(clampStandstill(bleSpeedKmh), SpeedSource.BLE)
        }
        return ResolvedSpeed(0, SpeedSource.NONE)
    }

    private fun gpsUsable(gps: GpsFix?, nowMs: Long): Boolean {
        if (gps == null || gps.speedMps == null) return false
        if (nowMs - gps.ts > MAX_GPS_AGE_MS) return false
        val acc = gps.accuracyM
        if (acc != null && acc > MAX_ACCURACY_M) return false
        return true
    }

    private fun clampStandstill(kmh: Int): Int = if (kmh < STANDSTILL_KMH) 0 else kmh
}

object CompassCardinal {
    private val labels = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    fun label(headingDeg: Float?): String? {
        if (headingDeg == null) return null
        val d = ((headingDeg % 360f) + 360f) % 360f
        val i = ((d + 22.5f) / 45f).toInt() % 8
        return labels[i]
    }
}
