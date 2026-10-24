package dev.gdk.bumblebee.ui.sharecard

import android.graphics.Matrix
import android.graphics.RectF
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.SharePlot
import dev.gdk.bumblebee.ride.TileCover
import dev.gdk.bumblebee.ride.WebMercator
import kotlin.math.floor

/**
 * Visual-only map/route placement. GPS and TrackPlot stay untouched.
 * Wide routes on Story are rotated with the mosaic so the dominant axis is vertical.
 */
data class ShareCardMapTransform(
    val mosaicW: Float,
    val mosaicH: Float,
    val matrix: Matrix,
    val rotateDeg: Float,
) {
    companion object {
        /** About one tile per screen tile on a story card, so the map stays sharp. */
        private const val MAX_TILES = 40
        private const val MAX_ZOOM = 16
        private const val MIN_ZOOM = 6

        fun rotateDegrees(routeW: Float, routeH: Float, startOnMinSide: Boolean, portrait: Boolean): Float {
            if (portrait && routeW > routeH * 1.12f) return if (startOnMinSide) 90f else -90f
            if (!portrait && routeH > routeW * 1.35f) return if (startOnMinSide) -90f else 90f
            return 0f
        }

        /**
         * Tiles that fill the whole card once the route is fitted to [stage]. The fit only depends
         * on the route's shape, so the route lands in the same place whatever zoom the tiles are.
         * The card's corners are mapped back onto the world, and the tiles under that area are
         * taken at the highest zoom that stays within [maxTiles].
         */
        fun cardCover(
            route: List<GpsFix>,
            stage: RectF,
            width: Float,
            height: Float,
            portrait: Boolean,
            maxTiles: Int = MAX_TILES,
        ): TileCover? {
            if (route.size < 2) return null
            val z = MAX_ZOOM
            // Relative to the first fix, so floats keep their precision at street zoom.
            val ox = WebMercator.lngToX(route[0].lng, z)
            val oy = WebMercator.latToY(route[0].lat, z)
            val pts = route.map { fix ->
                ((WebMercator.lngToX(fix.lng, z) - ox) * 256.0).toFloat() to ((WebMercator.latToY(fix.lat, z) - oy) * 256.0).toFloat()
            }
            val inverse = Matrix()
            if (!fit(pts, 0f, 0f, stage, portrait).matrix.invert(inverse)) return null
            val area = RectF()
            inverse.mapRect(area, RectF(0f, 0f, width, height))
            // A little extra so rounding in the fit never leaves an edge uncovered.
            area.inset(-area.width() * 0.03f, -area.height() * 0.03f)
            val minX = ox + area.left / 256.0
            val maxX = ox + area.right / 256.0
            val minY = oy + area.top / 256.0
            val maxY = oy + area.bottom / 256.0
            var zoom = z
            while (true) {
                val shrink = (1 shl (z - zoom)).toDouble()
                val last = (1 shl zoom) - 1
                val cover = TileCover(
                    zoom,
                    floor(minX / shrink).toInt().coerceIn(0, last),
                    floor(minY / shrink).toInt().coerceIn(0, last),
                    floor(maxX / shrink).toInt().coerceIn(0, last),
                    floor(maxY / shrink).toInt().coerceIn(0, last),
                )
                if (cover.count <= maxTiles || zoom <= MIN_ZOOM) return cover
                zoom--
            }
        }

        fun ofRoute(route: List<GpsFix>, cover: TileCover, stage: RectF, portrait: Boolean): ShareCardMapTransform {
            val mosaicW = cover.cols * 256f
            val mosaicH = cover.rows * 256f
            val zoom = cover.zoom
            val pts = route.map { fix ->
                val x = (WebMercator.lngToX(fix.lng, zoom) - cover.x0).toFloat() * 256f
                val y = (WebMercator.latToY(fix.lat, zoom) - cover.y0).toFloat() * 256f
                x to y
            }
            return fit(pts, mosaicW, mosaicH, stage, portrait)
        }

        fun ofPlot(plot: List<SharePlot>, stage: RectF, portrait: Boolean): ShareCardMapTransform {
            val pts = plot.map { it.x * 1000f to it.y * 1000f }
            return fit(pts, 1000f, 1000f, stage, portrait)
        }

        private fun fit(
            pts: List<Pair<Float, Float>>,
            mosaicW: Float,
            mosaicH: Float,
            stage: RectF,
            portrait: Boolean,
        ): ShareCardMapTransform {
            var minX = pts.minOf { it.first }
            var maxX = pts.maxOf { it.first }
            var minY = pts.minOf { it.second }
            var maxY = pts.maxOf { it.second }
            if (maxX - minX < 12f) {
                minX -= 6f
                maxX += 6f
            }
            if (maxY - minY < 12f) {
                minY -= 6f
                maxY += 6f
            }
            val route = RectF(minX, minY, maxX, maxY)
            val start = pts.first()
            val startOnMin = start.first < route.centerX()
            val rotate = rotateDegrees(route.width(), route.height(), startOnMin, portrait)
            val matrix = Matrix()
            matrix.setRotate(rotate, route.centerX(), route.centerY())
            val rotatedRoute = RectF()
            matrix.mapRect(rotatedRoute, route)
            val pad = minOf(stage.width(), stage.height()) * 0.04f
            val inner = RectF(stage.left + pad, stage.top + pad, stage.right - pad, stage.bottom - pad)
            val scale = minOf(inner.width() / rotatedRoute.width(), inner.height() / rotatedRoute.height())
            matrix.postScale(scale, scale, rotatedRoute.centerX(), rotatedRoute.centerY())
            val placed = RectF()
            matrix.mapRect(placed, route)
            matrix.postTranslate(inner.centerX() - placed.centerX(), inner.centerY() - placed.centerY())
            return ShareCardMapTransform(mosaicW, mosaicH, matrix, rotate)
        }
    }
}
