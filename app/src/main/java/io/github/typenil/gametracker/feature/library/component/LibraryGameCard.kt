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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.typenil.gametracker.core.designsystem.component.contentColor
import io.github.typenil.gametracker.core.designsystem.component.displayNameRes
import io.github.typenil.gametracker.core.designsystem.component.selectCardTags
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val COVER_WIDTH_DP = 96
private val FavoriteHitSize = 48.dp
private val MetaIconSize = 18.dp
private val MetaChevronSize = 16.dp
private val LibraryAddedDateFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

const val LIBRARY_CARD_ADDED_TEST_TAG = "library_card_added"
const val LIBRARY_CARD_FAVORITE_TEST_TAG = "library_card_favorite"
const val LIBRARY_CARD_STATUS_TEST_TAG = "library_card_status"
const val LIBRARY_CARD_HOURS_TEST_TAG = "library_card_hours"
const val LIBRARY_CARD_HOURS_TEXT_TEST_TAG = "library_card_hours_text"
const val LIBRARY_CARD_ADDED_TEXT_TEST_TAG = "library_card_added_text"

/**
 * Full-width library row: cover, title, developer, tags, then a status control,
 * optional hours, and added date. Favorite and status are siblings of the
 * cover/title click target.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryGameCard(
    libraryGame: LibraryGame,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFavoriteClick: () -> Unit = {},
    onStatusSelected: (LibraryStatus) -> Unit = {},
    onHoursClick: () -> Unit = {},
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
                    .padding(GtDimens.Card),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClick),
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

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LibraryStatusControl(
                            status = entry.status,
                            onStatusSelected = onStatusSelected,
                        )
                        if (entry.showsHours()) {
                            VerticalDivider(
                                modifier = Modifier.height(16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onHoursClick)
                                    .testTag(LIBRARY_CARD_HOURS_TEST_TAG)
                                    .padding(horizontal = 4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Schedule,
                                    contentDescription = stringResource(R.string.library_hours_played),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(MetaIconSize),
                                )
                                Text(
                                    text = stringResource(
                                        R.string.library_hours_short,
                                        entry.hoursPlayed,
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag(LIBRARY_CARD_HOURS_TEXT_TEST_TAG),
                                )
                            }
                        }
                    }
                    if (entry.showsHours()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        VerticalDivider(
                            modifier = Modifier.height(16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.testTag(LIBRARY_CARD_ADDED_TEST_TAG),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = stringResource(R.string.library_added),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(MetaIconSize),
                        )
                        Text(
                            text = addedDate,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.testTag(LIBRARY_CARD_ADDED_TEXT_TEST_TAG),
                        )
                    }
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
private fun LibraryStatusControl(
    status: LibraryStatus,
    onStatusSelected: (LibraryStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = status.contentColor()
    val statusLabel = stringResource(status.displayNameRes())
    val changeStatus = stringResource(R.string.library_change_status, statusLabel)

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .testTag(LIBRARY_CARD_STATUS_TEST_TAG)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = status.leadingIcon(),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(MetaIconSize),
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = changeStatus,
                tint = accent,
                modifier = Modifier.size(MetaChevronSize),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            LibraryStatus.entries.forEach { option ->
                val optionAccent = option.contentColor()
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(option.displayNameRes()),
                            color = optionAccent,
                            fontWeight = if (option == status) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = option.leadingIcon(),
                            contentDescription = null,
                            tint = optionAccent,
                        )
                    },
                    onClick = {
                        expanded = false
                        if (option != status) {
                            onStatusSelected(option)
                        }
                    },
                )
            }
        }
    }
}


private fun LibraryStatus.leadingIcon(): ImageVector = when (this) {
    LibraryStatus.PLAYING -> Icons.Filled.Bookmark
    LibraryStatus.WISHLIST -> Icons.Filled.BookmarkBorder
    LibraryStatus.COMPLETED -> Icons.Filled.Check
    LibraryStatus.DROPPED -> Icons.Filled.Close
    LibraryStatus.NOT_INTERESTED -> Icons.Filled.RemoveCircleOutline
}

private fun LibraryEntry.showsHours(): Boolean =
    hoursPlayed > 0 &&
        status != LibraryStatus.WISHLIST &&
        status != LibraryStatus.NOT_INTERESTED

internal fun formatLibraryAddedDate(
    epochSeconds: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = Instant.ofEpochSecond(epochSeconds)
    .atZone(zoneId)
    .toLocalDate()
    .format(LibraryAddedDateFormatter)
