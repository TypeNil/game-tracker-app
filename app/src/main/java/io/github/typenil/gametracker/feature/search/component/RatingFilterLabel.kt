package io.github.typenil.gametracker.feature.search.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import io.github.typenil.gametracker.feature.search.MinRatingFilter

val RatingTier90Color = Color(0xFF4CAF50)
val RatingTier80Color = Color(0xFF8BC34A)
val RatingTier70Color = Color(0xFFFFB300)

/**
 * Text label for rating filter chips with color-coded score digits.
 */
@Composable
fun RatingFilterLabel(
    ratingOption: MinRatingFilter,
    modifier: Modifier = Modifier,
) {
    val text = stringResource(ratingOption.labelRes)
    val ratingColor = when (ratingOption) {
        MinRatingFilter.R90 -> RatingTier90Color
        MinRatingFilter.R80 -> RatingTier80Color
        MinRatingFilter.R70 -> RatingTier70Color
        MinRatingFilter.ANY -> Color.Unspecified
    }

    if (ratingColor == Color.Unspecified) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = modifier,
        )
    } else {
        val scorePart = "${ratingOption.minRating}+"
        val annotated = buildAnnotatedString {
            withStyle(SpanStyle(color = ratingColor, fontWeight = FontWeight.Bold)) {
                append(scorePart)
            }
            if (text.length > scorePart.length) {
                append(text.removePrefix(scorePart))
            }
        }
        Text(
            text = annotated,
            style = MaterialTheme.typography.labelMedium,
            modifier = modifier,
        )
    }
}
