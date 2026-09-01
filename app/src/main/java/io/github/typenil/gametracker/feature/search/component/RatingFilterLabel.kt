package io.github.typenil.gametracker.feature.search.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.core.designsystem.theme.HighRatingBg
import io.github.typenil.gametracker.core.designsystem.theme.HighRatingFg
import io.github.typenil.gametracker.core.designsystem.theme.MediumRatingBg
import io.github.typenil.gametracker.core.designsystem.theme.MediumRatingFg
import io.github.typenil.gametracker.feature.search.MinRatingFilter

private val RatingBadgeShape = RoundedCornerShape(6.dp)

/**
 * Visual label for rating filter chips featuring an authentic rating pill badge.
 */
@Composable
fun RatingFilterLabel(
    ratingOption: MinRatingFilter,
    modifier: Modifier = Modifier,
) {
    if (ratingOption == MinRatingFilter.ANY) {
        Text(
            text = stringResource(ratingOption.labelRes),
            style = MaterialTheme.typography.labelMedium,
            modifier = modifier,
        )
        return
    }

    val (bgColor, fgColor) = when (ratingOption) {
        MinRatingFilter.R90, MinRatingFilter.R80 -> HighRatingBg to HighRatingFg
        MinRatingFilter.R70 -> MediumRatingBg to MediumRatingFg
        MinRatingFilter.ANY -> Color.Transparent to Color.Unspecified
    }

    val scorePart = "${ratingOption.minRating}+"
    val fullText = stringResource(ratingOption.labelRes)
    val suffix = fullText.removePrefix(scorePart).trim()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .clip(RatingBadgeShape)
                .background(bgColor)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = scorePart,
                color = fgColor,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            )
        }

        if (suffix.isNotEmpty()) {
            Text(
                text = suffix,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
