package dev.gdk.bumblebee.video

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** What a part of the bike is made of; [PAINT] and [HELMET] take colours at render time. */
enum class BikeMaterial(val rgb: Int, val shine: Float, val glow: Float = 0f) {
    PAINT(0, 0.55f),
    BLACK(0x1C1D21, 0.25f),
    PLASTIC(0x2A2C31, 0.15f),
    METAL(0xA9ADB3, 0.8f),
    RUBBER(0x111214, 0.05f),
    GLASS(0x3A4652, 0.9f),
    HEADLIGHT(0xFFF6DD, 0.3f, 1f),
    TAILLIGHT(0xE0202A, 0.3f, 0.8f),
    JACKET(0x2B2E35, 0.15f),
    PANTS(0x1E2026, 0.1f),
    HELMET(0, 0.7f),
    VISOR(0x101318, 0.95f),
}

/**
 * A stylised, low-poly adventure bike with V-Strom 250 SX proportions (2.15 m long, 19" front and
 * 17" rear wheels), and optionally its rider. Flat-shaded facets, so it reads as a crafted model
 * at any size. Metres, x forward, y left, z up, with the ground at z = 0 between the wheels.
 *
 * Arrays are ready for a vertex buffer: [positions] and [normals] (x, y, z per vertex), and
 * [materials] (one [BikeMaterial] ordinal per vertex), drawn as plain triangles.
 */
class BikeModel private constructor(val positions: FloatArray, val normals: FloatArray, val materials: FloatArray) {
    val vertexCount get() = positions.size / 3

    companion object {
        const val LENGTH = 2.15
        private const val FRONT_X = 0.72
        private const val REAR_X = -0.72
        private const val FRONT_R = 0.335
        private const val REAR_R = 0.315

        fun build(rider: Boolean = true): BikeModel {
            val b = Builder()
            // Wheels: tyre rings with metal hubs.
            b.wheel(FRONT_X, FRONT_R, 0.11)
            b.wheel(REAR_X, REAR_R, 0.14)
            // Front fork and the steering head.
            for (side in listOf(-1.0, 1.0)) {
                b.tube(Vec3(FRONT_X, 0.075 * side, FRONT_R), Vec3(0.47, 0.075 * side, 0.98), 0.026, BikeMaterial.METAL)
            }
            // Swingarm to the rear axle.
            for (side in listOf(-1.0, 1.0)) {
                b.tube(Vec3(REAR_X, 0.08 * side, REAR_R), Vec3(-0.12, 0.07 * side, 0.43), 0.03, BikeMaterial.BLACK)
            }
            // Engine and frame block.
            b.prism(listOf(-0.22 to 0.25, 0.30 to 0.22, 0.38 to 0.48, 0.18 to 0.66, -0.20 to 0.62, -0.30 to 0.44), 0.30, BikeMaterial.PLASTIC)
            // Sump guard.
            b.prism(listOf(-0.15 to 0.20, 0.30 to 0.20, 0.36 to 0.27, -0.18 to 0.27), 0.22, BikeMaterial.METAL)
            // Exhaust, low on the right then up behind.
            b.tube(Vec3(0.20, -0.14, 0.30), Vec3(-0.30, -0.17, 0.42), 0.04, BikeMaterial.METAL)
            b.tube(Vec3(-0.30, -0.17, 0.42), Vec3(-0.70, -0.18, 0.62), 0.055, BikeMaterial.BLACK)
            // Fuel tank and side panels: the bike's colour.
            b.prism(listOf(-0.12 to 0.70, 0.30 to 0.74, 0.46 to 0.98, 0.16 to 1.06, -0.16 to 0.96), 0.40, BikeMaterial.PAINT, bevel = 0.06)
            // Fairing and the beak.
            b.prism(listOf(0.30 to 0.74, 0.62 to 0.84, 0.72 to 1.02, 0.62 to 1.22, 0.42 to 1.18, 0.40 to 0.96), 0.34, BikeMaterial.PAINT, bevel = 0.05)
            b.prism(listOf(0.62 to 0.80, 0.92 to 0.76, 0.86 to 0.84, 0.66 to 0.90), 0.16, BikeMaterial.PAINT)
            // Front mudguard over the wheel.
            b.prism(listOf(0.52 to 0.70, 0.92 to 0.66, 0.96 to 0.70, 0.56 to 0.76), 0.14, BikeMaterial.BLACK)
            // Windscreen, raked back.
            b.prism(listOf(0.58 to 1.20, 0.68 to 1.22, 0.52 to 1.46, 0.46 to 1.44), 0.30, BikeMaterial.GLASS)
            // Headlight in the fairing nose.
            b.prism(listOf(0.70 to 0.98, 0.745 to 1.00, 0.735 to 1.10, 0.69 to 1.10), 0.18, BikeMaterial.HEADLIGHT)
            // Seat and the tail with its rack and light.
            b.prism(listOf(-0.60 to 0.90, -0.14 to 0.86, 0.10 to 0.95, -0.12 to 1.00, -0.60 to 1.00), 0.30, BikeMaterial.BLACK, bevel = 0.04)
            b.prism(listOf(-0.95 to 0.86, -0.50 to 0.72, -0.20 to 0.74, -0.30 to 0.88, -0.70 to 0.92), 0.26, BikeMaterial.PAINT, bevel = 0.03)
            b.prism(listOf(-1.02 to 0.93, -0.58 to 0.93, -0.58 to 0.97, -1.02 to 0.97), 0.28, BikeMaterial.METAL)
            b.prism(listOf(-0.98 to 0.84, -0.94 to 0.84, -0.94 to 0.90, -0.99 to 0.90), 0.14, BikeMaterial.TAILLIGHT)
            // Rear hugger over the wheel.
            b.prism(listOf(-0.95 to 0.64, -0.70 to 0.72, -0.50 to 0.62, -0.56 to 0.58), 0.12, BikeMaterial.BLACK)
            // Handlebar and mirrors.
            b.tube(Vec3(0.40, 0.36, 1.12), Vec3(0.40, -0.36, 1.12), 0.016, BikeMaterial.BLACK)
            for (side in listOf(-1.0, 1.0)) {
                b.tube(Vec3(0.42, 0.24 * side, 1.12), Vec3(0.45, 0.30 * side, 1.30), 0.010, BikeMaterial.BLACK)
                b.prism(listOf(0.40 to 1.28, 0.50 to 1.28, 0.50 to 1.36, 0.40 to 1.36), 0.10, BikeMaterial.BLACK, centerY = 0.32 * side)
            }
            if (rider) b.rider()
            return BikeModel(b.pos.toFloatArray(), b.nrm.toFloatArray(), b.mat.toFloatArray())
        }
    }

