package com.august.intervaltimer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.sync.HrSample
import kotlin.math.ceil
import kotlin.math.floor

/**
 * A simple heart-rate line chart drawn by hand with Canvas. The vertical
 * range is padded to the nearest 10 bpm so the line never touches the edges.
 */
@Composable
fun HrChart(series: List<HrSample>, lineColor: Color, modifier: Modifier = Modifier) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = labelColor)

    Canvas(modifier = modifier) {
        if (series.size < 2) return@Canvas

        val minBpm = floor(series.minOf { it.bpm } / 10.0) * 10.0 - 5.0
        val maxBpm = ceil(series.maxOf { it.bpm } / 10.0) * 10.0 + 5.0
        val bpmRange = (maxBpm - minBpm).coerceAtLeast(1.0)
        val maxElapsed = series.maxOf { it.elapsedSeconds }.coerceAtLeast(1)

        val labelSpace = 34.dp.toPx()
        val chartWidth = size.width - labelSpace
        val chartHeight = size.height - 18.dp.toPx()

        fun x(sample: HrSample) =
            labelSpace + chartWidth * (sample.elapsedSeconds.toFloat() / maxElapsed)

        fun y(bpm: Double) =
            chartHeight * (1f - ((bpm - minBpm) / bpmRange).toFloat())

        // Three horizontal grid lines with bpm labels: top, middle, bottom.
        val gridBpms = listOf(maxBpm - 5.0, (maxBpm + minBpm) / 2.0, minBpm + 5.0)
        gridBpms.forEach { bpm ->
            val lineY = y(bpm)
            drawLine(
                color = gridColor,
                start = Offset(labelSpace, lineY),
                end = Offset(size.width, lineY),
                strokeWidth = 1f,
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "${bpm.toInt()}",
                style = labelStyle,
                topLeft = Offset(0f, lineY - 7.dp.toPx()),
            )
        }

        // Time labels along the bottom.
        drawText(
            textMeasurer = textMeasurer,
            text = "0:00",
            style = labelStyle,
            topLeft = Offset(labelSpace, chartHeight + 4.dp.toPx()),
        )
        val endLabel = formatDuration(maxElapsed)
        val endLabelWidth = textMeasurer.measure(endLabel, labelStyle).size.width
        drawText(
            textMeasurer = textMeasurer,
            text = endLabel,
            style = labelStyle,
            topLeft = Offset(size.width - endLabelWidth, chartHeight + 4.dp.toPx()),
        )

        // The heart-rate line itself.
        val path = Path()
        series.forEachIndexed { index, sample ->
            val point = Offset(x(sample), y(sample.bpm))
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
