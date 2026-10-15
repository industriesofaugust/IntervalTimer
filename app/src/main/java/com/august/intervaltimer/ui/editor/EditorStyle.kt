package com.august.intervaltimer.ui.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import com.august.intervaltimer.ui.theme.DialType
import com.august.intervaltimer.ui.theme.isDial

// The editor's small labels and buttons: mono capitals in Dial, the Material
// defaults in Minimal.

@Composable
internal fun labelStyle(): TextStyle =
    if (isDial()) DialType.label else MaterialTheme.typography.labelLarge

@Composable
internal fun buttonStyle(): TextStyle =
    if (isDial()) DialType.button else MaterialTheme.typography.labelLarge
