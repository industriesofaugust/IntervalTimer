package com.august.intervaltimer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.august.intervaltimer.sync.LinkState
import com.august.intervaltimer.sync.STOP_SYNC_CONFIRM_MILLIS
import com.august.intervaltimer.ui.RestAccent
import com.august.intervaltimer.ui.WorkAccent
import com.august.intervaltimer.workout.WorkoutSession
import kotlinx.coroutines.delay

/**
 * True for a few seconds after the first tap on a linked phone indicator,
 * while it reads STOP SYNC?. A second tap in that time stops the sync.
 */
@Composable
private fun rememberStopSyncPrompt(linked: Boolean): MutableState<Boolean> {
    val prompting = remember { mutableStateOf(false) }
    LaunchedEffect(linked, prompting.value) {
        if (!linked) {
            prompting.value = false
        } else if (prompting.value) {
            delay(STOP_SYNC_CONFIRM_MILLIS)
            prompting.value = false
        }
    }
    return prompting
}

/** The tap on a linked indicator: first arms STOP SYNC?, second confirms. */
private fun MutableState<Boolean>.tapLinked() {
    if (value) {
        value = false
        WorkoutSession.stopSync()
    } else {
        value = true
    }
}

/**
 * A tiny phone: lit when the phone is running this workout too, dim otherwise.
 * With [tappable] it is also the SYNC PHONE and STOP SYNC? button (the Minimal
 * face has no tap-for-controls screen to hold the chip).
 */
@Composable
fun PhoneGlyph(state: LinkState, modifier: Modifier = Modifier, tappable: Boolean = false) {
    val linked = state == LinkState.LINKED
    val stopPrompt = rememberStopSyncPrompt(linked)
    val tapTarget = if (tappable) {
        modifier.clickable {
            when {
                linked -> stopPrompt.tapLinked()
                state == LinkState.OFF -> WorkoutSession.requestPhoneSync()
            }
        }
    } else {
        modifier
    }
    Row(tapTarget.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.PhoneAndroid,
            contentDescription = if (linked) "Phone linked" else "Phone not linked",
            tint = if (stopPrompt.value) WorkAccent
            else if (linked) RestAccent
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(12.dp),
        )
        if (stopPrompt.value) {
            Text(
                text = " STOP SYNC?",
                style = MaterialTheme.typography.labelSmall,
                color = WorkAccent,
                maxLines = 1,
            )
        }
    }
}

/**
 * `PHONE ●`, `SYNC PHONE` or `SENT…`, above the controls. SYNC PHONE asks the
 * phone to follow; tapping `PHONE ●` twice stops syncing. [onTap] lets the
 * screen know the chip is in use, so it keeps the controls up.
 */
@Composable
fun PhoneLinkChip(state: LinkState, modifier: Modifier = Modifier, onTap: () -> Unit = {}) {
    val linked = state == LinkState.LINKED
    val sending = state == LinkState.CONNECTING
    val stopPrompt = rememberStopSyncPrompt(linked)
    CompactButton(
        onClick = {
            onTap()
            when {
                linked -> stopPrompt.tapLinked()
                !sending -> WorkoutSession.requestPhoneSync()
            }
        },
        enabled = !sending,
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(),
    ) {
        Text(
            text = when {
                stopPrompt.value -> buildAnnotatedString {
                    withStyle(SpanStyle(color = WorkAccent)) { append("STOP SYNC?") }
                }
                linked -> buildAnnotatedString {
                    append("PHONE ")
                    withStyle(SpanStyle(color = RestAccent)) { append("●") }
                }
                sending -> buildAnnotatedString { append("SENT…") }
                else -> buildAnnotatedString { append("SYNC PHONE") }
            },
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}
