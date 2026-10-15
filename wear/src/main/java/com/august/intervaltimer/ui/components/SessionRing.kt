package com.august.intervaltimer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
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
 * It is drawn in two separate layers. The static layer (finished and coming
 * arcs, tick marks) is drawn once per interval and kept; the live layer holds
 * only the current arc, and is the only thing redrawn as time passes.
 * [currentRemaining] (1 = just started, 0 = done) is read only inside that
 * live layer's drawing, so a tick redraws one arc and recomposes nothing.
 *
 * With [outline] set (the dimmed ambient screen) it draws thin arcs only, to
 * keep lit pixels to a minimum.
 */
@Composable
fun SessionRing(
    segments: List<RingSegment>,
    currentRemaining: () -> Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 7.dp,
    showTicks: Boolean = true,
    outline: Boolean = false,
) {
    val current = segments.firstOrNull { it.state == SegmentState.CURRENT }

    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawWithCache {
                    val layout = ArcLayout(this, size, strokeWidth, outline)
                    val ticks = if (showTicks && !outline) {
                        TickMarks(size, ringWidth = strokeWidth.toPx(), density = this)
                    } else {
                        null
                    }
                    onDrawBehind {
                        segments.forEach { segment ->
                            when (segment.state) {
                                SegmentState.DONE ->
                                    arc(layout, litFor(segment.type, outline), segment.startAngle, segment.sweep)
                                SegmentState.UPCOMING ->
                                    arc(layout, greyFor(segment.type, outline), segment.startAngle, segment.sweep)
                                SegmentState.CURRENT -> Unit // drawn by the live layer
                            }
                        }
                        ticks?.draw(this)
                    }
                }
        )
        if (current != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer()
                    .drawWithCache {
                        val layout = ArcLayout(this, size, strokeWidth, outline)
                        val lit = litFor(current.type, outline)
                        val grey = greyFor(current.type, outline)
                        onDrawBehind {
                            val filled = current.sweep * (1f - currentRemaining())
                            arc(layout, lit, current.startAngle, filled)
                            arc(layout, grey, current.startAngle + filled, current.sweep - filled)
                        }
                    }
            )
        }
    }
}

private fun litFor(type: IntervalType, outline: Boolean): Color =
    if (outline) Color.LightGray else litColor(type)

private fun greyFor(type: IntervalType, outline: Boolean): Color =
    if (outline) Color.DarkGray else greyColor(type)

/** Where the arcs sit and how thick they are, worked out once per size. */
private class ArcLayout(density: Density, size: Size, strokeWidth: Dp, outline: Boolean) {
    private val inset = with(density) { strokeWidth.toPx() } / 2f
    val topLeft = Offset(inset, inset)
    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
    val stroke = Stroke(width = with(density) { (if (outline) 2.dp else strokeWidth).toPx() })
}

private fun DrawScope.arc(layout: ArcLayout, color: Color, start: Float, sweep: Float) {
    if (sweep <= 0f) return
    // Canvas angles start at 3 o'clock; ours start at 12.
    drawArc(
        color = color,
        startAngle = start - 90f,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = layout.topLeft,
        size = layout.arcSize,
        style = layout.stroke,
    )
}

/**
 * Sixty marks just inside the ring, every fifth one longer, like a
 * stopwatch. The points are worked out once and drawn as two batches.
 */
private class TickMarks(size: Size, ringWidth: Float, private val density: Density) {
    private val shortTicks = ArrayList<Offset>(96)
    private val longTicks = ArrayList<Offset>(24)

    init {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outer = size.minDimension / 2f - ringWidth - with(density) { 3.dp.toPx() }
        for (i in 0 until 60) {
            val long = i % 5 == 0
            val inner = outer - with(density) { (if (long) 5.dp else 2.5.dp).toPx() }
            val radians = Math.toRadians(i * 6.0 - 90.0)
            val direction = Offset(cos(radians).toFloat(), sin(radians).toFloat())
            val points = if (long) longTicks else shortTicks
            points.add(center + direction * inner)
            points.add(center + direction * outer)
        }
    }

    fun draw(scope: DrawScope) {
        val color = Color.White.copy(alpha = 0.22f)
        val width = with(density) { 1.dp.toPx() }
        scope.drawPoints(shortTicks, PointMode.Lines, color, width)
        scope.drawPoints(longTicks, PointMode.Lines, color.copy(alpha = 0.4f), width)
    }
}

/** The same session flattened into a thin bar, for lists and cards. */
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
