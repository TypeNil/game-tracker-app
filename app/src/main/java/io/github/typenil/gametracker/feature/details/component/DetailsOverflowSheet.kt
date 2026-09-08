package io.github.typenil.gametracker.feature.details.component

import androidx.compose.runtime.Composable
import io.github.typenil.gametracker.core.model.GameDetails

internal enum class DetailsOverflowSheet {
    None,
    Platforms,
    Tags,
    GameModes,
}

@Composable
internal fun DetailsOverflowSheets(
    overflowSheet: DetailsOverflowSheet,
    game: GameDetails?,
    onDismiss: () -> Unit,
) {
    when (overflowSheet) {
        DetailsOverflowSheet.None -> Unit
        DetailsOverflowSheet.Platforms -> {
            game?.let { currentGame ->
                PlatformsBottomSheet(
                    platforms = currentGame.platforms,
                    releaseDates = currentGame.releaseDates,
                    onDismiss = onDismiss,
                )
            }
        }
        DetailsOverflowSheet.Tags -> {
            game?.let { currentGame ->
                TagsBottomSheet(
                    genres = currentGame.genres,
                    themes = currentGame.themes,
                    onDismiss = onDismiss,
                )
            }
        }
        DetailsOverflowSheet.GameModes -> {
            game?.let { currentGame ->
                GameModesBottomSheet(
                    gameModes = currentGame.gameModes,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}
