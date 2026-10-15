package com.august.intervaltimer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.IconButtonDefaults
import com.august.intervaltimer.ui.ReadyAccent

private const val HOLD_MILLIS = 1_000

/**
 * Ends the workout only after being held for a second, so a stray touch
 * mid-exercise can't throw the session away. A ring fills while held.
 */
@Composable
fun HoldToEndButton(
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = IconButtonDefaults.DefaultButtonSize,
) {
    val pressSource = remember { MutableInteractionSource() }
    val pressed by pressSource.collectIsPressedAsState()
    val progress = remember { Animatable(0f) }

    LaunchedEffect(pressed) {
        if (pressed) {
            progress.animateTo(1f, tween(HOLD_MILLIS, easing = LinearEasing))
            onEnd()
        } else {
            progress.snapTo(0f)
        }
    }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        FilledTonalIconButton(
            onClick = {},
            modifier = Modifier.fillMaxSize(),
            interactionSource = pressSource,
        ) {
            ButtonIcon(Icons.Filled.Stop, "Hold to end workout", size)
        }
        ProgressRing(
            progress = { progress.value },
            color = ReadyAccent,
            trackColor = Color.Transparent,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
