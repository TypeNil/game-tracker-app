package io.github.typenil.gametracker.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryStatus
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

private const val COVER_WIDTH_DP = 100
private val CardShape = RoundedCornerShape(16.dp)
private val CoverShape = RoundedCornerShape(
    topStart = 16.dp,
    bottomStart = 16.dp,
    topEnd = 0.dp,
    bottomEnd = 0.dp,
)

const val GAME_CARD_LIBRARY_ACTION_TEST_TAG = "game_card_library_action"

internal fun formatReleaseYear(
    epochSeconds: Long,
    zoneId: ZoneId = ZoneOffset.UTC,
): String = Instant.ofEpochSecond(epochSeconds).atZone(zoneId).year.toString()

/**
 * Compact catalog row: cover, title, tags, optional library action.
 * Trailing [onLibraryAction] is a sibling of the row click target.
 */
@Composable
fun GameCard(
    game: Game,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingLines: List<String> = emptyList(),
    libraryStatus: LibraryStatus? = null,
    onLibraryAction: ((Game) -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClick = onClick),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .width(COVER_WIDTH_DP.dp)
                        .fillMaxHeight()
                        .defaultMinSize(minHeight = (COVER_WIDTH_DP / GAME_COVER_ASPECT_RATIO).dp)
                        .clip(CoverShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    if (!game.coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = game.coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    if (game.rating != null) {
                        RatingBadge(
                            rating = game.rating,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = game.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val genreTags = remember(game.genres) {
                        selectGenreTags(game.genres)
                    }
                    val platformFamilies = remember(game.platforms) {
                        resolvePlatformFamilies(game.platforms)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (genreTags.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                genreTags.forEach { tag ->
                                    TagChip(
                                        text = tag,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
                            }
                        }
                        if (platformFamilies.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                ),
                            ) {
                                PlatformIconsRow(
                                    platforms = platformFamilies,
                                    iconSize = 16.dp,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        horizontal = 8.dp,
                                        vertical = 4.dp,
                                    ),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (supportingLines.isNotEmpty()) {
                        Text(
                            text = supportingLines.first(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (onLibraryAction != null) {
                IconButton(
                    onClick = { onLibraryAction(game) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(GAME_CARD_LIBRARY_ACTION_TEST_TAG),
                ) {
                    val inLibrary = libraryStatus != null
                    Icon(
                        imageVector = if (inLibrary) {
                            Icons.Filled.Bookmark
                        } else {
                            Icons.Filled.BookmarkBorder
                        },
                        contentDescription = if (inLibrary) {
                            stringResource(
                                R.string.game_card_edit_library,
                                stringResource(libraryStatus.displayNameRes()),
                            )
                        } else {
                            stringResource(R.string.game_card_add_to_wishlist)
                        },
                    )
                }
            }
        }
    }
}
