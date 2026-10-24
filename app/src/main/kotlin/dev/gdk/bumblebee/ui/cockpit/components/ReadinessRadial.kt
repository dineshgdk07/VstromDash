package dev.gdk.bumblebee.ui.cockpit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.cockpit.PillarStatus
import dev.gdk.bumblebee.ui.cockpit.ReadinessBand
import dev.gdk.bumblebee.ui.cockpit.ReadinessScore
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.NumberLarge
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.TextDisabled
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import dev.gdk.bumblebee.ui.theme.Warning

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadinessRadial(
    score: ReadinessScore,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val arc = bandColor(score.band)
    if (compact) {
        ReadinessRing(score, arc, modifier)
        return
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = score.label.uppercase(),
            style = MaterialTheme.typography.displayMedium,
            color = arc,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowRow(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                score.pillars.forEach { pillar ->
                    Text(
                        text = pillar.name.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = pillarTint(pillar.status),
                    )
                }
            }
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(120.dp)) {
                    val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(SurfaceRaised, 145f, 250f, false, style = stroke)
                    if (score.band != ReadinessBand.COMPUTING) {
                        val sweep = 250f * (score.percent.coerceIn(0, 100) / 100f)
                        drawArc(arc, 145f, sweep, false, style = stroke)
                    }
                }
                Text(
                    text = if (score.band == ReadinessBand.COMPUTING) "…" else "${score.percent}",
                    style = NumberLarge,
                    color = TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun ReadinessRing(score: ReadinessScore, arc: Color, modifier: Modifier) {
    Box(modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(56.dp)) {
            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            drawArc(SurfaceRaised, 145f, 250f, false, style = stroke)
            if (score.band != ReadinessBand.COMPUTING) {
                val sweep = 250f * (score.percent.coerceIn(0, 100) / 100f)
                drawArc(arc, 145f, sweep, false, style = stroke)
            }
        }
        Text(
            text = if (score.band == ReadinessBand.COMPUTING) "…" else "${score.percent}",
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
        )
    }
}

private fun bandColor(band: ReadinessBand): Color = when (band) {
    ReadinessBand.READY -> Lime
    ReadinessBand.CAUTION -> Warning
    ReadinessBand.NOT_READY -> Danger
    ReadinessBand.COMPUTING -> TextSecondary
}

private fun pillarTint(status: PillarStatus): Color = when (status) {
    PillarStatus.READY -> Lime
    PillarStatus.CAUTION -> Warning
    PillarStatus.NOT_READY -> Danger
    PillarStatus.UNKNOWN -> TextDisabled
}
