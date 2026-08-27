package io.github.typenil.gametracker.feature.library.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.component.GAME_COVER_ASPECT_RATIO
import io.github.typenil.gametracker.core.designsystem.component.RatingBadge
import io.github.typenil.gametracker.core.designsystem.component.selectCardTags
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import io.github.typenil.gametracker.core.model.LibraryGame
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val COVER_WIDTH_DP = 96
private val FavoriteHitSize = 48.dp

const val LIBRARY_CARD_ADDED_TEST_TAG = "library_card_added"
const val LIBRARY_CARD_FAVORITE_TEST_TAG = "library_card_favorite"

/**
 * Full-width library row: cover, title, developer, tags, hours (left) and added date (trailing).
 * Favorite is a sibling overlay of the row click target.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryGameCard(
    libraryGame: LibraryGame,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFavoriteClick: () -> Unit = {},
) {
    val game = libraryGame.game
    val entry = libraryGame.entry
    val tags = remember(game.genres, game.platforms) {
        selectCardTags(game.genres, game.platforms)
    }
    val addedDate = remember(entry.addedAtEpochSeconds) {
        formatLibraryAddedDate(entry.addedAtEpochSeconds)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 2.dp,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(GtDimens.Card),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(COVER_WIDTH_DP.dp)
                            .aspectRatio(GAME_COVER_ASPECT_RATIO)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        if (!game.coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = game.coverUrl,
                                contentDescription = game.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        RatingBadge(
                            rating = game.rating,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = game.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val developerName = libraryGame.developerName
                        if (!developerName.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = developerName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (tags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                tags.forEach { tag ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                        ),
                                    ) {
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(
                                                horizontal = 10.dp,
                                                vertical = 4.dp,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(FavoriteHitSize))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (entry.hoursPlayed > 0) {
                        StatInfoBlock(
                            icon = Icons.Outlined.Schedule,
                            value = stringResource(
                                R.string.library_hours_format,
                                entry.hoursPlayed,
                            ),
                            label = stringResource(R.string.library_hours_played),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    StatInfoBlock(
                        icon = Icons.Outlined.CalendarToday,
                        value = addedDate,
                        label = stringResource(R.string.library_added),
                        modifier = Modifier.testTag(LIBRARY_CARD_ADDED_TEST_TAG),
                    )
                }
            }

            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(FavoriteHitSize)
                    .testTag(LIBRARY_CARD_FAVORITE_TEST_TAG),
            ) {
                Icon(
                    imageVector = if (entry.isFavorite) {
                        Icons.Filled.Favorite
                    } else {
                        Icons.Filled.FavoriteBorder
                    },
                    contentDescription = stringResource(
                        if (entry.isFavorite) {
                            R.string.library_favorite_remove
                        } else {
                            R.string.library_favorite_add
                        },
                    ),
                    tint = if (entry.isFavorite) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun StatInfoBlock(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun formatLibraryAddedDate(
    epochSeconds: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = Instant.ofEpochSecond(epochSeconds)
    .atZone(zoneId)
    .toLocalDate()
    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
