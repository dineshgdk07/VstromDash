package dev.gdk.bumblebee.video

/**
 * A triangle mesh of an [ElevationGrid], ready for a vertex buffer: positions (x, y, z) and unit
 * normals (x, y, z) per vertex, and indices for GL_TRIANGLES. Heights are multiplied by
 * [exaggeration]; [lod] keeps every n-th grid point. With a [surface], heights come from it at
 * the grid's points instead (so a detail mesh blends into the base at its edge), less [sink]
 * metres (so the base sits just under a detail mesh laid over it).
 */
class TerrainMesh(val positions: FloatArray, val normals: FloatArray, val indices: IntArray, val cols: Int, val rows: Int) {
    val vertexCount get() = positions.size / 3

    companion object {
        fun build(
            grid: ElevationGrid,
            exaggeration: Double,
            lod: Int = 1,
            surface: Elevation? = null,
            sink: (x: Double, y: Double) -> Double = { _, _ -> 0.0 },
        ): TerrainMesh {
            val step = lod.coerceAtLeast(1)
            val cols = (grid.cols - 1) / step + 1
            val rows = (grid.rows - 1) / step + 1
            val pos = FloatArray(cols * rows * 3)
            val nrm = FloatArray(cols * rows * 3)
            val d = grid.cell * step
            fun hAt(gc: Int, gr: Int): Double {
                val c = gc.coerceIn(0, grid.cols - 1)
                val r = gr.coerceIn(0, grid.rows - 1)
                val x = grid.x0 + c * grid.cell
                val y = grid.y0 + r * grid.cell
                return (surface?.heightAt(x, y) ?: grid.at(c, r)) - sink(x, y)
            }
            for (r in 0 until rows) for (c in 0 until cols) {
                val gc = c * step
                val gr = r * step
                val i = (r * cols + c) * 3
                pos[i] = (grid.x0 + gc * grid.cell).toFloat()
                pos[i + 1] = (grid.y0 + gr * grid.cell).toFloat()
                pos[i + 2] = (hAt(gc, gr) * exaggeration).toFloat()
                // Central differences on the exaggerated surface.
                val dzdx = (hAt(gc + step, gr) - hAt(gc - step, gr)) * exaggeration / (2 * d)
                val dzdy = (hAt(gc, gr + step) - hAt(gc, gr - step)) * exaggeration / (2 * d)
                val n = Vec3(-dzdx, -dzdy, 1.0).normalized()
                nrm[i] = n.x.toFloat()
                nrm[i + 1] = n.y.toFloat()
                nrm[i + 2] = n.z.toFloat()
            }
            val idx = IntArray((cols - 1) * (rows - 1) * 6)
            var k = 0
            for (r in 0 until rows - 1) for (c in 0 until cols - 1) {
                val a = r * cols + c
                val b = a + 1
                val e = a + cols
                val f = e + 1
                idx[k++] = a; idx[k++] = b; idx[k++] = f
                idx[k++] = a; idx[k++] = f; idx[k++] = e
            }
            return TerrainMesh(pos, nrm, idx, cols, rows)
        }
    }
}

/** How much to stretch heights so the ground has shape without looking fake. */
object Exaggeration {
    const val MIN = 1.3
    const val MAX = 2.5

    /**
     * From the relief (highest minus lowest) over the area's span: mountains (relief over 8% of the
     * span) stay near [MIN], flat country (under 1%) gets [MAX], in between is interpolated.
     */
    fun choose(reliefM: Double, spanM: Double): Double {
        if (spanM <= 0) return MIN
        val ratio = reliefM / spanM
        val t = ((0.08 - ratio) / (0.08 - 0.01)).coerceIn(0.0, 1.0)
        return MIN + (MAX - MIN) * t
    }
}
