package dev.gdk.bumblebee.ui.sharecard

import android.graphics.Matrix
import android.graphics.RectF
import dev.gdk.bumblebee.ride.GeoBounds
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.SharePlot
import dev.gdk.bumblebee.ride.TileCover
import dev.gdk.bumblebee.ride.WebMercator
import kotlin.math.cos
import kotlin.math.max

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
        fun rotateDegrees(routeW: Float, routeH: Float, startOnMinSide: Boolean, portrait: Boolean): Float {
            if (portrait && routeW > routeH * 1.12f) return if (startOnMinSide) 90f else -90f
            if (!portrait && routeH > routeW * 1.35f) return if (startOnMinSide) -90f else 90f
            return 0f
        }

        fun posterCover(route: List<GpsFix>, portrait: Boolean): TileCover? {
            val geo = posterBounds(route, portrait) ?: return null
            return WebMercator.cover(geo, maxTiles = 16)
        }

        fun posterBounds(route: List<GpsFix>, portrait: Boolean): GeoBounds? {
            val base = WebMercator.bounds(route, padFrac = 0.22) ?: return null
            val midLat = (base.minLat + base.maxLat) / 2.0
            val dLat = (base.maxLat - base.minLat).coerceAtLeast(1e-5)
            val dLng = (base.maxLng - base.minLng).coerceAtLeast(1e-5)
            val lngAsLat = dLng * cos(Math.toRadians(midLat)).coerceAtLeast(0.2)
            if (portrait && lngAsLat > dLat * 1.12) {
                val target = max(dLat, lngAsLat * 0.90)
                val extra = (target - dLat) / 2.0
                return base.copy(minLat = base.minLat - extra, maxLat = base.maxLat + extra)
            }
            if (!portrait && dLat > lngAsLat * 1.35) {
                val scale = cos(Math.toRadians(midLat)).coerceAtLeast(0.2)
                val targetLng = max(dLng, (dLat * 0.90) / scale)
                val extra = (targetLng - dLng) / 2.0
                return base.copy(minLng = base.minLng - extra, maxLng = base.maxLng + extra)
            }
            return base
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
            val inner = RectF(
                stage.left + 36f,
                stage.top + 168f,
                stage.right - 36f,
                stage.bottom - 8f,
            )
            val fill = if (portrait) 0.88f else 0.80f
            val scale = minOf(inner.width() * fill / rotatedRoute.width(), inner.height() * fill / rotatedRoute.height())
            matrix.postScale(scale, scale, rotatedRoute.centerX(), rotatedRoute.centerY())
            val placed = RectF()
            matrix.mapRect(placed, route)
            matrix.postTranslate(inner.centerX() - placed.centerX(), inner.centerY() - placed.centerY())
            coverWidth(matrix, mosaicW, mosaicH, stage)
            return ShareCardMapTransform(mosaicW, mosaicH, matrix, rotate)
        }

        private fun coverWidth(matrix: Matrix, mosaicW: Float, mosaicH: Float, stage: RectF) {
            val mosaic = RectF()
            matrix.mapRect(mosaic, RectF(0f, 0f, mosaicW, mosaicH))
            if (mosaic.width() < stage.width()) {
                val bump = (stage.width() / mosaic.width()) * 1.02f
                matrix.postScale(bump, bump, mosaic.centerX(), mosaic.centerY())
                matrix.mapRect(mosaic, RectF(0f, 0f, mosaicW, mosaicH))
            }
            var dx = 0f
            if (mosaic.left > 0f) dx = -mosaic.left
            if (mosaic.right + dx < stage.width()) dx = stage.width() - mosaic.right
            if (dx != 0f) matrix.postTranslate(dx, 0f)
        }
    }
}
