package dev.gdk.bumblebee.video

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Where the camera is, what it looks at, its vertical field of view, and [rollDeg], how far it
 * leans about its view axis (positive lowers its right side, as when banking into a right turn).
 */
data class CameraPose(val eye: Vec3, val target: Vec3, val fovYDeg: Double, val rollDeg: Double = 0.0) {
    companion object {
        fun blend(a: CameraPose, b: CameraPose, t: Double) = CameraPose(
            Vec3.lerp(a.eye, b.eye, t),
            Vec3.lerp(a.target, b.target, t),
            a.fovYDeg + (b.fovYDeg - a.fovYDeg) * t,
            a.rollDeg + (b.rollDeg - a.rollDeg) * t,
        )

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
            return CameraPose(
                target + dir * dist,
                target,
                a.fovYDeg + (b.fovYDeg - a.fovYDeg) * t,
                a.rollDeg + (b.rollDeg - a.rollDeg) * t,
            )
        }
    }
}

/** 0..1 → 0..1 with zero speed and acceleration at both ends. */
fun smootherstep(t: Double): Double {
    val x = t.coerceIn(0.0, 1.0)
    return x * x * x * (x * (x * 6 - 15) + 10)
}

/** The rider's camera choices, from [VideoConfig]. */
data class CameraStyle(
    val distanceScale: Double = 1.0,
    /** How much to lean into turns, 0..1. */
    val bank: Double = 0.6,
    /** Rise toward top-down over hairpins. */
    val hairpinView: Boolean = true,
    /** A move per kind of moment rather than one orbit for all. */
    val momentShots: Boolean = true,
    /** The finale goes top-down so the route can redraw in light. */
    val mapReveal: Boolean = true,
) {
    companion object {
        fun of(c: VideoConfig) = CameraStyle(c.cameraDistance, c.bank, c.hairpinView, c.momentShots, c.mapReveal)
    }
}

/**
 * Turns [Cue]s into camera poses. Everything is a pure function of the cue, so the same ride and
 * settings always give the same video, and a frame can be rendered alone for the preview.
 *
 * - Ride: a chase camera behind and above the marker that leans into turns, and pulls back and up
 *   where the road gets twisty, nearly top-down over hairpins, so they read as a shape.
 * - Hold: a move chosen by the moment: low and fast alongside for top speed, a slow high orbit at
 *   the summit, a look back down a climb, a gentle push-in at a break. Each returns to the chase
 *   pose, so the cut back to the ride is seamless.
 * - Cold open: a teaser orbit around the best moment, before anything else.
 * - Intro: a high overview that drifts, then dives into the chase position at the start.
 * - Finish: climbs from the end of the ride to a top-down view for the map reveal (or a wide
 *   orbit), then turns slowly.
 */
