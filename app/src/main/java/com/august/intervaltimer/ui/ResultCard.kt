package com.august.intervaltimer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.august.intervaltimer.data.WorkoutResultEntity
import com.august.intervaltimer.model.formatDuration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val resultDateFormat = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a")

fun formatResultDate(completedAt: Long): String =
    Instant.ofEpochMilli(completedAt).atZone(ZoneId.systemDefault()).format(resultDateFormat)

@Composable
fun ResultCard(result: WorkoutResultEntity, onClick: (() -> Unit)? = null) {
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
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatDuration(result.totalSeconds),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatResultDate(result.completedAt),
                style = MaterialTheme.typography.bodyMedium,
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
