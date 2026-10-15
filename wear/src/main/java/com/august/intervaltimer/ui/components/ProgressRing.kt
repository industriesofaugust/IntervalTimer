package com.august.intervaltimer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A ring that fills clockwise from the top, with a faint track behind it.
 *
 * [progress] (0 to 1) is read only while drawing, so a value that changes
 * every tick redraws the ring without recomposing anything. This is used
 * instead of the Material progress indicator, which animates every change
 * and would restart that animation on each tick.
 */
@Composable
fun ProgressRing(
    progress: () -> Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 4.dp,
) {
    Box(
        modifier
            .graphicsLayer()
            .drawWithCache {
                val width = strokeWidth.toPx()
                val stroke = Stroke(width = width)
                val topLeft = Offset(width / 2f, width / 2f)
                val arcSize = Size(size.width - width, size.height - width)
                onDrawBehind {
                    if (trackColor.alpha > 0f) {
                        drawArc(trackColor, -90f, 360f, false, topLeft, arcSize, style = stroke)
                    }
                    val sweep = 360f * progress().coerceIn(0f, 1f)
                    if (sweep > 0f) {
                        drawArc(color, -90f, sweep, false, topLeft, arcSize, style = stroke)
                    }
                }
            }
    )
}
