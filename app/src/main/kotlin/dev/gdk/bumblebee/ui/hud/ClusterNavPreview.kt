package dev.gdk.bumblebee.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.protocol.NavFrame
import dev.gdk.bumblebee.ui.journey.clusterCue
import dev.gdk.bumblebee.ui.journey.prettyDistance
import dev.gdk.bumblebee.ui.journey.prettyEta
import dev.gdk.bumblebee.ui.theme.ForgeWhite
import dev.gdk.bumblebee.ui.theme.LabelMute
import dev.gdk.bumblebee.ui.theme.SuzukiYellow

/**
 * Readout that mirrors the bike TFT. Colours and type stay on the cluster
 * palette on purpose — same exemption as the rider HUD.
 */
@Composable
fun ClusterNavFace(street: String?, frame: NavFrame) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            street?.ifBlank { null } ?: "Next road unnamed",
            style = MaterialTheme.typography.headlineMedium,
            color = ForgeWhite,
        )
        Text(
            prettyDistance(frame.distNext, frame.distNextUnit),
            style = MaterialTheme.typography.displayLarge,
            color = SuzukiYellow,
        )
        Text(
            "until the next cue",
            style = MaterialTheme.typography.labelLarge,
            color = LabelMute,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Mini("ETA", prettyEta(frame.eta), Modifier.weight(1f))
            Mini("Remain", prettyDistance(frame.distTotal, frame.distTotalUnit), Modifier.weight(1f))
        }
        Text(
            clusterCue(frame.maneuverId),
            style = MaterialTheme.typography.bodySmall,
            color = LabelMute,
        )
    }
}

@Composable
private fun Mini(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(ForgeWhite.copy(alpha = 0.05f))
            .padding(10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = LabelMute)
        Text(value, style = MaterialTheme.typography.titleLarge, color = ForgeWhite)
    }
}
