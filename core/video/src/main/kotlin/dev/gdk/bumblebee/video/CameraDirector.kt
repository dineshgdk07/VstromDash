package dev.gdk.bumblebee.video

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/** Where the camera is, what it looks at, and its vertical field of view. */
data class CameraPose(val eye: Vec3, val target: Vec3, val fovYDeg: Double) {
    companion object {
        fun blend(a: CameraPose, b: CameraPose, t: Double) =
            CameraPose(Vec3.lerp(a.eye, b.eye, t), Vec3.lerp(a.target, b.target, t), a.fovYDeg + (b.fovYDeg - a.fovYDeg) * t)

        /**
         * A move between two very different distances (a dive from high up, a climb away): the
         * viewing distance changes at a steady rate on a log scale, so the motion feels even
         * instead of rushing through the last part.
         */
        fun swoop(a: CameraPose, b: CameraPose, t: Double, divingIn: Boolean): CameraPose {
            // Diving in, the look point settles first and then the camera closes in; climbing
            // away, the camera backs off first and then the look point moves. Either way the view
            // never swings while the camera is close.
            val tt = if (divingIn) smootherstep(t * 1.35) else smootherstep(t * 1.35 - 0.35)
            val target = Vec3.lerp(a.target, b.target, tt)
            val da = (a.eye - a.target).length
            val db = (b.eye - b.target).length
            val dist = exp(ln(da) + (ln(db) - ln(da)) * t)
            val dir = Vec3.lerp((a.eye - a.target).normalized(), (b.eye - b.target).normalized(), t).normalized()
            return CameraPose(target + dir * dist, target, a.fovYDeg + (b.fovYDeg - a.fovYDeg) * t)
        }
    }
}

/** 0..1 → 0..1 with zero speed and acceleration at both ends. */
fun smootherstep(t: Double): Double {
    val x = t.coerceIn(0.0, 1.0)
    return x * x * x * (x * (x * 6 - 15) + 10)
}

/**
 * Turns [Cue]s into camera poses. Everything is a pure function of the cue, so the same ride and
 * settings always give the same video, and a frame can be rendered alone for the preview.
 *
 * - Ride: a chase camera behind and above the marker that pulls back and up where the road gets
 *   twisty, so a run of hairpins reads as a shape rather than a blur.
 * - Hold: a slow half-orbit around the moment that returns to the chase pose, so the cut back to
 *   the ride is seamless.
 * - Intro: a high overview that drifts, then dives into the chase position at the start.
 * - Finish: climbs from the end of the ride into a wide orbit framing the whole route.
 */
