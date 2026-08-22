package io.github.typenil.gametracker.feature.details.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryStatus

private const val MAX_NOTES_LENGTH = 500

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

    var selectedStatus by remember(initialEntry) {
        mutableStateOf(initialEntry?.status ?: LibraryStatus.WISHLIST)
    }
    var selectedRating by remember(initialEntry) {
        mutableStateOf(initialEntry?.userRating)
    }
    var hoursPlayed by remember(initialEntry) {
        mutableIntStateOf(initialEntry?.hoursPlayed ?: 0)
    }
    var isFavorite by remember(initialEntry) {
        mutableStateOf(initialEntry?.isFavorite ?: false)
    }
    var notes by remember(initialEntry) {
        mutableStateOf(initialEntry?.userNotes ?: "")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (initialEntry != null) "Редактировать в библиотеке" else "Добавить в библиотеку",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Status selector
            Text(
                text = "Статус",
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
                    label = "В планах",
                    selected = selectedStatus == LibraryStatus.WISHLIST,
                    onClick = { selectedStatus = LibraryStatus.WISHLIST }
                )
                StatusChip(
                    label = "Играю",
                    selected = selectedStatus == LibraryStatus.PLAYING,
                    onClick = { selectedStatus = LibraryStatus.PLAYING }
                )
                StatusChip(
                    label = "Пройдено",
                    selected = selectedStatus == LibraryStatus.COMPLETED,
                    onClick = { selectedStatus = LibraryStatus.COMPLETED }
                )
                StatusChip(
                    label = "Заброшено",
                    selected = selectedStatus == LibraryStatus.DROPPED,
                    onClick = { selectedStatus = LibraryStatus.DROPPED }
                )
                StatusChip(
                    label = "Не интересует",
                    selected = selectedStatus == LibraryStatus.NOT_INTERESTED,
                    onClick = { selectedStatus = LibraryStatus.NOT_INTERESTED }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. User Rating (1-10)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Моя оценка (1–10)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                if (selectedRating != null) {
                    TextButton(onClick = { selectedRating = null }) {
                        Text("Очистить", style = MaterialTheme.typography.bodySmall)
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
                for (rating in 1..10) {
                    val isSelected = selectedRating == rating
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedRating = if (isSelected) null else rating
                        },
                        label = {
                            Text(
                                text = "$rating",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Hours Played
            Text(
                text = "Наиграно часов",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { if (hoursPlayed > 0) hoursPlayed-- },
                    enabled = hoursPlayed > 0
                ) {
                    Icon(imageVector = Icons.Default.Remove, contentDescription = "Уменьшить часы")
                }
                Text(
                    text = "$hoursPlayed ч.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                OutlinedButton(
                    onClick = { hoursPlayed++ }
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Увеличить часы")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Favorite Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "В избранном",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                Switch(
                    checked = isFavorite,
                    onCheckedChange = { isFavorite = it }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { input ->
                    if (input.codePointCount(0, input.length) <= MAX_NOTES_LENGTH) {
                        notes = input
                    }
                },
                label = { Text("Личные заметки") },
                supportingText = {
                    Text(
                        text = "${notes.codePointCount(0, notes.length)} / $MAX_NOTES_LENGTH",
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 6. Action Buttons
            Button(
                onClick = {
                    onSave(selectedStatus, selectedRating, hoursPlayed, notes, isFavorite)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сохранить")
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
                    Text("Удалить из библиотеки")
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
