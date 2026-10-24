package dev.gdk.bumblebee.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.Stroke
import dev.gdk.bumblebee.ui.theme.TextDisabled
import dev.gdk.bumblebee.ui.theme.TextSecondary

/** Uppercased row label with optional count and hairline (§4.10). Ref: `empty-state.jpeg`. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    showHairline: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
        )
        if (count != null) {
            Text(
                text = " $count",
                style = MaterialTheme.typography.labelMedium,
                color = TextDisabled,
            )
        }
        if (showHairline) {
            Box(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
                    .height(1.dp)
                    .background(Stroke),
            )
        }
    }
}
