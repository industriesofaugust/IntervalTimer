package com.august.intervaltimer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.august.intervaltimer.health.HeartRateZones
import com.august.intervaltimer.model.Interval
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.sync.HrSample
import com.august.intervaltimer.timer.IntervalSpan
import com.august.intervaltimer.timer.TimerUiState
import com.august.intervaltimer.ui.theme.MonoFamily
import com.august.intervaltimer.ui.theme.isAppInDarkTheme
import com.august.intervaltimer.ui.theme.isDial
import com.august.intervaltimer.ui.theme.typeColor
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Heart rate over the workout, drawn by hand with Canvas.
 *
 * Behind the line, a band for each interval (dark coral for work, dark teal
 * for rest, each with a bright cap) shows heart rate climbing during work
 * and recovering during rest. A dotted line marks the average and a tagged
 * point marks the maximum.
 *
 * Bands come from when each interval really ran (see [timedBands]); for old
 * workouts that did not record it, they are laid out from the planned
 * durations (see [plannedBands]) and drift if the workout was paused or skipped.
 *
 * Faint lines mark where each heart-rate zone starts, labelled at the right
 * edge, when [zones] is known.
 */
@Composable
fun HrChart(
    series: List<HrSample>,
    bands: List<HrBand>,
    endSeconds: Int,
    modifier: Modifier = Modifier,
    zones: HeartRateZones? = null,
) {
    val dark = isAppInDarkTheme()
    val dial = isDial()
    val lineColor = MaterialTheme.colorScheme.onBackground
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val workBand = if (dark) Color(0xFF3D261E) else Color(0xFFF5DDD3)
    val restBand = if (dark) Color(0xFF1D3531) else Color(0xFFD3E9E4)
    val workCap = typeColor(IntervalType.WORK)
    val restCap = typeColor(IntervalType.REST)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontSize = 11.sp,
        color = labelColor,
        fontFamily = if (dial) MonoFamily else null,
    )

    Canvas(modifier = modifier) {
        if (series.size < 2) return@Canvas

        // The chart covers what was actually done, so a workout that ended
        // early is not squeezed into the corner of its planned length.
        val maxElapsed = maxOf(series.maxOf { it.elapsedSeconds }, endSeconds).coerceAtLeast(1)

        val minBpm = floor(series.minOf { it.bpm } / 10.0) * 10.0 - 5.0
        val maxBpm = ceil(series.maxOf { it.bpm } / 10.0) * 10.0 + 10.0
        val bpmRange = (maxBpm - minBpm).coerceAtLeast(1.0)

        val chartHeight = size.height - 20.dp.toPx()
        fun x(seconds: Float) = size.width * (seconds / maxElapsed)
        fun y(bpm: Double) = chartHeight * (1f - ((bpm - minBpm) / bpmRange).toFloat())

        bands.forEach { band ->
            if (band.startSeconds >= maxElapsed) return@forEach
            val left = x(band.startSeconds)
            val width = x(minOf(band.endSeconds, maxElapsed.toFloat())) - left
            val work = band.type == IntervalType.WORK
            drawRect(
                color = if (work) workBand else restBand,
                topLeft = Offset(left, 0f),
                size = Size(width, chartHeight),
            )
            drawRect(
                color = if (work) workCap else restCap,
                topLeft = Offset(left, 0f),
                size = Size(width, 3.dp.toPx()),
            )
        }

        // Where each heart-rate zone starts: a faint line, named at the right edge.
        zones?.lowerBounds?.forEachIndexed { index, bound ->
            if (bound < minBpm || bound > maxBpm) return@forEachIndexed
            val lineY = y(bound.toDouble())
            drawLine(
                color = lineColor.copy(alpha = 0.18f),
                start = Offset(0f, lineY),
                end = Offset(size.width, lineY),
                strokeWidth = 1.dp.toPx(),
            )
            val zoneLabel = textMeasurer.measure(
                "Z${index + 1}",
                labelStyle.copy(fontSize = 9.sp, color = labelColor.copy(alpha = 0.8f)),
            )
            drawText(
                zoneLabel,
                topLeft = Offset(size.width - zoneLabel.size.width - 2.dp.toPx(), lineY - zoneLabel.size.height),
            )
        }

        // Average: a dotted line with its value.
        val average = series.map { it.bpm }.average()
        val averageY = y(average)
        drawLine(
            color = lineColor.copy(alpha = 0.65f),
            start = Offset(0f, averageY),
            end = Offset(size.width, averageY),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
        )

        // The heart-rate line itself.
        val path = Path()
        series.forEachIndexed { index, sample ->
            val point = Offset(x(sample.elapsedSeconds.toFloat()), y(sample.bpm))
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // The average's label goes on after the line, so the line never crosses it.
        val averageLabel = textMeasurer.measure("AVG ${average.roundToInt()}", labelStyle.copy(color = lineColor))
        val averageLabelTop = (averageY + 4.dp.toPx())
            .coerceAtMost(chartHeight - averageLabel.size.height - 4.dp.toPx())
        drawRoundRect(
            color = surface,
            topLeft = Offset(2.dp.toPx(), averageLabelTop),
            size = Size(averageLabel.size.width + 8.dp.toPx(), averageLabel.size.height + 2.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )
        drawText(averageLabel, topLeft = Offset(6.dp.toPx(), averageLabelTop + 1.dp.toPx()))

        // Maximum: a ringed point with a tag.
        val peak = series.maxBy { it.bpm }
        val peakPoint = Offset(x(peak.elapsedSeconds.toFloat()), y(peak.bpm))
        drawCircle(color = surface, radius = 5.dp.toPx(), center = peakPoint)
        drawCircle(color = lineColor, radius = 5.dp.toPx(), center = peakPoint, style = Stroke(2.dp.toPx()))
        val peakLabel = textMeasurer.measure(
            "${peak.bpm.roundToInt()}",
            labelStyle.copy(color = surface, fontWeight = FontWeight.Bold),
        )
        val tagWidth = peakLabel.size.width + 10.dp.toPx()
        val tagHeight = peakLabel.size.height + 2.dp.toPx()
        // The tag sits right above the point, kept inside the chart.
        val tagLeft = (peakPoint.x - tagWidth / 2f).coerceIn(0f, size.width - tagWidth)
        val tagTop = (peakPoint.y - tagHeight - 9.dp.toPx()).coerceAtLeast(4.dp.toPx())
        drawRoundRect(
            color = lineColor,
            topLeft = Offset(tagLeft, tagTop),
            size = Size(tagWidth, tagHeight),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )
        drawText(peakLabel, topLeft = Offset(tagLeft + 5.dp.toPx(), tagTop + 1.dp.toPx()))

        // Time axis.
        drawText(
            textMeasurer = textMeasurer,
            text = "0:00",
            style = labelStyle,
            topLeft = Offset(0f, chartHeight + 5.dp.toPx()),
        )
        val endLabel = textMeasurer.measure(formatDuration(maxElapsed), labelStyle)
        drawText(endLabel, topLeft = Offset(size.width - endLabel.size.width, chartHeight + 5.dp.toPx()))
    }
}

/** One stretch of the chart's background, in seconds from the moment recording began. */
data class HrBand(val type: IntervalType, val startSeconds: Float, val endSeconds: Float)

private val LEAD_IN_SECONDS = (TimerUiState.LEAD_IN_MILLIS / 1000).toInt()

/** Bands laid end to end from the planned durations, after the get-ready. */
fun plannedBands(intervals: List<Interval>): List<HrBand> {
    var start = LEAD_IN_SECONDS
    return intervals.map { interval ->
        HrBand(interval.type, start.toFloat(), (start + interval.durationSeconds).toFloat())
            .also { start += interval.durationSeconds }
    }
}

/**
 * Bands from when each interval really ran. Recording began at the get-ready,
 * which is [LEAD_IN_SECONDS] before the first interval.
 */
fun timedBands(spans: List<IntervalSpan>, intervals: List<Interval>): List<HrBand> {
    val recordingStart = (spans.minOfOrNull { it.startMillis } ?: return emptyList()) -
        TimerUiState.LEAD_IN_MILLIS
    return spans.mapNotNull { span ->
        val type = intervals.getOrNull(span.index)?.type ?: return@mapNotNull null
        HrBand(
            type,
            (span.startMillis - recordingStart) / 1000f,
            (span.endMillis - recordingStart) / 1000f,
        )
    }
}
