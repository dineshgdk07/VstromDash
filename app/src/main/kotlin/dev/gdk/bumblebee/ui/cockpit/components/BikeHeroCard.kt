package dev.gdk.bumblebee.ui.cockpit.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.R
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.ui.components.ChunkyIconButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.theme.BlockYellow
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.ShapeLg
import dev.gdk.bumblebee.ui.theme.Surface
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import dev.gdk.bumblebee.ui.theme.Warning

@Composable
fun BikeHeroCard(
    model: String,
    connection: ConnectionState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (status, statusColor) = when (connection) {
        is ConnectionState.Ready -> "Connected" to Lime
        is ConnectionState.Connecting, ConnectionState.Discovering -> "Connecting" to TextSecondary
        is ConnectionState.Failed, is ConnectionState.Disconnected -> "Not connected" to Warning
        else -> "Not connected" to TextSecondary
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(ShapeLg)
            .background(Surface),
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .background(BlockYellow),
        )
        Image(
            painter = painterResource(R.drawable.vstrom_hero),
            contentDescription = model,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
                .width(200.dp)
                .height(148.dp),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 72.dp, bottom = 20.dp),
        ) {
            Text(
                text = model,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        ChunkyIconButton(
            icon = Icons.Rounded.ChevronRight,
            onClick = onOpen,
            style = ChunkyStyle.Lime,
            contentDescription = "Open garage",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
        )
    }
}
