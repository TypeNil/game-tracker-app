package io.github.typenil.gametracker.feature.search.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.component.FeedSkeleton
import io.github.typenil.gametracker.core.designsystem.component.errorMessage
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppErrorException

/**
 * Paging only carries [Throwable]; the data boundary (GamesRemoteMediator) already classified it
 * into an [AppError] via AppErrorException. Presentation must not import transport-specific
 * mapping, so anything unrecognized degrades to UnknownError.
 */
internal fun Throwable.toPresentedAppError(): AppError =
    (this as? AppErrorException)?.error ?: AppError.UnknownError(this)

/**
 * Full-screen skeleton loading state when search results are being fetched initially.
 */
@Composable
fun SearchLoadingState(modifier: Modifier = Modifier) {
    FeedSkeleton(
        label = stringResource(R.string.search_loading_games),
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * Empty results state offering clear query or reset filters actions.
 */
@Composable
fun SearchEmptyState(
    query: String,
    hasConstraints: Boolean,
    onClearQuery: () -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(GtDimens.Empty),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (hasConstraints && query.isBlank()) {
                Text(
                    text = stringResource(R.string.search_empty_filters_title),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.search_empty_filters_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onResetFilters) {
                    Text(stringResource(R.string.search_empty_filters_action))
                }
            } else {
                Text(
                    text = stringResource(R.string.search_empty_results_format, query),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = if (hasConstraints) onResetFilters else onClearQuery) {
                    Text(
                        if (hasConstraints) {
                            stringResource(R.string.search_empty_filters_action)
                        } else {
                            stringResource(R.string.search_empty_action)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Full-screen error state with retry button.
 */
@Composable
fun SearchErrorState(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(GtDimens.Empty),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error.errorMessage(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = stringResource(R.string.retry_button))
            }
        }
    }
}
