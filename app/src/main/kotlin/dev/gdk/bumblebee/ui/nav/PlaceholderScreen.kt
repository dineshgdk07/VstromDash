package dev.gdk.bumblebee.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.gdk.bumblebee.ui.components.EmptyState
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import dev.gdk.bumblebee.ui.theme.Canvas

@Composable
fun PlaceholderScreen(
    title: String,
    onPrimary: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(
            headline = title,
            subtitle = "This stretch isn't on the map yet.",
            primaryLabel = "Alright",
            onPrimary = onPrimary,
        )
    }
}

@Preview
@Composable
private fun PlaceholderScreenPreview() {
    BumblebeeTheme {
        PlaceholderScreen(title = "Nowhere")
    }
}
