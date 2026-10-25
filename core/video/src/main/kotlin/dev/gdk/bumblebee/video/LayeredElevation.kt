package dev.gdk.bumblebee.video

/**
 * A wide, coarse [base] that reaches the horizon, with a sharp [detail] grid over the route. Inside
 * the detail area its heights win; across its outer [blend] fraction they fade into the base, so
 * the two meshes meet without a step.
 */
class LayeredElevation(val base: ElevationGrid, val detail: ElevationGrid?, private val blend: Double = 0.08) : Elevation {
    override val frame: LocalFrame get() = base.frame

    /** 1 well inside the detail area, falling to 0 at its edge. */
    fun detailWeight(x: Double, y: Double): Double {
        val d = detail ?: return 0.0
        if (!d.contains(x, y)) return 0.0
        val fx = (x - d.x0) / d.width
        val fy = (y - d.y0) / d.height
        val edge = minOf(fx, 1 - fx, fy, 1 - fy)
        return (edge / blend).coerceIn(0.0, 1.0)
    }

    override fun heightAt(x: Double, y: Double): Double {
        val w = detailWeight(x, y)
        if (w <= 0.0) return base.heightAt(x, y)
        val dh = detail!!.heightAt(x, y)
        return if (w >= 1.0) dh else base.heightAt(x, y) * (1 - w) + dh * w
    }

    val minHeight: Double get() = minOf(base.minHeight, detail?.minHeight ?: Double.MAX_VALUE)
    val maxHeight: Double get() = maxOf(base.maxHeight, detail?.maxHeight ?: -Double.MAX_VALUE)

    companion object {
        /**
         * Builds both layers for a route: the base over [wide] at whatever zoom fits [baseTiles]
         * tiles, the detail over the route's own padded bounds at up to [detailTiles] tiles, and
         * only when that is actually finer than the base. Both grids share the base's frame.
         */
        fun forRoute(
            route: LatLngBounds,
            wide: LatLngBounds,
            baseTiles: Int = 16,
            detailTiles: Int = 36,
            maxCells: Int = 512,
            tile: (z: Int, x: Int, y: Int) -> IntArray?,
        ): LayeredElevation {
            val zb = Terrarium.zoomFor(wide, maxTiles = baseTiles)
            val base = ElevationGrid.fromTiles(wide, zb, maxCells, tile = tile)
            val zd = Terrarium.zoomFor(route, maxTiles = detailTiles, maxZoom = 14)
            val detail = if (zd > zb) ElevationGrid.fromTiles(route, zd, maxCells, frame = base.frame, tile = tile) else null
            return LayeredElevation(base, detail?.takeIf { it.cell < base.cell * 0.8 })
        }
    }
}
