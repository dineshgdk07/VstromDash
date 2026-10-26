package dev.gdk.bumblebee.video

import kotlin.math.abs
import kotlin.math.max

/**
 * Finds water in an [ElevationGrid]. The elevation data flattens lakes and reservoirs to a single
 * height, and the sea sits at or below zero, so water is either sea level or a large, perfectly
 * level patch. Ordinary flat country still rises a metre or two across that size, so it doesn't
 * qualify. Returns 1 for water and 0 for land per grid cell, row 0 at the south edge.
 */
object WaterMask {
    /** A level patch must cover at least this area to count as a lake. */
    const val MIN_LAKE_M2 = 150_000.0

    /** How level a lake is: every cell within this many metres of where the fill started. */
    const val LEVEL_M = 0.3

    /**
     * Share of a patch's cells whose four neighbours are all in the patch. A lake is a blob; a
     * band of equal height across sloping ground is long and thin and fails this.
     */
    const val MIN_SOLIDITY = 0.6

    fun detect(grid: ElevationGrid): FloatArray {
        val cols = grid.cols
        val rows = grid.rows
        val mask = FloatArray(cols * rows)
        val seen = BooleanArray(cols * rows)
        val minCells = max(6, (MIN_LAKE_M2 / (grid.cell * grid.cell)).toInt())
        val queue = IntArray(cols * rows)
        val members = IntArray(cols * rows)
        for (start in 0 until cols * rows) {
            if (seen[start]) continue
            val h0 = grid.heights[start]
            if (h0 <= 0.5f) {
                // Sea (and anything below it), whatever its size.
                mask[start] = 1f
                seen[start] = true
                continue
            }
            // Flood the level patch around this cell.
            var head = 0
            var tail = 0
            var count = 0
            queue[tail++] = start
            seen[start] = true
            while (head < tail) {
                val i = queue[head++]
                members[count++] = i
                val c = i % cols
                val r = i / cols
                for (k in 0 until 4) {
                    val nc = c + DC[k]
                    val nr = r + DR[k]
                    if (nc !in 0 until cols || nr !in 0 until rows) continue
                    val j = nr * cols + nc
                    if (seen[j]) continue
                    if (abs(grid.heights[j] - h0) <= LEVEL_M) {
                        seen[j] = true
                        queue[tail++] = j
                    }
                }
            }
            if (count >= minCells && solidity(members, count, grid, h0) >= MIN_SOLIDITY) {
                for (k in 0 until count) mask[members[k]] = 1f
            }
        }
        return mask
    }

    private fun solidity(members: IntArray, count: Int, grid: ElevationGrid, h0: Float): Double {
        val cols = grid.cols
        val rows = grid.rows
        var inner = 0
        for (k in 0 until count) {
            val i = members[k]
            val c = i % cols
            val r = i / cols
            var all = true
            for (d in 0 until 4) {
                val nc = c + DC[d]
                val nr = r + DR[d]
                if (nc !in 0 until cols || nr !in 0 until rows || abs(grid.heights[nr * cols + nc] - h0) > LEVEL_M) {
                    all = false
                    break
                }
            }
            if (all) inner++
        }
        return inner.toDouble() / count
    }

    /** The mask sampled at local metres, 0..1 with soft edges. */
    fun sample(grid: ElevationGrid, mask: FloatArray, x: Double, y: Double): Double {
        val fx = ((x - grid.x0) / grid.cell).coerceIn(0.0, grid.cols - 1.0)
        val fy = ((y - grid.y0) / grid.cell).coerceIn(0.0, grid.rows - 1.0)
        val c = fx.toInt().coerceAtMost(grid.cols - 2).coerceAtLeast(0)
        val r = fy.toInt().coerceAtMost(grid.rows - 2).coerceAtLeast(0)
        val tx = fx - c
        val ty = fy - r
        fun m(cc: Int, rr: Int) = mask[rr * grid.cols + cc].toDouble()
        val a = m(c, r) * (1 - tx) + m(c + 1, r) * tx
        val b = m(c, r + 1) * (1 - tx) + m(c + 1, r + 1) * tx
        return a * (1 - ty) + b * ty
    }

    private val DC = intArrayOf(1, -1, 0, 0)
    private val DR = intArrayOf(0, 0, 1, -1)
}

/**
 * Water from OpenStreetMap's standard map tiles, where every lake, reservoir, river and sea is
 * drawn in one blue. Inland reservoirs aren't level in the elevation data, so the map is the
 * reliable source; the elevation check still catches the sea when tiles can't be had.
 */
object OsmWater {
    /** Water fill on the standard OSM style. */
    const val R = 0xAA
    const val G = 0xD3
    const val B = 0xDF

    fun isWater(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return abs(r - R) + abs(g - G) + abs(b - B) <= 24
    }

    /**
     * Samples [tile] (256×256 ARGB OSM tiles) at each cell of [grid], at the finest zoom that
     * covers it in at most [maxTiles] tiles. Null when no tile could be had.
     */
    fun mask(grid: ElevationGrid, maxTiles: Int = 36, tile: (z: Int, x: Int, y: Int) -> IntArray?): FloatArray? {
        val f = grid.frame
        val bounds = LatLngBounds(f.lat(grid.y0), f.lng(grid.x0), f.lat(grid.y0 + grid.height), f.lng(grid.x0 + grid.width))
        val z = Terrarium.zoomFor(bounds, maxTiles = maxTiles, maxZoom = 14)
        val cache = HashMap<Long, IntArray?>()
        var found = false
        val out = FloatArray(grid.cols * grid.rows)
        for (r in 0 until grid.rows) {
            val py = Terrarium.latToY(f.lat(grid.y0 + r * grid.cell), z) * 256
            for (c in 0 until grid.cols) {
                val px = Terrarium.lngToX(f.lng(grid.x0 + c * grid.cell), z) * 256
                val tx = (px / 256).toInt()
                val ty = (py / 256).toInt()
                val key = tx.toLong() shl 32 or (ty.toLong() and 0xFFFFFFFFL)
                val data = cache.getOrPut(key) { tile(z, tx, ty) } ?: continue
                found = true
                val ix = (px - tx * 256).toInt().coerceIn(0, 255)
                val iy = (py - ty * 256).toInt().coerceIn(0, 255)
                if (isWater(data[iy * 256 + ix])) out[r * grid.cols + c] = 1f
            }
        }
        return if (found) out else null
    }
}

/**
 * Water across both terrain layers, blended where the detail layer fades into the base: the map's
 * water where tiles were had ([mapTile]), plus anything the elevation says is sea or level lake.
 */
class WaterLayer(private val land: LayeredElevation, mapTile: ((z: Int, x: Int, y: Int) -> IntArray?)? = null) {
    private val base = combine(WaterMask.detect(land.base), mapTile?.let { OsmWater.mask(land.base, tile = it) })
    private val detail = land.detail?.let { d -> combine(WaterMask.detect(d), mapTile?.let { OsmWater.mask(d, tile = it) }) }

    private fun combine(a: FloatArray, b: FloatArray?): FloatArray {
        if (b == null) return a
        for (i in a.indices) a[i] = max(a[i], b[i])
        return a
    }

    /** True when any water was found at all. */
    val any: Boolean = base.any { it > 0f } || (detail?.any { it > 0f } ?: false)

    fun at(x: Double, y: Double): Double {
        val b = WaterMask.sample(land.base, base, x, y)
        val d = land.detail ?: return b
        val w = land.detailWeight(x, y)
        if (w <= 0.0) return b
        return b * (1 - w) + WaterMask.sample(d, detail!!, x, y) * w
    }
}
