package io.github.typenil.gametracker.feature.search.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.component.PlatformFamily
import io.github.typenil.gametracker.core.designsystem.component.formatGenreTag
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import io.github.typenil.gametracker.feature.search.MinRatingFilter
import io.github.typenil.gametracker.feature.search.ReleaseYearFilter
import io.github.typenil.gametracker.feature.search.SearchFilters
import io.github.typenil.gametracker.feature.search.SearchSortOption

/**
 * Horizontal scrollable filter and sort chip bar displayed below the search TextField.
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
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val activeCount = filters.activeConstraintsCount()

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
            label = {
                Text(
                    text = if (activeCount > 0) {
                        stringResource(R.string.search_filters_count_format, activeCount)
                    } else {
                        stringResource(R.string.search_filters_title)
                    },
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

        // 2. Active Sort indicator chip
        if (filters.sort != SearchSortOption.RELEVANCE) {
            FilterChip(
                selected = true,
                onClick = onOpenFilterSheet,
                label = { Text(stringResource(filters.sort.labelRes)) },
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

        // 3. Removable Genre chips
        for (genre in filters.genres) {
            val genreDisplay = formatGenreTag(genre)
            InputChip(
                selected = true,
                onClick = { onRemoveGenre(genre) },
                label = { Text(genreDisplay) },
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

        // 4. Removable Platform chips
        for (platform in filters.platforms) {
            val platformLabel = stringResource(platform.labelRes)
            InputChip(
                selected = true,
                onClick = { onRemovePlatform(platform) },
                label = { Text(platformLabel) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(platform.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(InputChipDefaults.IconSize),
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.search_remove_filter_desc, platformLabel),
                        modifier = Modifier.size(InputChipDefaults.IconSize),
                    )
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            )
        }

        // 5. Removable Release Year chip
        if (filters.releaseYear != ReleaseYearFilter.ALL) {
            val yearLabel = stringResource(filters.releaseYear.labelRes)
            InputChip(
                selected = true,
                onClick = onRemoveReleaseYear,
                label = { Text(yearLabel) },
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

        // 6. Removable Min Rating chip
        if (filters.minRating != MinRatingFilter.ANY) {
            val ratingLabel = stringResource(filters.minRating.labelRes)
            InputChip(
                selected = true,
                onClick = onRemoveMinRating,
                label = { Text(ratingLabel) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.search_remove_filter_desc, ratingLabel),
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
                label = {
                    Text(
                        text = stringResource(R.string.search_filter_reset),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            )
        }
    }
}
