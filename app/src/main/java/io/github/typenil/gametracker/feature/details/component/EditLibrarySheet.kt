package io.github.typenil.gametracker.feature.details.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_NOTES_LENGTH = 500
private const val MAX_RATING = 10
private const val MAX_HOURS_DIGITS = 6
private const val BRING_INTO_VIEW_DELAY_MS = 250L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditLibrarySheet(
    initialEntry: LibraryEntry?,
    onDismiss: () -> Unit,
    onSave: (status: LibraryStatus, rating: Int?, hours: Int, notes: String?, isFavorite: Boolean) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val notesBringIntoViewRequester = remember { BringIntoViewRequester() }

    var selectedStatus by remember(initialEntry) {
        mutableStateOf(initialEntry?.status ?: LibraryStatus.WISHLIST)
    }
    var selectedRating by remember(initialEntry) {
        mutableStateOf(initialEntry?.userRating)
    }
    var hoursText by remember(initialEntry) {
        mutableStateOf((initialEntry?.hoursPlayed ?: 0).toString())
    }
    var isFavorite by remember(initialEntry) {
        mutableStateOf(initialEntry?.isFavorite ?: false)
    }
    var notes by remember(initialEntry) {
        mutableStateOf(initialEntry?.userNotes ?: "")
    }

    val currentHours = hoursText.toIntOrNull() ?: 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(
                    if (initialEntry != null) R.string.library_edit_entry_title else R.string.library_add_to_library
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Status selector
            Text(
                text = stringResource(R.string.library_status_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusChip(
                    label = stringResource(R.string.library_status_wishlist),
                    selected = selectedStatus == LibraryStatus.WISHLIST,
                    onClick = { selectedStatus = LibraryStatus.WISHLIST }
                )
                StatusChip(
                    label = stringResource(R.string.library_status_playing),
                    selected = selectedStatus == LibraryStatus.PLAYING,
                    onClick = { selectedStatus = LibraryStatus.PLAYING }
                )
                StatusChip(
                    label = stringResource(R.string.library_status_completed),
                    selected = selectedStatus == LibraryStatus.COMPLETED,
                    onClick = { selectedStatus = LibraryStatus.COMPLETED }
                )
                StatusChip(
                    label = stringResource(R.string.library_status_dropped),
                    selected = selectedStatus == LibraryStatus.DROPPED,
                    onClick = { selectedStatus = LibraryStatus.DROPPED }
                )
                StatusChip(
                    label = stringResource(R.string.library_status_not_interested),
                    selected = selectedStatus == LibraryStatus.NOT_INTERESTED,
                    onClick = { selectedStatus = LibraryStatus.NOT_INTERESTED }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. User Rating (1-10)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.library_my_rating),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                if (selectedRating != null) {
                    TextButton(
                        onClick = { selectedRating = null },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.library_clear_rating),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (rating in 1..MAX_RATING) {
                    val isSelected = selectedRating == rating
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedRating = if (isSelected) null else rating
                        },
                        label = {
                            Text(
                                text = if (isSelected) "★ $rating" else "$rating",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Hours Played (Direct numerical input + stepper, centered)
            Text(
                text = stringResource(R.string.library_hours_played),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = {
                        val decremented = (currentHours - 1).coerceAtLeast(0)
                        hoursText = decremented.toString()
                    },
                    enabled = currentHours > 0
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = stringResource(R.string.library_hours_decrement_desc)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.all { it.isDigit() }) {
                            hoursText = input.take(MAX_HOURS_DIGITS)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.width(110.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        val incremented = currentHours + 1
                        hoursText = incremented.toString()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.library_hours_increment_desc)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Favorite Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.library_favorite),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.library_favorite_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = isFavorite,
                    onCheckedChange = { isFavorite = it }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Notes
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(notesBringIntoViewRequester)
            ) {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { input ->
                        if (input.codePointCount(0, input.length) <= MAX_NOTES_LENGTH) {
                            notes = input
                        }
                    },
                    label = { Text(stringResource(R.string.library_personal_notes)) },
                    supportingText = {
                        Text(
                            text = "${notes.codePointCount(0, notes.length)} / $MAX_NOTES_LENGTH",
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusEvent { focusState ->
                            if (focusState.isFocused) {
                                coroutineScope.launch {
                                    delay(BRING_INTO_VIEW_DELAY_MS)
                                    notesBringIntoViewRequester.bringIntoView()
                                }
                            }
                        }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 6. Action Buttons
            Button(
                onClick = {
                    onSave(selectedStatus, selectedRating, currentHours, notes, isFavorite)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.library_save))
            }

            if (initialEntry != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.library_remove))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}
