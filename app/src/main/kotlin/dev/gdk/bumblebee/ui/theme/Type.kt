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

private const val TABULAR = "tnum"

val ForgeTypography = Typography(
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
    bodyLarge = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(
        fontFamily = Rajdhani,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.8.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Rajdhani,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 1.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Rajdhani,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
    ),
)
