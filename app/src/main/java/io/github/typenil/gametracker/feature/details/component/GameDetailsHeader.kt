package io.github.typenil.gametracker.feature.details.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.component.GAME_COVER_ASPECT_RATIO
import io.github.typenil.gametracker.core.designsystem.component.OverflowTagChip
import io.github.typenil.gametracker.core.designsystem.component.RatingBadge
import io.github.typenil.gametracker.core.designsystem.component.TagChip
import io.github.typenil.gametracker.core.designsystem.component.rememberImageModel
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import io.github.typenil.gametracker.core.model.GameDetails
import io.github.typenil.gametracker.feature.details.TITLE_DOCK_SCALE_DELTA
import io.github.typenil.gametracker.feature.details.TRANSFORM_ORIGIN_CENTER_Y
import io.github.typenil.gametracker.feature.details.formatHeaderTagPreview



private val DETAILS_GUTTER = GtDimens.Gutter

private const val ARTWORK_ALPHA = 0.72f
private const val ARTWORK_SCRIM_ALPHA = 0.15f
private const val TITLE_A11Y_MAX_ALPHA = 0.95f

@Composable
internal fun GameDetailsHeader(
    game: GameDetails,
    contentTopPadding: Dp,
    onTagsOverflowClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleHandoffProgress: () -> Float = { 0f },
    titleTranslationRangePx: Float = 0f,
    imageReloadToken: Long = 0L,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        if (!game.artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = rememberImageModel(game.artworkUrl, imageReloadToken),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = ARTWORK_ALPHA,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = ARTWORK_SCRIM_ALPHA),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
        }
        Column(
            modifier = Modifier.padding(
                top = contentTopPadding,
                start = DETAILS_GUTTER,
                end = DETAILS_GUTTER,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .width(detailsHeaderCoverWidth)
                        .aspectRatio(GAME_COVER_ASPECT_RATIO)
                        .clip(RoundedCornerShape(detailsHeaderCoverCorner))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp),
                    )
                    if (!game.coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = rememberImageModel(game.coverUrl, imageReloadToken),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = game.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .graphicsLayer {
                                val progress = titleHandoffProgress()
                                alpha = (1f - progress).coerceIn(0f, 1f)
                                translationY = -titleTranslationRangePx * progress
                                scaleX = 1f - TITLE_DOCK_SCALE_DELTA * progress
                                scaleY = 1f - TITLE_DOCK_SCALE_DELTA * progress
                                transformOrigin = TransformOrigin(0f, TRANSFORM_ORIGIN_CENTER_Y)
                            }
                            .semantics {
                                if (titleHandoffProgress() > TITLE_A11Y_MAX_ALPHA) {
                                    hideFromAccessibility()
                                }
                            },
                    )
                    // Aggregate rating with vote count; falls back to the critic rating the
                    // catalog already carries while the details row is still hydrating.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RatingBadge(rating = game.totalRating ?: game.rating)
                        if (game.totalRatingCount != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.details_votes_count_format,
                                    game.totalRatingCount,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }

                    game.companiesLine()?.let { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    val tagPreview = remember(game.genres, game.themes) {
                        formatHeaderTagPreview(game.genres, game.themes)
                    }
                    if (tagPreview.previewTags.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            tagPreview.previewTags.forEach { tag ->
                                TagChip(
                                    text = tag,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                            if (tagPreview.overflowCount > 0) {
                                val totalCount = tagPreview.previewTags.size + tagPreview.overflowCount
                                val overflowDesc = stringResource(R.string.details_more_tags_desc, totalCount)
                                OverflowTagChip(
                                    text = stringResource(R.string.details_more_count, tagPreview.overflowCount),
                                    onClick = onTagsOverflowClick,
                                    contentDescription = overflowDesc,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameDetails.companiesLine(): String? {
    val developers = companies.filter { it.isDeveloper }.map { it.name }
    val publishers = companies.filter { it.isPublisher }.map { it.name }
    val others = companies
        .filterNot { it.isDeveloper || it.isPublisher }
        .map { it.name }

    val parts = buildList {
        when {
            developers.isNotEmpty() && developers == publishers ->
                add(stringResource(R.string.details_developed_and_published_format, developers.joinToString()))
            else -> {
                if (developers.isNotEmpty()) {
                    add(stringResource(R.string.details_developed_by_format, developers.joinToString()))
                }
                if (publishers.isNotEmpty()) {
                    add(stringResource(R.string.details_published_by_format, publishers.joinToString()))
                }
            }
        }
        // Porting/supporting studios (both flags false) are still credited,
        // appended as plain names so they never hide behind dev/pub lines.
        if (others.isNotEmpty()) {
            add(others.joinToString())
        }
    }
    return parts.joinToString(" · ").takeIf { it.isNotBlank() }
}
