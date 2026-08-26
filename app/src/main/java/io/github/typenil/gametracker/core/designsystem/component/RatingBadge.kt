package io.github.typenil.gametracker.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.core.designsystem.theme.HighRatingBg
import io.github.typenil.gametracker.core.designsystem.theme.HighRatingFg
import io.github.typenil.gametracker.core.designsystem.theme.MediumRatingBg
import io.github.typenil.gametracker.core.designsystem.theme.MediumRatingFg
import java.util.Locale

private const val HIGH_RATING_THRESHOLD = 80.0
private const val MEDIUM_RATING_THRESHOLD = 60.0

/**
 * Modern pill badge indicating game rating with dynamic semantic colors.
 */
@Composable
fun RatingBadge(
    rating: Double?,
    modifier: Modifier = Modifier
) {
    if (rating == null || rating <= 0.0) return

    val (backgroundColor, contentColor) = when {
        rating >= HIGH_RATING_THRESHOLD -> HighRatingBg to HighRatingFg
        rating >= MEDIUM_RATING_THRESHOLD -> MediumRatingBg to MediumRatingFg
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val formattedRating = String.format(Locale.US, "%.1f", rating)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formattedRating,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold
            )
        )
    }
}
