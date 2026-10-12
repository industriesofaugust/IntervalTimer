package com.brian.intervaltimer.ui

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors

// Watch screens are always dark, so we use the light accent variants.
val WorkAccent = Color(0xFFE87F63)
val RestAccent = Color(0xFF52B3A5)
val ReadyAccent = Color(0xFFD6A954)

val WearColors = Colors(
    primary = WorkAccent,
    onPrimary = Color(0xFF3A140A),
    secondary = RestAccent,
    onSecondary = Color(0xFF06302B),
    background = Color.Black,
    onBackground = Color(0xFFEFEAE2),
    surface = Color(0xFF201D19),
    onSurface = Color(0xFFEFEAE2),
    onSurfaceVariant = Color(0xFFA39B8F),
)
