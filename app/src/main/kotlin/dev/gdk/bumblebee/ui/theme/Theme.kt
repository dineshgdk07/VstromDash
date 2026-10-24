package dev.gdk.bumblebee.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ForgeColorScheme = darkColorScheme(
    primary = Lime,
    onPrimary = OnBlock,
    background = Canvas,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextSecondary,
    outline = Stroke,
    error = Danger,
    onError = TextPrimary,
    scrim = Scrim,
)

private val HudColorScheme = darkColorScheme(
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
        typography = ForgeTypography,
        shapes = ForgeShapes,
        content = content,
    )
}

/** Cluster theme. Landscape HUD reads Rajdhani and the Forge colours through this. */
@Composable
fun HudTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HudColorScheme,
        typography = HudTypography,
        shapes = HudShapes,
        content = content,
    )
}
