package com.august.intervaltimer.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.august.intervaltimer.core.R as CoreR

/** True when the watch is using the Dial style, false for Minimal. */
val LocalDial = compositionLocalOf { true }

// Both fonts are "variable": one file holds every weight, and the
// variation setting picks which one to draw.
@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, weight: Int) = Font(
    resId = resId,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Numerals, durations, and capital labels. Every digit is the same width. */
val MonoFamily = FontFamily(
    variable(CoreR.font.jetbrains_mono, 400),
    variable(CoreR.font.jetbrains_mono, 500),
    variable(CoreR.font.jetbrains_mono, 700),
)

/** Titles and template names. */
val GroteskFamily = FontFamily(
    variable(CoreR.font.space_grotesk, 500),
    variable(CoreR.font.space_grotesk, 700),
)

object DialType {
    /** The big timer number. */
    val countdown = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 60.sp)

    /** The countdown when it is written as 1:30 and needs more room. */
    val countdownWide = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 44.sp)

    /** Totals on the finish and preview screens. */
    val time = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 30.sp)

    /** "WORK 3/5", section headings. */
    val label = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
    )

    /** The exercise name on the timer, in capitals. */
    val exercise = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp,
    )
    val caption = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp)
    val figure = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 20.sp)

    /** Template names in lists. */
    val name = TextStyle(fontFamily = GroteskFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    val button = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.sp,
    )
}
