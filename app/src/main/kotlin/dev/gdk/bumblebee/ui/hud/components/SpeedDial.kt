package dev.gdk.bumblebee.ui.hud.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ride.SpeedSource
import dev.gdk.bumblebee.ui.theme.ErrorRed
import dev.gdk.bumblebee.ui.theme.ForgeGraphite
import dev.gdk.bumblebee.ui.theme.ForgeWhite
import dev.gdk.bumblebee.ui.theme.SuccessGreen
import dev.gdk.bumblebee.ui.theme.SuzukiYellow
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val START_DEG = 135f
private const val SWEEP_DEG = 270f

@Composable
fun SpeedDial(
    speedKmh: Int,
    source: SpeedSource,
    digital: String,
    heading: String?,
    odometerKm: Int?,
    modifier: Modifier = Modifier,
    maxKmh: Int = 160,
) {
    val needleKmh = speedKmh.coerceAtMost(maxKmh)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = min(size.width, size.height) / 2f - 28.dp.toPx()
            val c = Offset(size.width / 2f, size.height / 2f)
            val arcSize = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            val topLeft = Offset(c.x - radius, c.y - radius)
            drawArc(
                color = ForgeGraphite,
                startAngle = START_DEG,
                sweepAngle = SWEEP_DEG,
                useCenter = false,
                style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Butt),
                topLeft = topLeft,
                size = arcSize,
            )
            val paint = Paint().apply {
                color = android.graphics.Color.argb(200, 245, 245, 245)
                textAlign = Paint.Align.CENTER
                textSize = 11.dp.toPx()
                isAntiAlias = true
            }
            for (k in 0..maxKmh step 10) {
                val frac = k / maxKmh.toFloat()
                val deg = START_DEG + SWEEP_DEG * frac
                val rad = Math.toRadians(deg.toDouble())
                val major = k % 20 == 0
                val inner = if (major) radius * 0.82f else radius * 0.88f
                val outer = radius * 0.96f
                val cos = cos(rad).toFloat()
                val sin = sin(rad).toFloat()
                drawLine(
                    color = if (major) ForgeWhite else ForgeGraphite,
                    start = Offset(c.x + inner * cos, c.y + inner * sin),
                    end = Offset(c.x + outer * cos, c.y + outer * sin),
                    strokeWidth = if (major) 3.dp.toPx() else 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                if (major) {
                    val lx = c.x + radius * 1.08f * cos
                    val ly = c.y + radius * 1.08f * sin + 4.dp.toPx()
                    drawContext.canvas.nativeCanvas.drawText("$k", lx, ly, paint)
                }
            }
            val frac = needleKmh / maxKmh.toFloat()
            val needleAngle = Math.toRadians((START_DEG + SWEEP_DEG * frac).toDouble())
            drawLine(
                color = SuzukiYellow,
                start = Offset(
                    c.x + radius * 0.12f * cos(needleAngle).toFloat(),
                    c.y + radius * 0.12f * sin(needleAngle).toFloat(),
                ),
                end = Offset(
                    c.x + radius * 0.78f * cos(needleAngle).toFloat(),
                    c.y + radius * 0.78f * sin(needleAngle).toFloat(),
                ),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(SuzukiYellow, radius = 7.dp.toPx(), center = c)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(digital, style = MaterialTheme.typography.displayLarge, color = ForgeWhite)
            Text("km/h", style = MaterialTheme.typography.labelLarge, color = ForgeWhite.copy(alpha = 0.55f))
            Text(heading ?: "—", style = MaterialTheme.typography.headlineMedium, color = SuccessGreen)
            Text("HEADING", style = MaterialTheme.typography.labelSmall, color = ForgeWhite.copy(alpha = 0.4f))
            Text(
                "ODO ${odometerKm?.let { "%,d".format(it) } ?: "—"} km",
                style = MaterialTheme.typography.labelLarge,
                color = ForgeWhite.copy(alpha = 0.75f),
            )
        }
        Text("F", Modifier.align(Alignment.CenterStart).padding(start = 28.dp), color = ForgeWhite, style = MaterialTheme.typography.labelLarge)
        Text("E", Modifier.align(Alignment.BottomStart).padding(start = 36.dp, bottom = 48.dp), color = ForgeWhite, style = MaterialTheme.typography.labelLarge)
        Text("H", Modifier.align(Alignment.CenterEnd).padding(end = 28.dp), color = ForgeWhite, style = MaterialTheme.typography.labelLarge)
        Text("L", Modifier.align(Alignment.BottomEnd).padding(end = 36.dp, bottom = 48.dp), color = ForgeWhite, style = MaterialTheme.typography.labelLarge)
        val dot = when (source) {
            SpeedSource.GPS -> SuccessGreen
            SpeedSource.BLE -> SuzukiYellow
            SpeedSource.NONE -> ErrorRed
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(10.dp)
                .background(dot, CircleShape),
        )
    }
}
