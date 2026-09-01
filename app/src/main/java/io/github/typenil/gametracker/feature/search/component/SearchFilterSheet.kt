package io.github.typenil.gametracker.feature.search.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.component.PlatformFamily
import io.github.typenil.gametracker.core.designsystem.component.formatGenreTag
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import io.github.typenil.gametracker.feature.search.MinRatingFilter
import io.github.typenil.gametracker.feature.search.ReleaseYearFilter
import io.github.typenil.gametracker.feature.search.SearchFilters
import io.github.typenil.gametracker.feature.search.SearchGenreCatalog
import io.github.typenil.gametracker.feature.search.SearchSortOption

/**
 * Modal Bottom Sheet allowing the user to configure comprehensive search filters and sort preferences.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchFilterSheet(
    initialFilters: SearchFilters,
    onDismiss: () -> Unit,
    onApply: (SearchFilters) -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    var draftFilters by rememberSaveable(stateSaver = SearchFilters.Saver) { mutableStateOf(initialFilters) }
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            // Header: Title & Reset Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GtDimens.Gutter, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.search_filters_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(
                    onClick = { draftFilters = SearchFilters.Empty },
                    enabled = draftFilters != SearchFilters.Empty,
                ) {
                    Text(text = stringResource(R.string.search_filter_reset_all))
                }
            }

            HorizontalDivider()

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
                    .padding(GtDimens.Gutter),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Section 1: Sort By
                FilterSection(title = stringResource(R.string.search_filter_section_sort)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (option in SearchSortOption.entries) {
                            FilterChip(
                                selected = draftFilters.sort == option,
                                onClick = { draftFilters = draftFilters.copy(sort = option) },
                                label = { Text(stringResource(option.labelRes)) },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            )
                        }
                    }
                }

                // Section 2: Platforms
                FilterSection(title = stringResource(R.string.search_filter_section_platforms)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (platform in PlatformFamily.entries) {
                            val selected = draftFilters.platforms.contains(platform)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val updated = if (selected) {
                                        draftFilters.platforms - platform
                                    } else {
                                        draftFilters.platforms + platform
                                    }
                                    draftFilters = draftFilters.copy(platforms = updated)
                                },
                                label = { Text(stringResource(platform.labelRes)) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(platform.iconRes),
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            )
                        }
                    }
                }

                // Section 3: Genres
                FilterSection(title = stringResource(R.string.search_filter_section_genres)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (genreWireName in SearchGenreCatalog.wireNames) {
                            val selected = draftFilters.genres.contains(genreWireName)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val updated = if (selected) {
                                        draftFilters.genres - genreWireName
                                    } else {
                                        draftFilters.genres + genreWireName
                                    }
                                    draftFilters = draftFilters.copy(genres = updated)
                                },
                                label = { Text(formatGenreTag(genreWireName)) },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            )
                        }
                    }
                }

                // Section 4: Release Year
                FilterSection(title = stringResource(R.string.search_filter_section_year)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (yearOption in ReleaseYearFilter.entries) {
                            FilterChip(
                                selected = draftFilters.releaseYear == yearOption,
                                onClick = { draftFilters = draftFilters.copy(releaseYear = yearOption) },
                                label = { Text(stringResource(yearOption.labelRes)) },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            )
                        }
                    }
                }

                // Section 5: Minimum Rating
                FilterSection(title = stringResource(R.string.search_filter_section_rating)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (ratingOption in MinRatingFilter.entries) {
                            FilterChip(
                                selected = draftFilters.minRating == ratingOption,
                                onClick = { draftFilters = draftFilters.copy(minRating = ratingOption) },
                                label = { Text(stringResource(ratingOption.labelRes)) },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            HorizontalDivider()

            // Sticky Bottom Footer: Apply Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(GtDimens.Gutter),
            ) {
                Button(
                    onClick = { onApply(draftFilters) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                ) {
                    val activeCount = draftFilters.activeConstraintsCount()
                    Text(
                        text = if (activeCount > 0) {
                            pluralStringResource(R.plurals.search_filter_apply_count, activeCount, activeCount)
                        } else {
                            stringResource(R.string.search_filter_apply)
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}
