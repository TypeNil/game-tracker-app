package io.github.typenil.gametracker.feature.library.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.typenil.gametracker.R

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
    var hoursText by rememberSaveable { mutableStateOf(initialHours.toString()) }
    val parsedHours = hoursText.toIntOrNull()
    val currentHours = parsedHours ?: 0
    val isHoursValid = parsedHours != null && parsedHours in 0..MAX_HOURS_VALUE

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismissRequest() },
        modifier = modifier.testTag(QUICK_HOURS_DIALOG_TEST_TAG),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.library_log_hours),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (coverUrl != null) {
                            Box(
                                modifier = Modifier
                                    .size(width = 38.dp, height = 50.dp)
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

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
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
                                    hoursText = decremented.toString()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                enabled = currentHours > 0 && !isSaving,
                                modifier = Modifier.size(44.dp),
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
                                            hoursText = input.take(MAX_HOURS_DIGITS)
                                        }
                                    },
                                    enabled = !isSaving,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done,
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (isHoursValid && !isSaving) {
                                                parsedHours?.let(onConfirm)
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
                                            modifier = Modifier.defaultMinSize(minWidth = 64.dp),
                                        ) {
                                            innerTextField()
                                        }
                                    },
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 64.dp)
                                        .testTag(QUICK_HOURS_INPUT_TEST_TAG),
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
                                    hoursText = incremented.toString()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                enabled = currentHours < MAX_HOURS_VALUE && !isSaving,
                                modifier = Modifier.size(44.dp),
                                shape = CircleShape,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.library_hours_increment_desc),
                                )
                            }
                        }

                        val diff = currentHours - initialHours
                        AnimatedVisibility(
                            visible = parsedHours != null && diff != 0,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            val isPositive = diff > 0
                            val badgeBg = if (isPositive) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            }
                            val badgeFg = if (isPositive) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = badgeBg,
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SuggestionChip(
                        onClick = {
                            val next = (currentHours + QUICK_ADD_1).coerceAtMost(MAX_HOURS_VALUE)
                            hoursText = next.toString()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
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
                        onClick = {
                            val next = (currentHours + QUICK_ADD_2).coerceAtMost(MAX_HOURS_VALUE)
                            hoursText = next.toString()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
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
                        onClick = {
                            val next = (currentHours + QUICK_ADD_5).coerceAtMost(MAX_HOURS_VALUE)
                            hoursText = next.toString()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
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
        },
        confirmButton = {
            Button(
                onClick = {
                    parsedHours?.let(onConfirm)
                },
                enabled = isHoursValid && !isSaving,
                modifier = Modifier.testTag(QUICK_HOURS_SAVE_TEST_TAG),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Text(stringResource(R.string.library_save))
                }
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
