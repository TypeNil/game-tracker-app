package io.github.typenil.gametracker.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private val TagChipShape = RoundedCornerShape(8.dp)
private val TagChipPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)

/**
 * Shared surface primitives ensuring [TagChip] and [OverflowTagChip] share
 * identical geometry, shape, border, and background tokens.
 */
@Composable
private fun TagChipSurface(
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = TagChipShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = contentColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
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
 * Interactive overflow tag chip (+N more) that matches [TagChip] visual surface
 * geometry, padding and shape pixel-for-pixel while providing a minimum 48x48dp
 * touch-target bounding box for accessibility compliance.
 */
@Composable
fun OverflowTagChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        TagChipSurface(contentColor = MaterialTheme.colorScheme.primary) {
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
}

@Preview(showBackground = true)
@Composable
private fun TagChipsRowPreview() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TagChip(text = "RPG")
        TagChip(text = "Adventure")
        OverflowTagChip(text = "+3 more", onClick = {})
    }
}
