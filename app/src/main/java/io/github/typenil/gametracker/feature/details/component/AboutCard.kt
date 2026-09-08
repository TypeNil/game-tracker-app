package io.github.typenil.gametracker.feature.details.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.feature.details.ARROW_COLLAPSED_ROTATION
import io.github.typenil.gametracker.feature.details.ARROW_EXPANDED_ROTATION

/** Collapsed About summary line count before the arrow toggle reveals the rest. */
private const val ABOUT_COLLAPSED_LINES = 2

/** Collapsed About card: 2-line summary with an in-card header and arrow toggle. */
@Composable
fun AboutCard(
    summary: String,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(summary) { mutableStateOf(false) }
    var hasVisualOverflow by remember(summary) { mutableStateOf(false) }
    val showMoreDesc = stringResource(R.string.details_about_show_more)
    val showLessDesc = stringResource(R.string.details_about_show_less)
    val actionDescription = if (expanded) showLessDesc else showMoreDesc
    val headerInteractionSource = remember { MutableInteractionSource() }

    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) ARROW_EXPANDED_ROTATION else ARROW_COLLAPSED_ROTATION,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "aboutArrowRotation",
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp)
                    .then(
                        if (hasVisualOverflow || expanded) {
                            Modifier
                                .clickable(
                                    interactionSource = headerInteractionSource,
                                    indication = null,
                                    role = Role.Button,
                                    onClick = { expanded = !expanded },
                                )
                                .semantics(mergeDescendants = true) {
                                    contentDescription = actionDescription
                                }
                        } else {
                            Modifier
                        }
                    )
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.details_section_about),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (hasVisualOverflow || expanded) {
                        Icon(
                            imageVector = Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer { rotationZ = arrowRotation },
                        )
                    }
                }
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else ABOUT_COLLAPSED_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 12.dp),
                onTextLayout = { layoutResult ->
                    if (!expanded) {
                        hasVisualOverflow = layoutResult.hasVisualOverflow ||
                            layoutResult.lineCount > ABOUT_COLLAPSED_LINES
                    }
                },
            )
        }
    }
}
