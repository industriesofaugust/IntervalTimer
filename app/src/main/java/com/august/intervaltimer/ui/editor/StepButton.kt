package com.august.intervaltimer.ui.editor

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.august.intervaltimer.ui.theme.figureStyle

// Holding a stepper: first repeat after a short pause, then steadily, then
// faster once the finger has been down a while.
private const val FIRST_REPEAT_MILLIS = 400L
private const val REPEAT_MILLIS = 120L
private const val FAST_AFTER_MILLIS = 2000L
private const val FAST_REPEAT_MILLIS = 60L

/**
 * A − or + button. A tap steps once; holding keeps stepping.
 *
 * It listens to the finger itself instead of using a click handler, because a
 * click only fires on release. The semantics block gives screen readers a
 * plain click, which steps once.
 */
@Composable
fun StepButton(
    symbol: String,
    description: String,
    enabled: Boolean,
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The gesture below outlives recompositions, so it reads the newest lambda.
    val currentOnStep by rememberUpdatedState(onStep)
    val container = MaterialTheme.colorScheme.secondaryContainer
    val content = MaterialTheme.colorScheme.onSecondaryContainer

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) container else container.copy(alpha = 0.4f))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    currentOnStep()
                    val pressedAt = SystemClock.uptimeMillis()
                    var nextStepAt = pressedAt + FIRST_REPEAT_MILLIS
                    while (true) {
                        val wait = nextStepAt - SystemClock.uptimeMillis()
                        val event = if (wait > 0) withTimeoutOrNull(wait) { awaitPointerEvent() } else null
                        if (event == null) {
                            // Still held when the timer ran out: step again.
                            currentOnStep()
                            val held = SystemClock.uptimeMillis() - pressedAt
                            nextStepAt += if (held > FAST_AFTER_MILLIS) FAST_REPEAT_MILLIS else REPEAT_MILLIS
                        } else if (event.changes.any { !it.pressed || it.isConsumed }) {
                            // Let go, or the list took the touch to scroll.
                            break
                        }
                    }
                }
            }
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                if (enabled) onClick(label = description) { currentOnStep(); true } else disabled()
            },
    ) {
        Text(
            text = symbol,
            style = figureStyle(),
            color = if (enabled) content else content.copy(alpha = 0.4f),
        )
    }
}
