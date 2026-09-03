package io.github.typenil.gametracker.feature.library.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.component.GAME_COVER_ASPECT_RATIO

private const val MAX_HOURS_DIGITS = 6
private const val MAX_HOURS_VALUE = 999_999
private const val QUICK_ADD_1 = 1
private const val QUICK_ADD_2 = 2
private const val QUICK_ADD_5 = 5

const val QUICK_HOURS_DIALOG_TEST_TAG = "quick_hours_dialog"
const val QUICK_HOURS_INPUT_TEST_TAG = "quick_hours_input"
const val QUICK_HOURS_SAVE_TEST_TAG = "quick_hours_save"

/**
 * Compact dialog for quickly logging hours played directly from a library card.
 */
@Composable
fun QuickHoursDialog(
    gameName: String,
    initialHours: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
    coverUrl: String? = null,
) {
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var hoursText by rememberSaveable { mutableStateOf(initialHours.toString()) }
    val parsedHours = hoursText.toIntOrNull()
    val currentHours = parsedHours ?: 0
    val isHoursValid = parsedHours != null && parsedHours in 0..MAX_HOURS_VALUE

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismissRequest() },
        modifier = modifier.testTag(QUICK_HOURS_DIALOG_TEST_TAG),
        icon = {
            Icon(
                imageVector = Icons.Outlined.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.library_log_hours),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GameContextHeader(
                    gameName = gameName,
                    initialHours = initialHours,
                    coverUrl = coverUrl,
                )

                HoursHeroStepper(
                    hoursText = hoursText,
                    onHoursTextChange = { hoursText = it },
                    currentHours = currentHours,
                    initialHours = initialHours,
                    isHoursValid = isHoursValid,
                    isSaving = isSaving,
                    onConfirm = { parsedHours?.let(onConfirm) },
                    haptic = haptic,
                    keyboardController = keyboardController,
                )

                QuickAddChips(
                    isSaving = isSaving,
                    onAddHours = { delta ->
                        val next = (currentHours + delta).coerceAtMost(MAX_HOURS_VALUE)
                        hoursText = next.toString()
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    parsedHours?.let(onConfirm)
                },
                enabled = isHoursValid && !isSaving,
                modifier = Modifier.testTag(QUICK_HOURS_SAVE_TEST_TAG),
                colors = if (isSaving) {
                    ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.primary,
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.library_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = !isSaving,
            ) {
                Text(stringResource(R.string.library_cancel))
            }
        },
    )
}

@Composable
private fun GameContextHeader(
    gameName: String,
    initialHours: Int,
    coverUrl: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!coverUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .aspectRatio(GAME_COVER_ASPECT_RATIO)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = gameName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.library_current_hours, initialHours),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HoursHeroStepper(
    hoursText: String,
    onHoursTextChange: (String) -> Unit,
    currentHours: Int,
    initialHours: Int,
    isHoursValid: Boolean,
    isSaving: Boolean,
    onConfirm: () -> Unit,
    haptic: HapticFeedback,
    keyboardController: SoftwareKeyboardController?,
    modifier: Modifier = Modifier,
) {
    val hoursFieldDesc = stringResource(R.string.library_hours_played)
    val hoursInvalidError = stringResource(R.string.library_log_hours)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FilledTonalIconButton(
                    onClick = {
                        val decremented = (currentHours - 1).coerceAtLeast(0)
                        onHoursTextChange(decremented.toString())
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    enabled = currentHours > 0 && !isSaving,
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = stringResource(R.string.library_hours_decrement_desc),
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    BasicTextField(
                        value = hoursText,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.all { it.isDigit() }) {
                                onHoursTextChange(input.take(MAX_HOURS_DIGITS))
                            }
                        },
                        enabled = !isSaving,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                if (isHoursValid && !isSaving) {
                                    onConfirm()
                                }
                            },
                        ),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineLarge.copy(
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = if (isHoursValid) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp),
                            ) {
                                innerTextField()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag(QUICK_HOURS_INPUT_TEST_TAG)
                            .semantics {
                                contentDescription = hoursFieldDesc
                                if (!isHoursValid) error(hoursInvalidError)
                            },
                    )
                    Text(
                        text = stringResource(R.string.library_hours_unit),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        val incremented = (currentHours + 1).coerceAtMost(MAX_HOURS_VALUE)
                        onHoursTextChange(incremented.toString())
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    enabled = currentHours < MAX_HOURS_VALUE && !isSaving,
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.library_hours_increment_desc),
                    )
                }
            }

            val diff = currentHours - initialHours
            Box(
                modifier = Modifier.height(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                this@Column.AnimatedVisibility(
                    visible = isHoursValid && diff != 0,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    val isPositive = diff > 0
                    val badgeBg = if (isPositive) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    }
                    val badgeFg = if (isPositive) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = badgeBg,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Text(
                            text = if (isPositive) {
                                stringResource(R.string.library_session_delta_plus, diff)
                            } else {
                                stringResource(R.string.library_session_delta_minus, diff)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAddChips(
    isSaving: Boolean,
    onAddHours: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SuggestionChip(
            onClick = { onAddHours(QUICK_ADD_1) },
            label = {
                Text(
                    text = stringResource(R.string.library_quick_add_1h),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            },
            enabled = !isSaving,
            modifier = Modifier.weight(1f),
        )
        SuggestionChip(
            onClick = { onAddHours(QUICK_ADD_2) },
            label = {
                Text(
                    text = stringResource(R.string.library_quick_add_2h),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            },
            enabled = !isSaving,
            modifier = Modifier.weight(1f),
        )
        SuggestionChip(
            onClick = { onAddHours(QUICK_ADD_5) },
            label = {
                Text(
                    text = stringResource(R.string.library_quick_add_5h),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            },
            enabled = !isSaving,
            modifier = Modifier.weight(1f),
        )
    }
}
