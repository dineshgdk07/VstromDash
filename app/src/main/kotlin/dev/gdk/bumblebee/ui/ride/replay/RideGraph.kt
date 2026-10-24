package dev.gdk.bumblebee.ui.ride.replay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ride.PlotPoint
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.Stroke as GridStroke

internal data class GraphLeg(val a: PlotPoint, val b: PlotPoint, val color: Color)

internal data class GraphPinMark(val x: Float, val color: Color)

internal fun DrawScope.drawGraphSeries(
    legs: List<GraphLeg>,
    pins: List<GraphPinMark>,
    cursorFrac: Float?,
    cursorY: Float?,
) {
    val padL = 4.dp.toPx()
    val padB = 6.dp.toPx()
    val w = size.width - padL
    val h = size.height - padB
    fun pt(p: PlotPoint) = Offset(padL + p.x * w, p.y * h)
    for (i in 1..3) {
        val y = h * (i / 4f)
        drawLine(GridStroke, Offset(padL, y), Offset(size.width, y))
    }
    val oneColor = legs.isNotEmpty() && legs.all { it.color == legs.first().color }
    if (oneColor) {
        val path = Path().apply {
            val first = pt(legs.first().a)
            moveTo(first.x, first.y)
            for (leg in legs) lineTo(pt(leg.b).x, pt(leg.b).y)
        }
        drawPath(
            path,
            legs.first().color,
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    } else {
        for (leg in legs) {
            drawLine(leg.color, pt(leg.a), pt(leg.b), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
    }
    pins.forEach { pin ->
        drawCircle(pin.color, 4.dp.toPx(), Offset(padL + pin.x.coerceIn(0f, 1f) * w, 4.dp.toPx()))
    }
    if (cursorFrac != null) {
        val x = padL + cursorFrac.coerceIn(0f, 1f) * w
        drawLine(GridStroke, Offset(x, 0f), Offset(x, h), strokeWidth = 1.5.dp.toPx())
        val y = (cursorY ?: 0.5f).coerceIn(0f, 1f) * h
        drawCircle(OnBlock, 7.dp.toPx(), Offset(x, y))
        drawCircle(Lime, 4.dp.toPx(), Offset(x, y))
    }
}
