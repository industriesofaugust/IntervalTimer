package com.august.intervaltimer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.august.intervaltimer.TimerApp
import com.august.intervaltimer.data.WatchConnection
import com.august.intervaltimer.data.WatchDeviceState
import com.august.intervaltimer.data.WatchLinkMode
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.sync.WatchInfo
import com.august.intervaltimer.ui.CueRow
import com.august.intervaltimer.ui.LabeledChoice
import com.august.intervaltimer.ui.SettingsCard
import com.august.intervaltimer.ui.SmallTextButton
import com.august.intervaltimer.ui.TestButton
import com.august.intervaltimer.ui.WatchCuesViewModel
import com.august.intervaltimer.ui.rememberWatchDevice
import com.august.intervaltimer.ui.theme.DialType
import com.august.intervaltimer.ui.theme.SectionHeader
import com.august.intervaltimer.ui.theme.captionStyle
import com.august.intervaltimer.ui.theme.isDial
import com.august.intervaltimer.ui.theme.labelCase
import com.august.intervaltimer.ui.theme.nameStyle
import com.august.intervaltimer.ui.theme.typeColor
import kotlinx.coroutines.delay

/**
 * The WATCH group in Settings: whether the watch is connected, which version
 * it runs, how fresh its templates are, how the phone and watch link up for
 * workouts, and the watch's own cue settings.
 */
