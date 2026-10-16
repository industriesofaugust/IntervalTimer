package com.august.intervaltimer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.Composable
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
import com.august.intervaltimer.ui.RestAccent
import com.august.intervaltimer.workout.WorkoutSession

/**
 * A tiny phone: lit when the phone is running this workout too, dim otherwise.
 * With [onClick] it also asks the phone to follow (the Minimal face has no
 * tap-for-controls screen to hold the SYNC PHONE chip).
 */
@Composable
fun PhoneGlyph(state: LinkState, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val tapTarget = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Box(tapTarget.padding(3.dp)) {
        Icon(
            imageVector = Icons.Filled.PhoneAndroid,
            contentDescription = if (state == LinkState.LINKED) "Phone linked" else "Phone not linked",
            tint = if (state == LinkState.LINKED) RestAccent
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(12.dp),
        )
    }
}

/** `PHONE ●`, `SYNC PHONE` or `SENT…`, above the controls. Only SYNC PHONE does anything. */
@Composable
fun PhoneLinkChip(state: LinkState, modifier: Modifier = Modifier) {
    val linked = state == LinkState.LINKED
    val sending = state == LinkState.CONNECTING
    CompactButton(
        onClick = { if (!linked && !sending) WorkoutSession.requestPhoneSync() },
        enabled = !sending,
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(),
    ) {
        Text(
            text = when {
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
