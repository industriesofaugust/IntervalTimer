package com.brian.intervaltimer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = SandDeep,
    onSecondaryContainer = Ink,
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = Sand,
    onSurfaceVariant = InkMuted,
    outline = Color(0xFFCBC3B6),
)

private val DarkColors = darkColorScheme(
    primary = CoralLight,
    onPrimary = Color(0xFF3A140A),
    secondary = TealLight,
    onSecondary = Color(0xFF06302B),
    secondaryContainer = CharcoalCardDeep,
    onSecondaryContainer = Parchment,
    background = Charcoal,
    onBackground = Parchment,
    surface = Charcoal,
    onSurface = Parchment,
    surfaceVariant = CharcoalCard,
    onSurfaceVariant = ParchmentMuted,
    outline = Color(0xFF57534B),
)

@Composable
fun IntervalTimerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
