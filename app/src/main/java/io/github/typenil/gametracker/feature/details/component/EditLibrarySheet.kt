package io.github.typenil.gametracker.feature.details.component

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.component.contentColor
import io.github.typenil.gametracker.core.designsystem.component.displayNameRes
import io.github.typenil.gametracker.core.designsystem.component.leadingIcon
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryStatus
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val MAX_NOTES_LENGTH = 1000
private const val MAX_HOURS = 99999
private val RATING_RANGE = 1..10
private val QUICK_HOURS_OFFSETS = listOf(1, 2, 5)
private const val SHEET_MAX_HEIGHT_FRACTION = 0.94f

private fun Modifier.maxHeightFraction(fraction: Float): Modifier =
    this.then(
        Modifier.layout { measurable, constraints ->
            val cappedMax = (constraints.maxHeight * fraction)
                .roundToInt()
                .coerceAtLeast(constraints.minHeight)
            val placeable = measurable.measure(constraints.copy(maxHeight = cappedMax))
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLibrarySheet(
    initialEntry: LibraryEntry?,
    onDismiss: () -> Unit,
    onSave: (status: LibraryStatus, rating: Int?, hours: Int, notes: String?, isFavorite: Boolean) -> Unit,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    actionsEnabled: Boolean = true,
) {
    var showConfirmDelete by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        modifier = modifier,
    ) {
        EditLibrarySheetContent(
            initialEntry = initialEntry,
            onDismiss = onDismiss,
            onSave = onSave,
            onDeleteClick = if (onRemove != null && initialEntry != null) {
                { showConfirmDelete = true }
            } else {
                null
            },
            actionsEnabled = actionsEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .maxHeightFraction(SHEET_MAX_HEIGHT_FRACTION),
        )
    }

    if (showConfirmDelete && onRemove != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = {
                Text(
                    text = stringResource(R.string.library_remove_confirm_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.library_remove_confirm_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDelete = false
                        onRemove()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    enabled = actionsEnabled,
                ) {
                    Text(stringResource(R.string.library_remove_confirm_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDelete = false },
                ) {
                    Text(stringResource(R.string.library_cancel))
                }
            },
        )
    }
}

@Composable
fun EditLibrarySheetContent(
    initialEntry: LibraryEntry?,
    onDismiss: () -> Unit,
    onSave: (status: LibraryStatus, rating: Int?, hours: Int, notes: String?, isFavorite: Boolean) -> Unit,
    onDeleteClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actionsEnabled: Boolean = true,
) {
    val isNewEntry = initialEntry == null
    val entryId = initialEntry?.gameId

    var selectedStatus by rememberSaveable(entryId) {
        mutableStateOf(initialEntry?.status ?: LibraryStatus.PLAYING)
    }
    var rating by rememberSaveable(entryId) {
        mutableStateOf<Int?>(initialEntry?.userRating)
    }
    var hours by rememberSaveable(entryId) {
        mutableIntStateOf(initialEntry?.hoursPlayed ?: 0)
    }
    var notes by rememberSaveable(entryId) {
        mutableStateOf(initialEntry?.userNotes ?: "")
    }
    var isFavorite by rememberSaveable(entryId) {
        mutableStateOf(initialEntry?.isFavorite ?: false)
    }

    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val hoursRequester = remember { BringIntoViewRequester() }
    val notesRequester = remember { BringIntoViewRequester() }

    Column(modifier = modifier) {
        // 1. Fixed Header (outside scroll, instant swipe-down target)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GtDimens.Gutter, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (isNewEntry) Icons.Filled.Bookmark else Icons.Filled.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(
                        if (isNewEntry) R.string.library_add_to_library else R.string.library_edit_entry_title
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.library_cancel),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 2. Scrollable form body
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(scrollState)
                .padding(horizontal = GtDimens.Gutter, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Section 1: Status chips
            StatusSelectionSection(
                selectedStatus = selectedStatus,
                onStatusSelected = { status ->
                    if (selectedStatus != status) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedStatus = status
                    }
                },
            )

            // Section 2: Rating (1-10)
            RatingSection(
                rating = rating,
                onRatingSelected = { newRating ->
                    rating = newRating
                },
            )

            // Section 3: Hours Played (Conditional on status: PLAYING, COMPLETED, DROPPED)
            val supportsHours = selectedStatus in setOf(
                LibraryStatus.PLAYING,
                LibraryStatus.COMPLETED,
                LibraryStatus.DROPPED,
            )
            AnimatedVisibility(
                visible = supportsHours,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .bringIntoViewRequester(hoursRequester)
                        .onFocusEvent { state ->
                            if (state.isFocused) {
                                coroutineScope.launch {
                                    hoursRequester.bringIntoView()
                                }
                            }
                        },
                ) {
                    HoursPlayedSection(
                        hours = hours,
                        onHoursChange = { newHours ->
                            val clamped = newHours.coerceIn(0, MAX_HOURS)
                            if (hours != clamped) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                hours = clamped
                            }
                        },
                    )
                }
            }

            // Retained progress notice when hours > 0 but status hidden
            if (!supportsHours && hours > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.library_retained_hours, hours),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Section 4: Favorite toggle
            FavoriteToggleSection(
                isFavorite = isFavorite,
                onFavoriteChange = { newFavorite ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    isFavorite = newFavorite
                },
            )

            // Section 5: Personal Notes
            Column(
                modifier = Modifier
                    .bringIntoViewRequester(notesRequester)
                    .onFocusEvent { state ->
                        if (state.isFocused) {
                            coroutineScope.launch {
                                notesRequester.bringIntoView()
                            }
                        }
                    },
            ) {
                PersonalNotesSection(
                    notes = notes,
                    onNotesChange = { newNotes ->
                        if (newNotes.length <= MAX_NOTES_LENGTH) {
                            notes = newNotes
                        }
                    },
                    onDone = {
                        focusManager.clearFocus()
                    },
                )
            }
        }

        // Sticky Bottom Actions Footer
        StickyActionFooter(
            isNewEntry = isNewEntry,
            actionsEnabled = actionsEnabled,
            onSaveClick = {
                onSave(
                    selectedStatus,
                    rating,
                    hours,
                    notes.trim().ifEmpty { null },
                    isFavorite,
                )
            },
            onDeleteClick = onDeleteClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusSelectionSection(
    selectedStatus: LibraryStatus,
    onStatusSelected: (LibraryStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.library_status_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // 0dp: chips carry a standard 48dp minimum interactive size, so the
            // visible pill spacing already comes from the touch-target insets.
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            LibraryStatus.entries.forEach { status ->
                val isSelected = status == selectedStatus
                val statusColor = status.contentColor()

                FilterChip(
                    selected = isSelected,
                    onClick = { onStatusSelected(status) },
                    label = {
                        Text(
                            text = stringResource(status.displayNameRes()),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = status.leadingIcon(),
                            contentDescription = null,
                            tint = if (isSelected) statusColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = statusColor.copy(alpha = 0.15f),
                        selectedLabelColor = statusColor,
                        selectedLeadingIconColor = statusColor,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = statusColor,
                        borderWidth = if (isSelected) 1.5.dp else 1.dp,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }
    }
}

@Composable
private fun RatingSection(
    rating: Int?,
    onRatingSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    val haptic = LocalHapticFeedback.current
    val currentRating by rememberUpdatedState(rating)
    val currentOnRatingSelected by rememberUpdatedState(onRatingSelected)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.library_my_rating),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (rating != null) {
                    val activePalette = getRatingTierPalette(rating)
                    Surface(
                        color = activePalette.activeBg,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = "$rating/10",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = activePalette.onActive,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            if (rating != null) {
                TextButton(
                    onClick = { currentOnRatingSelected(null) },
                ) {
                    Text(
                        text = stringResource(R.string.library_clear_rating),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .selectableGroup()
                .pointerInput(layoutDirection) {
                    detectRatingScrub(
                        layoutDirection = layoutDirection,
                        onPreview = { previewRating ->
                            if (previewRating != currentRating) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnRatingSelected(previewRating)
                            }
                        },
                        onTapToggle = { tappedRating ->
                            val next = if (tappedRating == currentRating) null else tappedRating
                            if (next != currentRating) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            currentOnRatingSelected(next)
                        },
                    )
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RATING_RANGE.forEach { value ->
                val isSelected = rating == value
                val isBelow = rating != null && value < rating
                val palette = getRatingTierPalette(value)
                val cd = stringResource(R.string.library_rating_format, value)
                val stateDesc = stringResource(
                    if (isSelected) R.string.library_rating_selected else R.string.library_rating_not_selected
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when {
                                isSelected -> palette.activeBg
                                isBelow -> palette.containerBg
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        )
                        .semantics {
                            contentDescription = cd
                            role = Role.RadioButton
                            selected = isSelected
                            stateDescription = stateDesc
                            onClick {
                                val next = if (isSelected) null else value
                                if (next != currentRating) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                currentOnRatingSelected(next)
                                true
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = value.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = when {
                            isSelected -> palette.onActive
                            isBelow -> palette.onContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

private data class RatingTierPalette(
    val activeBg: Color,
    val onActive: Color,
    val containerBg: Color,
    val onContainer: Color,
)

@Suppress("MagicNumber")
private fun getRatingTierPalette(value: Int): RatingTierPalette {
    return when (value) {
        in 1..3 -> RatingTierPalette(
            activeBg = Color(0xFFE53935),
            onActive = Color.White,
            containerBg = Color(0xFFE53935).copy(alpha = 0.28f),
            onContainer = Color(0xFFFFB4AB),
        )
        in 4..5 -> RatingTierPalette(
            activeBg = Color(0xFFFB8C00),
            onActive = Color.White,
            containerBg = Color(0xFFFB8C00).copy(alpha = 0.28f),
            onContainer = Color(0xFFFFCC80),
        )
        in 6..7 -> RatingTierPalette(
            activeBg = Color(0xFF7CB342),
            onActive = Color.White,
            containerBg = Color(0xFF7CB342).copy(alpha = 0.28f),
            onContainer = Color(0xFFDCEDC8),
        )
        in 8..9 -> RatingTierPalette(
            activeBg = Color(0xFF2E7D32),
            onActive = Color.White,
            containerBg = Color(0xFF2E7D32).copy(alpha = 0.28f),
            onContainer = Color(0xFFA5D6A7),
        )
        else -> RatingTierPalette(
            activeBg = Color(0xFF00C853),
            onActive = Color.White,
            containerBg = Color(0xFF00C853).copy(alpha = 0.32f),
            onContainer = Color(0xFFB9F6CA),
        )
    }
}

private suspend fun PointerInputScope.detectRatingScrub(
    layoutDirection: LayoutDirection,
    onPreview: (Int) -> Unit,
    onTapToggle: (Int) -> Unit,
) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var dragging = false
        var finished = false
        while (!finished) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val width = size.width.toFloat().coerceAtLeast(1f)
            val rawX = change.position.x.coerceIn(0f, width)
            val x = if (layoutDirection == LayoutDirection.Ltr) rawX else width - rawX
            val segment = ((x / width) * RATING_RANGE.count().toFloat()).toInt().coerceIn(0, RATING_RANGE.count() - 1) + 1
            val delta = change.position - down.position
            when {
                !change.pressed -> {
                    if (dragging) {
                        onPreview(segment)
                    } else {
                        onTapToggle(segment)
                    }
                    finished = true
                }
                !dragging && kotlin.math.abs(delta.y) > slop &&
                    kotlin.math.abs(delta.y) > kotlin.math.abs(delta.x) -> {
                    // Vertical slop won: allow parent verticalScroll / sheet to consume
                    return@awaitEachGesture
                }
                !dragging && kotlin.math.abs(delta.x) > slop &&
                    kotlin.math.abs(delta.x) > kotlin.math.abs(delta.y) -> {
                    dragging = true
                    change.consume()
                    onPreview(segment)
                }
                dragging -> {
                    change.consume()
                    onPreview(segment)
                }
            }
        }
    }
}

@Composable
private fun HoursPlayedSection(
    hours: Int,
    onHoursChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var rawText by remember(hours) { mutableStateOf(hours.toString()) }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.library_hours_played),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Decrement button
            FilledTonalIconButton(
                onClick = { onHoursChange(hours - 1) },
                enabled = hours > 0,
                shape = CircleShape,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = stringResource(R.string.library_hours_decrement_desc),
                )
            }

            // Numeric input field
            OutlinedTextField(
                value = rawText,
                onValueChange = { input ->
                    if (input.all { it.isDigit() }) {
                        rawText = input
                        val parsed = input.toIntOrNull()
                        if (parsed != null) {
                            onHoursChange(parsed)
                        } else if (input.isEmpty()) {
                            onHoursChange(0)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            )

            // Increment button
            FilledTonalIconButton(
                onClick = { onHoursChange(hours + 1) },
                enabled = hours < MAX_HOURS,
                shape = CircleShape,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.library_hours_increment_desc),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick add chips (+1h, +2h, +5h)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QUICK_HOURS_OFFSETS.forEach { offset ->
                val labelRes = when (offset) {
                    1 -> R.string.library_quick_add_1h
                    2 -> R.string.library_quick_add_2h
                    5 -> R.string.library_quick_add_5h
                    else -> R.string.library_quick_add_1h
                }
                FilledTonalButton(
                    onClick = { onHoursChange(hours + offset) },
                    enabled = hours + offset <= MAX_HOURS,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteToggleSection(
    isFavorite: Boolean,
    onFavoriteChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val heartColor = Color(0xFFE91E63)

    OutlinedCard(
        onClick = { onFavoriteChange(!isFavorite) },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isFavorite) 1.5.dp else 1.dp,
            color = if (isFavorite) heartColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isFavorite) heartColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isFavorite) heartColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isFavorite) heartColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Column {
                    Text(
                        text = stringResource(R.string.library_favorite),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.library_favorite_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Switch(
                checked = isFavorite,
                onCheckedChange = onFavoriteChange,
            )
        }
    }
}

@Composable
private fun PersonalNotesSection(
    notes: String,
    onNotesChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.library_personal_notes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${notes.length}/$MAX_NOTES_LENGTH",
                style = MaterialTheme.typography.labelSmall,
                color = if (notes.length >= MAX_NOTES_LENGTH) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp, max = 130.dp),
            placeholder = {
                Text(
                    text = stringResource(R.string.library_personal_notes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onDone() },
            ),
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
    }
}

@Composable
private fun StickyActionFooter(
    isNewEntry: Boolean,
    actionsEnabled: Boolean,
    onSaveClick: () -> Unit,
    onDeleteClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GtDimens.Gutter, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onDeleteClick != null) {
                    IconButton(
                        onClick = onDeleteClick,
                        enabled = actionsEnabled,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.library_remove),
                        )
                    }
                }

                Button(
                    onClick = onSaveClick,
                    enabled = actionsEnabled,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text(
                        text = stringResource(if (isNewEntry) R.string.library_add_to_library else R.string.library_save),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Preview(name = "Light - New Entry", showBackground = true)
@Preview(name = "Dark - New Entry", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun EditLibrarySheetContentNewPreview() {
    GameTrackerTheme {
        Surface {
            EditLibrarySheetContent(
                initialEntry = null,
                onDismiss = {},
                onSave = { _, _, _, _, _ -> },
                onDeleteClick = null,
            )
        }
    }
}

@Preview(name = "Light - Existing Entry", showBackground = true)
@Preview(name = "Dark - Existing Entry", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun EditLibrarySheetContentExistingPreview() {
    GameTrackerTheme {
        Surface {
            EditLibrarySheetContent(
                initialEntry = LibraryEntry(
                    gameId = 1L,
                    status = LibraryStatus.PLAYING,
                    userRating = 9,
                    userNotes = "Great game, loving the combat!",
                    isFavorite = true,
                    hoursPlayed = 42,
                    addedAtEpochSeconds = 1700000000L,
                    updatedAtEpochSeconds = 1700000000L,
                ),
                onDismiss = {},
                onSave = { _, _, _, _, _ -> },
                onDeleteClick = {},
            )
        }
    }
}
