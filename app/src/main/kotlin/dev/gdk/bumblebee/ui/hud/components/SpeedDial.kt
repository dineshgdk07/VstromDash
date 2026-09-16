package dev.gdk.bumblebee.ui.hud.components

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
    modifier: Modifier = Modifier,
    maxKmh: Int = 160,
) {
    val needleKmh = speedKmh.coerceAtMost(maxKmh)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            val radius = min(size.width, size.height) / 2f - 16.dp.toPx()
            val c = Offset(size.width / 2f, size.height / 2f)
            drawArc(
                color = ForgeGraphite,
                startAngle = START_DEG,
                sweepAngle = SWEEP_DEG,
                useCenter = false,
                style = stroke,
                topLeft = Offset(c.x - radius, c.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            )
            val frac = needleKmh / maxKmh.toFloat()
            val needleAngle = Math.toRadians((START_DEG + SWEEP_DEG * frac).toDouble())
            val inner = radius * 0.2f
            val outer = radius * 0.88f
            val sx = c.x + inner * cos(needleAngle).toFloat()
            val sy = c.y + inner * sin(needleAngle).toFloat()
            val ex = c.x + outer * cos(needleAngle).toFloat()
            val ey = c.y + outer * sin(needleAngle).toFloat()
            drawLine(
                color = SuzukiYellow,
                start = Offset(sx, sy),
                end = Offset(ex, ey),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(digital, style = MaterialTheme.typography.displayLarge, color = ForgeWhite)
            Text("km/h", style = MaterialTheme.typography.labelLarge)
            Text(heading ?: "—", style = MaterialTheme.typography.headlineSmall, color = SuzukiYellow)
        }
        val dot = when (source) {
            SpeedSource.GPS -> SuccessGreen
            SpeedSource.BLE -> SuzukiYellow
            SpeedSource.NONE -> ErrorRed
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(12.dp)
                .background(dot, CircleShape),
        )
    }
}