    private class Builder {
        val pos = ArrayList<Float>()
        val nrm = ArrayList<Float>()
        val mat = ArrayList<Float>()

        /** The centre of the part being built; every face is turned to point away from it. */
        var ref: Vec3 = Vec3.ZERO

        fun tri(a: Vec3, b0: Vec3, c0: Vec3, m: BikeMaterial) {
            var b = b0
            var c = c0
            var n = (b - a).cross(c - a).normalized()
            if (n.length < 0.5) return
            val centroid = (a + b + c) * (1.0 / 3)
            if (n.dot(centroid - ref) < 0) {
                b = c0
                c = b0
                n = n * -1.0
            }
            for (v in listOf(a, b, c)) {
                pos += v.x.toFloat(); pos += v.y.toFloat(); pos += v.z.toFloat()
                nrm += n.x.toFloat(); nrm += n.y.toFloat(); nrm += n.z.toFloat()
                mat += m.ordinal.toFloat()
            }
        }

        fun quad(a: Vec3, b: Vec3, c: Vec3, d: Vec3, m: BikeMaterial) {
            tri(a, b, c, m)
            tri(a, c, d, m)
        }

        /**
         * A side-view outline ([x], [z] pairs, either winding) extruded [width] across the bike,
         * centred on [centerY], with the outline inset by [bevel] on the outer faces so edges
         * read as rounded facets.
         */
        fun prism(outline: List<Pair<Double, Double>>, width: Double, m: BikeMaterial, bevel: Double = 0.0, centerY: Double = 0.0) {
            // Counter-clockwise when seen from +y (the bike's left).
            val area = outline.indices.sumOf { i ->
                val (x1, z1) = outline[i]
                val (x2, z2) = outline[(i + 1) % outline.size]
                x1 * z2 - x2 * z1
            }
            val pts = if (area < 0) outline.reversed() else outline
            val cx = pts.sumOf { it.first } / pts.size
            val cz = pts.sumOf { it.second } / pts.size
            val inner = pts.map { (x, z) ->
                val dx = x - cx
                val dz = z - cz
                val l = sqrt(dx * dx + dz * dz).coerceAtLeast(1e-6)
                val k = (1 - bevel / l).coerceAtLeast(0.3)
                (cx + dx * k) to (cz + dz * k)
            }
            val half = width / 2
            val edge = half - bevel
            ref = Vec3(cx, centerY, cz)
            fun v(p: Pair<Double, Double>, y: Double) = Vec3(p.first, centerY + y, p.second)
            val faces = earClip(pts)
            for ((i, j, k) in faces) {
                // Left (+y) face uses the inset outline when bevelled.
                val l = if (bevel > 0) inner else pts
                tri(v(l[i], half), v(l[j], half), v(l[k], half), m)
                tri(v(l[i], -half), v(l[k], -half), v(l[j], -half), m)
            }
            for (i in pts.indices) {
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                if (bevel > 0) {
                    val ia = inner[i]
                    val ib = inner[(i + 1) % pts.size]
                    quad(v(a, -edge), v(b, -edge), v(b, edge), v(a, edge), m)
                    quad(v(a, edge), v(b, edge), v(ib, half), v(ia, half), m)
                    quad(v(ia, -half), v(ib, -half), v(b, -edge), v(a, -edge), m)
                } else {
                    quad(v(a, -half), v(b, -half), v(b, half), v(a, half), m)
                }
            }
        }

