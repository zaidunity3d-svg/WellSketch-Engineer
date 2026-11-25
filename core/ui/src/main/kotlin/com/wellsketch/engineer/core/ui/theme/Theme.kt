package com.wellsketch.engineer.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy = Color(0xFF16324F)
private val Steel = Color(0xFF3E5C76)
private val Amber = Color(0xFFE09F3E)
private val Paper = Color(0xFFF6F7F9)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4F5),
    onPrimaryContainer = Color(0xFF0B1D2E),
    secondary = Steel,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE5EE),
    onSecondaryContainer = Color(0xFF14232F),
    tertiary = Amber,
    onTertiary = Color(0xFF2B1A00),
    tertiaryContainer = Color(0xFFFFE2B8),
    onTertiaryContainer = Color(0xFF2B1A00),
    background = Paper,
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA6C8EA),
    onPrimary = Color(0xFF0B1D2E),
    primaryContainer = Color(0xFF24466A),
    onPrimaryContainer = Color(0xFFD3E4F5),
    secondary = Color(0xFFB4C7D9),
    onSecondary = Color(0xFF14232F),
    tertiary = Color(0xFFF2C27F),
    onTertiary = Color(0xFF2B1A00),
    background = Color(0xFF0F1418),
    surface = Color(0xFF161C21),
)

@Composable
fun WellSketchTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
