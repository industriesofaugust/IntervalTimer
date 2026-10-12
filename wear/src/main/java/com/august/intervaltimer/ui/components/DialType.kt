package com.august.intervaltimer.ui.components

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.august.intervaltimer.core.R as CoreR

// Both fonts are "variable": one file holds every weight, and the
// variation setting picks which one to draw.
@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, weight: Int) = Font(
    resId = resId,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Numerals, durations, and small capital labels. Every digit is the same width. */
val MonoFamily = FontFamily(
    variable(CoreR.font.jetbrains_mono, 400),
    variable(CoreR.font.jetbrains_mono, 500),
    variable(CoreR.font.jetbrains_mono, 700),
)

/** Titles and names. */
val GroteskFamily = FontFamily(
    variable(CoreR.font.space_grotesk, 500),
    variable(CoreR.font.space_grotesk, 700),
)

object DialType {
    val time = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 38.sp)
    val label = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
    )
    val caption = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Normal, fontSize = 10.sp)
    val figure = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    val name = TextStyle(fontFamily = GroteskFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    val title = TextStyle(fontFamily = GroteskFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp)
}