        /** A tube from [a] to [b] with [r] radius, eight-sided, capped. */
        fun tube(a: Vec3, b: Vec3, r: Double, m: BikeMaterial, sides: Int = 8) {
            val axis = (b - a).normalized()
            val across = if (kotlin.math.abs(axis.z) < 0.9) Vec3.UP else Vec3(1.0, 0.0, 0.0)
            val u = axis.cross(across).normalized()
            val w = axis.cross(u).normalized()
            ref = (a + b) * 0.5
            fun ring(c: Vec3, i: Int): Vec3 {
                val t = 2 * PI * i / sides
                return c + u * (cos(t) * r) + w * (sin(t) * r)
            }
            for (i in 0 until sides) {
                quad(ring(a, i), ring(a, i + 1), ring(b, i + 1), ring(b, i), m)
                tri(a, ring(a, i + 1), ring(a, i), m)
                tri(b, ring(b, i), ring(b, i + 1), m)
            }
        }

        /** A wheel at [x]: a tyre ring of [r] radius and [width], with a metal hub and spokes. */
        fun wheel(x: Double, r: Double, width: Double) {
            val seg = 24
            val rimR = r * 0.72
            val half = width / 2
            fun p(rr: Double, t: Double, y: Double) = Vec3(x + cos(t) * rr, y, r + sin(t) * rr)
            ref = Vec3(x, 0.0, r)
            for (i in 0 until seg) {
                val t0 = 2 * PI * i / seg
                val t1 = 2 * PI * (i + 1) / seg
                // Tread, and the tyre's two sidewalls.
                quad(p(r, t0, -half), p(r, t1, -half), p(r, t1, half), p(r, t0, half), BikeMaterial.RUBBER)
                quad(p(r, t0, half), p(r, t1, half), p(rimR, t1, half * 0.8), p(rimR, t0, half * 0.8), BikeMaterial.RUBBER)
                quad(p(rimR, t0, -half * 0.8), p(rimR, t1, -half * 0.8), p(r, t1, -half), p(r, t0, -half), BikeMaterial.RUBBER)
                // Rim band.
                quad(p(rimR, t1, -half * 0.8), p(rimR, t0, -half * 0.8), p(rimR, t0, half * 0.8), p(rimR, t1, half * 0.8), BikeMaterial.BLACK)
            }
            // Hub and five spokes each side.
            tube(Vec3(x, -half * 0.9, r), Vec3(x, half * 0.9, r), r * 0.14, BikeMaterial.METAL)
            for (k in 0 until 5) {
                val t = 2 * PI * k / 5
                for (y in listOf(-half * 0.5, half * 0.5)) {
                    tube(p(r * 0.12, t, y), p(rimR, t, y), 0.008, BikeMaterial.METAL, sides = 4)
                }
            }
            // Brake disc on the left.
            ref = Vec3(x, 0.0, r)
            for (i in 0 until seg) {
                val t0 = 2 * PI * i / seg
                val t1 = 2 * PI * (i + 1) / seg
                quad(p(r * 0.18, t0, half * 0.95), p(r * 0.18, t1, half * 0.95), p(r * 0.55, t1, half * 0.95), p(r * 0.55, t0, half * 0.95), BikeMaterial.METAL)
            }
        }

