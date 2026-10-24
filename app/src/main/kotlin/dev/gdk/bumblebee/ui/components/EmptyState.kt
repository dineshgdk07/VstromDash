package dev.gdk.bumblebee.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.TextSecondary

/** Centred empty list pattern (§4.13). Ref: `empty-state.jpeg`. */
@Composable
fun EmptyState(
    headline: String,
    subtitle: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    art: (@Composable () -> Unit)? = null,
    linkLabel: String? = null,
    linkIcon: ImageVector? = null,
    onLink: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (art != null) {
            Box(
                Modifier
                    .sizeIn(maxWidth = 200.dp, maxHeight = 200.dp)
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                art()
            }
        }
        Text(
            text = headline.uppercase(),
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        ChunkyButton(
            text = primaryLabel,
            onClick = onPrimary,
            style = ChunkyStyle.Outline,
            modifier = Modifier.fillMaxWidth(),
        )
        if (linkLabel != null && onLink != null) {
            Row(
                Modifier
                    .padding(top = 16.dp)
                    .clickable(onClick = onLink),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (linkIcon != null) {
                    Icon(
                        linkIcon,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(20.dp),
                    )
                }
                Text(
                    text = linkLabel.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                )
            }
        }
    }
}
