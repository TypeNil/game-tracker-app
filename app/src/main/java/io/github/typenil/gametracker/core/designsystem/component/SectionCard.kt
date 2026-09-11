package io.github.typenil.gametracker.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens

/** Corner radius of every section container, including the feature-local variants. */
val SectionCardShape = RoundedCornerShape(16.dp)

/**
 * Container for one titled block of secondary content.
 *
 * Deliberately minimal: it carries the container's shape, role and padding, and nothing else.
 * It sets no vertical arrangement, because sections space their own children — Insights already
 * inserts its own spacers, and a fixed rhythm here would silently reflow it. It takes no colour
 * either: the one screen that needs a different surface (a failed result) keeps its own, rather
 * than every reader having to know which variant means what.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SectionCardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(GtDimens.Gutter),
            content = content,
        )
    }
}

/**
 * Heading of a [SectionCard].
 *
 * Exposed as a heading so assistive technology can jump section by section instead of walking every
 * control on the screen.
 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
