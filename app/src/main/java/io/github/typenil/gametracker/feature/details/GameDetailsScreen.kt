package io.github.typenil.gametracker.feature.details

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.model.GameVideo
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.feature.details.component.DetailsOverflowSheet
import io.github.typenil.gametracker.feature.details.component.DetailsOverflowSheets
import io.github.typenil.gametracker.feature.details.component.DetailsTopAppBar
import io.github.typenil.gametracker.feature.details.component.EditLibrarySheet
import io.github.typenil.gametracker.feature.details.component.GameDetailsContent
import io.github.typenil.gametracker.feature.details.component.GameDetailsErrorState
import io.github.typenil.gametracker.feature.details.component.rememberDetailsAppBarScrollState
import kotlinx.coroutines.launch

/**
 * Host composable wiring the [GameDetailsViewModel] into the stateless screen.
 * The gameId arrives via SavedStateHandle from the type-safe route argument,
 * not through composition parameters.
 */
@Composable
fun GameDetailsRoute(
    onGameClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GameDetailsViewModel = hiltViewModel(),
) {
    LifecycleStartEffect(viewModel) {
        viewModel.onScreenStarted()
        onStopOrDispose {
            viewModel.onScreenStopped()
        }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    GameDetailsScreen(
        uiState = uiState,
        onGameClick = onGameClick,
        onBackClick = onBackClick,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onUserMessageShown = viewModel::onUserMessageShown,
        onEditLibraryClicked = viewModel::onEditLibraryClicked,
        onDismissEditLibrary = viewModel::onDismissEditLibrary,
        onSaveLibraryEntry = viewModel::onSaveLibraryEntry,
        onRemoveFromLibrary = viewModel::onRemoveFromLibrary,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailsScreen(
    uiState: GameDetailsUiState,
    onGameClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onUserMessageShown: () -> Unit,
    onEditLibraryClicked: () -> Unit = {},
    onDismissEditLibrary: () -> Unit = {},
    onSaveLibraryEntry: (
        status: LibraryStatus,
        rating: Int?,
        hours: Int,
        notes: String?,
        isFavorite: Boolean
    ) -> Unit = { _, _, _, _, _ -> },
    onRemoveFromLibrary: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var overflowSheet by rememberSaveable { mutableStateOf(DetailsOverflowSheet.None) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val userMessage = uiState.userMessageRes?.let { stringResource(it) }
    val shareError = stringResource(R.string.details_share_error)
    val videoError = stringResource(R.string.details_video_error)

    LaunchedEffect(userMessage) {
        userMessage?.let { message ->
            snackbarHostState.showSnackbar(message = message)
            onUserMessageShown()
        }
    }

    val game = uiState.game
    val onShareClick = remember(context, game, shareError) {
        {
            game?.let { current ->
                try {
                    context.startActivity(DetailsIntents.shareIntent(current.name, current.url))
                } catch (_: ActivityNotFoundException) {
                    scope.launch { snackbarHostState.showSnackbar(shareError) }
                }
            }
            Unit
        }
    }
    val onVideoClick = remember(context, videoError) {
        { video: GameVideo ->
            try {
                context.startActivity(DetailsIntents.videoIntent(video.videoId))
            } catch (_: ActivityNotFoundException) {
                scope.launch { snackbarHostState.showSnackbar(videoError) }
            }
            Unit
        }
    }

    val scrollState = rememberDetailsAppBarScrollState()

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.navigationBars),
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            DetailsTopAppBar(
                gameName = game?.name,
                onBackClick = onBackClick,
                onShareClick = onShareClick,
                titleHandoffProgress = scrollState.titleHandoffProgress,
                appBarBgAlpha = scrollState.appBarBgAlpha,
                titleTranslationRangePx = scrollState.titleTranslationRangePx,
            )
        }
    ) { innerPadding ->
        when {
            // Full-screen skeleton renders inside the same LazyColumn as content
            // (single list state), so the first hydrated frame replaces
            // placeholders without a spinner swap or scroll-state conflict.
            uiState.error != null && game == null && !uiState.isLoading -> GameDetailsErrorState(
                error = uiState.error,
                onRetry = onRetry,
                modifier = Modifier.padding(innerPadding)
            )

            else -> GameDetailsContent(
                game = game,
                libraryEntry = uiState.libraryEntry,
                libraryLoadError = uiState.libraryLoadError,
                isLibraryLoading = uiState.isLibraryLoading,
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                onGameClick = onGameClick,
                onVideoClick = onVideoClick,
                onEditLibraryClicked = onEditLibraryClicked,
                onPlatformsClick = { overflowSheet = DetailsOverflowSheet.Platforms },
                onTagsOverflowClick = { overflowSheet = DetailsOverflowSheet.Tags },
                onGameModesClick = { overflowSheet = DetailsOverflowSheet.GameModes },
                contentTopPadding = innerPadding.calculateTopPadding(),
                lazyListState = scrollState.lazyListState,
                titleHandoffProgress = scrollState.titleHandoffProgress,
                titleTranslationRangePx = scrollState.titleTranslationRangePx,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                imageReloadToken = uiState.imageReloadToken,
            )
        }

        DetailsOverflowSheets(
            overflowSheet = overflowSheet,
            game = game,
            onDismiss = { overflowSheet = DetailsOverflowSheet.None },
        )

        if (uiState.isEditingLibrary && uiState.libraryLoadError == null) {
            EditLibrarySheet(
                initialEntry = uiState.libraryEntry,
                onDismiss = onDismissEditLibrary,
                onSave = onSaveLibraryEntry,
                onRemove = onRemoveFromLibrary,
                actionsEnabled = !uiState.isLibrarySubmitting,
            )
        }
    }
}