        /** A rider in the riding position: boots on the pegs, hands on the bar, helmet up. */
        fun rider() {
            // Legs: thigh along the seat, shin down to the peg, each side.
            for (side in listOf(-1.0, 1.0)) {
                val y = 0.17 * side
                tube(Vec3(-0.22, y, 1.02), Vec3(0.12, y * 1.1, 0.92), 0.075, BikeMaterial.PANTS)
                tube(Vec3(0.12, y * 1.1, 0.92), Vec3(-0.04, y * 1.25, 0.42), 0.06, BikeMaterial.PANTS)
                prism(listOf(-0.10 to 0.36, 0.08 to 0.36, 0.08 to 0.46, -0.06 to 0.50), 0.09, BikeMaterial.BLACK, centerY = y * 1.25)
                // Arm from shoulder to grip.
                tube(Vec3(-0.08, y * 1.25, 1.50), Vec3(0.16, y * 1.6, 1.28), 0.05, BikeMaterial.JACKET)
                tube(Vec3(0.16, y * 1.6, 1.28), Vec3(0.40, 0.33 * side, 1.14), 0.045, BikeMaterial.JACKET)
            }
            // Torso leaning slightly forward.
            prism(listOf(-0.36 to 1.00, -0.06 to 1.00, 0.04 to 1.52, -0.22 to 1.58), 0.42, BikeMaterial.JACKET, bevel = 0.06)
            // Helmet: a faceted ball with a visor.
            val c = Vec3(-0.04, 0.0, 1.74)
            val r = 0.15
            ref = c
            val lat = 6
            val lon = 10
            fun s(i: Int, j: Int): Vec3 {
                val th = PI * i / lat
                val ph = 2 * PI * j / lon
                return c + Vec3(sin(th) * cos(ph) * r * 1.1, sin(th) * sin(ph) * r, cos(th) * r)
            }
            for (i in 0 until lat) for (j in 0 until lon) {
                val a = s(i, j)
                val b2 = s(i + 1, j)
                val cc = s(i + 1, j + 1)
                val d = s(i, j + 1)
                // The visor faces forward, around the middle band.
                val ph = 2 * PI * (j + 0.5) / lon
                val visor = i in 2..3 && cos(ph) > 0.55
                val m = if (visor) BikeMaterial.VISOR else BikeMaterial.HELMET
                quad(a, b2, cc, d, m)
            }
        }

        /** Triangulates a simple polygon (counter-clockwise) by ear clipping. */
        fun earClip(p: List<Pair<Double, Double>>): List<Triple<Int, Int, Int>> {
            val idx = p.indices.toMutableList()
            val out = mutableListOf<Triple<Int, Int, Int>>()
            fun cross(o: Int, a: Int, b: Int) =
                (p[a].first - p[o].first) * (p[b].second - p[o].second) - (p[a].second - p[o].second) * (p[b].first - p[o].first)
            fun inside(t: Int, a: Int, b: Int, c: Int) = cross(a, b, t) >= 0 && cross(b, c, t) >= 0 && cross(c, a, t) >= 0
            var guard = 0
            while (idx.size > 3 && guard++ < 500) {
                var clipped = false
                for (k in idx.indices) {
                    val a = idx[(k + idx.size - 1) % idx.size]
                    val b = idx[k]
                    val c = idx[(k + 1) % idx.size]
                    if (cross(a, b, c) <= 0) continue
                    if (idx.any { it != a && it != b && it != c && inside(it, a, b, c) }) continue
                    out += Triple(a, b, c)
                    idx.removeAt(k)
                    clipped = true
                    break
                }
                if (!clipped) break
            }
            if (idx.size == 3) out += Triple(idx[0], idx[1], idx[2])
            return out
        }
    }
}
