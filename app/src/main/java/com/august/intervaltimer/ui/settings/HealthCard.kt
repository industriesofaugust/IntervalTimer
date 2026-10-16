package com.august.intervaltimer.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.august.intervaltimer.health.maxHeartRateFor
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.ui.SettingsCard
import com.august.intervaltimer.ui.SmallTextButton
import com.august.intervaltimer.ui.theme.DialType
import com.august.intervaltimer.ui.theme.SectionHeader
import com.august.intervaltimer.ui.theme.captionStyle
import com.august.intervaltimer.ui.theme.figureStyle
import com.august.intervaltimer.ui.theme.isDial
import com.august.intervaltimer.ui.theme.labelCase
import com.august.intervaltimer.ui.theme.typeColor


private enum class Confirm { STOP_SHARING, CLEAN_UP }

/**
 * The HEALTH group: Health Connect sharing (only where Health Connect
 * exists) and the age that feeds the heart-rate zones.
 */
@Composable
fun HealthCard(viewModel: HealthViewModel = viewModel()) {
    Spacer(Modifier.height(24.dp))
    SectionHeader("Health")
    Spacer(Modifier.height(8.dp))
    SettingsCard {
        if (viewModel.available) {
            HealthConnectContent(viewModel)
            Spacer(Modifier.height(16.dp))
        }
        AgeContent(viewModel)
    }
}

@Composable
private fun HealthConnectContent(viewModel: HealthViewModel) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsState()
    val progress = viewModel.progress
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { viewModel.onPermissionsAnswered() }

    // The person may change what is allowed in Health Connect itself, then come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val labelStyle = if (isDial()) DialType.label else MaterialTheme.typography.titleMedium
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = (if (viewModel.linked) "Linked" else "Not linked").labelCase(),
            style = labelStyle,
            color = if (viewModel.linked) typeColor(IntervalType.REST)
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (!viewModel.linked) {
            Button(onClick = { permissionLauncher.launch(viewModel.permissions) }) {
                Text("Link".labelCase())
            }
        }
    }

    if (!viewModel.linked) {
        Text(
            text = "Save workouts where other fitness apps can see them",
            style = captionStyle(),
            color = muted,
        )
        viewModel.message?.let { MessageLine(it) }
        return
    }

    Spacer(Modifier.height(4.dp))
    Text(
        text = savedText(status.savedCount, status.lastSavedAt).labelCase(),
        style = captionStyle(),
        color = muted,
    )
    val shared = "Shares workouts, HR, steps, kcal" + if (viewModel.canReadResting) ", resting HR" else ""
    Text(text = shared, style = captionStyle(), color = muted)

    status.lastError?.let { error ->
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = error,
                style = captionStyle(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            SmallTextButton("Retry", onClick = viewModel::retry, enabled = progress == null)
        }
    }

    Spacer(Modifier.height(8.dp))
    if (progress != null) {
        val counted = if (progress.total > 0) " ${progress.done}/${progress.total}" else ""
        Text(text = (progress.text + counted).labelCase(), style = captionStyle(), color = muted)
        Spacer(Modifier.height(8.dp))
        if (progress.total > 0) {
            LinearProgressIndicator(
                progress = { progress.done / progress.total.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        return
    }

    viewModel.message?.let { MessageLine(it) }
    Row {
        SmallTextButton("Manage", onClick = { openManagePermissions(context) })
        SmallTextButton("Stop sharing", onClick = { confirm = Confirm.STOP_SHARING })
        // Only for workouts saved by older versions.
        if (!status.cleanedUp) {
            SmallTextButton("Clean up", onClick = { confirm = Confirm.CLEAN_UP })
        }
    }

    when (confirm) {
        Confirm.STOP_SHARING -> ConfirmDialog(
            title = "Stop sharing?",
            text = "The app loses access to Health Connect. Workouts already saved stay there " +
                "until you delete them.",
            confirmLabel = "Stop sharing",
            onConfirm = viewModel::stopSharing,
            onDismiss = { confirm = null },
        )
        Confirm.CLEAN_UP -> ConfirmDialog(
            title = "Clean up?",
            text = "Older versions saved workouts that cannot be updated or deleted. This " +
                "deletes every workout record this app saved to Health Connect in the " +
                "time range of your history, then saves your workouts again. Records from other " +
                "apps are not touched.",
            confirmLabel = "Clean up",
            onConfirm = viewModel::cleanUp,
            onDismiss = { confirm = null },
        )
        null -> {}
    }
}

@Composable
private fun AgeContent(viewModel: HealthViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Age".labelCase(),
            style = if (isDial()) DialType.label else MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = viewModel.ageText,
            onValueChange = viewModel::setAge,
            singleLine = true,
            textStyle = figureStyle(),
            placeholder = { Text("--", style = figureStyle()) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(96.dp),
        )
    }
    Spacer(Modifier.height(4.dp))
    val max = maxHeartRateFor(viewModel.ageText.toIntOrNull(), highestSeen = null)
    Text(
        text = if (max != null) {
            "Max heart rate $max (220 minus age), for workout zones"
        } else {
            "Optional: zones use your highest heart rate"
        },
        style = captionStyle(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MessageLine(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = text,
        style = captionStyle(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirmLabel.labelCase()) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel".labelCase()) }
        },
    )
}

/** Opens Health Connect's permission screen for this app, or its main data screen. */
private fun openManagePermissions(context: Context) {
    val thisApp = Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
        .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
    try {
        context.startActivity(thisApp)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(HealthConnectClient.getHealthConnectManageDataIntent(context))
        } catch (e: ActivityNotFoundException) {
            // Health Connect has no screen to open; nothing more to do.
        }
    }
}

private fun savedText(count: Int, lastSavedAt: Long?): String {
    val saved = "$count ${if (count == 1) "workout" else "workouts"} saved"
    if (lastSavedAt == null) return saved
    return "$saved · ${lastSavedText(System.currentTimeMillis() - lastSavedAt)}"
}

/** "just now", "last 2 min ago", "last 3 h ago", "last 4 days ago". */
internal fun lastSavedText(elapsedMillis: Long): String {
    val minutes = elapsedMillis / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "last $minutes min ago"
        minutes < 24 * 60 -> "last ${minutes / 60} h ago"
        else -> "last ${minutes / (24 * 60)} ${if (minutes / (24 * 60) == 1L) "day" else "days"} ago"
    }
}
