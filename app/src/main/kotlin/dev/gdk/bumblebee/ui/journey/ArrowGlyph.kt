package dev.gdk.bumblebee.ui.journey

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.gdk.bumblebee.nav.ArrowKind
import kotlin.math.cos
import kotlin.math.sin

/** A turn arrow in the app's own simple style, one per [ArrowKind]. */
@Composable
fun ArrowGlyph(kind: ArrowKind, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawArrow(kind, color) }
}

private fun DrawScope.drawArrow(kind: ArrowKind, color: Color) {
    val s = size.minDimension
    val ox = (size.width - s) / 2f
    val oy = (size.height - s) / 2f
    fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    val w = s * 0.11f
    val line = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun head(tip: Offset, angleDeg: Float) {
        val a = Math.toRadians(angleDeg.toDouble())
        val len = s * 0.2f
        val spread = Math.toRadians(32.0)
        val path = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo((tip.x - len * cos(a - spread)).toFloat(), (tip.y - len * sin(a - spread)).toFloat())
            lineTo((tip.x - len * cos(a + spread)).toFloat(), (tip.y - len * sin(a + spread)).toFloat())
            close()
        }
        drawPath(path, color)
    }

    /** Up from the bottom, then off at [turnDeg] from straight (negative is left). */
    fun bend(turnDeg: Float, stemTop: Float = 0.5f, armLen: Float = 0.3f, stemX: Float = 0.5f) {
        val base = p(stemX, 0.92f)
        val elbow = p(stemX, stemTop)
        // Screen angles: -90 is up; a right turn adds to it.
        val a = Math.toRadians((-90f + turnDeg).toDouble())
        val tip = Offset(elbow.x + (armLen * s * cos(a)).toFloat(), elbow.y + (armLen * s * sin(a)).toFloat())
        val path = Path().apply {
            moveTo(base.x, base.y)
            lineTo(elbow.x, elbow.y)
            lineTo(tip.x, tip.y)
        }
        drawPath(path, color, style = line)
        head(Offset(tip.x + (w * 0.6f * cos(a)).toFloat(), tip.y + (w * 0.6f * sin(a)).toFloat()), -90f + turnDeg)
    }

    /** A faded second road, for keep and fork. */
    fun ghost(turnDeg: Float, stemX: Float) {
        val elbow = p(stemX, 0.5f)
        val a = Math.toRadians((-90f + turnDeg).toDouble())
        val tip = Offset(elbow.x + (0.3f * s * cos(a)).toFloat(), elbow.y + (0.3f * s * sin(a)).toFloat())
        drawLine(color.copy(alpha = 0.3f), elbow, tip, strokeWidth = w, cap = StrokeCap.Round)
    }

    when (kind) {
        ArrowKind.STRAIGHT -> bend(0f, stemTop = 0.3f, armLen = 0.12f)
        ArrowKind.LEFT -> bend(-90f)
        ArrowKind.RIGHT -> bend(90f)
        ArrowKind.SLIGHT_LEFT -> bend(-45f)
        ArrowKind.SLIGHT_RIGHT -> bend(45f)
        ArrowKind.SHARP_LEFT -> bend(-135f, stemTop = 0.42f, armLen = 0.32f)
        ArrowKind.SHARP_RIGHT -> bend(135f, stemTop = 0.42f, armLen = 0.32f)
        ArrowKind.KEEP_LEFT -> {
            ghost(20f, 0.5f)
            bend(-20f)
        }
        ArrowKind.KEEP_RIGHT -> {
            ghost(-20f, 0.5f)
            bend(20f)
        }
        ArrowKind.FORK_LEFT -> {
            ghost(35f, 0.5f)
            bend(-35f)
        }
        ArrowKind.FORK_RIGHT -> {
            ghost(-35f, 0.5f)
            bend(35f)
        }
        ArrowKind.EXIT_LEFT -> {
            drawLine(color.copy(alpha = 0.3f), p(0.6f, 0.92f), p(0.6f, 0.1f), strokeWidth = w, cap = StrokeCap.Round)
            bend(-55f, stemX = 0.6f, stemTop = 0.55f)
        }
        ArrowKind.EXIT_RIGHT -> {
            drawLine(color.copy(alpha = 0.3f), p(0.4f, 0.92f), p(0.4f, 0.1f), strokeWidth = w, cap = StrokeCap.Round)
            bend(55f, stemX = 0.4f, stemTop = 0.55f)
        }
        ArrowKind.MERGE_LEFT -> {
            drawLine(color.copy(alpha = 0.3f), p(0.35f, 0.92f), p(0.35f, 0.1f), strokeWidth = w, cap = StrokeCap.Round)
            bend(-30f, stemX = 0.65f, stemTop = 0.6f, armLen = 0.32f)
        }
        ArrowKind.MERGE_RIGHT -> {
            drawLine(color.copy(alpha = 0.3f), p(0.65f, 0.92f), p(0.65f, 0.1f), strokeWidth = w, cap = StrokeCap.Round)
            bend(30f, stemX = 0.35f, stemTop = 0.6f, armLen = 0.32f)
        }
        ArrowKind.U_TURN -> {
            val path = Path().apply {
                moveTo(p(0.62f, 0.92f).x, p(0.62f, 0.92f).y)
                lineTo(p(0.62f, 0.4f).x, p(0.62f, 0.4f).y)
                arcTo(Rect(p(0.28f, 0.22f), p(0.62f, 0.56f)), 0f, -180f, false)
                lineTo(p(0.28f, 0.62f).x, p(0.28f, 0.62f).y)
            }
            drawPath(path, color, style = line)
            head(p(0.28f, 0.72f), 90f)
        }
        ArrowKind.ROUNDABOUT -> {
            drawCircle(color, s * 0.17f, p(0.5f, 0.45f), style = Stroke(width = w))
            drawLine(color, p(0.5f, 0.92f), p(0.5f, 0.62f), strokeWidth = w, cap = StrokeCap.Round)
            val from = p(0.62f, 0.33f)
            val to = p(0.8f, 0.15f)
            drawLine(color, from, to, strokeWidth = w, cap = StrokeCap.Round)
            head(Offset(to.x + w * 0.4f, to.y - w * 0.4f), -45f)
        }
        ArrowKind.ARRIVE -> {
            val c = p(0.5f, 0.36f)
            val r = s * 0.2f
            val pin = Path().apply {
                moveTo(p(0.5f, 0.9f).x, p(0.5f, 0.9f).y)
                lineTo(c.x - r * 0.86f, c.y + r * 0.5f)
                arcTo(Rect(c.x - r, c.y - r, c.x + r, c.y + r), 150f, 240f, false)
                close()
            }
            drawPath(pin, color)
            drawCircle(Color.Black.copy(alpha = 0.55f), r * 0.42f, c)
        }
    }
}
