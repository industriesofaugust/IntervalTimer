package com.august.intervaltimer.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.august.intervaltimer.ui.theme.figureStyle
import com.august.intervaltimer.ui.theme.labelCase

/**
 * One of the three tiles that shape the whole workout: a label, a value, and
 * − / + underneath. With [onValueClick] the value can be tapped to type it in.
 */
@Composable
fun PatternTile(
    label: String,
    value: String,
    valueColor: Color,
    decreaseDescription: String,
    increaseDescription: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    onValueClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label.labelCase(),
                style = labelStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = figureStyle(),
                color = valueColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (onValueClick != null) Modifier.clickable(onClick = onValueClick) else Modifier)
                    .padding(vertical = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StepButton(
                    symbol = "−",
                    description = decreaseDescription,
                    enabled = canDecrease,
                    onStep = onDecrease,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                )
                StepButton(
                    symbol = "+",
                    description = increaseDescription,
                    enabled = canIncrease,
                    onStep = onIncrease,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                )
            }
        }
    }
}
