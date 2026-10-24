package dev.gdk.bumblebee.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val ShapeXs = RoundedCornerShape(6.dp)
val ShapeSm = RoundedCornerShape(12.dp)
val ShapeMd = RoundedCornerShape(16.dp)
val ShapeLg = RoundedCornerShape(20.dp)
val ShapeXl = RoundedCornerShape(28.dp)
val ShapePill = RoundedCornerShape(percent = 50)

/** Portrait screen gutter (§2.4). The shell applies it once. */
val ScreenGutter = 20.dp

/** Corner radii the HUD shipped with. [HudTheme] keeps them. */
val HudShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
)

val ForgeShapes = Shapes(
    extraSmall = ShapeXs,
    small = ShapeSm,
    medium = ShapeMd,
    large = ShapeLg,
    extraLarge = ShapeXl,
)
