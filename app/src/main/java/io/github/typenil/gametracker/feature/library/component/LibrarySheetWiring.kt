package io.github.typenil.gametracker.feature.library.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.feature.details.component.EditLibrarySheet
import io.github.typenil.gametracker.feature.library.HoursSaveState
import io.github.typenil.gametracker.feature.library.LibraryMutationState

@Composable
fun LibraryHoursDialogWiring(
    allGames: List<LibraryGame>,
    editingHoursGameId: Long?,
    hoursSaveState: HoursSaveState,
    onDismiss: () -> Unit,
    onHoursUpdated: (Long, Int) -> Unit,
) {
    val targetGame = allGames.firstOrNull { it.game.id == editingHoursGameId } ?: return
    val isSaving = hoursSaveState is HoursSaveState.Saving &&
        hoursSaveState.gameId == targetGame.game.id
    QuickHoursDialog(
        gameName = targetGame.game.name,
        initialHours = targetGame.entry.hoursPlayed,
        coverUrl = targetGame.game.coverUrl,
        onDismissRequest = {
            if (!isSaving) {
                onDismiss()
            }
        },
        onConfirm = { hours ->
            onHoursUpdated(targetGame.game.id, hours)
        },
        isSaving = isSaving,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryEditSheetWiring(
    allGames: List<LibraryGame>,
    editingGameId: Long?,
    libraryMutationState: LibraryMutationState,
    onDismiss: () -> Unit,
    onSaveLibraryEntry: (
        gameId: Long,
        status: LibraryStatus,
        rating: Int?,
        hours: Int,
        notes: String?,
        isFavorite: Boolean,
    ) -> Unit,
    onRemoveFromLibrary: (Long) -> Unit,
) {
    val editingEntry = allGames.firstOrNull { it.game.id == editingGameId }?.entry ?: return
    val isMutating = libraryMutationState is LibraryMutationState.Saving
    val currentIsMutating = rememberUpdatedState(isMutating)
    val confirmSheetValueChange = remember {
        { target: SheetValue ->
            target != SheetValue.Hidden || !currentIsMutating.value
        }
    }
    val editSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = confirmSheetValueChange,
    )

    EditLibrarySheet(
        initialEntry = editingEntry,
        sheetState = editSheetState,
        onDismiss = {
            if (!isMutating) {
                onDismiss()
            }
        },
        onSave = { status, rating, hours, notes, favorite ->
            onSaveLibraryEntry(
                editingEntry.gameId,
                status,
                rating,
                hours,
                notes,
                favorite,
            )
        },
        onRemove = {
            onRemoveFromLibrary(editingEntry.gameId)
        },
        actionsEnabled = !isMutating,
    )
}
