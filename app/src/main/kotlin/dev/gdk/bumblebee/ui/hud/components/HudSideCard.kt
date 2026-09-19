package dev.gdk.bumblebee.ui.hud.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.ForgeGraphite
import dev.gdk.bumblebee.ui.theme.ForgeWhite

@Composable
fun HudSideCard(
    label: String,
    value: String,
    sub: String,
    accent: Color = ForgeWhite,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ForgeGraphite)
            .padding(12.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = ForgeWhite.copy(alpha = 0.5f))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = accent)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = ForgeWhite.copy(alpha = 0.55f))
    }
}
