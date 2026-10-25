package dev.gdk.bumblebee.nav

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A latitude and longitude, in degrees. */
data class LatLng(val lat: Double, val lng: Double)

/** One turn-by-turn step: do [arrow] at the end of it. [endIndex] is where it ends on the route line. */
data class RouteStep(
    val arrow: ArrowKind,
    val instruction: String,
    val distanceM: Double,
    val endIndex: Int,
)

/** A planned route: the line to follow and the steps along it. */
data class NavRoute(
    val points: List<LatLng>,
    val steps: List<RouteStep>,
    val distanceM: Double,
    val durationS: Double,
    val destination: String,
)

/** Where the rider is on the route right now. */
data class NavProgress(
    val step: RouteStep?,
    val nextStep: RouteStep?,
    val toTurnM: Double,
    val remainingM: Double,
    val remainingS: Double,
    val offRouteM: Double,
    val arrived: Boolean,
    /** Index of the route point just behind the rider, for drawing the ridden part. */
    val index: Int,
)

object Polyline {
    /** Decodes Google's encoded polyline format, also used by OpenRouteService. */
    fun decode(encoded: String, precision: Int = 5): List<LatLng> {
        val factor = Math.pow(10.0, precision.toDouble())
        val out = ArrayList<LatLng>()
        var i = 0
        var lat = 0L
        var lng = 0L
        while (i < encoded.length) {
            for (axis in 0..1) {
                var shift = 0
                var result = 0L
                var b: Int
                do {
                    b = encoded[i++].code - 63
                    result = result or ((b and 0x1f).toLong() shl shift)
                    shift += 5
                } while (b >= 0x20 && i < encoded.length)
                val delta = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
                if (axis == 0) lat += delta else lng += delta
            }
            out += LatLng(lat / factor, lng / factor)
        }
        return out
    }

    fun encode(points: List<LatLng>, precision: Int = 5): String {
        val factor = Math.pow(10.0, precision.toDouble())
        val sb = StringBuilder()
        var pLat = 0L
        var pLng = 0L
        fun put(v: Long) {
            var x = if (v < 0) (v shl 1).inv() else v shl 1
            while (x >= 0x20) {
                sb.append(((0x20 or (x and 0x1f).toInt()) + 63).toChar())
                x = x shr 5
            }
            sb.append((x + 63).toInt().toChar())
        }
        for (p in points) {
            val la = (p.lat * factor).roundToInt().toLong()
            val ln = (p.lng * factor).roundToInt().toLong()
            put(la - pLat)
            put(ln - pLng)
            pLat = la
            pLng = ln
        }
        return sb.toString()
    }
}

/** Each routing service's turn names, as the cluster's arrows. */
object Maneuvers {
    /** Google Routes API `navigationInstruction.maneuver`. */
    fun google(maneuver: String?, instruction: String?): ArrowKind = when (maneuver?.uppercase()) {
        "TURN_LEFT" -> ArrowKind.LEFT
        "TURN_RIGHT" -> ArrowKind.RIGHT
        "TURN_SLIGHT_LEFT" -> ArrowKind.SLIGHT_LEFT
        "TURN_SLIGHT_RIGHT" -> ArrowKind.SLIGHT_RIGHT
        "TURN_SHARP_LEFT" -> ArrowKind.SHARP_LEFT
        "TURN_SHARP_RIGHT" -> ArrowKind.SHARP_RIGHT
        "UTURN_LEFT", "UTURN_RIGHT" -> ArrowKind.U_TURN
        "RAMP_LEFT" -> ArrowKind.EXIT_LEFT
        "RAMP_RIGHT" -> ArrowKind.EXIT_RIGHT
        "FORK_LEFT" -> ArrowKind.FORK_LEFT
        "FORK_RIGHT" -> ArrowKind.FORK_RIGHT
        "MERGE" -> if (instruction?.contains("left", ignoreCase = true) == true) ArrowKind.MERGE_LEFT else ArrowKind.MERGE_RIGHT
        "ROUNDABOUT_LEFT", "ROUNDABOUT_RIGHT" -> ArrowKind.ROUNDABOUT
        "STRAIGHT", "DEPART", "NAME_CHANGE" -> ArrowKind.STRAIGHT
        "FERRY", "FERRY_TRAIN" -> ArrowKind.STRAIGHT
        else -> ManeuverText.classify(instruction) ?: ArrowKind.STRAIGHT
    }

    /** OpenRouteService step `type`. */
    fun ors(type: Int, instruction: String?): ArrowKind = when (type) {
        0 -> ArrowKind.LEFT
        1 -> ArrowKind.RIGHT
        2 -> ArrowKind.SHARP_LEFT
        3 -> ArrowKind.SHARP_RIGHT
        4 -> ArrowKind.SLIGHT_LEFT
        5 -> ArrowKind.SLIGHT_RIGHT
        6, 11 -> ArrowKind.STRAIGHT
        7, 8 -> ArrowKind.ROUNDABOUT
        9 -> ArrowKind.U_TURN
        10 -> ArrowKind.ARRIVE
        12 -> ArrowKind.KEEP_LEFT
        13 -> ArrowKind.KEEP_RIGHT
        else -> ManeuverText.classify(instruction) ?: ArrowKind.STRAIGHT
    }
}

