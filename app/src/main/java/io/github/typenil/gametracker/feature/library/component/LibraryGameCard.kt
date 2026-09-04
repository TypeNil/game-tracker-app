package io.github.typenil.gametracker.feature.library.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.component.RatingBadge
import io.github.typenil.gametracker.core.designsystem.component.contentColor
import io.github.typenil.gametracker.core.designsystem.component.displayNameRes
import io.github.typenil.gametracker.core.designsystem.component.leadingIcon
import io.github.typenil.gametracker.core.designsystem.component.PlatformIconsRow
import io.github.typenil.gametracker.core.designsystem.component.resolvePlatformFamilies
import io.github.typenil.gametracker.core.designsystem.component.selectGenreTags
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val HERO_ASPECT_RATIO = 16f / 9f
private val CardShape = RoundedCornerShape(16.dp)
private val HeroShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val HeroScrim = Brush.verticalGradient(
    0.00f to Color.Transparent,
    0.35f to Color.Transparent,
    0.70f to Color.Black.copy(alpha = 0.65f),
    1.00f to Color.Black.copy(alpha = 0.90f),
)
private const val HERO_CONTROL_INSET_DP = 8
private const val FAVORITE_HIT_SIZE_DP = 48
private val FavoriteHitSize = FAVORITE_HIT_SIZE_DP.dp
private val HeroContentTopPadding = (HERO_CONTROL_INSET_DP + FAVORITE_HIT_SIZE_DP + HERO_CONTROL_INSET_DP).dp
private val MetaIconSize = 18.dp
private val MetaChevronSize = 16.dp
private val LibraryAddedDateFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
private const val ANIM_EXPAND_ENTER_MS = 250
private const val ANIM_SHRINK_EXIT_MS = 200
private const val ANIM_TEXT_FADE_IN_MS = 200
private const val ANIM_TEXT_FADE_OUT_MS = 150
private const val ANIM_ACCENT_COLOR_MS = 250

const val LIBRARY_CARD_ADDED_TEST_TAG = "library_card_added"
const val LIBRARY_CARD_FAVORITE_TEST_TAG = "library_card_favorite"
const val LIBRARY_CARD_STATUS_TEST_TAG = "library_card_status"
const val LIBRARY_CARD_HOURS_TEST_TAG = "library_card_hours"
const val LIBRARY_CARD_HOURS_TEXT_TEST_TAG = "library_card_hours_text"
const val LIBRARY_CARD_ADDED_TEXT_TEST_TAG = "library_card_added_text"
const val LIBRARY_CARD_BANNER_TEST_TAG = "library_card_banner"

const val LIBRARY_CARD_CLICK_TARGET_TEST_TAG = "library_card_click_target"
internal fun resolveLibraryBannerUrl(
    bannerUrl: String?,
    coverUrl: String?,
): String? = bannerUrl?.takeIf(String::isNotBlank)
    ?: coverUrl?.takeIf(String::isNotBlank)
private fun Modifier.aspectRatioOrContent(aspectRatio: Float): Modifier = layout { measurable, constraints ->
    val minHeight = if (constraints.hasBoundedWidth) {
        (constraints.maxWidth / aspectRatio).toInt().coerceIn(constraints.minHeight, constraints.maxHeight)
    } else {
        constraints.minHeight
    }
    val childConstraints = constraints.copy(minHeight = minHeight)
    val placeable = measurable.measure(childConstraints)
    layout(placeable.width, placeable.height) {
        placeable.placeRelative(0, 0)
    }
}

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
    val genreTags = remember(game.genres) {
        selectGenreTags(game.genres)
    }
    val platformFamilies = remember(game.platforms) {
        resolvePlatformFamilies(game.platforms)
    }
    val addedDate = remember(entry.addedAtEpochSeconds) {
        formatLibraryAddedDate(entry.addedAtEpochSeconds)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(HeroShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .testTag(LIBRARY_CARD_BANNER_TEST_TAG),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatioOrContent(HERO_ASPECT_RATIO)
                        .clickable(onClick = onClick)
                        .testTag(LIBRARY_CARD_CLICK_TARGET_TEST_TAG),
                ) {
                    val bannerImage = resolveLibraryBannerUrl(libraryGame.bannerUrl, game.coverUrl)
                    var bannerLoadFailed by remember(bannerImage) { mutableStateOf(false) }
                    if (bannerImage == null || bannerLoadFailed) {
                        Icon(
                            imageVector = Icons.Default.VideogameAsset,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.20f),
                            modifier = Modifier
                                .size(64.dp)
                                .align(Alignment.Center),
                        )
                    }
                    if (bannerImage != null) {
                        AsyncImage(
                            model = bannerImage,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.Center,
                            onSuccess = { bannerLoadFailed = false },
                            onError = { bannerLoadFailed = true },
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(HeroScrim),
                    )
                    if (game.rating != null) {
                        RatingBadge(
                            rating = game.rating,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = HeroContentTopPadding),
                    ) {
                        Text(
                            text = game.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val developerName = libraryGame.developerName
                        if (!developerName.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = developerName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    if (genreTags.isNotEmpty() || platformFamilies.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            maxItemsInEachRow = 3,
                        ) {
                            genreTags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.5f),
                                    border = BorderStroke(
                                        1.dp,
                                        Color.White.copy(alpha = 0.35f),
                                    ),
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White.copy(alpha = 0.95f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(
                                            horizontal = 12.dp,
                                            vertical = 5.dp,
                                        ),
                                    )
                                }
                            }
                            if (platformFamilies.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.5f),
                                    border = BorderStroke(
                                        1.dp,
                                        Color.White.copy(alpha = 0.35f),
                                    ),
                                ) {
                                    PlatformIconsRow(
                                        platforms = platformFamilies,
                                        tint = Color.White.copy(alpha = 0.95f),
                                        iconSize = 18.dp,
                                        modifier = Modifier.padding(
                                            horizontal = 10.dp,
                                            vertical = 5.dp,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
                }
                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(FavoriteHitSize)
                        .testTag(LIBRARY_CARD_FAVORITE_TEST_TAG),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
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
                                Color.White.copy(alpha = 0.95f)
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            val useStackedMetadata = LocalDensity.current.fontScale >= 1.3f
            if (useStackedMetadata) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        LibraryStatusControl(
                            status = entry.status,
                            onStatusSelected = onStatusSelected,
                        )
                        AnimatedVisibility(
                            visible = entry.showsHours(),
                            enter = fadeIn(animationSpec = tween(ANIM_EXPAND_ENTER_MS)),
                            exit = fadeOut(animationSpec = tween(ANIM_SHRINK_EXIT_MS)),
                        ) {
                            LibraryHoursControl(
                                hoursPlayed = entry.hoursPlayed,
                                onClick = onHoursClick,
                            )
                        }
                        LibraryAddedDate(addedDate = addedDate)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            LibraryStatusControl(
                                status = entry.status,
                                onStatusSelected = onStatusSelected,
                            )
                        }
                        AnimatedVisibility(
                            visible = entry.showsHours(),
                            enter = fadeIn(animationSpec = tween(ANIM_EXPAND_ENTER_MS)) +
                                slideInVertically(
                                    animationSpec = tween(ANIM_EXPAND_ENTER_MS),
                                    initialOffsetY = { fullHeight -> fullHeight },
                                ) +
                                expandHorizontally(
                                    animationSpec = tween(ANIM_EXPAND_ENTER_MS),
                                    expandFrom = Alignment.CenterHorizontally,
                                ),
                            exit = fadeOut(animationSpec = tween(ANIM_SHRINK_EXIT_MS)) +
                                slideOutVertically(
                                    animationSpec = tween(ANIM_SHRINK_EXIT_MS),
                                    targetOffsetY = { fullHeight -> fullHeight },
                                ) +
                                shrinkHorizontally(
                                    animationSpec = tween(ANIM_SHRINK_EXIT_MS),
                                    shrinkTowards = Alignment.CenterHorizontally,
                                ),
                        ) {
                            LibraryHoursControl(
                                hoursPlayed = entry.hoursPlayed,
                                onClick = onHoursClick,
                            )
                        }
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            LibraryAddedDate(addedDate = addedDate)
                        }
                    }
                }
        }
    }
}

