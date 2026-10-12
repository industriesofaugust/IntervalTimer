package com.brian.intervaltimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.brian.intervaltimer.model.Interval
import com.brian.intervaltimer.model.QuickBuildConfig
import com.brian.intervaltimer.model.buildSession
import com.brian.intervaltimer.model.formatDuration
import com.brian.intervaltimer.ui.theme.Coral
import com.brian.intervaltimer.ui.theme.CoralLight
import com.brian.intervaltimer.ui.theme.IntervalTimerTheme
import com.brian.intervaltimer.ui.theme.Teal
import com.brian.intervaltimer.ui.theme.TealLight

@Composable
fun QuickBuildScreen(
    onStart: (List<Interval>) -> Unit,
    onCustomize: (rounds: Int, workSeconds: Int, restSeconds: Int) -> Unit = { _, _, _ -> },
) {
    var rounds by rememberSaveable { mutableIntStateOf(8) }
    var workSeconds by rememberSaveable { mutableIntStateOf(40) }
    var restSeconds by rememberSaveable { mutableIntStateOf(20) }
    // Which duration the wheel picker is editing: "work", "rest", or null.
    var pickerTarget by rememberSaveable { mutableStateOf<String?>(null) }

    val totalSeconds = rounds * workSeconds + (rounds - 1).coerceAtLeast(0) * restSeconds
    val dark = isSystemInDarkTheme()
    val workAccent = if (dark) CoralLight else Coral
    val restAccent = if (dark) TealLight else Teal

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Text(
            text = "Interval timer",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Set your rounds, then start.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        SettingStepper(
            label = "Rounds",
            value = "$rounds",
            dotColor = null,
            onDecrease = { rounds = (rounds - 1).coerceAtLeast(1) },
            onIncrease = { rounds = (rounds + 1).coerceAtMost(50) },
        )
        Spacer(Modifier.height(12.dp))
        SettingStepper(
            label = "Work",
            value = formatDuration(workSeconds),
            dotColor = workAccent,
            onDecrease = { workSeconds = (workSeconds - 5).coerceAtLeast(5) },
            onIncrease = { workSeconds = (workSeconds + 5).coerceAtMost(600) },
            onValueClick = { pickerTarget = "work" },
        )
        Spacer(Modifier.height(12.dp))
        SettingStepper(
            label = "Rest",
            value = formatDuration(restSeconds),
            dotColor = restAccent,
            onDecrease = { restSeconds = (restSeconds - 5).coerceAtLeast(5) },
            onIncrease = { restSeconds = (restSeconds + 5).coerceAtMost(600) },
            onValueClick = { pickerTarget = "rest" },
        )

        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Session length",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatDuration(totalSeconds),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                onStart(buildSession(QuickBuildConfig(rounds, workSeconds, restSeconds)))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Start session", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { onCustomize(rounds, workSeconds, restSeconds) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Customize intervals", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(24.dp))
    }

    pickerTarget?.let { target ->
        DurationPickerDialog(
            initialSeconds = if (target == "work") workSeconds else restSeconds,
            onDismiss = { pickerTarget = null },
            onConfirm = { seconds ->
                if (target == "work") workSeconds = seconds else restSeconds = seconds
                pickerTarget = null
            },
        )
    }
}

@Composable
private fun SettingStepper(
    label: String,
    value: String,
    dotColor: Color?,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onValueClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (dotColor != null) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = if (onValueClick != null) {
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onValueClick)
                            .padding(horizontal = 4.dp)
                    } else {
                        Modifier
                    },
                )
            }
            StepButton(text = "−", onClick = onDecrease)
            Spacer(Modifier.width(10.dp))
            StepButton(text = "+", onClick = onIncrease)
        }
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier.size(48.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Preview(showBackground = true)
@Composable
private fun QuickBuildPreview() {
    IntervalTimerTheme {
        QuickBuildScreen(onStart = {})
    }
}