/**
 * Follows the rider along a [NavRoute]: snaps each fix to the route line, says which step is
 * next, how far to the turn and the end, and notices when the rider has left the route.
 */
class RouteGuide(val route: NavRoute) {
    /** Distance along the route at each point, from the start. */
    private val along: DoubleArray = DoubleArray(route.points.size).also { a ->
        for (i in 1 until route.points.size) a[i] = a[i - 1] + metres(route.points[i - 1], route.points[i])
    }
    private var index = 0
    private var offSince: Long? = null

    val totalM: Double get() = along.lastOrNull() ?: 0.0

    /**
     * Where [fix] is on the route. Searches forward from the last match (plus a little back) so
     * a road that passes near itself doesn't jump the rider ahead.
     */
    fun progress(fix: LatLng): NavProgress {
        val pts = route.points
        if (pts.size < 2) return NavProgress(null, null, 0.0, 0.0, 0.0, 0.0, true, 0)
        var bestI = index
        var bestT = 0.0
        var bestD = Double.MAX_VALUE
        val from = (index - 3).coerceAtLeast(0)
        val to = (index + 400).coerceAtMost(pts.size - 2)
        for (i in from..to) {
            val (t, d) = project(fix, pts[i], pts[i + 1])
            if (d < bestD) {
                bestD = d
                bestI = i
                bestT = t
            }
        }
        // Lost the thread (a long tunnel, a big jump): search the whole route.
        if (bestD > 150) {
            for (i in 0 until pts.size - 1) {
                val (t, d) = project(fix, pts[i], pts[i + 1])
                if (d < bestD) {
                    bestD = d
                    bestI = i
                    bestT = t
                }
            }
        }
        index = bestI
        val done = along[bestI] + (along[bestI + 1] - along[bestI]) * bestT
        val remaining = (totalM - done).coerceAtLeast(0.0)
        val stepIdx = route.steps.indexOfFirst { along[it.endIndex.coerceIn(0, pts.lastIndex)] > done + 1 }
        val step = route.steps.getOrNull(stepIdx)
        val toTurn = step?.let { (along[it.endIndex.coerceIn(0, pts.lastIndex)] - done).coerceAtLeast(0.0) } ?: remaining
        val pace = if (totalM > 0) route.durationS / totalM else 0.0
        return NavProgress(
            step = step,
            nextStep = route.steps.getOrNull(stepIdx + 1),
            toTurnM = toTurn,
            remainingM = remaining,
            remainingS = remaining * pace,
            offRouteM = bestD,
            arrived = remaining < ARRIVE_M,
            index = bestI,
        )
    }

    /**
     * True once the rider has been more than [OFF_ROUTE_M] from the line for [OFF_ROUTE_MS], so a
     * GPS wobble or a service-lane detour doesn't trigger a re-route.
     */
    fun shouldReroute(p: NavProgress, now: Long): Boolean {
        if (p.offRouteM <= OFF_ROUTE_M) {
            offSince = null
            return false
        }
        val since = offSince ?: now.also { offSince = it }
        return now - since >= OFF_ROUTE_MS
    }

    companion object {
        const val ARRIVE_M = 30.0
        const val OFF_ROUTE_M = 60.0
        const val OFF_ROUTE_MS = 8_000L

        fun metres(a: LatLng, b: LatLng): Double {
            val r = 6_371_000.0
            val dLat = (b.lat - a.lat) * PI / 180
            val dLng = (b.lng - a.lng) * PI / 180
            val h = sin(dLat / 2).let { it * it } + cos(a.lat * PI / 180) * cos(b.lat * PI / 180) * sin(dLng / 2).let { it * it }
            return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
        }

        /** Where [p] falls on segment a→b (0..1) and how far it is from it, in metres. */
        fun project(p: LatLng, a: LatLng, b: LatLng): Pair<Double, Double> {
            // A flat projection is plenty at road scale.
            val k = cos(p.lat * PI / 180)
            val ax = a.lng * k
            val bx = b.lng * k
            val px = p.lng * k
            val dx = bx - ax
            val dy = b.lat - a.lat
            val len = dx * dx + dy * dy
            val t = if (len == 0.0) 0.0 else (((px - ax) * dx + (p.lat - a.lat) * dy) / len).coerceIn(0.0, 1.0)
            val q = LatLng(a.lat + dy * t, a.lng + (b.lng - a.lng) * t)
            return t to metres(p, q)
        }
    }
}

/** The cluster's 4-character distance for [metres]. */
fun clusterDistance(metres: Double): Pair<String, String> = formatClusterDistance(metres)
