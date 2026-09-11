package io.github.typenil.gametracker.devtools.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.data.backup.LibraryImportMode
import io.github.typenil.gametracker.core.designsystem.component.SectionCardShape
import io.github.typenil.gametracker.core.designsystem.component.SectionTitle
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import io.github.typenil.gametracker.devtools.DevDiagnostics
import io.github.typenil.gametracker.devtools.DevSeedPreset
import io.github.typenil.gametracker.devtools.DevSeedRequest
import io.github.typenil.gametracker.devtools.DevToolsCommandParser
import io.github.typenil.gametracker.devtools.DevWipeTarget
import kotlinx.coroutines.launch

/**
 * The developer tools screen: state, seeding and clearing.
 *
 * Diagnostics are rendered verbatim from `DevDiagnostics.toClipboardText()`, the same text the Copy
 * button produces, so what a tester pastes into a bug report is exactly what the screen showed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DevToolsScreen(
    uiState: DevToolsUiState,
    onSeed: (DevSeedRequest) -> Unit,
    onWipe: (DevWipeTarget) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.devtools_copied)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.devtools_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_action_desc),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(GtDimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(GtDimens.Gutter),
        ) {
            Text(
                text = stringResource(R.string.devtools_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DiagnosticsCard(
                diagnostics = uiState.diagnostics,
                unavailable = uiState.diagnosticsUnavailable,
                onCopy = { text ->
                    copyToClipboard(context, text)
                    scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                },
            )
            SeedCard(isBusy = uiState.isBusy, onSeed = onSeed)
            WipeCard(isBusy = uiState.isBusy, onWipe = onWipe)
            BusyIndicator(isBusy = uiState.isBusy)
            ResultCard(result = uiState.lastResult)
        }
    }
}

@Composable
private fun DiagnosticsCard(
    diagnostics: DevDiagnostics?,
    unavailable: Boolean,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DevToolsCard(title = stringResource(R.string.devtools_diagnostics_title), modifier = modifier) {
        if (diagnostics == null) {
            Text(
                text = stringResource(R.string.devtools_diagnostics_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = if (unavailable) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            return@DevToolsCard
        }
        val text = diagnostics.toClipboardText()
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        OutlinedButton(onClick = { onCopy(text) }, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.devtools_copy))
        }
    }
}

@Composable
private fun SeedCard(
    isBusy: Boolean,
    onSeed: (DevSeedRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The form owns its own state: nothing outside this card reads the preset, count or seed.
    var preset by rememberSaveable { mutableStateOf(DevSeedPreset.REALISTIC) }
    var countText by rememberSaveable { mutableStateOf(DevSeedPreset.REALISTIC.defaultCount.toString()) }
    var seedText by rememberSaveable { mutableStateOf(DevToolsCommandParser.DEFAULT_SEED.toString()) }
    var mode by rememberSaveable { mutableStateOf(LibraryImportMode.REPLACE) }
    var countIsNotANumber by rememberSaveable { mutableStateOf(false) }
    var seedIsNotANumber by rememberSaveable { mutableStateOf(false) }

    DevToolsCard(title = stringResource(R.string.devtools_seed_title), modifier = modifier) {
        Text(
            text = stringResource(R.string.devtools_seed_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.devtools_preset_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Column(modifier = Modifier.selectableGroup()) {
            DevSeedPreset.entries.forEach { candidate ->
                PresetRow(
                    preset = candidate,
                    selected = preset == candidate,
                    onSelect = {
                        preset = candidate
                        countText = candidate.defaultCount.toString()
                        countIsNotANumber = false
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = countText,
                onValueChange = {
                    countText = it
                    countIsNotANumber = false
                },
                label = { Text(stringResource(R.string.devtools_count_label)) },
                isError = countIsNotANumber,
                supportingText = {
                    Text(
                        text = if (countIsNotANumber) {
                            stringResource(
                                R.string.devtools_count_invalid,
                                DevSeedPreset.MIN_COUNT,
                                DevSeedPreset.MAX_COUNT,
                            )
                        } else {
                            stringResource(
                                R.string.devtools_count_hint,
                                DevSeedPreset.MIN_COUNT,
                                DevSeedPreset.MAX_COUNT,
                            )
                        },
                    )
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = seedText,
                onValueChange = {
                    seedText = it
                    seedIsNotANumber = false
                },
                label = { Text(stringResource(R.string.devtools_seed_value_label)) },
                isError = seedIsNotANumber,
                supportingText = {
                    if (seedIsNotANumber) Text(stringResource(R.string.devtools_seed_value_invalid))
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Column(modifier = Modifier.selectableGroup()) {
            LibraryImportMode.entries.forEach { candidate ->
                ModeRow(
                    mode = candidate,
                    selected = mode == candidate,
                    onSelect = { mode = candidate },
                )
            }
        }
        Button(
            onClick = {
                val count = countText.trim().toIntOrNull()
                val seed = seedText.trim().toLongOrNull()
                countIsNotANumber = count == null
                seedIsNotANumber = seed == null
                if (count != null && seed != null) {
                    // Same clamping as the adb parser: the two entry points must not disagree.
                    onSeed(DevSeedRequest(preset, DevSeedPreset.clampCount(count), seed, mode))
                }
            },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.devtools_seed_action))
        }
    }
}

@Composable
private fun PresetRow(
    preset: DevSeedPreset,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = preset.name, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ModeRow(
    mode: LibraryImportMode,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = stringResource(
                when (mode) {
                    LibraryImportMode.REPLACE -> R.string.devtools_mode_replace
                    LibraryImportMode.MERGE -> R.string.devtools_mode_merge
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun WipeCard(
    isBusy: Boolean,
    onWipe: (DevWipeTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingTarget by rememberSaveable { mutableStateOf<DevWipeTarget?>(null) }

    DevToolsCard(title = stringResource(R.string.devtools_wipe_title), modifier = modifier) {
        Text(
            text = stringResource(R.string.devtools_wipe_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DevWipeTarget.entries.forEach { target ->
            OutlinedButton(
                onClick = { pendingTarget = target },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(target.labelRes()))
            }
        }
        Text(
            text = stringResource(R.string.devtools_wipe_all_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    pendingTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingTarget = null },
            title = { Text(stringResource(R.string.devtools_confirm_title, stringResource(target.labelRes()))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingTarget = null
                        onWipe(target)
                    },
                ) {
                    Text(text = stringResource(R.string.devtools_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTarget = null }) {
                    Text(text = stringResource(R.string.devtools_cancel))
                }
            },
        )
    }
}

@Composable
private fun BusyIndicator(isBusy: Boolean, modifier: Modifier = Modifier) {
    if (!isBusy) return
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp))
        Text(text = stringResource(R.string.devtools_busy), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ResultCard(result: DevActionResult?, modifier: Modifier = Modifier) {
    if (result == null) return
    DevToolsCard(
        title = stringResource(R.string.devtools_result_title),
        modifier = modifier,
        highlightAsError = result is DevActionResult.Failed || result is DevActionResult.Rejected,
    ) {
        when (result) {
            is DevActionResult.Seeded -> {
                Text(
                    text = stringResource(
                        R.string.devtools_result_seeded,
                        result.outcome.preset.name,
                        result.outcome.deliveredCount,
                        result.outcome.requestedCount,
                        stringResource(
                            when (result.outcome.mode) {
                                LibraryImportMode.REPLACE -> R.string.devtools_mode_replace
                                LibraryImportMode.MERGE -> R.string.devtools_mode_merge
                            },
                        ),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!result.outcome.catalogHydrated) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.devtools_result_seed_partial),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            is DevActionResult.Wiped -> Text(
                text = stringResource(
                    R.string.devtools_result_wiped,
                    stringResource(result.outcome.target.labelRes()),
                    result.outcome.libraryEntries,
                    result.outcome.searchHistoryEntries,
                    result.outcome.notificationEvents,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            is DevActionResult.Failed -> Text(
                text = stringResource(R.string.devtools_result_failed, result.operation, result.message),
                style = MaterialTheme.typography.bodyMedium,
            )

            is DevActionResult.Rejected -> Text(
                text = stringResource(R.string.devtools_result_rejected, result.reason),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Deliberately not the shared `SectionCard`: this one switches container role to mark a failed or
 * rejected result, and keeps a tighter 12dp rhythm between its controls than a Settings section does.
 * It shares the shape and the heading so the two screens still read as one vocabulary.
 */
@Composable
private fun DevToolsCard(
    title: String,
    modifier: Modifier = Modifier,
    highlightAsError: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SectionCardShape,
        color = if (highlightAsError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(GtDimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(text = title)
            content()
        }
    }
}

private fun DevWipeTarget.labelRes(): Int = when (this) {
    DevWipeTarget.LIBRARY -> R.string.devtools_wipe_library
    DevWipeTarget.SEARCH_HISTORY -> R.string.devtools_wipe_search_history
    DevWipeTarget.NOTIFICATION_LEDGER -> R.string.devtools_wipe_ledger
    DevWipeTarget.ALL -> R.string.devtools_wipe_all
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(text, text))
}

