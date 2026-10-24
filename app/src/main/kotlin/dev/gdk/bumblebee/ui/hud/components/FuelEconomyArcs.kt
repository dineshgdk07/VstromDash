package dev.gdk.bumblebee.ui.hud.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.ForgeGraphite
import dev.gdk.bumblebee.ui.theme.ForgeWhite
import dev.gdk.bumblebee.ui.theme.SuzukiYellow
import dev.gdk.bumblebee.ui.theme.WarningAmber

@Composable
fun FuelArc(bars: Int?, modifier: Modifier = Modifier) {
    val frac = ((bars ?: 0).coerceIn(0, 6)) / 6f
    MiniArc(
        top = "F",
        bottom = "E",
        value = bars?.let { "$it bars" } ?: "—",
        caption = "FUEL",
        fraction = if (bars == null) 0f else frac,
        accent = if (bars != null && bars <= 1) WarningAmber else SuzukiYellow,
        modifier = modifier,
    )
}

@Composable
fun EconomyArc(kmL: Double?, modifier: Modifier = Modifier) {
    val frac = ((kmL ?: 0.0).coerceIn(0.0, 60.0) / 60.0).toFloat()
    MiniArc(
        top = "H",
        bottom = "L",
        value = kmL?.let { "%.1f".format(it) } ?: "—",
        caption = "km/L",
        fraction = if (kmL == null) 0f else frac,
        accent = SuzukiYellow,
        modifier = modifier,
    )
}

@Composable
private fun MiniArc(
    top: String,
    bottom: String,
    value: String,
    caption: String,
    fraction: Float,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                drawArc(ForgeGraphite, 140f, 260f, false, style = stroke)
                if (fraction > 0f) {
                    drawArc(accent, 140f, 260f * fraction, false, style = stroke)
                }
            }
            Text(top, Modifier.align(Alignment.TopCenter).padding(top = 10.dp), style = MaterialTheme.typography.labelSmall, color = ForgeWhite)
            Text(bottom, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), style = MaterialTheme.typography.labelSmall, color = ForgeWhite)
        }
        Text(caption, style = MaterialTheme.typography.labelSmall, color = ForgeWhite.copy(alpha = 0.55f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = ForgeWhite)
    }
}
