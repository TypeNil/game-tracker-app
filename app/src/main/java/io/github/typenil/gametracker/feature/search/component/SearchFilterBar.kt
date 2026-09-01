package io.github.typenil.gametracker.feature.search.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.component.PlatformFamily
import io.github.typenil.gametracker.core.designsystem.component.formatGenreTag
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import io.github.typenil.gametracker.feature.search.MinRatingFilter
import io.github.typenil.gametracker.feature.search.QuickSearchPreset
import io.github.typenil.gametracker.feature.search.ReleaseYearFilter
import io.github.typenil.gametracker.feature.search.SearchFilters
import io.github.typenil.gametracker.feature.search.SearchSortOption

val SearchChipShape = RoundedCornerShape(8.dp)

@Composable
fun searchChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

@Composable
fun searchChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = MaterialTheme.colorScheme.outlineVariant,
    selectedBorderColor = MaterialTheme.colorScheme.primary,
    borderWidth = 1.dp,
    selectedBorderWidth = 1.dp,
)

@Composable
fun searchInputChipColors() = InputChipDefaults.inputChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

@Composable
fun searchInputChipBorder(selected: Boolean) = InputChipDefaults.inputChipBorder(
    enabled = true,
    selected = selected,
    borderColor = MaterialTheme.colorScheme.outlineVariant,
    selectedBorderColor = MaterialTheme.colorScheme.primary,
    borderWidth = 1.dp,
    selectedBorderWidth = 1.dp,
)

private val QuickPresetGenres = listOf(
    "Role-playing (RPG)",
    "Action",
    "Adventure",
    "Shooter",
    "Indie",
)

private val QuickPresetPlatforms = listOf(
    PlatformFamily.PC,
    PlatformFamily.PLAYSTATION,
    PlatformFamily.XBOX,
    PlatformFamily.NINTENDO,
)

/**
 * Horizontal scrollable filter, sort, and quick presets bar displayed below the search TextField.
 */
