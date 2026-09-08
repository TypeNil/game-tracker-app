package io.github.typenil.gametracker.feature.search.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import io.github.typenil.gametracker.core.connectivity.NetworkStatus

/**
 * Pure reconnect decision: a network-recovery edge must only retry the failed Paging
 * load. Idle, Loading and NotLoading are never re-triggered by connectivity events.
 */
internal fun shouldRetryOnReconnect(loadStates: CombinedLoadStates): Boolean =
    loadStates.refresh is LoadState.Error || loadStates.append is LoadState.Error

/** True while a refresh or append transition is still in flight. */
private val CombinedLoadStates.isLoading: Boolean
    get() = refresh is LoadState.Loading || append is LoadState.Loading

/**
 * Device-network recovery is an event, not data: only a genuine Unavailable ->
 * Available transition may retry a currently failed Paging load. Both the baseline
 * and the one pending recovery intent must survive configuration recreation: the
 * ViewModel-scope cachedIn generation outlives it, and a restored composition can
 * replay the failed LoadStates a frame after its first effect pass (Loading first).
 * The first composition (Unknown baseline) is never treated as recovery.
 */
@Composable
fun SearchReconnectEffect(
    networkStatus: NetworkStatus,
    lazyItems: LazyPagingItems<*>,
) {
    var previousNetworkStatus by rememberSaveable { mutableStateOf(NetworkStatus.Unknown) }
    var pendingRecoveryRetry by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(networkStatus, lazyItems.loadState.refresh, lazyItems.loadState.append) {
        val loadStates = lazyItems.loadState
        val recoveredNow = previousNetworkStatus == NetworkStatus.Unavailable &&
            networkStatus == NetworkStatus.Available
        previousNetworkStatus = networkStatus
        if (recoveredNow) {
            pendingRecoveryRetry = true
        }
        when {
            networkStatus != NetworkStatus.Available -> pendingRecoveryRetry = false
            pendingRecoveryRetry && shouldRetryOnReconnect(loadStates) -> {
                lazyItems.retry()
                pendingRecoveryRetry = false
            }
            // Settled healthy without ever needing the retry: drop the intent. While any
            // load is still in flight the intent stays pending until it resolves.
            pendingRecoveryRetry && !loadStates.isLoading -> pendingRecoveryRetry = false
        }
    }
}
