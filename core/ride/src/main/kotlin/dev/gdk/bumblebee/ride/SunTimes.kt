package dev.gdk.bumblebee.ride

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

data class SunDay(val sunriseTs: Long?, val sunsetTs: Long?)

object SunTimes {
    private const val ZENITH = 90.833
    private const val WINDOW_MS = 30L * 60_000L

    /** NOAA solar position. Null sunrise or sunset means polar day or night. */
    fun of(date: LocalDate, lat: Double, lng: Double, zone: ZoneId): SunDay {
        val jd0 = julianDay(date)
        val riseMin = eventUtcMinutes(jd0, lat, lng, rise = true)
        val setMin = eventUtcMinutes(jd0, lat, lng, rise = false)
        val base = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        return SunDay(
            sunriseTs = riseMin?.let { base.plusMillis((it * 60_000.0).toLong()).toEpochMilli() },
            sunsetTs = setMin?.let { base.plusMillis((it * 60_000.0).toLong()).toEpochMilli() },
        )
    }

    /** "sunrise ride" or "sunset ride" when moving time overlaps ±30 minutes. Sunrise wins. */
    fun tag(startTs: Long, endTs: Long, sun: SunDay): String? {
        if (overlaps(startTs, endTs, sun.sunriseTs)) return "sunrise ride"
        if (overlaps(startTs, endTs, sun.sunsetTs)) return "sunset ride"
        return null
    }

    /** "22°C · Clear Sky · sunrise ride", with any missing part dropped. */
    fun weatherLine(tempC: Double?, label: String?, sunTag: String?): String? {
        val parts = ArrayList<String>(3)
        if (tempC != null) parts += "${tempC.roundToInt()}°C"
        if (!label.isNullOrBlank()) parts += label
        if (!sunTag.isNullOrBlank()) parts += sunTag
        return parts.joinToString(" · ").ifEmpty { null }
    }

    private fun overlaps(startTs: Long, endTs: Long, eventTs: Long?): Boolean {
        if (eventTs == null) return false
        val open = eventTs - WINDOW_MS
        val close = eventTs + WINDOW_MS
        return startTs <= close && endTs >= open
    }

    private fun eventUtcMinutes(jd0: Double, lat: Double, lngEast: Double, rise: Boolean): Double? {
        var guess = if (rise) 0.0 else 12.0 * 60.0
        repeat(5) {
            val next = once(jd0, lat, lngEast, rise, guess) ?: return null
            guess = next
        }
        return guess
    }

    private fun once(jd0: Double, lat: Double, lngEast: Double, rise: Boolean, guessUtcMin: Double): Double? {
        val (decl, eqtime) = declAndEq(jd0 + guessUtcMin / 1440.0)
        val latr = Math.toRadians(lat)
        val decr = Math.toRadians(decl)
        val cosH = cos(Math.toRadians(ZENITH)) / (cos(latr) * cos(decr)) - tan(latr) * tan(decr)
        if (cosH > 1.0 || cosH < -1.0) return null
        val ha = Math.toDegrees(acos(cosH))
        val minutes = if (rise) {
            720.0 - 4.0 * (lngEast + ha) - eqtime
        } else {
            720.0 - 4.0 * (lngEast - ha) - eqtime
        }
        return (minutes % 1440.0 + 1440.0) % 1440.0
    }

    private fun declAndEq(jd: Double): Pair<Double, Double> {
        val t = (jd - 2451545.0) / 36525.0
        val l0 = (280.46646 + t * (36000.76983 + t * 0.0003032)) % 360.0
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val mr = Math.toRadians(m)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val c = (1.914602 - t * (0.004817 + 0.000014 * t)) * sin(mr) +
            (0.019993 - 0.000101 * t) * sin(2 * mr) +
            0.000289 * sin(3 * mr)
        val sunTrue = l0 + c
        val omega = 125.04 - 1934.136 * t
        val lam = sunTrue - 0.00569 - 0.00478 * sin(Math.toRadians(omega))
        val eps0 = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val eps = eps0 + 0.00256 * cos(Math.toRadians(omega))
        val decl = Math.toDegrees(asin(sin(Math.toRadians(eps)) * sin(Math.toRadians(lam))))
        val y = tan(Math.toRadians(eps / 2.0)).pow(2.0)
        val l0r = Math.toRadians(l0)
        val eqtime = 4.0 * Math.toDegrees(
            y * sin(2 * l0r) -
                2 * e * sin(mr) +
                4 * e * y * sin(mr) * cos(2 * l0r) -
                0.5 * y * y * sin(4 * l0r) -
                1.25 * e * e * sin(2 * mr),
        )
        return decl to eqtime
    }

    /** Julian day at 0h UT. */
    private fun julianDay(date: LocalDate): Double {
        var y = date.year
        var m = date.monthValue
        val a = (14 - m) / 12
        y += 4800 - a
        m += 12 * a - 3
        val jdn = date.dayOfMonth + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        return jdn - 0.5
    }
}
