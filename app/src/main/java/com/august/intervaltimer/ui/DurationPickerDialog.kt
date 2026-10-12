package com.august.intervaltimer.ui

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Two spinning wheels — minutes and seconds, 00 to 59 — for setting an
 * exact duration. The +/- steppers stay for quick nudges; this is for
 * "I want exactly 0:07".
 */
@Composable
fun DurationPickerDialog(
    initialSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var minutes by remember { mutableIntStateOf((initialSeconds / 60).coerceIn(0, 59)) }
    var seconds by remember { mutableIntStateOf(initialSeconds % 60) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Set duration",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberWheel(value = minutes, onValueChange = { minutes = it })
                    Text(
                        text = ":",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    NumberWheel(value = seconds, onValueChange = { seconds = it })
                }
                Row {
                    Text(
                        text = "min",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(64.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.width(24.dp))
                    Text(
                        text = "sec",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(64.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm((minutes * 60 + seconds).coerceAtLeast(1)) },
                    ) {
                        Text("Set")
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberWheel(
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    val itemHeight = 40.dp
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = value.coerceIn(0, 59))
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // The item resting in the middle row is the selected one.
    val centeredIndex by remember {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset
            listState.firstVisibleItemIndex + if (offset > 60) 1 else 0
        }
    }
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            onValueChange(centeredIndex.coerceIn(0, 59))
        }
    }

    Box(
        modifier = Modifier
            .size(width = 64.dp, height = itemHeight * 3),
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(60) { number ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    val selected = number == centeredIndex
                    Text(
                        text = "%02d".format(number),
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = itemHeight),
            color = MaterialTheme.colorScheme.outline,
        )
        HorizontalDivider(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = itemHeight * 2),
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
