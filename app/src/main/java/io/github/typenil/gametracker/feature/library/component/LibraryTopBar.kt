package io.github.typenil.gametracker.feature.library.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.feature.library.LibrarySortOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryTopBar(
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onToggleSearchActive: (Boolean) -> Unit,
    onClearSearch: () -> Unit,
    sortOption: LibrarySortOption,
    onSortOptionSelected: (LibrarySortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    if (isSearchActive) {
        TopAppBar(
            title = {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text(stringResource(R.string.library_search_hint)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = onClearSearch) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.search_clear_desc)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            navigationIcon = {
                IconButton(onClick = { onToggleSearchActive(false) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_action_desc)
                    )
                }
            },
            modifier = modifier
        )
    } else {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.library_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                IconButton(onClick = { onToggleSearchActive(true) }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.search_action_desc)
                    )
                }
                Box {
                    IconButton(onClick = { sortMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.library_sort_menu_desc)
                        )
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        LibrarySortOption.entries.forEach { option ->
                            val isSelected = sortOption == option
                            DropdownMenuItem(
                                text = { Text(stringResource(option.labelRes)) },
                                onClick = {
                                    onSortOptionSelected(option)
                                    sortMenuExpanded = false
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            },
            modifier = modifier
        )
    }
}

@Composable
internal fun LibraryFavoritesFilter(
    filterFavoritesOnly: Boolean,
    onToggleFavoritesOnly: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = filterFavoritesOnly,
            onClick = onToggleFavoritesOnly,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = if (filterFavoritesOnly) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(16.dp)
                )
            },
            label = { Text(stringResource(R.string.library_filter_favorites)) }
        )
    }
}
