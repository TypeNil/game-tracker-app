package io.github.typenil.gametracker.feature.discover.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.component.PlatformFamily
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import io.github.typenil.gametracker.core.model.RecommendationGenreCatalog
import io.github.typenil.gametracker.core.model.UserPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TuneRecommendationsSheet(
    initialGenres: Set<String>,
    initialPlatforms: Set<String>,
    onDismiss: () -> Unit,
    onSave: suspend (Set<String>, Set<String>) -> Boolean,
    onSkip: suspend () -> Boolean,
    modifier: Modifier = Modifier,
) {
    var isSubmitting by remember { mutableStateOf(false) }
    var submitFailed by remember { mutableStateOf(false) }
    val currentSubmitting by rememberUpdatedState(isSubmitting)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            target != SheetValue.Hidden || !currentSubmitting
        },
    )
    val scope = rememberCoroutineScope()
    var selectedGenres by rememberSaveable(initialGenres) { mutableStateOf(initialGenres) }
    var selectedPlatforms by rememberSaveable(initialPlatforms) { mutableStateOf(initialPlatforms) }
    val canSave = selectedGenres.size >= UserPreferences.MIN_COLD_START_GENRES &&
        selectedPlatforms.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = GtDimens.Gutter)
                .padding(bottom = GtDimens.Gutter)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.discover_tune_recommendations),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.discover_onboarding_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.discover_onboarding_genres),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RecommendationGenreCatalog.wireNames.forEach { genre ->
                    FilterChip(
                        selected = genre in selectedGenres,
                        onClick = {
                            selectedGenres = if (genre in selectedGenres) {
                                selectedGenres - genre
                            } else {
                                selectedGenres + genre
                            }
                        },
                        label = { Text(genre) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.discover_onboarding_platforms),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlatformFamily.entries.forEach { family ->
                    FilterChip(
                        selected = family.name in selectedPlatforms,
                        onClick = {
                            selectedPlatforms = if (family.name in selectedPlatforms) {
                                selectedPlatforms - family.name
                            } else {
                                selectedPlatforms + family.name
                            }
                        },
                        label = { Text(stringResource(family.labelRes)) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.discover_onboarding_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (submitFailed) {
                Text(
                    text = stringResource(R.string.error_preferences_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        scope.launch {
                            isSubmitting = true
                            submitFailed = false
                            try {
                                val succeeded = onSkip()
                                submitFailed = !succeeded
                                if (succeeded) onDismiss()
                            } finally {
                                isSubmitting = false
                            }
                        }
                    },
                    enabled = !isSubmitting,
                ) {
                    Text(stringResource(R.string.discover_onboarding_skip))
                }
                Button(
                    onClick = {
                        scope.launch {
                            isSubmitting = true
                            submitFailed = false
                            try {
                                val succeeded = onSave(selectedGenres, selectedPlatforms)
                                submitFailed = !succeeded
                                if (succeeded) onDismiss()
                            } finally {
                                isSubmitting = false
                            }
                        }
                    },
                    enabled = canSave && !isSubmitting,
                ) {
                    Text(stringResource(R.string.discover_onboarding_save))
                }
            }
        }
    }
}
