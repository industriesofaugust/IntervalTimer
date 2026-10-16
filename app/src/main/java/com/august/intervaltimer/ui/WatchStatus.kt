package com.august.intervaltimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.august.intervaltimer.TimerApp
import com.august.intervaltimer.data.WatchConnection
import com.august.intervaltimer.data.WatchDeviceState
import com.august.intervaltimer.data.WatchLinkMode
import com.august.intervaltimer.data.WatchRequest
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.ui.settings.TroubleshootingSteps
import com.august.intervaltimer.ui.settings.connectionLabel
import com.august.intervaltimer.ui.theme.DialType
import com.august.intervaltimer.ui.theme.SectionHeader
import com.august.intervaltimer.ui.theme.captionStyle
import com.august.intervaltimer.ui.theme.isDial
import com.august.intervaltimer.ui.theme.labelCase
import com.august.intervaltimer.ui.theme.typeColor
import kotlinx.coroutines.flow.map

/**
 * "WATCH READY" or "NO WATCH" with a small dot, for the screens where a
 * workout is started. Hidden when the watch link is off. Tapping NO WATCH
 * opens the steps for fixing it.
 */
@Composable
fun WatchReadyStatus(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as TimerApp
    val mode by remember { app.settingsRepository.settings.map { it.watchLink } }
        .collectAsState(initial = WatchLinkMode.OFF)
    if (mode != WatchLinkMode.OFF) WatchReadyRow(modifier)
}

@Composable
private fun WatchReadyRow(modifier: Modifier) {
    val watch = rememberWatchDevice()
    val ready = watch.connection == WatchConnection.CONNECTED
    var showHelp by remember { mutableStateOf(false) }
    val color = if (ready) typeColor(IntervalType.REST) else MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !ready) { showHelp = true }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = (if (ready) "Watch ready" else "No watch").labelCase(),
            style = captionStyle(),
            color = if (ready) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // Closes by itself if the watch connects while it is open.
    if (showHelp && !ready) WatchHelpSheet(watch.connection, onDismiss = { showHelp = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchHelpSheet(connection: WatchConnection, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            SectionHeader(connectionLabel(connection))
            Spacer(Modifier.height(12.dp))
            TroubleshootingSteps(connection)
        }
    }
}

/**
 * What the phone knows about the watch. Checked now, whenever the screen
 * comes back, and (inside [com.august.intervaltimer.data.WatchDevice])
 * whenever Play services reports a change.
 */
@Composable
fun rememberWatchDevice(): WatchDeviceState {
    val app = LocalContext.current.applicationContext as TimerApp
    val device = app.watchDevice
    LifecycleResumeEffect(device) {
        device.refresh()
        onPauseOrDispose { }
    }
    val state by device.state.collectAsState()
    return state
}

/** Shown on Home while the watch is asking the phone to follow its workout. */
@Composable
fun WatchRequestBanner(pending: WatchRequest, onFollow: () -> Unit, modifier: Modifier = Modifier) {
    val color = typeColor(IntervalType.REST)
    Surface(
        onClick = onFollow,
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.14f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Watch,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Workout on watch · Follow".labelCase(),
                    style = if (isDial()) DialType.label else MaterialTheme.typography.labelLarge,
                    color = color,
                )
                Text(
                    text = pending.request.sessionName,
                    style = captionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
