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
class VideoLook(sun: Sun, val accentArgb: Int) {
    /** 0 in daylight, rising through dusk to 1 at night, so the palette never jumps. */
    val nightness: Double = smoothstep(-1.0, -9.0, sun.elevationDeg)
    val night: Boolean get() = nightness > 0.5
    private val golden = ((16 - sun.elevationDeg) / 16).coerceIn(0.0, 1.0)
    private val n = nightness

    /** Direction toward the light: the sun, easing up to a high moon-like light as night falls. */
    val lightDir: Vec3 = Vec3.lerp(
        sun.direction.let { if (it.z < 0.02) Vec3(it.x, it.y, 0.02).normalized() else it },
        Sun(sun.azimuthDeg, 35.0).direction,
        n,
    ).normalized()

    val sunColor: Lin = (Lin(1.0, 0.96, 0.90).mix(Lin(1.0, 0.70, 0.42), golden) * 1.55).mix(Lin(0.32, 0.40, 0.62), n)
    val ambient: Lin = Lin(0.20, 0.25, 0.34).mix(Lin(0.22, 0.20, 0.30), golden).mix(Lin(0.05, 0.07, 0.12), n)
    val zenith: Lin = Lin.hex(0x2A5A9A).mix(Lin.hex(0x24345E), golden).mix(Lin.hex(0x070B18), n)
    val horizon: Lin = Lin.hex(0xBFD6EA).mix(Lin.hex(0xF4B48A), golden).mix(Lin.hex(0x1A2238), n)
    val glow: Lin = Lin(1.0, 0.85, 0.6).mix(Lin(1.0, 0.62, 0.32), golden).mix(Lin(0.0, 0.0, 0.0), n)
    val haze: Lin = Lin.hex(0xA9BFD4).mix(Lin.hex(0xE0A888), golden).mix(Lin.hex(0x141B2C), n)

    /** Water: sky-tinted by day, near black with the sky's glow at night. */
    val water: Lin = Lin.hex(0x2F5F7A).mix(Lin.hex(0x2A3550), golden).mix(Lin.hex(0x070C16), n)

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
    val pathGlow: Double = 1.0 + 0.6 * n

    fun ground(t: Double, slope: Double): Lin {
        val i = groundStops.indexOfLast { it.first <= t }.coerceIn(0, groundStops.size - 2)
        val (a, ca) = groundStops[i]
        val (b, cb) = groundStops[i + 1]
        val c = ca.mix(cb, ((t - a) / (b - a)).coerceIn(0.0, 1.0))
        return c.mix(rock, ((slope - 0.40) * 1.6).coerceIn(0.0, 0.6))
    }

    private companion object {
        fun smoothstep(a: Double, b: Double, x: Double): Double {
            val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
            return t * t * (3 - 2 * t)
        }
    }
}
