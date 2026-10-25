package dev.gdk.bumblebee.video

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Heights on a regular grid in the [frame]'s metres, covering [width] × [height] metres from
 * ([x0], [y0]) at the south-west corner. Row 0 is the south edge.
 */
/** Ground height in a [LocalFrame]'s metres. */
interface Elevation {
    val frame: LocalFrame
    fun heightAt(x: Double, y: Double): Double
}

class ElevationGrid(
    override val frame: LocalFrame,
    val x0: Double,
    val y0: Double,
    val cell: Double,
    val cols: Int,
    val rows: Int,
    val heights: FloatArray,
    /** True where no source tile was available and the height was filled in. */
    val missing: Int = 0,
) : Elevation {
    val width get() = cell * (cols - 1)
    val height get() = cell * (rows - 1)
    val minHeight: Double by lazy { heights.minOrNull()?.toDouble() ?: 0.0 }
    val maxHeight: Double by lazy { heights.maxOrNull()?.toDouble() ?: 0.0 }

    fun at(col: Int, row: Int): Double =
        heights[row.coerceIn(0, rows - 1) * cols + col.coerceIn(0, cols - 1)].toDouble()

    fun contains(x: Double, y: Double) = x >= x0 && y >= y0 && x <= x0 + width && y <= y0 + height

    /** Bilinear height at local metres; clamps to the edge outside the grid. */
    override fun heightAt(x: Double, y: Double): Double {
        val fx = ((x - x0) / cell).coerceIn(0.0, cols - 1.0)
        val fy = ((y - y0) / cell).coerceIn(0.0, rows - 1.0)
        val c = min(floor(fx).toInt(), cols - 2).coerceAtLeast(0)
        val r = min(floor(fy).toInt(), rows - 2).coerceAtLeast(0)
        val tx = fx - c
        val ty = fy - r
        val a = at(c, r) * (1 - tx) + at(c + 1, r) * tx
        val b = at(c, r + 1) * (1 - tx) + at(c + 1, r + 1) * tx
        return a * (1 - ty) + b * ty
    }

    fun heightAtLatLng(lat: Double, lng: Double) = heightAt(frame.x(lng), frame.y(lat))

    companion object {
        /**
         * Samples Terrarium tiles at zoom [z] onto a grid over [bounds] with at most [maxCells]
         * cells on the long side. [tile] returns a tile's 256×256 ARGB pixels, or null when it
         * can't be had; those areas take the mean of what was found.
         */
        fun fromTiles(
            bounds: LatLngBounds,
            z: Int,
            maxCells: Int = 512,
            frame: LocalFrame = LocalFrame(bounds.centerLat, bounds.centerLng),
            tile: (z: Int, x: Int, y: Int) -> IntArray?,
        ): ElevationGrid {
            val x0 = frame.x(bounds.west)
            val y0 = frame.y(bounds.south)
            val w = frame.x(bounds.east) - x0
            val h = frame.y(bounds.north) - y0
            // No finer than the source: one cell per source pixel at most.
            val pixelM = 40_075_016.0 * kotlin.math.cos(Math.toRadians(bounds.centerLat)) / ((1 shl z) * Terrarium.TILE)
            val cell = max(max(w, h) / (maxCells - 1), pixelM)
            val cols = (w / cell).toInt() + 1
            val rows = (h / cell).toInt() + 1
            val cache = HashMap<Long, IntArray?>()
            fun pixel(px: Double, py: Double): Double? {
                val tx = floor(px / Terrarium.TILE).toInt()
                val ty = floor(py / Terrarium.TILE).toInt()
                val key = tx.toLong() shl 32 or (ty.toLong() and 0xFFFFFFFFL)
                val data = cache.getOrPut(key) { tile(z, tx, ty) } ?: return null
                val ix = (px - tx * Terrarium.TILE).toInt().coerceIn(0, Terrarium.TILE - 1)
                val iy = (py - ty * Terrarium.TILE).toInt().coerceIn(0, Terrarium.TILE - 1)
                return Terrarium.decode(data[iy * Terrarium.TILE + ix])
            }
            fun sample(px: Double, py: Double): Double? {
                // Bilinear between source pixels, so the mesh has no stair steps.
                val bx = px - 0.5
                val by = py - 0.5
                val ix = floor(bx)
                val iy = floor(by)
                val tx = bx - ix
                val ty = by - iy
                val a = pixel(ix, iy) ?: return null
                val b = pixel(ix + 1, iy) ?: a
                val c = pixel(ix, iy + 1) ?: a
                val d = pixel(ix + 1, iy + 1) ?: a
                return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty
            }
            val heights = FloatArray(cols * rows)
            val gaps = BooleanArray(cols * rows)
            var sum = 0.0
            var found = 0
            for (r in 0 until rows) {
                val lat = frame.lat(y0 + r * cell)
                val py = Terrarium.latToY(lat, z) * Terrarium.TILE
                for (c in 0 until cols) {
                    val lng = frame.lng(x0 + c * cell)
                    val v = sample(Terrarium.lngToX(lng, z) * Terrarium.TILE, py)
                    if (v == null) {
                        gaps[r * cols + c] = true
                    } else {
                        heights[r * cols + c] = v.toFloat()
                        sum += v
                        found++
                    }
                }
            }
            val missing = cols * rows - found
            if (missing > 0) {
                val fill = if (found > 0) (sum / found).toFloat() else 0f
                for (i in heights.indices) if (gaps[i]) heights[i] = fill
            }
            despike(heights)
            return ElevationGrid(frame, x0, y0, cell, cols, rows, heights, missing)
        }

        /**
         * The source has occasional voids and spikes (a few cells thousands of metres off). Clamp
         * anything far outside the bulk of the heights to the edge of that bulk.
         */
        internal fun despike(h: FloatArray) {
            if (h.size < 16) return
            val sorted = h.copyOf().also { it.sort() }
            val lo = sorted[(sorted.size * 0.005).toInt()] - 300f
            val hi = sorted[(sorted.size * 0.995).toInt().coerceAtMost(sorted.lastIndex)] + 300f
            for (i in h.indices) h[i] = h[i].coerceIn(lo, hi)
        }

        /** A flat grid, for the no-data fallback and tests. */
        fun flat(bounds: LatLngBounds, cells: Int = 64, heightM: Double = 0.0): ElevationGrid {
            val frame = LocalFrame(bounds.centerLat, bounds.centerLng)
            val x0 = frame.x(bounds.west)
            val y0 = frame.y(bounds.south)
            val w = frame.x(bounds.east) - x0
            val h = frame.y(bounds.north) - y0
            val cell = max(w, h) / (cells - 1)
            val cols = (w / cell).toInt() + 1
            val rows = (h / cell).toInt() + 1
            return ElevationGrid(frame, x0, y0, cell, cols, rows, FloatArray(cols * rows) { heightM.toFloat() })
        }
    }
}
