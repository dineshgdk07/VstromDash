package dev.gdk.bumblebee.ui.cockpit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.cockpit.PillarStatus
import dev.gdk.bumblebee.ui.cockpit.ReadinessBand
import dev.gdk.bumblebee.ui.cockpit.ReadinessScore
import dev.gdk.bumblebee.ui.theme.ErrorRed
import dev.gdk.bumblebee.ui.theme.ForgeGraphite
import dev.gdk.bumblebee.ui.theme.ForgeWhite
import dev.gdk.bumblebee.ui.theme.SuccessGreen
import dev.gdk.bumblebee.ui.theme.WarningAmber

@Composable
fun ReadinessRadial(
    score: ReadinessScore,
    modifier: Modifier = Modifier,
) {
    val bandColor = when (score.band) {
        ReadinessBand.READY -> SuccessGreen
        ReadinessBand.CAUTION -> WarningAmber
        ReadinessBand.NOT_READY -> ErrorRed
        ReadinessBand.COMPUTING -> ForgeGraphite
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
            Canvas(modifier = Modifier.size(160.dp)) {
                val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = ForgeGraphite,
                    startAngle = 140f,
                    sweepAngle = 260f,
                    useCenter = false,
                    style = stroke,
                )
                val sweep = 260f * (score.percent.coerceIn(0, 100) / 100f)
                if (score.band != ReadinessBand.COMPUTING) {
                    drawArc(
                        color = bandColor,
                        startAngle = 140f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = stroke,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (score.band == ReadinessBand.COMPUTING) "…" else "${score.percent}%",
                    style = MaterialTheme.typography.headlineLarge,
                    color = ForgeWhite,
                )
                Text(score.label, style = MaterialTheme.typography.labelLarge, color = bandColor)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            score.pillars.forEach { pillar ->
                val color = when (pillar.status) {
                    PillarStatus.READY -> SuccessGreen
                    PillarStatus.CAUTION -> WarningAmber
                    PillarStatus.NOT_READY -> ErrorRed
                    PillarStatus.UNKNOWN -> ForgeGraphite
                }
                Text(
                    text = "${pillar.name} · ${pillar.detail}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ForgeWhite,
                    modifier = Modifier
                        .background(color.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
