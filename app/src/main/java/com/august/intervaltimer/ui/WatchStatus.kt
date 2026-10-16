package com.august.intervaltimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.august.intervaltimer.TimerApp
import com.august.intervaltimer.data.WatchRequest
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.sync.LivePaths
import com.august.intervaltimer.ui.theme.DialType
import com.august.intervaltimer.ui.theme.captionStyle
import com.august.intervaltimer.ui.theme.isDial
import com.august.intervaltimer.ui.theme.labelCase
import com.august.intervaltimer.ui.theme.typeColor
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * "WATCH READY" or "NO WATCH" with a small dot, for the screens where a
 * workout is started. Hidden when "Use watch" is off.
 */
@Composable
fun WatchReadyStatus(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as TimerApp
    val useWatch by remember { app.settingsRepository.settings.map { it.useWatch } }
        .collectAsState(initial = false)
    if (useWatch) WatchReadyRow(modifier)
}

@Composable
private fun WatchReadyRow(modifier: Modifier) {
    val ready = rememberWatchReady()
    val color = if (ready) typeColor(IntervalType.REST) else MaterialTheme.colorScheme.outline
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
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
}

/**
 * True while a connected watch has our app. The watch declares a capability
 * for this; it is checked now, whenever the screen comes back, and whenever
 * the capability changes.
 */
@Composable
private fun rememberWatchReady(): Boolean {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            ready = runCatching {
                Wearable.getCapabilityClient(context)
                    .getCapability(LivePaths.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                    .await()
                    .nodes
                    .isNotEmpty()
            }.getOrDefault(false)
        }
    }

    // The change event only says who has the capability, not who can be
    // reached right now, so it just triggers the same check again.
    DisposableEffect(context) {
        val client = Wearable.getCapabilityClient(context)
        val listener = CapabilityClient.OnCapabilityChangedListener { refresh() }
        runCatching { client.addListener(listener, LivePaths.WATCH_CAPABILITY) }
        onDispose { runCatching { client.removeListener(listener) } }
    }
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }
    return ready
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
