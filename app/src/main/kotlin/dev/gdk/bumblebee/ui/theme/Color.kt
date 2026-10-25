package dev.gdk.bumblebee.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Portrait canvas. Cluster colours stay in [HudColors]. */
val Canvas = Color(0xFF171717)
val Surface = Color(0xFF222222)
val SurfaceRaised = Color(0xFF2A2A2A)
val SurfaceSunken = Color(0xFF111111)
val Stroke = Color(0xFF3A3A3A)
val TextPrimary = Color(0xFFF4F5F7)
val TextSecondary = Color(0xFF9A9A9A)
val TextDisabled = Color(0xFF5A5A5A)
val OnBlock = Color(0xFF111111)
/**
 * The app's accent. It follows the bike's colour unless the rider picks another in Bike profile;
 * it lives in snapshot state, so everything drawn with [Lime] repaints when it changes.
 */
object Accent {
    val DEFAULT = Color(0xFFA6FF6A)
    var color by mutableStateOf(DEFAULT)
}

/** Historical name for the accent; reads the current [Accent]. */
val Lime: Color get() = Accent.color

/** The accent, darkened for fills behind light text. */
val LimeDeep: Color get() = lerp(Accent.color, Color.Black, 0.42f)
val BlockYellow = Color(0xFFF7DD48)
val BlockLavender = Color(0xFFA39CE9)
val BlockMint = Color(0xFF82D6C6)
val BlockCoral = Color(0xFFE17A57)
val BlockPink = Color(0xFFF08CE0)
val Danger = Color(0xFFE06B6B)
val Warning = Color(0xFFFF8A2A)
val Scrim = Color.Black.copy(alpha = 0.70f)
