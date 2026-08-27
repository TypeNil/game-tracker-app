package io.github.typenil.gametracker.feature.library.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R

private const val MAX_HOURS_DIGITS = 6
private const val MAX_HOURS_VALUE = 999_999
private const val QUICK_ADD_1 = 1
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
) {
    var hoursText by rememberSaveable { mutableStateOf(initialHours.toString()) }
    val parsedHours = hoursText.toIntOrNull()
    val currentHours = parsedHours ?: 0
    val isHoursValid = parsedHours != null && parsedHours in 0..MAX_HOURS_VALUE

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismissRequest() },
        modifier = modifier.testTag(QUICK_HOURS_DIALOG_TEST_TAG),
        title = {
            Text(
                text = stringResource(R.string.library_log_hours),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = gameName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = {
                            val decremented = (currentHours - 1).coerceAtLeast(0)
                            hoursText = decremented.toString()
                        },
                        enabled = currentHours > 0 && !isSaving,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = stringResource(R.string.library_hours_decrement_desc),
                        )
                    }
                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.all { it.isDigit() }) {
                                hoursText = input.take(MAX_HOURS_DIGITS)
                            }
                        },
                        enabled = !isSaving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                        modifier = Modifier
                            .weight(1f)
                            .testTag(QUICK_HOURS_INPUT_TEST_TAG),
                    )
                    IconButton(
                        onClick = {
                            val incremented = (currentHours + 1).coerceAtMost(MAX_HOURS_VALUE)
                            hoursText = incremented.toString()
                        },
                        enabled = currentHours < MAX_HOURS_VALUE && !isSaving,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.library_hours_increment_desc),
                        )
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
                        },
                        label = { Text(stringResource(R.string.library_quick_add_1h)) },
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                    )
                    SuggestionChip(
                        onClick = {
                            val next = (currentHours + QUICK_ADD_5).coerceAtMost(MAX_HOURS_VALUE)
                            hoursText = next.toString()
                        },
                        label = { Text(stringResource(R.string.library_quick_add_5h)) },
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    parsedHours?.let(onConfirm)
                },
                enabled = isHoursValid && !isSaving,
                modifier = Modifier.testTag(QUICK_HOURS_SAVE_TEST_TAG),
            ) {
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
