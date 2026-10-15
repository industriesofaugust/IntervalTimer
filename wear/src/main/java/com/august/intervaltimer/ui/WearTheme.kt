package com.august.intervaltimer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography
import com.august.intervaltimer.ui.components.GroteskFamily
import com.august.intervaltimer.ui.components.MonoFamily

// Watch screens are always dark, so we use the light accent variants.
// Coral = work, teal = rest, amber = get ready.
val WorkAccent = Color(0xFFE87F63)
val RestAccent = Color(0xFF52B3A5)
val ReadyAccent = Color(0xFFD6A954)

// Every role is set, so no default colour from the library shows anywhere.
private val WearColorScheme = ColorScheme(
    primary = WorkAccent,
    primaryDim = Color(0xFFC9674D),
    primaryContainer = Color(0xFF4A2A20),
    onPrimary = Color(0xFF3A140A),
    onPrimaryContainer = Color(0xFFFFD9CC),
    secondary = RestAccent,
    secondaryDim = Color(0xFF3E9286),
    secondaryContainer = Color(0xFF183A35),
    onSecondary = Color(0xFF06302B),
    onSecondaryContainer = Color(0xFFBFEDE5),
    tertiary = ReadyAccent,
    tertiaryDim = Color(0xFFB38B3F),
    tertiaryContainer = Color(0xFF43351A),
    onTertiary = Color(0xFF2E2106),
    onTertiaryContainer = Color(0xFFF6E1B5),
    surfaceContainerLow = Color(0xFF161411),
    surfaceContainer = Color(0xFF201D19),
    surfaceContainerHigh = Color(0xFF2B2722),
    onSurface = Color(0xFFEFEAE2),
    onSurfaceVariant = Color(0xFFA39B8F),
    outline = Color(0xFF6B645A),
    outlineVariant = Color(0xFF3A362F),
    background = Color.Black,
    onBackground = Color(0xFFEFEAE2),
    error = Color(0xFFEE675C),
    errorDim = Color(0xFFC9524A),
    errorContainer = Color(0xFF4A1F1B),
    onError = Color(0xFF3A0C09),
    onErrorContainer = Color(0xFFFFD9D5),
)

// In Wear Material 3 every text role carries its own font, so a single
// default font can't be set; each role gets ours. Labels and numbers are
// JetBrains Mono, titles and body text are Space Grotesk.
private fun mono(style: TextStyle) = style.copy(fontFamily = MonoFamily)
private fun grotesk(style: TextStyle) = style.copy(fontFamily = GroteskFamily)

// Mono capitals are much wider than the default font, so labels are scaled
// down to keep lines like "23 INTERVALS · 9:30" on one line of a round screen.
private fun monoLabel(style: TextStyle) = mono(style).copy(fontSize = style.fontSize * 0.88f)

private val WearTypography: Typography = Typography().let { base ->
    Typography(
        arcLarge = base.arcLarge.copy(fontFamily = MonoFamily),
        arcMedium = base.arcMedium.copy(fontFamily = MonoFamily),
        arcSmall = base.arcSmall.copy(fontFamily = MonoFamily),
        displayLarge = grotesk(base.displayLarge),
        displayMedium = grotesk(base.displayMedium),
        displaySmall = grotesk(base.displaySmall),
        titleLarge = grotesk(base.titleLarge),
        titleMedium = grotesk(base.titleMedium),
        titleSmall = grotesk(base.titleSmall),
        labelLarge = monoLabel(base.labelLarge),
        labelMedium = monoLabel(base.labelMedium),
        labelSmall = monoLabel(base.labelSmall),
        bodyLarge = grotesk(base.bodyLarge),
        bodyMedium = grotesk(base.bodyMedium),
        bodySmall = grotesk(base.bodySmall),
        bodyExtraSmall = grotesk(base.bodyExtraSmall),
        numeralExtraLarge = mono(base.numeralExtraLarge),
        numeralLarge = mono(base.numeralLarge),
        numeralMedium = mono(base.numeralMedium),
        numeralSmall = mono(base.numeralSmall),
        numeralExtraSmall = mono(base.numeralExtraSmall),
    )
}

@Composable
fun WearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WearColorScheme,
        typography = WearTypography,
        content = content,
    )
}
