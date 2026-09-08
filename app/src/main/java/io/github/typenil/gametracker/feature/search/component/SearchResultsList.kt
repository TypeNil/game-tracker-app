package io.github.typenil.gametracker.feature.search.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.component.GameCard
import io.github.typenil.gametracker.core.designsystem.component.SkeletonCardRow
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibrarySnapshot

/** Distinct LazyColumn slot type so a placeholder never rebinds into a loaded card. */
private const val GAME_ROW_CONTENT_TYPE = "game"

/**
 * Lazy column of search result games with result count header, refresh error banner,
 * placeholder rows, and append loading/error states.
 */
@Composable
internal fun SearchResultsList(
    games: LazyPagingItems<Game>,
    librarySnapshot: LibrarySnapshot,
    onGameClick: (Long) -> Unit,
    onLibraryAction: (Game) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = librarySnapshot as? LibrarySnapshot.Ready
    val refreshError = games.loadState.refresh as? LoadState.Error
    val appendState = games.loadState.append
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(GtDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (refreshError != null) {
            // Cached rows remain the SSOT while the background refresh failed: banner, not
            // full-screen replacement. games.retry() re-runs the failed mediator refresh and
            // always hits the network (initialize/TTL is not re-consulted on retry).
            item(key = "refresh_error_banner") {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.error_refresh_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { games.retry() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.retry_button),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
        item(key = "results_count_header") {
            val count = games.itemCount
            val finished = appendState is LoadState.NotLoading &&
                appendState.endOfPaginationReached &&
                games.loadState.refresh !is LoadState.Loading
            Text(
                text = if (!finished) {
                    stringResource(R.string.search_results_count_partial_format, count)
                } else {
                    // A terminal state can mean a truly exhausted result set or the BFF
                    // offset ceiling (1000): LazyPagingItems cannot tell the two apart, so
                    // the copy stays neutral about how many matches the server knows.
                    pluralStringResource(R.plurals.search_results_loaded_count, count, count)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        items(
            count = games.itemCount,
            key = games.itemKey { it.id },
            contentType = games.itemContentType { GAME_ROW_CONTENT_TYPE },
        ) { index ->
            // enablePlaceholders = true: a null slot is an unloaded local position whose count
            // is authoritative (dense window, committed-count invariant). Rendering a real
            // fixed-geometry row (minimum cover geometry shared with GameCard) keeps the list
            // space stable across the anchor re-generation that Room invalidation causes after
            // each mediator append; skipping nulls would collapse it and re-expose the first
            // cards. games[index] still submits the hint.
            val game = games[index]
            if (game == null) {
                SkeletonCardRow()
            } else {
                GameCard(
                    game = game,
                    onClick = { onGameClick(game.id) },
                    libraryStatus = ready?.entries?.get(game.id)?.status,
                    onLibraryAction = if (ready != null) onLibraryAction else null,
                )
            }
        }

        when (appendState) {
            is LoadState.Loading -> {
                item(key = "load_more") {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }
            }
            is LoadState.Error -> {
                item(key = "load_more") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.error_load_more_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = { games.retry() }) {
                            Text(stringResource(R.string.retry_button))
                        }
                    }
                }
            }
            else -> Unit
        }
    }
}