class CameraDirector(
    private val path: RoutePath,
    private val grid: Elevation,
    private val exaggeration: Double,
    aspect: Double,
    private val style: CameraStyle = CameraStyle(),
) {
    /** Kept for callers that only set a distance. */
    constructor(path: RoutePath, grid: Elevation, exaggeration: Double, aspect: Double, distanceScale: Double) :
        this(path, grid, exaggeration, aspect, CameraStyle(distanceScale = distanceScale))

    private val distanceScale = style.distanceScale

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
     * The camera at video time [t]: the cue poses around [t] (within [SMOOTH_S], but never across
     * a hard cut), each lifted clear of ridges, averaged with weights that fall off with distance
     * in time. However fast the ride clock runs through a hairpin, the camera can't swing faster
     * than a person would turn it. Still a pure function of [t], so frames render in any order.
     */
    fun shot(timeline: VideoTimeline, t: Double): CameraPose {
        val seg = timeline.segment(t)
        var eye = Vec3.ZERO
        var target = Vec3.ZERO
        var fov = 0.0
        var roll = 0.0
        var wsum = 0.0
        for (k in -TIME_TAPS..TIME_TAPS) {
            val w = exp(-(k * k).toDouble() / (TIME_TAPS * TIME_TAPS / 2.0))
            val tk = (t + SMOOTH_S * k / TIME_TAPS).coerceIn(seg.start, seg.endInclusive)
            val p = pose(timeline.cue(tk), followFor(timeline, tk))
            // Lift over ridges before averaging, so the lift eases in rather than popping.
            eye += clearView(p.eye, p.target) * w
            target += p.target * w
            fov += p.fovYDeg * w
            roll += p.rollDeg * w
            wsum += w
        }
        return CameraPose(clearTerrain(eye * (1 / wsum), back * 0.12), target * (1 / wsum), fov / wsum, roll / wsum)
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
        val seg = timeline.segment(t)
        val a = timeline.cue((t - 0.5).coerceIn(seg.start, seg.endInclusive))
        val b = timeline.cue((t + 0.5).coerceIn(seg.start, seg.endInclusive))
        val speed = abs(b.distanceM - a.distanceM) // metres per second of video
        return max(back, speed * FOLLOW_SECONDS * distanceScale).coerceAtMost(radius * 0.6)
    }

    fun pose(cue: Cue, follow: Double = back): CameraPose = when (cue.shot) {
        Shot.COLD -> teaser(cue.distanceM, cue.progress)
        Shot.RIDE -> chase(cue.distanceM, follow = follow)
        Shot.HOLD -> hold(cue.distanceM, cue.progress, follow, cue.moment?.kind)
        Shot.INTRO -> intro(cue.progress)
        Shot.FINISH -> finish(cue.progress)
    }

    /**
     * The chase pose averaged over the road around [d], weighted toward [d]. A camera that follows
     * the road exactly snaps round every bend; this one glides, like a drone pilot leading the turn.
     */
    fun chase(
        d: Double,
        orbitRad: Double = 0.0,
        closer: Double = 0.0,
        follow: Double = back,
        lower: Double = 0.0,
        fovScale: Double = 1.0,
    ): CameraPose {
        val span = follow * 0.9
        var eye = Vec3.ZERO
        var target = Vec3.ZERO
        var roll = 0.0
        var wsum = 0.0
        for (k in -SMOOTH_TAPS..SMOOTH_TAPS) {
            val w = exp(-(k * k).toDouble() / (SMOOTH_TAPS * SMOOTH_TAPS / 2.0))
            val p = rawChase((d + span * k / SMOOTH_TAPS).coerceIn(0.0, path.length), orbitRad, closer, follow, lower)
            eye += p.eye * w
            target += p.target * w
            roll += p.rollDeg * w
            wsum += w
        }
        eye = clearTerrain(eye * (1 / wsum), follow * 0.12)
        return CameraPose(eye, target * (1 / wsum), fovY * fovScale, roll / wsum)
    }

    private fun rawChase(d: Double, orbitRad: Double, closer: Double, follow: Double, lower: Double): CameraPose {
        val twist = path.twistiness(d, follow * 3)
        // Over hairpins, rise toward top-down so the switchbacks read as a shape.
        val hairpin = if (style.hairpinView) smoothstep(0.3, 0.7, twist) else 0.0
        val dist = follow * (1 + 0.7 * twist + 0.3 * hairpin) * (1 - closer)
        val heading = path.heading(d, dist * 0.8, dist * 1.8)
        val marker = path.at(d)
        val behind = rotate(heading * -1.0, orbitRad)
        val height = dist * (0.42 + 0.35 * twist + 1.1 * hairpin) * (1 - lower)
        val eye = clearTerrain(marker + behind * dist + Vec3.UP * height, dist * 0.12)
        // Look a little ahead of the marker, so the road to come fills the frame.
        val target = Vec3.lerp(marker, path.at(min(path.length, d + dist * 0.6)), 0.5)
        // Lean into the turn: how much the heading swings across the chase distance.
        val before = path.heading(d, dist * 1.2, 0.0)
        val after = path.heading(d, 0.0, dist * 1.2)
        val turn = Math.toDegrees(atan2(before.x * after.y - before.y * after.x, before.dot(after)))
        val roll = (-turn * 0.3).coerceIn(-MAX_ROLL, MAX_ROLL) * style.bank * (1 - hairpin)
        return CameraPose(eye, target, fovY, roll)
    }

    /**
     * The camera at a moment. Every move is shaped by s = sin(π·p), zero at both ends, so it
     * leaves and rejoins the chase pose exactly.
     */
    private fun hold(d: Double, p: Double, follow: Double, kind: MomentKind?): CameraPose {
        val s = sin(PI * smootherstep(p))
        if (!style.momentShots) return chase(d, orbitRad = s * Math.toRadians(55.0), closer = 0.28 * s, follow = follow)
        return when (kind) {
            // Low and fast alongside, the view widening as if the speed pulls it.
            MomentKind.TOP_SPEED -> chase(d, orbitRad = s * Math.toRadians(75.0), closer = 0.35 * s, follow = follow, lower = 0.6 * s, fovScale = 1 + 0.18 * s)
            // A slow, wide orbit at the top.
            MomentKind.HIGHEST -> chase(d, orbitRad = s * Math.toRadians(85.0), closer = 0.1 * s, follow = follow)
            // Swing round to look back down the climb.
            MomentKind.CLIMB -> chase(d, orbitRad = s * Math.toRadians(115.0), closer = -0.15 * s, follow = follow, lower = -0.4 * s)
            // A gentle push-in.
            MomentKind.STOP, MomentKind.REFUEL -> chase(d, orbitRad = s * Math.toRadians(20.0), closer = 0.45 * s, follow = follow)
            else -> chase(d, orbitRad = s * Math.toRadians(55.0), closer = 0.28 * s, follow = follow)
        }
    }

    /** The cold open: half an orbit round the best moment, slightly closer than the chase. */
    private fun teaser(d: Double, p: Double): CameraPose =
        chase(d, orbitRad = Math.toRadians(35.0 + 70.0 * p), closer = 0.25, follow = back)

    /** A high view framing the whole route, seen from [azimuthRad] (0 = south) at [pitchDeg]. */
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
        val pitch = if (style.mapReveal) 78.0 else 48.0
        if (p > CLIMB_OUT) return overview(Math.toRadians(40.0 * smootherstep((p - CLIMB_OUT) / (1 - CLIMB_OUT))), pitchDeg = pitch - 6 * smootherstep((p - CLIMB_OUT) / (1 - CLIMB_OUT)))
        return CameraPose.swoop(chase(path.length), overview(0.0, pitch), smootherstep(p / CLIMB_OUT), divingIn = false)
    }

    /** Keeps the camera at least [clearance] above the ground under it and a little around it. */
    private fun clearTerrain(eye: Vec3, clearance: Double): Vec3 {
        val r = clearance * 2
        var ground = groundAt(eye.x, eye.y)
        for ((dx, dy) in listOf(r to 0.0, -r to 0.0, 0.0 to r, 0.0 to -r)) ground = max(ground, groundAt(eye.x + dx, eye.y + dy))
        return if (eye.z < ground + clearance) Vec3(eye.x, eye.y, ground + clearance) else eye
    }

    private fun groundAt(x: Double, y: Double) = grid.heightAt(x, y) * exaggeration

    private fun smoothstep(a: Double, b: Double, x: Double): Double {
        val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    private companion object {
        const val SMOOTH_TAPS = 6
        const val TIME_TAPS = 8
        const val SMOOTH_S = 0.6
        const val FOLLOW_SECONDS = 1.4
        const val SIGHT_SAMPLES = 48
        const val MAX_ROLL = 12.0

        /** Share of the finish spent climbing away from the end of the ride. */
        const val CLIMB_OUT = 0.45
    }

    private fun rotate(v: Vec3, rad: Double): Vec3 {
        val c = cos(rad)
        val s = sin(rad)
        return Vec3(v.x * c - v.y * s, v.x * s + v.y * c, v.z)
    }
}
