package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = CobaltContainer,
    onPrimaryContainer = OnCobaltContainer,
    secondary = CoralAccent,
    onSecondary = Color.White,
    secondaryContainer = CoralContainer,
    onSecondaryContainer = OnCoralContainer,
    tertiary = EmeraldMastery,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = OnEmeraldContainer,
    background = IvoryBackground,
    onBackground = InkPrimary,
    surface = WarmSurface,
    onSurface = InkPrimary,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = InkSecondary,
    outline = HairlineOutline,
    error = CoralAccent,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = CobaltDarkPrimary,
    onPrimary = Color(0xFF071547),
    primaryContainer = CobaltDarkContainer,
    onPrimaryContainer = CobaltContainer,
    secondary = CoralDarkAccent,
    onSecondary = Color(0xFF470D05),
    secondaryContainer = CoralDarkContainer,
    onSecondaryContainer = CoralContainer,
    tertiary = EmeraldDarkMastery,
    onTertiary = Color(0xFF043020),
    tertiaryContainer = EmeraldDarkContainer,
    onTertiaryContainer = EmeraldContainer,
    background = NightBackground,
    onBackground = NightInkPrimary,
    surface = NightSurface,
    onSurface = NightInkPrimary,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightInkSecondary,
    outline = NightOutline,
    error = CoralDarkAccent,
    onError = Color(0xFF470D05)
)

val LingoFloatShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LingoFloatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LingoFloatTypography,
        shapes = LingoFloatShapes,
        content = content
    )
}
