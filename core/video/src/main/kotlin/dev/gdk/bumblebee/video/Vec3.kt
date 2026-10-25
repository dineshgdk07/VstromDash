package dev.gdk.bumblebee.video

import kotlin.math.sqrt

/** A point or direction in the local frame: metres east (x), north (y) and up (z). */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = Vec3(x * k, y * k, z * k)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val length: Double get() = sqrt(x * x + y * y + z * z)
    val flatLength: Double get() = sqrt(x * x + y * y)
    fun normalized(): Vec3 = length.let { if (it < 1e-12) this else this * (1.0 / it) }
    fun flat(): Vec3 = Vec3(x, y, 0.0)

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
        val UP = Vec3(0.0, 0.0, 1.0)
        fun lerp(a: Vec3, b: Vec3, t: Double) = a + (b - a) * t
    }
}
