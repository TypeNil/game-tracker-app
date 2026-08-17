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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

private const val HIGH_RATING_THRESHOLD = 80.0
private const val MEDIUM_RATING_THRESHOLD = 60.0

private const val HIGH_RATING_BG = 0xFF1B5E20
private const val HIGH_RATING_FG = 0xFFE8F5E9
private const val MEDIUM_RATING_BG = 0xFFF57F17
private const val MEDIUM_RATING_FG = 0xFFFFFDE7

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
        rating >= HIGH_RATING_THRESHOLD -> Color(HIGH_RATING_BG) to Color(HIGH_RATING_FG)
        rating >= MEDIUM_RATING_THRESHOLD -> Color(MEDIUM_RATING_BG) to Color(MEDIUM_RATING_FG)
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