@Composable
private fun LibraryHoursControl(
    hoursPlayed: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag(LIBRARY_CARD_HOURS_TEST_TAG)
            .padding(horizontal = 2.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = stringResource(R.string.library_hours_played),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(MetaIconSize),
        )
        AnimatedContent(
            targetState = hoursPlayed,
            transitionSpec = {
                (slideInVertically(tween(ANIM_TEXT_FADE_IN_MS)) { fullHeight -> fullHeight } +
                    fadeIn(tween(ANIM_TEXT_FADE_IN_MS))) togetherWith
                    (slideOutVertically(tween(ANIM_TEXT_FADE_OUT_MS)) { fullHeight -> -fullHeight } +
                        fadeOut(tween(ANIM_TEXT_FADE_OUT_MS)))
            },
            label = "hoursTextTransition",
        ) { hours ->
            Text(
                text = stringResource(
                    R.string.library_hours_short,
                    hours,
                ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(LIBRARY_CARD_HOURS_TEXT_TEST_TAG),
            )
        }
    }
}

@Composable
private fun LibraryAddedDate(
    addedDate: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.testTag(LIBRARY_CARD_ADDED_TEST_TAG),
    ) {
        Icon(
            imageVector = Icons.Outlined.CalendarToday,
            contentDescription = stringResource(R.string.library_added),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(MetaIconSize),
        )
        Text(
            text = addedDate,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(LIBRARY_CARD_ADDED_TEXT_TEST_TAG),
        )
    }
}

@Composable
private fun LibraryStatusControl(
    status: LibraryStatus,
    onStatusSelected: (LibraryStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val targetAccent = status.contentColor()
    val accent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(ANIM_ACCENT_COLOR_MS),
        label = "statusAccentColor",
    )
    val statusLabel = stringResource(status.displayNameRes())
    val changeStatus = stringResource(R.string.library_change_status, statusLabel)

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .testTag(LIBRARY_CARD_STATUS_TEST_TAG)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AnimatedContent(
                targetState = status,
                transitionSpec = {
                    (slideInVertically(tween(ANIM_TEXT_FADE_IN_MS)) { fullHeight -> fullHeight } +
                        fadeIn(tween(ANIM_TEXT_FADE_IN_MS))) togetherWith
                        (slideOutVertically(tween(ANIM_TEXT_FADE_OUT_MS)) { fullHeight -> -fullHeight } +
                            fadeOut(tween(ANIM_TEXT_FADE_OUT_MS)))
                },
                label = "statusContentTransition",
            ) { currentStatus ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = currentStatus.leadingIcon(),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(MetaIconSize),
                    )
                    Text(
                        text = stringResource(currentStatus.displayNameRes()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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



private fun LibraryEntry.showsHours(): Boolean =
    status.supportsHours && (status == LibraryStatus.PLAYING || hoursPlayed > 0)

internal fun formatLibraryAddedDate(
    epochSeconds: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = Instant.ofEpochSecond(epochSeconds)
    .atZone(zoneId)
    .toLocalDate()
    .format(LibraryAddedDateFormatter)