@Composable
fun DevicesCard(
    watchLink: WatchLinkMode,
    onWatchLinkChange: (WatchLinkMode) -> Unit,
    watchCues: WatchCuesViewModel,
) {
    val watch = rememberWatchDevice()

    SectionHeader("Watch")
    Spacer(Modifier.height(8.dp))
    SettingsCard {
        WatchRow(watch)
        if (watch.connection == WatchConnection.CONNECTED || watch.connection == WatchConnection.NOT_REACHABLE) {
            WatchVersionsAndTemplates(watch)
        }
        if (watch.connection != WatchConnection.CONNECTED) {
            Spacer(Modifier.height(8.dp))
            TroubleshootingSteps(watch.connection)
        }

        Spacer(Modifier.height(16.dp))
        LabeledChoice(
            label = "Link",
            options = listOf("Auto", "Manual", "Off"),
            selectedIndex = watchLink.ordinal,
            onSelect = { index -> onWatchLinkChange(WatchLinkMode.entries[index]) },
            caption = when (watchLink) {
                WatchLinkMode.AUTO -> "The watch joins every workout"
                WatchLinkMode.MANUAL -> "Tap Link watch on the timer to join"
                WatchLinkMode.OFF -> "Workouts never link to the watch"
            },
        )

        Spacer(Modifier.height(16.dp))
        if (!watchCues.loaded) {
            Text(
                text = "Open Interval Timer on your watch once to load its settings here.",
                style = captionStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CueRow(
                title = "Vibration",
                checked = watchCues.cues.vibrationEnabled,
                onCheckedChange = { on -> watchCues.update { it.copy(vibrationEnabled = on) } },
                level = watchCues.cues.vibrationLevel,
                color = typeColor(IntervalType.WORK),
                onLevelChange = { level -> watchCues.update { it.copy(vibrationLevel = level) } },
            )
            Spacer(Modifier.height(8.dp))
            CueRow(
                title = "Sound",
                checked = watchCues.cues.soundEnabled,
                onCheckedChange = { on -> watchCues.update { it.copy(soundEnabled = on) } },
                level = watchCues.cues.soundLevel,
                color = typeColor(IntervalType.REST),
                onLevelChange = { level -> watchCues.update { it.copy(soundLevel = level) } },
            )
        }
        Spacer(Modifier.height(8.dp))
        TestButton("Test on watch", onClick = watchCues::testOnWatch)
    }
}

/** A status dot, the watch's name and how well the phone can reach it. */
@Composable
private fun WatchRow(watch: WatchDeviceState) {
    val color = when (watch.connection) {
        WatchConnection.CONNECTED -> typeColor(IntervalType.REST)
        WatchConnection.NOT_REACHABLE, WatchConnection.APP_MISSING -> typeColor(IntervalType.WORK)
        WatchConnection.NO_WATCH -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = watch.name ?: "Watch",
                style = nameStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (watch.connection == WatchConnection.CONNECTED && !watch.isNearby) {
                Text(
                    text = "Connected through the cloud, not Bluetooth",
                    style = captionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = connectionLabel(watch.connection).labelCase(),
            style = if (isDial()) DialType.label else MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.14f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** App versions, a warning if they differ, and the watch's template count. */
@Composable
private fun WatchVersionsAndTemplates(watch: WatchDeviceState) {
    val info = watch.info
    if (info == null) {
        if (watch.connection == WatchConnection.CONNECTED) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Open Interval Timer on the watch to see its version and templates.",
                style = captionStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Spacer(Modifier.height(8.dp))
    Text(
        text = "Phone ${watch.phoneVersionName} · Watch ${info.appVersionName}".labelCase(),
        style = captionStyle(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (watch.versionMismatch) {
        Text(
            text = "Install the same version on both: run the install script",
            style = captionStyle(),
            color = typeColor(IntervalType.WORK),
        )
    }
    TemplatesRow(info, canSync = watch.connection == WatchConnection.CONNECTED)
}

@Composable
private fun TemplatesRow(info: WatchInfo, canSync: Boolean) {
    val device = (LocalContext.current.applicationContext as TimerApp).watchDevice
    val syncing by device.syncing.collectAsState()
    val now = rememberNow()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = templatesLine(info, now).labelCase(),
            style = captionStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        SmallTextButton(
            label = if (syncing) "Sent…" else "Sync now",
            onClick = device::syncNow,
            enabled = canSync && !syncing,
        )
    }
}

/** "5 templates · synced 2 min ago" */
private fun templatesLine(info: WatchInfo, now: Long): String {
    val count = if (info.templateCount == 1) "1 template" else "${info.templateCount} templates"
    val receivedAt = info.templatesReceivedAtEpochMillis ?: return count
    return "$count · synced ${timeAgo(now, receivedAt)}"
}

/** "just now", "2 min ago", "3 h ago", "2 d ago". */
internal fun timeAgo(now: Long, then: Long): String {
    val minutes = (now - then) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        else -> "${minutes / (24 * 60)} d ago"
    }
}

/** The current time, refreshed every 30 seconds so "2 min ago" keeps counting. */
@Composable
private fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** Short name for each connection, shown in the chip and as the sheet title. */
fun connectionLabel(connection: WatchConnection): String = when (connection) {
    WatchConnection.CONNECTED -> "Connected"
    WatchConnection.NOT_REACHABLE -> "Not reachable"
    WatchConnection.APP_MISSING -> "Watch app missing"
    WatchConnection.NO_WATCH -> "No watch"
}

/** Numbered steps for getting the watch connected, specific to what is wrong. */
@Composable
fun TroubleshootingSteps(connection: WatchConnection) {
    val steps = when (connection) {
        WatchConnection.NO_WATCH -> listOf(
            "Pair the watch in the Pixel Watch or Galaxy Wearable app.",
            "Keep Bluetooth on, on the phone and the watch.",
        )
        WatchConnection.APP_MISSING -> listOf(
            "Install Interval Timer on the watch: connect it and run scripts\\install.ps1.",
            "Open Interval Timer on the watch once.",
        )
        WatchConnection.NOT_REACHABLE -> listOf(
            "Open Interval Timer on the watch.",
            "Check that Bluetooth is on.",
            "Keep the watch near the phone.",
        )
        WatchConnection.CONNECTED -> emptyList()
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        steps.forEachIndexed { index, step ->
            Text(
                text = "${index + 1}. $step",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
