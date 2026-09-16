package dev.gdk.bumblebee.ui.hud.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
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
        label = "Fuel",
        value = bars?.let { "$it/6" } ?: "—",
        fraction = if (bars == null) 0f else frac,
        accent = if (bars != null && bars <= 1) WarningAmber else SuzukiYellow,
        modifier = modifier,
    )
}

@Composable
fun EconomyArc(kmL: Double?, modifier: Modifier = Modifier) {
    val frac = ((kmL ?: 0.0).coerceIn(0.0, 60.0) / 60.0).toFloat()
    MiniArc(
        label = "Econ",
        value = kmL?.let { "%.1f".format(it) } ?: "—",
        fraction = if (kmL == null) 0f else frac,
        accent = SuzukiYellow,
        modifier = modifier,
    )
}

@Composable
private fun MiniArc(
    label: String,
    value: String,
    fraction: Float,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.size(88.dp)) {
            val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            drawArc(ForgeGraphite, 140f, 260f, false, style = stroke)
            if (fraction > 0f) {
                drawArc(accent, 140f, 260f * fraction, false, style = stroke)
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = ForgeWhite)
        Text(value, style = MaterialTheme.typography.titleMedium, color = ForgeWhite)
    }
}