class CameraDirector(
    private val path: RoutePath,
    private val grid: Elevation,
    private val exaggeration: Double,
    aspect: Double,
    /** The rider's closer/further choice, × the automatic chase distance. */
    private val distanceScale: Double = 1.0,
) {
    val fovY: Double = when {
        aspect < 0.8 -> 58.0
        aspect < 1.2 -> 50.0
        else -> 40.0
    }
    private val fovX: Double = Math.toDegrees(2 * atan(tan(Math.toRadians(fovY / 2)) * aspect))

    private val minX = path.points.minOf { it.x }
    private val maxX = path.points.maxOf { it.x }
    private val minY = path.points.minOf { it.y }
    private val maxY = path.points.maxOf { it.y }
    private val center = Vec3((minX + maxX) / 2, (minY + maxY) / 2, path.points.map { it.z }.average())

    /** The route's size: half its bounding-box diagonal, at least 1 km. */
    val radius: Double = max(1_000.0, Vec3(maxX - minX, maxY - minY, 0.0).length / 2)

    /** Base chase distance behind the marker. */
    private val back: Double = (radius * 0.07).coerceIn(350.0, 4_000.0) * distanceScale

    /**
     * The camera at video time [t]: the cue poses around [t] (within [SMOOTH_S]), each lifted
     * clear of ridges, averaged with weights that fall off with distance in time. However fast the ride clock runs through a
     * hairpin, the camera can't swing faster than a person would turn it. Still a pure function of
     * [t], so frames can be rendered in any order.
     */
    fun shot(timeline: VideoTimeline, t: Double): CameraPose {
        var eye = Vec3.ZERO
        var target = Vec3.ZERO
        var fov = 0.0
        var wsum = 0.0
        for (k in -TIME_TAPS..TIME_TAPS) {
            val w = exp(-(k * k).toDouble() / (TIME_TAPS * TIME_TAPS / 2.0))
            val tk = (t + SMOOTH_S * k / TIME_TAPS).coerceIn(0.0, timeline.totalS)
            val p = pose(timeline.cue(tk), followFor(timeline, tk))
            // Lift over ridges before averaging, so the lift eases in rather than popping.
            eye += clearView(p.eye, p.target) * w
            target += p.target * w
            fov += p.fovYDeg * w
            wsum += w
        }
        return CameraPose(clearTerrain(eye * (1 / wsum), back * 0.12), target * (1 / wsum), fov / wsum)
    }

    /**
     * Raises the camera until no ridge stands between it and what it looks at. Checks the ground
     * along the sight line; each blocked sample lifts the eye just enough to see over it.
     */
    fun clearView(eye: Vec3, target: Vec3): Vec3 {
        var e = eye
        repeat(4) {
            var lift = 0.0
            for (i in 1 until SIGHT_SAMPLES) {
                val f = i.toDouble() / SIGHT_SAMPLES
                if (f > 0.97) break
                val p = Vec3.lerp(e, target, f)
                val ground = groundAt(p.x, p.y) + 15
                if (ground > p.z) {
                    // Lift the eye so the line passes over this point: z grows by 1/(1-f) per metre.
                    lift = max(lift, (ground - p.z) / (1 - f))
                }
            }
            if (lift <= 0) return e
            e = Vec3(e.x, e.y, e.z + lift)
        }
        return e
    }

    /**
     * How far behind to fly at video time [t]: far enough that the marker crosses about a third of
     * the view per second, so fast-forwarded stretches pull back and up and slow parts come close.
     */
    fun followFor(timeline: VideoTimeline, t: Double): Double {
        val a = timeline.cue((t - 0.5).coerceIn(0.0, timeline.totalS))
        val b = timeline.cue((t + 0.5).coerceIn(0.0, timeline.totalS))
        val speed = kotlin.math.abs(b.distanceM - a.distanceM) // metres per second of video
        return max(back, speed * FOLLOW_SECONDS * distanceScale).coerceAtMost(radius * 0.6)
    }

    fun pose(cue: Cue, follow: Double = back): CameraPose = when (cue.shot) {
        Shot.RIDE -> chase(cue.distanceM, follow = follow)
        Shot.HOLD -> hold(cue.distanceM, cue.progress, follow)
        Shot.INTRO -> intro(cue.progress)
        Shot.FINISH -> finish(cue.progress)
    }

    /**
     * The chase pose averaged over the road around [d], weighted toward [d]. A camera that follows
     * the road exactly snaps round every bend; this one glides, like a drone pilot leading the turn.
     */
    fun chase(d: Double, orbitRad: Double = 0.0, closer: Double = 0.0, follow: Double = back): CameraPose {
        val span = follow * 0.9
        var eye = Vec3.ZERO
        var target = Vec3.ZERO
        var wsum = 0.0
        for (k in -SMOOTH_TAPS..SMOOTH_TAPS) {
            val w = exp(-(k * k).toDouble() / (SMOOTH_TAPS * SMOOTH_TAPS / 2.0))
            val p = rawChase((d + span * k / SMOOTH_TAPS).coerceIn(0.0, path.length), orbitRad, closer, follow)
            eye += p.eye * w
            target += p.target * w
            wsum += w
        }
        eye = clearTerrain(eye * (1 / wsum), follow * 0.12)
        return CameraPose(eye, target * (1 / wsum), fovY)
    }

    private fun rawChase(d: Double, orbitRad: Double, closer: Double, follow: Double): CameraPose {
        val twist = path.twistiness(d, follow * 3)
        val dist = follow * (1 + 0.7 * twist) * (1 - closer)
        val heading = path.heading(d, dist * 0.8, dist * 1.8)
        val marker = path.at(d)
        val behind = rotate(heading * -1.0, orbitRad)
        val height = dist * (0.42 + 0.35 * twist)
        val eye = clearTerrain(marker + behind * dist + Vec3.UP * height, dist * 0.12)
        // Look a little ahead of the marker, so the road to come fills the frame.
        val target = Vec3.lerp(marker, path.at(min(path.length, d + dist * 0.6)), 0.5)
        return CameraPose(eye, target, fovY)
    }

    private fun hold(d: Double, p: Double, follow: Double): CameraPose {
        // Out and back: zero at both ends of the hold, so it joins the chase without a jump.
        val s = sin(PI * smootherstep(p))
        return chase(d, orbitRad = s * Math.toRadians(55.0), closer = 0.28 * s, follow = follow)
    }

    /** A high, slightly tilted view framing the whole route, seen from [azimuthRad] (0 = south). */
    fun overview(azimuthRad: Double = 0.0, pitchDeg: Double = 52.0): CameraPose {
        val half = Math.toRadians(min(fovY, fovX) / 2)
        val dist = radius / tan(half) * 1.15
        val pitch = Math.toRadians(pitchDeg)
        val dir = Vec3(-sin(azimuthRad), -cos(azimuthRad), 0.0)
        val eye = center + dir * (dist * cos(pitch)) + Vec3.UP * (dist * sin(pitch))
        return CameraPose(eye, center, fovY)
    }

    private fun intro(p: Double): CameraPose {
        val drift = Math.toRadians(-12.0 + 12.0 * p)
        val high = overview(drift, pitchDeg = 58.0 - 6 * p)
        if (p < 0.2) return high
        return CameraPose.swoop(high, chase(0.0), smootherstep((p - 0.2) / 0.8), divingIn = true)
    }

    private fun finish(p: Double): CameraPose {
        if (p > 0.55) return overview(Math.toRadians(60.0 * smootherstep((p - 0.55) / 0.45)), pitchDeg = 48.0)
        return CameraPose.swoop(chase(path.length), overview(0.0, 48.0), smootherstep(p / 0.55), divingIn = false)
    }

    /** Keeps the camera at least [clearance] above the ground under it and a little around it. */
    private fun clearTerrain(eye: Vec3, clearance: Double): Vec3 {
        val r = clearance * 2
        var ground = groundAt(eye.x, eye.y)
        for ((dx, dy) in listOf(r to 0.0, -r to 0.0, 0.0 to r, 0.0 to -r)) ground = max(ground, groundAt(eye.x + dx, eye.y + dy))
        return if (eye.z < ground + clearance) Vec3(eye.x, eye.y, ground + clearance) else eye
    }

    private fun groundAt(x: Double, y: Double) = grid.heightAt(x, y) * exaggeration

    private companion object {
        const val SMOOTH_TAPS = 6
        const val TIME_TAPS = 8
        const val SMOOTH_S = 0.6
        const val FOLLOW_SECONDS = 1.4
        const val SIGHT_SAMPLES = 48
    }

    private fun rotate(v: Vec3, rad: Double): Vec3 {
        val c = cos(rad)
        val s = sin(rad)
        return Vec3(v.x * c - v.y * s, v.x * s + v.y * c, v.z)
    }
}
