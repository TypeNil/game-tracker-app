package io.github.typenil.gametracker.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val TagChipShape = RoundedCornerShape(8.dp)
private val TagChipPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)

/**
 * Shared surface primitives ensuring [TagChip] and [OverflowTagChip] share
 * identical geometry, shape, border, and background tokens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagChipSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    val contentColor = if (onClick != null) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    if (onClick != null) {
        // Suppress Material 3 default 48.dp touch-target inflation to align
        // pixel-for-pixel with non-interactive TagChips in mixed FlowRows.
        // Meets WCAG 2.5.8 (24x24dp) baseline height.
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
        ) {
            Surface(
                onClick = onClick,
                shape = TagChipShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = contentColor,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = modifier.semantics(mergeDescendants = true) {
                    if (contentDescription != null) {
                        this.contentDescription = contentDescription
                    }
                    role = Role.Button
                },
                content = content,
            )
        }
    } else {
        Surface(
            modifier = modifier,
            shape = TagChipShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = contentColor,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            content = content,
        )
    }
}

/**
 * Non-interactive text tag chip: the single outline style for genres, themes,
 * game modes and platforms across catalog cards and details. Display only,
 * never clickable.
 */
@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    TagChipSurface(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(TagChipPadding),
        )
    }
}

/**
 * Interactive overflow tag chip (+N more) that matches [TagChip] geometry,
 * padding and shape pixel-for-pixel while providing a subtle primary tint
 * to denote clickability.
 */
@Composable
fun OverflowTagChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    TagChipSurface(
        onClick = onClick,
        contentDescription = contentDescription,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(TagChipPadding),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TagChipsRowPreview() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TagChip(text = "RPG")
        TagChip(text = "Adventure")
        OverflowTagChip(text = "+3 more", onClick = {})
    }
}
