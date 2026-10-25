package dev.gdk.bumblebee.video

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Where the sun is: [azimuthDeg] clockwise from north, [elevationDeg] above the horizon. */
data class Sun(val azimuthDeg: Double, val elevationDeg: Double) {
    /** Unit vector pointing toward the sun in the local frame (x east, y north, z up). */
    val direction: Vec3
        get() {
            val az = Math.toRadians(azimuthDeg)
            val el = Math.toRadians(elevationDeg)
            return Vec3(sin(az) * cos(el), cos(az) * cos(el), sin(el))
        }

    val isNight get() = elevationDeg < -4
    val isGolden get() = elevationDeg in -4.0..12.0
}

/** NOAA's solar position approximation, good to a fraction of a degree. */
object SunPosition {
    fun at(epochMs: Long, lat: Double, lng: Double): Sun {
        val jd = epochMs / 86_400_000.0 + 2440587.5
        val t = (jd - 2451545.0) / 36525.0
        val l0 = (280.46646 + t * (36000.76983 + t * 0.0003032)).mod(360.0)
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val mr = Math.toRadians(m)
        val c = sin(mr) * (1.914602 - t * (0.004817 + 0.000014 * t)) + sin(2 * mr) * (0.019993 - 0.000101 * t) + sin(3 * mr) * 0.000289
        val trueLong = l0 + c
        val omega = 125.04 - 1934.136 * t
        val lambda = trueLong - 0.00569 - 0.00478 * sin(Math.toRadians(omega))
        val eps0 = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val eps = eps0 + 0.00256 * cos(Math.toRadians(omega))
        val decl = asin(sin(Math.toRadians(eps)) * sin(Math.toRadians(lambda)))
        val y = Math.tan(Math.toRadians(eps / 2)).let { it * it }
        val l0r = Math.toRadians(l0)
        val eqTime = 4 * Math.toDegrees(
            y * sin(2 * l0r) - 2 * e * sin(mr) + 4 * e * y * sin(mr) * cos(2 * l0r) -
                0.5 * y * y * sin(4 * l0r) - 1.25 * e * e * sin(2 * mr),
        )
        val utcMin = (epochMs / 60_000.0).mod(1440.0)
        val trueSolar = (utcMin + eqTime + 4 * lng).mod(1440.0)
        val hourAngle = Math.toRadians(if (trueSolar / 4 < 0) trueSolar / 4 + 180 else trueSolar / 4 - 180)
        val latR = Math.toRadians(lat)
        val cosZenith = (sin(latR) * sin(decl) + cos(latR) * cos(decl) * cos(hourAngle)).coerceIn(-1.0, 1.0)
        val zenith = acos(cosZenith)
        val az = Math.toDegrees(
            atan2(sin(hourAngle), cos(hourAngle) * sin(latR) - Math.tan(decl) * cos(latR)),
        ) + 180
        return Sun(az.mod(360.0), 90 - Math.toDegrees(zenith))
    }
}
