package com.august.intervaltimer.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.sync.LinkState
import com.august.intervaltimer.sync.STOP_SYNC_CONFIRM_MILLIS
import com.august.intervaltimer.ui.theme.DialType
import com.august.intervaltimer.ui.theme.isDial
import com.august.intervaltimer.ui.theme.labelCase
import com.august.intervaltimer.ui.theme.typeColor
import kotlinx.coroutines.delay

/**
 * The live link's status beside the session name on the timer. Lit when the
 * watch has joined, dim otherwise. NO WATCH, LOST and LINK WATCH are buttons
 * that try to link ([onLink]). While linked, a tap turns it into STOP SYNC?
 * for a few seconds, and a second tap in that time calls [onStopSync].
 */
@Composable
internal fun LinkTag(
    state: LinkState,
    onLink: () -> Unit,
    onStopSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == LinkState.OFF) return

    var confirmingStop by remember { mutableStateOf(false) }
    LaunchedEffect(state, confirmingStop) {
        if (state != LinkState.LINKED) {
            confirmingStop = false
        } else if (confirmingStop) {
            delay(STOP_SYNC_CONFIRM_MILLIS)
            confirmingStop = false
        }
    }

    val label = when {
        confirmingStop -> "Stop sync?"
        state == LinkState.LINKED -> "Watch"
        state == LinkState.CONNECTING -> "Connecting"
        state == LinkState.NO_WATCH -> "No watch"
        state == LinkState.UNLINKED -> "Link watch"
        else -> "Lost"
    }
    val color = when {
        confirmingStop -> typeColor(IntervalType.WORK)
        state == LinkState.LINKED -> typeColor(IntervalType.REST)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }
    val onClick: (() -> Unit)? = when (state) {
        LinkState.LINKED -> {
            { if (confirmingStop) onStopSync() else confirmingStop = true }
        }
        LinkState.NO_WATCH, LinkState.LOST, LinkState.UNLINKED -> onLink
        else -> null
    }
    val pulse by rememberInfiniteTransition(label = "linkPulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "linkPulse",
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            // Read inside the layer, so the pulse never recomposes the screen.
            .graphicsLayer { alpha = if (state == LinkState.CONNECTING) pulse else 1f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Watch,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label.labelCase(),
            style = if (isDial()) DialType.label else MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
        )
    }
}
