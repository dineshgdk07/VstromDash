package dev.gdk.bumblebee.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.gdk.bumblebee.R

val Rajdhani = FontFamily(
    Font(R.font.rajdhani_regular, FontWeight.Normal),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold),
)

val Anton = FontFamily(Font(R.font.anton_regular, FontWeight.Normal))
val Marker = FontFamily(Font(R.font.permanent_marker_regular, FontWeight.Normal))

/** Portrait UI text. Anton stays the display face; see [HudTypography] for the cluster. */
val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
)

private const val TABULAR = "tnum"

/** Rajdhani scale the landscape HUD already ships with. Do not retune. */
val HudTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Rajdhani,
        fontWeight = FontWeight.Bold,
        fontSize = 56.sp,
        fontFeatureSettings = TABULAR,
        color = ForgeWhite,
    ),
    headlineLarge = TextStyle(
        fontFamily = Rajdhani,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        fontFeatureSettings = TABULAR,
        color = ForgeWhite,
    ),
    headlineMedium = TextStyle(
        fontFamily = Rajdhani,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        color = ForgeWhite,
    ),
    titleLarge = TextStyle(
        fontFamily = Rajdhani,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        color = ForgeWhite,
    ),
    titleMedium = TextStyle(
        fontFamily = Rajdhani,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        color = ForgeWhite,
    ),
    titleSmall = TextStyle(
        fontFamily = Rajdhani,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 1.2.sp,
        color = ForgeWhite,
    ),
    bodyLarge = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Normal, fontSize = 16.sp, color = ForgeWhite),
    bodyMedium = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Normal, fontSize = 14.sp, color = ForgeWhite),
    bodySmall = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Normal, fontSize = 12.sp, color = ForgeWhite),
    labelLarge = TextStyle(
        fontFamily = Rajdhani,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.8.sp,
        color = ForgeWhite,
    ),
    labelMedium = TextStyle(
        fontFamily = Rajdhani,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 1.4.sp,
        color = ForgeWhite,
    ),
    labelSmall = TextStyle(
        fontFamily = Rajdhani,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
        color = ForgeWhite,
    ),
)

private fun anton(size: Int, line: Int) = TextStyle(
    fontFamily = Anton,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp,
)

private fun nunito(
    weight: FontWeight,
    size: Int,
    line: Int,
    tracking: Float = 0f,
    tabular: Boolean = false,
) = TextStyle(
    fontFamily = Nunito,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = if (tabular) TABULAR else null,
)

val NumberLarge = nunito(FontWeight.ExtraBold, 32, 36, tabular = true)
val NumberMedium = nunito(FontWeight.Bold, 20, 24, tabular = true)

private val DisplayLarge = anton(48, 50)
private val DisplayMedium = anton(36, 38)
private val HeadlineMedium = anton(28, 30)
private val HeadlineSmall = anton(26, 28)

/**
 * Portrait type. Material slots the spec does not name take the nearest role
 * so nothing falls through to Roboto: displaySmall = headlineSmall,
 * headlineLarge = displayMedium, titleSmall = Nunito SemiBold 14/18,
 * bodySmall = Nunito Regular 12/16.
 */
val ForgeTypography = Typography(
    displayLarge = DisplayLarge,
    displayMedium = DisplayMedium,
    displaySmall = HeadlineSmall,
    headlineLarge = DisplayMedium,
    headlineMedium = HeadlineMedium,
    headlineSmall = HeadlineSmall,
    titleLarge = nunito(FontWeight.SemiBold, 20, 26),
    titleMedium = nunito(FontWeight.Bold, 17, 22),
    titleSmall = nunito(FontWeight.SemiBold, 14, 18),
    bodyLarge = nunito(FontWeight.Normal, 16, 22),
    bodyMedium = nunito(FontWeight.Normal, 14, 20),
    bodySmall = nunito(FontWeight.Normal, 12, 16),
    labelLarge = nunito(FontWeight.ExtraBold, 15, 20, tracking = 0.6f),
    labelMedium = nunito(FontWeight.Bold, 13, 16, tracking = 1.4f),
    labelSmall = nunito(FontWeight.Bold, 11, 14, tracking = 1.0f),
)
