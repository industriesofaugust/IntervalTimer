package com.august.intervaltimer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.august.intervaltimer.design.RingSegment
import com.august.intervaltimer.design.SegmentState
import com.august.intervaltimer.design.StripSegment
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.ui.RestAccent
import com.august.intervaltimer.ui.WorkAccent
import kotlin.math.cos
import kotlin.math.sin

// What is still to come is grey. Work is a little lighter than rest so the
// shape of the remaining session can still be read.
private val UpcomingWork = Color(0xFF5A544B)
private val UpcomingRest = Color(0xFF3A362F)

private fun litColor(type: IntervalType): Color = when (type) {
    IntervalType.WORK -> WorkAccent
    IntervalType.REST -> RestAccent
}

private fun greyColor(type: IntervalType): Color = when (type) {
    IntervalType.WORK -> UpcomingWork
    IntervalType.REST -> UpcomingRest
}

/**
 * The whole workout as a dial: one arc per interval. The ring lights up as
 * you go: finished intervals are coral (work) or teal (rest), the current
 * one fills clockwise, and what is still to come stays grey.
 *
 * With [outline] set (the dimmed ambient screen) it draws thin arcs only, to
 * keep lit pixels to a minimum.
 */
@Composable
fun SessionRing(
    segments: List<RingSegment>,
    currentRemaining: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 7.dp,
    showTicks: Boolean = true,
    outline: Boolean = false,
) {
    Canvas(modifier = modifier) {
        val stroke = (if (outline) 2.dp else strokeWidth).toPx()
        val inset = strokeWidth.toPx() / 2f
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)

        fun arc(color: Color, start: Float, sweep: Float) {
            if (sweep <= 0f) return
            // Canvas angles start at 3 o'clock; ours start at 12.
            drawArc(
                color = color,
                startAngle = start - 90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
        }

        segments.forEach { segment ->
            val lit = if (outline) Color.LightGray else litColor(segment.type)
            val grey = if (outline) Color.DarkGray else greyColor(segment.type)
            when (segment.state) {
                SegmentState.DONE -> arc(lit, segment.startAngle, segment.sweep)
                SegmentState.UPCOMING -> arc(grey, segment.startAngle, segment.sweep)
                SegmentState.CURRENT -> {
                    val filled = segment.sweep * (1f - currentRemaining)
                    arc(lit, segment.startAngle, filled)
                    arc(grey, segment.startAngle + filled, segment.sweep - filled)
                }
            }
        }

        if (showTicks && !outline) drawTicks(ringWidth = strokeWidth.toPx())
    }
}

/** Sixty marks just inside the ring, every fifth one longer, like a stopwatch. */
private fun DrawScope.drawTicks(ringWidth: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val outer = size.minDimension / 2f - ringWidth - 3.dp.toPx()
    val color = Color.White.copy(alpha = 0.22f)
    for (i in 0 until 60) {
        val long = i % 5 == 0
        val inner = outer - (if (long) 5.dp else 2.5.dp).toPx()
        val radians = Math.toRadians(i * 6.0 - 90.0)
        val direction = Offset(cos(radians).toFloat(), sin(radians).toFloat())
        drawLine(
            color = if (long) color.copy(alpha = 0.4f) else color,
            start = center + direction * inner,
            end = center + direction * outer,
            strokeWidth = 1.dp.toPx(),
        )
    }
}

/** The same session flattened into a thin bar, for lists and chips. */
@Composable
fun SessionStrip(
    segments: List<StripSegment>,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
) {
    Canvas(modifier = modifier.height(height)) {
        val gap = if (segments.size <= 40) 1.5.dp.toPx() else 0f
        var x = 0f
        segments.forEach { segment ->
            val width = size.width * segment.fraction
            drawRect(
                color = litColor(segment.type),
                topLeft = Offset(x, 0f),
                size = Size((width - gap).coerceAtLeast(1f), size.height),
            )
            x += width
        }
    }
}