@Composable
fun SearchFilterBar(
    filters: SearchFilters,
    onOpenFilterSheet: () -> Unit,
    onRemoveGenre: (String) -> Unit,
    onRemovePlatform: (PlatformFamily) -> Unit,
    onRemoveReleaseYear: () -> Unit,
    onRemoveMinRating: () -> Unit,
    onResetFilters: () -> Unit,
    onQuickPresetSelected: (QuickSearchPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val activeCount = filters.activeConstraintsCount()

    val chipColors = searchChipColors()
    val inputColors = searchInputChipColors()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = GtDimens.Gutter, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. Filter sheet trigger button with badge
        FilterChip(
            selected = activeCount > 0,
            onClick = onOpenFilterSheet,
            shape = SearchChipShape,
            colors = chipColors,
            border = searchChipBorder(selected = activeCount > 0),
            label = {
                Text(
                    text = if (activeCount > 0) {
                        stringResource(R.string.search_filters_count_format, activeCount)
                    } else {
                        stringResource(R.string.search_filters_title)
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = stringResource(R.string.search_filters_button_desc),
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
        )

        // 2. Vertical Divider between Filters button and quick presets / active chips
        VerticalDivider(
            modifier = Modifier
                .height(24.dp)
                .padding(horizontal = 2.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        // 3. Active Sort indicator chip (if not RELEVANCE)
        if (filters.sort != SearchSortOption.RELEVANCE) {
            FilterChip(
                selected = true,
                onClick = onOpenFilterSheet,
                shape = SearchChipShape,
                colors = chipColors,
                border = searchChipBorder(selected = true),
                label = {
                    Text(
                        text = stringResource(filters.sort.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            )
        }

        // 4. Removable Release Year chip (if not ALL)
        if (filters.releaseYear != ReleaseYearFilter.ALL) {
            val yearLabel = stringResource(filters.releaseYear.labelRes)
            InputChip(
                selected = true,
                onClick = onRemoveReleaseYear,
                shape = SearchChipShape,
                colors = inputColors,
                border = searchInputChipBorder(selected = true),
                label = {
                    Text(
                        text = yearLabel,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.search_remove_filter_desc, yearLabel),
                        modifier = Modifier.size(InputChipDefaults.IconSize),
                    )
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            )
        }

        // 5. Active custom genres not in standard quick presets list
        for (genre in filters.genres) {
            if (!QuickPresetGenres.contains(genre)) {
                val genreDisplay = formatGenreTag(genre)
                InputChip(
                    selected = true,
                    onClick = { onRemoveGenre(genre) },
                    shape = SearchChipShape,
                    colors = inputColors,
                    border = searchInputChipBorder(selected = true),
                    label = {
                        Text(
                            text = genreDisplay,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.search_remove_filter_desc, genreDisplay),
                            modifier = Modifier.size(InputChipDefaults.IconSize),
                        )
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                )
            }
        }

        // 6. Quick Presets: Genres
        for (genre in QuickPresetGenres) {
            val selected = filters.genres.contains(genre)
            val genreDisplay = formatGenreTag(genre)
            FilterChip(
                selected = selected,
                onClick = {
                    if (selected) {
                        onRemoveGenre(genre)
                    } else {
                        onQuickPresetSelected(QuickSearchPreset.Genre(genre))
                    }
                },
                shape = SearchChipShape,
                colors = chipColors,
                border = searchChipBorder(selected = selected),
                label = {
                    Text(
                        text = genreDisplay,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                trailingIcon = if (selected) {
                    {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.search_remove_filter_desc, genreDisplay),
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else null,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            )
        }

        // Quick Presets: Platforms
        for (platform in QuickPresetPlatforms) {
            val selected = filters.platforms.contains(platform)
            val platformLabel = stringResource(platform.labelRes)
            FilterChip(
                selected = selected,
                onClick = {
                    if (selected) {
                        onRemovePlatform(platform)
                    } else {
                        onQuickPresetSelected(QuickSearchPreset.Platform(platform))
                    }
                },
                shape = SearchChipShape,
                colors = chipColors,
                border = searchChipBorder(selected = selected),
                label = {
                    Text(
                        text = platformLabel,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(platform.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
                trailingIcon = if (selected) {
                    {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.search_remove_filter_desc, platformLabel),
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else null,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            )
        }

        // Quick Preset: Rating 80+
        val isRating80Selected = filters.minRating == MinRatingFilter.R80
        val rating80Label = stringResource(R.string.search_filter_rating_80)
        FilterChip(
            selected = isRating80Selected,
            onClick = {
                if (isRating80Selected) {
                    onRemoveMinRating()
                } else {
                    onQuickPresetSelected(QuickSearchPreset.Rating80)
                }
            },
            shape = SearchChipShape,
            colors = chipColors,
            border = searchChipBorder(selected = isRating80Selected),
            label = {
                Text(
                    text = rating80Label,
                    style = MaterialTheme.typography.labelMedium,
                )
            },
            trailingIcon = if (isRating80Selected) {
                {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.search_remove_filter_desc, rating80Label),
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                }
            } else null,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
        )

        // Custom Rating chip (if rating != ANY and rating != R80, e.g. R70 or R90)
        if (filters.minRating != MinRatingFilter.ANY && filters.minRating != MinRatingFilter.R80) {
            val customRatingLabel = stringResource(filters.minRating.labelRes)
            InputChip(
                selected = true,
                onClick = onRemoveMinRating,
                shape = SearchChipShape,
                colors = inputColors,
                border = searchInputChipBorder(selected = true),
                label = {
                    Text(
                        text = customRatingLabel,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.search_remove_filter_desc, customRatingLabel),
                        modifier = Modifier.size(InputChipDefaults.IconSize),
                    )
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            )
        }

        // 7. Clear all chip when multiple constraints active
        if (activeCount > 1) {
            FilterChip(
                selected = false,
                onClick = onResetFilters,
                shape = SearchChipShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    labelColor = MaterialTheme.colorScheme.error,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = false,
                    borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                    borderWidth = 1.dp,
                ),
                label = {
                    Text(
                        text = stringResource(R.string.search_filter_reset),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            )
        }
    }
}
