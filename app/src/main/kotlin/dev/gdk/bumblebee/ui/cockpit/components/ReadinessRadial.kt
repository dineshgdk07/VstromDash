package dev.gdk.bumblebee.ui.cockpit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
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
import dev.gdk.bumblebee.ui.theme.SuzukiYellow
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            Text("RIDE READINESS", style = MaterialTheme.typography.labelMedium, color = ForgeWhite.copy(alpha = 0.55f))
            Text(
                text = if (score.band == ReadinessBand.COMPUTING) "…" else "${score.percent}%",
                style = MaterialTheme.typography.displayLarge,
            )
            Text(score.label, style = MaterialTheme.typography.titleMedium, color = bandColor)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                score.pillars.take(4).forEach { pillar ->
                    val ok = pillar.status == PillarStatus.READY
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(
                            if (ok) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (ok) SuccessGreen else ForgeGraphite,
                        )
                        Text(pillar.name, style = MaterialTheme.typography.labelSmall, color = ForgeWhite.copy(alpha = 0.75f))
                    }
                }
            }
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(128.dp)) {
            Canvas(Modifier.size(128.dp)) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash)
                drawArc(ForgeGraphite, 145f, 250f, false, style = stroke)
                val sweep = 250f * (score.percent.coerceIn(0, 100) / 100f)
                if (score.band != ReadinessBand.COMPUTING) {
                    drawArc(SuzukiYellow, 145f, sweep, false, style = stroke)
                }
            }
            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = SuzukiYellow, modifier = Modifier.size(36.dp))
        }
    }
}
