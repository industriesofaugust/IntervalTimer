package com.august.intervaltimer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.august.intervaltimer.design.AppStyle
import com.august.intervaltimer.model.IntervalType

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
    // The bottom bar and dialogs use these; without them Material falls back
    // to its own bluish greys.
    surfaceContainer = Sand,
    surfaceContainerHigh = Sand,
    surfaceContainerHighest = SandDeep,
    outline = Color(0xFFCBC3B6),
    outlineVariant = SandDeep,
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
    surfaceContainer = CharcoalCard,
    surfaceContainerHigh = CharcoalCard,
    surfaceContainerHighest = CharcoalCardDeep,
    outline = Color(0xFF57534B),
    outlineVariant = CharcoalCardDeep,
)

private val LocalAppDarkTheme = staticCompositionLocalOf { false }
private val LocalAppStyle = staticCompositionLocalOf { AppStyle.DIAL }

/**
 * Whether the app is showing its dark theme. Screens must ask this instead
 * of the system setting, because the user can override the theme in Settings.
 */
@Composable
fun isAppInDarkTheme(): Boolean = LocalAppDarkTheme.current

/** True for the Dial style, false for Minimal. */
@Composable
fun isDial(): Boolean = LocalAppStyle.current == AppStyle.DIAL

/** Coral for work, teal for rest, in the shade that suits the current theme. */
@Composable
fun typeColor(type: IntervalType): Color {
    val dark = isAppInDarkTheme()
    return when (type) {
        IntervalType.WORK -> if (dark) CoralLight else Coral
        IntervalType.REST -> if (dark) TealLight else Teal
    }
}

/**
 * Grey for intervals that are still to come. Work is a little stronger than
 * rest so the shape of what's left can still be read.
 */
@Composable
fun upcomingColor(type: IntervalType): Color {
    val dark = isAppInDarkTheme()
    return when (type) {
        IntervalType.WORK -> if (dark) Color(0xFF5A544B) else Color(0xFFC4BBAC)
        IntervalType.REST -> if (dark) Color(0xFF3A362F) else Color(0xFFDDD5C7)
    }
}

/** Amber for the get-ready countdown. */
@Composable
fun readyColor(): Color = if (isAppInDarkTheme()) AmberLight else Amber

@Composable
fun IntervalTimerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    style: AppStyle = AppStyle.DIAL,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppDarkTheme provides darkTheme,
        LocalAppStyle provides style,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            content = content,
        )
    }
}
