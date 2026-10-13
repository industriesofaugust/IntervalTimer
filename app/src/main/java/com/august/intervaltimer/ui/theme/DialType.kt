package com.august.intervaltimer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

/** Text styles for the Dial look. Minimal uses the Material theme's own. */
object DialType {
    /** The big timer number. */
    val countdown = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 84.sp)

    /** The countdown when written as 1:30. */
    val countdownWide = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 60.sp)

    /** "WORK 3/5", section headings, bottom bar. */
    val label = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.5.sp,
    )

    /** The exercise name on the timer, in capitals. */
    val exercise = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 1.sp,
    )

    /** Small supporting text: "5 INTERVALS · 2:40", dates. */
    val caption = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp)

    /** Numbers in tiles and lists. */
    val figure = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 18.sp)
    val figureLarge = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 30.sp)

    val button = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 1.sp,
    )

    /** Template and workout names: normal case, not capitals. */
    val name = TextStyle(fontFamily = GroteskFamily, fontWeight = FontWeight.Medium, fontSize = 17.sp)
    val title = TextStyle(fontFamily = GroteskFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp)
}

/**
 * A section heading. Dial: spaced capitals in the mono font. Minimal: the
 * plain title style the app had before.
 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    if (isDial()) {
        Text(
            text = text.uppercase(),
            style = DialType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    } else {
        Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
    }
}

/** A page title: "ACTIVITY" in Dial, "Activity" in Minimal. */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    if (isDial()) {
        Text(text = text.uppercase(), style = DialType.title, modifier = modifier)
    } else {
        Text(text = text, style = MaterialTheme.typography.headlineMedium, modifier = modifier)
    }
}

/** Supporting text style: mono in Dial, body text in Minimal. */
@Composable
fun captionStyle(): TextStyle =
    if (isDial()) DialType.caption else MaterialTheme.typography.bodyMedium

/** Numbers: mono in Dial, title text in Minimal. */
@Composable
fun figureStyle(): TextStyle =
    if (isDial()) DialType.figure else MaterialTheme.typography.titleMedium

/** Names of templates and workouts. */
@Composable
fun nameStyle(): TextStyle =
    if (isDial()) DialType.name else MaterialTheme.typography.titleMedium

/** Capitals in Dial, unchanged in Minimal. For labels, never for names. */
@Composable
fun String.labelCase(): String = if (isDial()) uppercase() else this
