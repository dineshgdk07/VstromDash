package dev.gdk.bumblebee.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.ForgeBlack
import dev.gdk.bumblebee.ui.theme.ForgeGraphite
import dev.gdk.bumblebee.ui.theme.ForgeWhite
import dev.gdk.bumblebee.ui.theme.SuzukiYellow

val CockpitCoreOrder = listOf(
    Pillar.JOURNEY,
    Pillar.GARAGE,
    Pillar.COCKPIT,
    Pillar.RIDE,
    Pillar.EXPLORER,
)

@Composable
fun CockpitCore(
    currentRoute: String?,
    onSelect: (Pillar) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(ForgeBlack)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(ForgeGraphite)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CockpitCoreOrder.forEach { pillar ->
                if (pillar == Pillar.COCKPIT) {
                    Box(Modifier.weight(1f))
                } else {
                    CoreTab(
                        pillar = pillar,
                        selected = currentRoute == pillar.route,
                        onClick = { onSelect(pillar) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .offset(y = (-18).dp)
                .clickable { onSelect(Pillar.COCKPIT) },
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(ForgeBlack)
                    .border(3.dp, if (currentRoute == Pillar.COCKPIT.route) SuzukiYellow else ForgeGraphite, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("COCKPIT", style = MaterialTheme.typography.labelSmall, color = SuzukiYellow)
                    Text("Tap", style = MaterialTheme.typography.labelSmall, color = ForgeWhite)
                }
            }
        }
    }
}

@Composable
private fun CoreTab(
    pillar: Pillar,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) SuzukiYellow else ForgeWhite.copy(alpha = 0.55f)
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(pillar.icon, contentDescription = pillar.label, tint = tint, modifier = Modifier.size(22.dp))
        Text(pillar.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
