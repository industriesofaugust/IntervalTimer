package com.august.intervaltimer.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.august.intervaltimer.ui.WorkoutItem

// The pieces every menu screen is built from, so they all look the same.
// They are extensions of the list item, because the list needs to know about
// each item to shrink and fade it near the edges of the round screen.

/**
 * Makes plain content (not a button or card, which do this themselves)
 * shrink and fade at the screen edges like the items around it.
 */
fun Modifier.transformedItem(
    scope: TransformingLazyColumnItemScope,
    spec: TransformationSpec,
): Modifier = transformedHeight(scope, spec).graphicsLayer {
    with(scope) {
        val progress = scrollProgress
        with(spec) {
            applyContainerTransformation(progress)
            applyContentTransformation(progress)
        }
    }
}

/** The screen's title, in capitals. */
@Composable
fun TransformingLazyColumnItemScope.ScreenTitle(text: String, spec: TransformationSpec) {
    ListHeader(
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A smaller heading that splits a list into groups. */
@Composable
fun TransformingLazyColumnItemScope.SectionTitle(text: String, spec: TransformationSpec) {
    ListSubHeader(
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

/** A full-width button with an icon and a capital label. */
@Composable
fun TransformingLazyColumnItemScope.MenuButton(
    label: String,
    icon: ImageVector,
    spec: TransformationSpec,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize)) },
        transformation = SurfaceTransformation(spec),
    ) {
        Text(label, maxLines = 1)
    }
}

/** One workout: name, "15 INTERVALS · 3:50", and the session strip. */
@Composable
fun TransformingLazyColumnItemScope.WorkoutCard(
    workout: WorkoutItem,
    spec: TransformationSpec,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
    ) {
        Text(
            text = workout.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = workout.summary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        SessionStrip(segments = workout.strip, modifier = Modifier.fillMaxWidth())
    }
}

/** A short line of quiet text, for hints and totals. */
@Composable
fun TransformingLazyColumnItemScope.Caption(text: String, spec: TransformationSpec) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().transformedItem(this, spec),
    )
}

/** The icon inside a round button, sized to match the button. */
@Composable
fun ButtonIcon(icon: ImageVector, description: String?, buttonSize: Dp) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        modifier = Modifier.size(IconButtonDefaults.iconSizeFor(buttonSize)),
    )
}
