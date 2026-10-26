package dev.gdk.bumblebee.video

import kotlin.math.pow

/** A colour in linear light (not sRGB), 0..1 per channel; can exceed 1 for bright light. */
data class Lin(val r: Double, val g: Double, val b: Double) {
    operator fun times(k: Double) = Lin(r * k, g * k, b * k)
    operator fun plus(o: Lin) = Lin(r + o.r, g + o.g, b + o.b)
    fun mix(o: Lin, t: Double) = Lin(r + (o.r - r) * t, g + (o.g - g) * t, b + (o.b - b) * t)
    fun floats() = floatArrayOf(r.toFloat(), g.toFloat(), b.toFloat())

    companion object {
        /** From an sRGB hex like 0x3F6B2E. */
        fun hex(h: Int): Lin {
            fun c(v: Int) = (v / 255.0).pow(2.2)
            return Lin(c((h shr 16) and 255), c((h shr 8) and 255), c(h and 255))
        }
    }
}

/**
 * The colours of a video, from the sun at ride time: light, sky and haze shift from crisp midday
 * through golden low sun to a moonlit night palette. The ground palette runs from lowland green to
 * high grass and rock by height. The renderer's shaders and the preview harness both use these.
 */
class VideoLook(sun: Sun, accentArgb: Int) {
    val night = sun.isNight
    private val golden = ((16 - sun.elevationDeg) / 16).coerceIn(0.0, 1.0)

    /** Direction toward the light: the sun, or a high moon-like light at night. */
    val lightDir: Vec3 = if (night) Sun(sun.azimuthDeg + 180, 35.0).direction else sun.direction

    val sunColor: Lin = if (night) Lin(0.32, 0.40, 0.62) else Lin(1.0, 0.96, 0.90).mix(Lin(1.0, 0.70, 0.42), golden) * 1.55
    val ambient: Lin = if (night) Lin(0.05, 0.07, 0.12) else Lin(0.20, 0.25, 0.34).mix(Lin(0.22, 0.20, 0.30), golden)
    val zenith: Lin = if (night) Lin.hex(0x070B18) else Lin.hex(0x2A5A9A).mix(Lin.hex(0x24345E), golden)
    val horizon: Lin = if (night) Lin.hex(0x1A2238) else Lin.hex(0xBFD6EA).mix(Lin.hex(0xF4B48A), golden)
    val glow: Lin = if (night) Lin(0.0, 0.0, 0.0) else Lin(1.0, 0.85, 0.6).mix(Lin(1.0, 0.62, 0.32), golden)
    val haze: Lin = if (night) Lin.hex(0x141B2C) else Lin.hex(0xA9BFD4).mix(Lin.hex(0xE0A888), golden)

    /** Ground colour stops by relative height 0..1. */
    val groundStops: List<Pair<Double, Lin>> = listOf(
        0.0 to Lin.hex(0x2E5A2A),
        0.30 to Lin.hex(0x3F6B2E),
        0.55 to Lin.hex(0x5C7436),
        0.78 to Lin.hex(0x7E8248),
        0.92 to Lin.hex(0x8E8458),
        1.0 to Lin.hex(0x9C8F72),
    )
    val rock: Lin = Lin.hex(0x7A7064)

    val accent: Lin = Lin.hex(accentArgb and 0xFFFFFF)

    /** Glow strength of the path and marker; stronger at night when they light the scene. */
    val pathGlow: Double = if (night) 1.6 else 1.0

    fun ground(t: Double, slope: Double): Lin {
        val i = groundStops.indexOfLast { it.first <= t }.coerceIn(0, groundStops.size - 2)
        val (a, ca) = groundStops[i]
        val (b, cb) = groundStops[i + 1]
        val c = ca.mix(cb, ((t - a) / (b - a)).coerceIn(0.0, 1.0))
        return c.mix(rock, ((slope - 0.40) * 1.6).coerceIn(0.0, 0.6))
    }
}
