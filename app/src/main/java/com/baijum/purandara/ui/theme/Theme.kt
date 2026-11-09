@file:Suppress("MagicNumber")

package com.baijum.purandara.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Brand palette (docs/branding.md): temple maroon and turmeric gold on a warm cream base.
private val Maroon = Color(0xFF7A1F2B)
private val LightMaroon = Color(0xFFF2B8BE)
private val Gold = Color(0xFFC8962E)
private val Cream = Color(0xFFFFF8EE)
private val DeepBrown = Color(0xFF2E1A12)
private val MutedBrown = Color(0xFF7D6457)

private val LightColorScheme = lightColorScheme(
    primary = Maroon,
    onPrimary = Color.White,
    primaryContainer = LightMaroon,
    onPrimaryContainer = DeepBrown,
    secondary = Gold,
    onSecondary = DeepBrown,
    surface = Cream,
    onSurface = DeepBrown,
    onSurfaceVariant = MutedBrown,
    background = Cream,
    onBackground = DeepBrown,
)

private val DarkColorScheme = darkColorScheme(
    primary = LightMaroon,
    onPrimary = DeepBrown,
    primaryContainer = Maroon,
    onPrimaryContainer = Color(0xFFFFDADD),
    secondary = Gold,
    onSecondary = DeepBrown,
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    onSurfaceVariant = Color(0xFFCAC4D0),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = 0.5.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, letterSpacing = 0.25.sp),
)

@Composable
fun PurandaraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        content = content,
    )
}
