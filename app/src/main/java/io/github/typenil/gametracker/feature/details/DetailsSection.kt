package io.github.typenil.gametracker.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.core.model.GameReleaseDate
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Landscape 16:9 aspect ratio for screenshot thumbnails. */
internal const val SCREENSHOT_ASPECT_RATIO = 16f / 9f

internal const val TITLE_DOCK_SCALE_DELTA = 0.08f
internal const val TRANSFORM_ORIGIN_CENTER_Y = 0.5f
internal const val ARROW_EXPANDED_ROTATION = 180f
internal const val ARROW_COLLAPSED_ROTATION = 0f

@Composable
internal fun DetailsSection(
    title: String,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = titleModifier,
        )
        content()
    }
}

internal fun GameReleaseDate.displayDate(
    unknown: String,
    locale: Locale = Locale.getDefault(),
): String = when {
    dateEpochSeconds != null ->
        Instant.ofEpochSecond(dateEpochSeconds)
            .atZone(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
    year != null -> year.toString()
    else -> unknown
}
