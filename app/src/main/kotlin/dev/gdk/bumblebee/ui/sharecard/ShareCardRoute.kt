package dev.gdk.bumblebee.ui.sharecard

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.SharePlot
import dev.gdk.bumblebee.ride.TileCover
import dev.gdk.bumblebee.ride.WebMercator
import kotlin.math.roundToInt

object ShareCardRoute {
    fun drawPlot(canvas: Canvas, plot: List<SharePlot>, matrix: Matrix) {
        tube(canvas, plot.size) { i ->
            val a = map(matrix, plot[i].x * 1000f, plot[i].y * 1000f)
            val b = map(matrix, plot[i + 1].x * 1000f, plot[i + 1].y * 1000f)
            Quad(a.first, a.second, b.first, b.second, (plot[i].pace + plot[i + 1].pace) / 2f)
        }
    }

    fun drawMap(canvas: Canvas, route: List<GpsFix>, cover: TileCover, matrix: Matrix) {
        if (route.size < 2) return
        val xy = route.map { fix ->
            map(
                matrix,
                (WebMercator.lngToX(fix.lng, cover.zoom) - cover.x0).toFloat() * 256f,
                (WebMercator.latToY(fix.lat, cover.zoom) - cover.y0).toFloat() * 256f,
            )
        }
        val speeds = route.map { (it.speedMps ?: 0f).coerceAtLeast(0f) }
        val peak = speeds.maxOrNull()?.coerceAtLeast(0.001f) ?: 1f
        val paces = speeds.map { (it / peak).coerceIn(0f, 1f) }
        tube(canvas, xy.size) { i ->
            Quad(xy[i].first, xy[i].second, xy[i + 1].first, xy[i + 1].second, (paces[i] + paces[i + 1]) / 2f)
        }
    }

    fun drawFaintRoad(canvas: Canvas, plot: List<SharePlot>, matrix: Matrix) {
        val paint = stroke(28f, 0xFF243044.toInt())
        for (i in 0 until plot.lastIndex) {
            val a = map(matrix, plot[i].x * 1000f, plot[i].y * 1000f)
            val b = map(matrix, plot[i + 1].x * 1000f, plot[i + 1].y * 1000f)
            canvas.drawLine(a.first, a.second, b.first, b.second, paint)
        }
    }

    private fun map(matrix: Matrix, x: Float, y: Float): Pair<Float, Float> {
        val pts = floatArrayOf(x, y)
        matrix.mapPoints(pts)
        return pts[0] to pts[1]
    }

    private fun tube(canvas: Canvas, count: Int, segment: (Int) -> Quad) {
        if (count < 2) return
        val path = Path()
        val first = segment(0)
        path.moveTo(first.x1, first.y1)
        var end = first
        for (i in 0 until count - 1) {
            end = segment(i)
            path.lineTo(end.x2, end.y2)
        }
        val glow = stroke(28f, Color.argb(42, 255, 106, 0)).apply {
            maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(path, glow)
        canvas.drawPath(path, stroke(20f, Color.rgb(255, 106, 0)))
        val body = stroke(15f, 0)
        for (i in 0 until count - 1) {
            val q = segment(i)
            body.color = bodyColor(q.pace)
            canvas.drawLine(q.x1, q.y1, q.x2, q.y2, body)
        }
        canvas.drawPath(path, stroke(3.5f, Color.rgb(255, 247, 199)))
        markers(canvas, first.x1, first.y1, end.x2, end.y2)
    }

    private fun markers(canvas: Canvas, sx: Float, sy: Float, ex: Float, ey: Float) {
        val white = stroke(3.5f, Color.WHITE)
        val yellowRing = stroke(3.5f, Color.rgb(255, 247, 199))
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(250, 200, 22) }
        canvas.drawCircle(sx, sy, 12f, white)
        canvas.drawCircle(sx, sy, 4.5f, fill)
        canvas.drawCircle(ex, ey, 12f, yellowRing)
        fill.color = Color.rgb(255, 140, 0)
        canvas.drawCircle(ex, ey, 4.5f, fill)
    }

    private fun stroke(width: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = width
        this.color = color
    }

    private fun bodyColor(pace: Float): Int {
        val t = pace.coerceIn(0f, 1f)
        return Color.rgb(250, lerp(196f, 200f, t).roundToInt(), lerp(20f, 22f, t).roundToInt())
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}

private data class Quad(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val pace: Float)
