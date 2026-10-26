package dev.gdk.bumblebee.video

import kotlin.math.tan

/**
 * The camera as the GPU needs it. Geometry is drawn relative to the eye (positions minus [eye]),
 * so float precision holds up across a route hundreds of kilometres wide.
 */
class ViewMath(pose: CameraPose, val aspect: Double, val near: Double, val far: Double) {
    val eye: Vec3 = pose.eye
    val forward: Vec3 = (pose.target - pose.eye).normalized()
    private val level: Vec3 = forward.cross(Vec3.UP).normalized().let { if (it.length < 0.5) Vec3(1.0, 0.0, 0.0) else it }
    private val levelUp: Vec3 = level.cross(forward)
    private val roll = Math.toRadians(pose.rollDeg)

    /** Screen right and up in the world, after the camera's roll (positive lowers the right). */
    val right: Vec3 = level * kotlin.math.cos(roll) - levelUp * kotlin.math.sin(roll)
    val up: Vec3 = levelUp * kotlin.math.cos(roll) + level * kotlin.math.sin(roll)
    val tanHalfFov: Double = tan(Math.toRadians(pose.fovYDeg / 2))

    /** Column-major view-projection for eye-relative positions. */
    val viewProj: FloatArray = run {
        val f = 1.0 / tanHalfFov
        val p00 = f / aspect
        val p11 = f
        val p22 = -(far + near) / (far - near)
        val p32 = -2 * far * near / (far - near)
        // View rows: right, up, -forward (camera looks down -z).
        val v = arrayOf(
            doubleArrayOf(right.x, right.y, right.z),
            doubleArrayOf(up.x, up.y, up.z),
            doubleArrayOf(-forward.x, -forward.y, -forward.z),
        )
        val m = FloatArray(16)
        for (c in 0 until 3) {
            m[c * 4 + 0] = (p00 * v[0][c]).toFloat()
            m[c * 4 + 1] = (p11 * v[1][c]).toFloat()
            m[c * 4 + 2] = (p22 * v[2][c]).toFloat()
            m[c * 4 + 3] = (-v[2][c]).toFloat()
        }
        m[12] = 0f
        m[13] = 0f
        m[14] = p32.toFloat()
        m[15] = 0f
        m
    }

    /** Screen position in pixels (origin top-left) and view depth, or null behind the camera. */
    fun project(p: Vec3, width: Int, height: Int): DoubleArray? {
        val v = p - eye
        val z = v.dot(forward)
        if (z < near) return null
        val sx = v.dot(right) / (z * tanHalfFov * aspect)
        val sy = v.dot(up) / (z * tanHalfFov)
        return doubleArrayOf((sx + 1) / 2 * width, (1 - sy) / 2 * height, z)
    }

    /** Applies [viewProj] to an eye-relative point: clip-space x, y, z, w. */
    fun clip(rel: Vec3): DoubleArray {
        val m = viewProj
        return DoubleArray(4) { r -> m[r] * rel.x + m[4 + r] * rel.y + m[8 + r] * rel.z + m[12 + r] }
    }
}
