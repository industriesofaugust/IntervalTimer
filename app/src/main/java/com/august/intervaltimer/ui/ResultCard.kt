package com.august.intervaltimer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.ui.theme.typeColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.august.intervaltimer.data.WorkoutResultEntity
import com.august.intervaltimer.data.intervals
import com.august.intervaltimer.model.formatDuration
import com.august.intervaltimer.ui.components.SessionStrip
import com.august.intervaltimer.ui.theme.captionStyle
import com.august.intervaltimer.ui.theme.figureStyle
import com.august.intervaltimer.ui.theme.isDial
import com.august.intervaltimer.ui.theme.nameStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val resultDateFormat = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a")
private val dialDateTimeFormat = DateTimeFormatter.ofPattern("EEE d MMM · h:mm a")
private val dialDateFormat = DateTimeFormatter.ofPattern("EEE d MMM")

private fun zoned(completedAt: Long) =
    Instant.ofEpochMilli(completedAt).atZone(ZoneId.systemDefault())

fun formatResultDate(completedAt: Long): String = zoned(completedAt).format(resultDateFormat)

/** "SUN 11 OCT · 6:18 PM" */
fun formatDialDateTime(completedAt: Long): String =
    zoned(completedAt).format(dialDateTimeFormat).uppercase()

/** "SUN 11 OCT" */
fun formatDialDate(completedAt: Long): String = zoned(completedAt).format(dialDateFormat).uppercase()

@Composable
fun ResultCard(result: WorkoutResultEntity, onClick: (() -> Unit)? = null) {
    val dial = isDial()
    val intervals = result.intervals()
    val completed = result.completedIntervals

    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row {
                Text(
                    text = result.templateName ?: "Workout",
                    style = nameStyle(),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(text = formatDuration(result.totalSeconds), style = figureStyle())
            }
            Spacer(Modifier.height(2.dp))
            if (dial) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val heartRate = result.avgHeartRate
                    Text(
                        text = formatDialDate(result.completedAt) + if (heartRate != null) " · " else "",
                        style = captionStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (heartRate != null) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Average heart rate",
                            tint = typeColor(IntervalType.WORK),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    val rest = buildList {
                        if (heartRate != null) add(" ${heartRate.roundToInt()}")
                        // Only worth saying when the workout was cut short.
                        if (completed != null && intervals.isNotEmpty() && completed < intervals.size) {
                            add("$completed/${intervals.size}")
                        }
                    }
                    Text(
                        text = (if (heartRate == null && rest.isNotEmpty()) " · " else "") +
                            rest.joinToString(" · "),
                        style = captionStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (intervals.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    // Lit as far as the workout got, grey after that.
                    SessionStrip(
                        intervals = intervals,
                        modifier = Modifier.fillMaxWidth(),
                        completedCount = completed,
                    )
                }
            } else {
                Text(
                    text = formatResultDate(result.completedAt),
                    style = captionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val stats = buildList {
                    if (result.avgHeartRate != null && result.maxHeartRate != null) {
                        add("♥ ${result.avgHeartRate.roundToInt()} avg · ${result.maxHeartRate.roundToInt()} max")
                    }
                    result.steps?.let { add("$it steps") }
                    result.calories?.let { add("${it.roundToInt()} kcal") }
                }
                if (stats.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stats.joinToString(" · "),
                        style = captionStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
