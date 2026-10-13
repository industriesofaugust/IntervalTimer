package com.august.intervaltimer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
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
import com.august.intervaltimer.design.stripSegments
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.ui.theme.typeColor
import com.august.intervaltimer.ui.theme.upcomingColor
import kotlin.math.cos
import kotlin.math.sin

/**
 * The whole workout as a dial: one arc per interval. The ring lights up as
 * you go: finished intervals are coral (work) or teal (rest), the current
 * one fills clockwise, and what is still to come stays grey.
 */
@Composable
fun SessionRing(
    segments: List<RingSegment>,
    currentRemaining: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 14.dp,
    showTicks: Boolean = true,
) {
    val workLit = typeColor(IntervalType.WORK)
    val restLit = typeColor(IntervalType.REST)
    val workGrey = upcomingColor(IntervalType.WORK)
    val restGrey = upcomingColor(IntervalType.REST)
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
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
            val lit = if (segment.type == IntervalType.WORK) workLit else restLit
            val grey = if (segment.type == IntervalType.WORK) workGrey else restGrey
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

        if (showTicks) drawTicks(ringWidth = stroke, color = tickColor)
    }
}

/** Sixty marks just inside the ring, every fifth one longer, like a stopwatch. */
private fun DrawScope.drawTicks(ringWidth: Float, color: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val outer = size.minDimension / 2f - ringWidth - 6.dp.toPx()
    for (i in 0 until 60) {
        val long = i % 5 == 0
        val inner = outer - (if (long) 8.dp else 4.dp).toPx()
        val radians = Math.toRadians(i * 6.0 - 90.0)
        val direction = Offset(cos(radians).toFloat(), sin(radians).toFloat())
        drawLine(
            color = color.copy(alpha = if (long) 0.55f else 0.3f),
            start = center + direction * inner,
            end = center + direction * outer,
            strokeWidth = 1.dp.toPx(),
        )
    }
}

/**
 * The same session flattened into a thin bar, for cards and lists. With
 * [completedCount] set, intervals after that many are drawn grey, to show how
 * far a workout got.
 */
@Composable
fun SessionStrip(
    intervals: List<Interval>,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    completedCount: Int? = null,
) {
    val workLit = typeColor(IntervalType.WORK)
    val restLit = typeColor(IntervalType.REST)
    val workGrey = upcomingColor(IntervalType.WORK)
    val restGrey = upcomingColor(IntervalType.REST)
    val segments = stripSegments(intervals)

    Canvas(modifier = modifier.height(height)) {
        val gap = if (segments.size <= 40) 2.dp.toPx() else 0f
        var x = 0f
        segments.forEachIndexed { index, segment ->
            val width = size.width * segment.fraction
            val done = completedCount == null || index < completedCount
            val work = segment.type == IntervalType.WORK
            drawRect(
                color = when {
                    done && work -> workLit
                    done -> restLit
                    work -> workGrey
                    else -> restGrey
                },
                topLeft = Offset(x, 0f),
                size = Size((width - gap).coerceAtLeast(1f), size.height),
            )
            x += width
        }
    }
}
