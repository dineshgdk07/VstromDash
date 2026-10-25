package dev.gdk.bumblebee.ui.cockpit.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.R
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.ui.cockpit.ReadinessScore
import dev.gdk.bumblebee.ui.theme.BlockYellow
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.ShapeLg
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.Surface
import dev.gdk.bumblebee.ui.theme.TextPrimary

/** Yellow band holds name, connection and bike art side by side; the verdict sits in a short strip under it. */
@Composable
fun BikeHeroCard(
    model: String,
    connection: ConnectionState,
    verdict: String,
    score: ReadinessScore,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(ShapeLg)
            .background(Surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(136.dp)
                .background(BlockYellow),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = model,
                    style = MaterialTheme.typography.titleMedium,
                    color = OnBlock,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                ConnectionChip(connection)
            }
            Image(
                painter = painterResource(dev.gdk.bumblebee.bike.CurrentBike.variant.image),
                contentDescription = model,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .weight(1.25f)
                    .fillMaxHeight(),
            )
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = verdict,
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            )
            ReadinessRadial(score = score, compact = true)
        }
    }
}

/** On the yellow band: a dark filled pill when connected, a dark outline otherwise. */
@Composable
private fun ConnectionChip(connection: ConnectionState, modifier: Modifier = Modifier) {
    val ready = connection is ConnectionState.Ready
    val label = when (connection) {
        ConnectionState.Ready -> "Connected"
        ConnectionState.Connecting, ConnectionState.Discovering -> "Connecting"
        else -> "Not connected"
    }
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (ready) Lime else OnBlock,
        maxLines = 1,
        modifier = modifier
            .then(if (ready) Modifier.background(OnBlock, ShapePill) else Modifier.border(1.dp, OnBlock, ShapePill))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
