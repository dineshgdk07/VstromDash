package dev.gdk.bumblebee.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ForgeColorScheme = darkColorScheme(
    primary = SuzukiYellow,
    onPrimary = ForgeBlack,
    background = ForgeBlack,
    onBackground = ForgeWhite,
    surface = ForgeGraphite,
    onSurface = ForgeWhite,
    error = ErrorRed,
    onError = ForgeWhite,
)

@Composable
fun BumblebeeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ForgeColorScheme,
        shapes = ForgeShapes,
        content = content,
    )
}
