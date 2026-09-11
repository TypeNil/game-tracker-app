package io.github.typenil.gametracker.feature.settings

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.typenil.gametracker.core.designsystem.component.SectionCard
import io.github.typenil.gametracker.core.designsystem.component.SectionTitle
import androidx.compose.ui.text.font.FontWeight
import io.github.typenil.gametracker.BuildConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import io.github.typenil.gametracker.core.model.ThemeMode
import io.github.typenil.gametracker.core.data.backup.LibraryImportMode
import io.github.typenil.gametracker.core.data.backup.LibraryImportPreview
import io.github.typenil.gametracker.core.notification.NotificationIntents
import io.github.typenil.gametracker.core.notification.rememberNotificationPermissionState
import io.github.typenil.gametracker.core.work.ReleaseNotificationScheduler
import io.github.typenil.gametracker.devtools.DevToolsEntry
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Notifications
import io.github.typenil.gametracker.feature.recommendations.TuneRecommendationsSheet

@Composable
fun SettingsRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    recommendationGenres: Set<String> = emptySet(),
    recommendationThemes: Set<String> = emptySet(),
    recommendationPlatforms: Set<String> = emptySet(),
    onboardingDismissed: Boolean = false,
    recommendationPreferencesLoaded: Boolean = true,
    onSaveRecommendationPreferences: suspend (Set<String>, Set<String>) -> Boolean = { _, _ -> true },
    onSkipRecommendationOnboarding: suspend () -> Boolean = { true },
    onResetRecommendationPreferences: suspend () -> Boolean = { true },
    userMessageRes: Int? = null,
    onUserMessageShown: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    dynamicColorSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onDynamicColorChange: (Boolean) -> Unit = {},
    onExportDocumentPicked: (Uri) -> Unit = {},
    onImportDocumentPicked: (Uri) -> Unit = {},
    backupBusy: Boolean = false,
    importPreview: LibraryImportPreview? = null,
    onConfirmImport: (LibraryImportMode) -> Unit = {},
    onDismissImportPreview: () -> Unit = {},
    onOpenDevTools: () -> Unit = {},
) {
    val context = LocalContext.current
    var isTuneSheetOpen by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionState = rememberNotificationPermissionState()
    var debugBffUrl by rememberSaveable {
        mutableStateOf(DebugBffUrlActions.currentUrl(context))
    }
    var debugBffUrlError by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    val debugBffInvalidError = stringResource(R.string.settings_debug_bff_invalid_url)
    val exportLibraryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let(onExportDocumentPicked)
    }
    val importLibraryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(onImportDocumentPicked)
    }

    SettingsScreen(
        onTuneRecommendations = {
            if (recommendationPreferencesLoaded) isTuneSheetOpen = true
        },
        recommendationPreferencesLoaded = recommendationPreferencesLoaded,
        userMessageRes = userMessageRes,
        onUserMessageShown = onUserMessageShown,
        hasNotificationPermission = notificationPermissionState.hasPermission,
        onRequestPermission = { notificationPermissionState.requestPermission() },
        onManageNotifications = {
            try {
                context.startActivity(
                    NotificationIntents.appNotificationSettingsIntent(context.packageName)
                )
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.settings_notifications_open_error, Toast.LENGTH_SHORT).show()
            }
        },
        onSendTestNotification = {
            DebugNotificationActions.send(context)
        },
        onCheckReleasesNow = {
            ReleaseNotificationScheduler.triggerImmediateCheck(context)
            Toast.makeText(context, R.string.settings_notifications_check_triggered, Toast.LENGTH_SHORT).show()
        },
        debugBffUrl = debugBffUrl,
        debugBffUrlError = debugBffUrlError,
        isDebugBffUrlVisible = DebugBffUrlActions.isVisible,
        onDebugBffUrlChange = { newUrl ->
            debugBffUrl = newUrl
            debugBffUrlError = null
        },
        onSaveDebugBffUrl = {
            val invalidError = debugBffInvalidError
            val origin = DebugBffUrlActions.toDebugBffOriginOrNull(debugBffUrl)
            if (origin == null) {
                debugBffUrlError = invalidError
            } else {
                val success = DebugBffUrlActions.setUrl(context, origin)
                if (success) {
                    debugBffUrl = origin
                    debugBffUrlError = null
                    Toast.makeText(context, R.string.settings_debug_bff_saved, Toast.LENGTH_SHORT).show()
                } else {
                    debugBffUrlError = invalidError
                }
            }
        },
        onResetDebugBffUrl = {
            DebugBffUrlActions.resetUrl(context)
            debugBffUrl = ""
            debugBffUrlError = null
            Toast.makeText(context, R.string.settings_debug_bff_reset_done, Toast.LENGTH_SHORT).show()
        },
        onBackClick = onBackClick,
        onOpenIgdb = {
            try {
                context.startActivity(SettingsIntents.igdbAttributionIntent())
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.settings_igdb_open_error, Toast.LENGTH_SHORT).show()
            }
        },
        onOpenGitHub = {
            try {
                context.startActivity(SettingsIntents.gitHubIntent())
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.settings_github_open_error, Toast.LENGTH_SHORT).show()
            }
        },
        modifier = modifier,
        themeMode = themeMode,
        dynamicColor = dynamicColor,
        dynamicColorSupported = dynamicColorSupported,
        onThemeModeChange = onThemeModeChange,
        onDynamicColorChange = onDynamicColorChange,
        backupBusy = backupBusy,
        importPreview = importPreview,
        onExportLibrary = {
            val fileName = "gametracker-library-${java.time.LocalDate.now(java.time.ZoneOffset.UTC)}.json"
            exportLibraryLauncher.launch(fileName)
        },
        onImportLibrary = {
            importLibraryLauncher.launch(
                arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"),
            )
        },
        onConfirmImport = onConfirmImport,
        onDismissImportPreview = onDismissImportPreview,
        onOpenDevTools = onOpenDevTools,
    )
    if (isTuneSheetOpen && recommendationPreferencesLoaded) {
        TuneRecommendationsSheet(
            initialTags = recommendationGenres + recommendationThemes,
            initialPlatforms = recommendationPlatforms,
            onboardingDismissed = onboardingDismissed,
            onDismiss = { isTuneSheetOpen = false },
            onSave = onSaveRecommendationPreferences,
            onSkip = onSkipRecommendationOnboarding,
            onReset = onResetRecommendationPreferences,
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    hasNotificationPermission: Boolean,
    onTuneRecommendations: () -> Unit = {},
    recommendationPreferencesLoaded: Boolean = true,
    onRequestPermission: () -> Unit,
    onManageNotifications: () -> Unit,
    onBackClick: () -> Unit,
    onOpenIgdb: () -> Unit,
    onOpenGitHub: () -> Unit = {},
    onCheckReleasesNow: () -> Unit = {},
    onSendTestNotification: () -> Unit = {},
    isSendTestNotificationVisible: Boolean = DebugNotificationActions.isVisible,
    debugBffUrl: String = "",
    debugBffUrlError: String? = null,
    isDebugBffUrlVisible: Boolean = DebugBffUrlActions.isVisible,
    onDebugBffUrlChange: (String) -> Unit = {},
    onSaveDebugBffUrl: () -> Unit = {},
    onResetDebugBffUrl: () -> Unit = {},
    userMessageRes: Int? = null,
    onUserMessageShown: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    dynamicColorSupported: Boolean = true,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onDynamicColorChange: (Boolean) -> Unit = {},
    backupBusy: Boolean = false,
    importPreview: LibraryImportPreview? = null,
    onExportLibrary: () -> Unit = {},
    onImportLibrary: () -> Unit = {},
    onConfirmImport: (LibraryImportMode) -> Unit = {},
    onDismissImportPreview: () -> Unit = {},
    isDevToolsAvailable: Boolean = DevToolsEntry.isAvailable,
    onOpenDevTools: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val userMessage = userMessageRes?.let { stringResource(it) }
    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            onUserMessageShown()
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_action_desc)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    start = GtDimens.Gutter,
                    end = GtDimens.Gutter,
                    top = GtDimens.Card,
                    bottom = GtDimens.Empty,
                ),
            verticalArrangement = Arrangement.spacedBy(GtDimens.Card),
        ) {
            AppearanceSettingsCard(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                dynamicColorSupported = dynamicColorSupported,
                onThemeModeChange = onThemeModeChange,
                onDynamicColorChange = onDynamicColorChange,
            )

            NotificationsSection(
                hasNotificationPermission = hasNotificationPermission,
                onRequestPermission = onRequestPermission,
                onManageNotifications = onManageNotifications,
                onSendTestNotification = onSendTestNotification,
                isSendTestNotificationVisible = isSendTestNotificationVisible,
                onCheckReleasesNow = onCheckReleasesNow,
            )

            RecommendationsSection(
                recommendationPreferencesLoaded = recommendationPreferencesLoaded,
                onTuneRecommendations = onTuneRecommendations,
            )

            DataSettingsCard(
                backupBusy = backupBusy,
                onExportLibrary = onExportLibrary,
                onImportLibrary = onImportLibrary,
            )

            if (DevToolsEntry.isAvailable && isDevToolsAvailable) {
                DeveloperToolsSection(onOpenDevTools = onOpenDevTools)
            }

            if (DebugBffUrlActions.isVisible && isDebugBffUrlVisible) {
                DebugBffSection(
                    debugBffUrl = debugBffUrl,
                    debugBffUrlError = debugBffUrlError,
                    onDebugBffUrlChange = onDebugBffUrlChange,
                    onSaveDebugBffUrl = onSaveDebugBffUrl,
                    onResetDebugBffUrl = onResetDebugBffUrl,
                )
            }

            AppInformationSection(
                onOpenGitHub = onOpenGitHub,
                onOpenIgdb = onOpenIgdb,
            )
        }
    }
    importPreview?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            busy = backupBusy,
            onMerge = { onConfirmImport(LibraryImportMode.MERGE) },
            onReplace = { onConfirmImport(LibraryImportMode.REPLACE) },
            onDismiss = onDismissImportPreview,
        )
    }
}

/**
 * One titled Settings section. Sections without a description (App information) pass none, and nothing
 * is emitted for it: no empty text node, no phantom gap under the heading.
 */
@Composable
private fun SettingsSection(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCard {
        SectionTitle(text = title)
        if (description != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(GtDimens.Card))
        Column(
            verticalArrangement = Arrangement.spacedBy(GtDimens.Card),
            content = content,
        )
    }
}

/**
 * A Settings action that takes the user somewhere, as opposed to a button that runs a command.
 *
 * [enabled] is not decoration: Tune recommendations is unavailable while the stored preferences are still
 * loading, and a row that looks available would be a lying affordance.
 *
 * The label is weighted and never capped to one line, so a long translation grows the row instead of
 * clipping it; both icons are decorative, and the label is what a screen reader announces.
 */
@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    external: Boolean = false,
) {
    val labelColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SettingsActionRowMinHeight)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else labelColor,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = labelColor,
            )
            Icon(
                imageVector = if (external) {
                    Icons.Filled.OpenInNew
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun NotificationsSection(
    hasNotificationPermission: Boolean,
    onRequestPermission: () -> Unit,
    onManageNotifications: () -> Unit,
    onSendTestNotification: () -> Unit,
    isSendTestNotificationVisible: Boolean,
    onCheckReleasesNow: () -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_notifications_title),
        description = stringResource(R.string.settings_notifications_desc),
    ) {
        NotificationPermissionPill(hasNotificationPermission = hasNotificationPermission)

        if (!hasNotificationPermission) {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.settings_notifications_enable))
            }
        } else {
            SettingsActionRow(
                icon = Icons.Default.Notifications,
                label = stringResource(R.string.settings_notifications_manage),
                onClick = onManageNotifications,
            )
            if (DebugNotificationActions.isVisible && isSendTestNotificationVisible) {
                OutlinedButton(
                    onClick = onSendTestNotification,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.settings_notifications_send_test))
                }
            }
            OutlinedButton(
                onClick = onCheckReleasesNow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.settings_notifications_check_now))
            }
        }
    }
}

@Composable
private fun NotificationPermissionPill(hasNotificationPermission: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (hasNotificationPermission) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasNotificationPermission) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
            )
            Text(
                text = if (hasNotificationPermission) {
                    stringResource(R.string.settings_notifications_enabled)
                } else {
                    stringResource(R.string.settings_notifications_disabled)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (hasNotificationPermission) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                },
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RecommendationsSection(
    recommendationPreferencesLoaded: Boolean,
    onTuneRecommendations: () -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_recommendations_title),
        description = stringResource(R.string.settings_recommendations_desc),
    ) {
        SettingsActionRow(
            icon = Icons.Default.Tune,
            label = stringResource(R.string.discover_tune_recommendations),
            onClick = onTuneRecommendations,
            enabled = recommendationPreferencesLoaded,
        )
    }
}

@Composable
private fun DeveloperToolsSection(onOpenDevTools: () -> Unit) {
    SettingsSection(
        title = stringResource(R.string.settings_devtools_title),
        description = stringResource(R.string.settings_devtools_desc),
    ) {
        SettingsActionRow(
            icon = Icons.Default.Build,
            label = stringResource(R.string.settings_devtools_open),
            onClick = onOpenDevTools,
        )
    }
}

@Composable
private fun DebugBffSection(
    debugBffUrl: String,
    debugBffUrlError: String?,
    onDebugBffUrlChange: (String) -> Unit,
    onSaveDebugBffUrl: () -> Unit,
    onResetDebugBffUrl: () -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_debug_bff_title),
        description = stringResource(R.string.settings_debug_bff_desc),
    ) {
        OutlinedTextField(
            value = debugBffUrl,
            onValueChange = onDebugBffUrlChange,
            label = { Text(stringResource(R.string.settings_debug_bff_label)) },
            placeholder = { Text("http://10.0.2.2:8080") },
            isError = debugBffUrlError != null,
            supportingText = debugBffUrlError?.let { errorText ->
                { Text(text = errorText) }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSaveDebugBffUrl,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.settings_debug_bff_save))
            }
            OutlinedButton(
                onClick = onResetDebugBffUrl,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.settings_debug_bff_reset))
            }
        }
    }
}

@Composable
private fun AppInformationSection(
    onOpenGitHub: () -> Unit,
    onOpenIgdb: () -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_app_info_title)) {
        Text(
            text = stringResource(R.string.settings_app_name),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(
                R.string.settings_app_version,
                BuildConfig.VERSION_NAME,
                BuildConfig.FLAVOR
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SettingsActionRow(
            icon = Icons.Default.Code,
            label = stringResource(R.string.settings_github_link),
            onClick = onOpenGitHub,
            external = true,
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        Text(
            text = stringResource(R.string.settings_igdb_attribution),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SettingsActionRow(
            icon = Icons.Default.SportsEsports,
            label = stringResource(R.string.settings_igdb_link),
            onClick = onOpenIgdb,
            external = true,
        )
    }
}

@Composable
private fun AppearanceSettingsCard(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    dynamicColorSupported: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_appearance_title),
        description = stringResource(R.string.settings_appearance_desc),
    ) {
        Text(
            text = stringResource(R.string.settings_theme_title),
            style = MaterialTheme.typography.labelLarge,
        )
        Column(modifier = Modifier.selectableGroup()) {
            ThemeMode.entries.forEach { mode ->
                ThemeModeRow(
                    mode = mode,
                    selected = themeMode == mode,
                    onSelect = { onThemeModeChange(mode) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_dynamic_color_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(
                        if (dynamicColorSupported) {
                            R.string.settings_dynamic_color_desc
                        } else {
                            R.string.settings_dynamic_color_unsupported
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = dynamicColor && dynamicColorSupported,
                onCheckedChange = onDynamicColorChange,
                enabled = dynamicColorSupported,
            )
        }
    }
}

@Composable
private fun ThemeModeRow(mode: ThemeMode, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = stringResource(mode.labelRes()),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

@Composable
private fun DataSettingsCard(
    backupBusy: Boolean,
    onExportLibrary: () -> Unit,
    onImportLibrary: () -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_data_title),
        description = stringResource(R.string.settings_data_desc),
    ) {
        OutlinedButton(
            onClick = onExportLibrary,
            enabled = !backupBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.settings_export_library))
        }
        OutlinedButton(
            onClick = onImportLibrary,
            enabled = !backupBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.settings_import_library))
        }
    }
}

@Composable
private fun ImportPreviewDialog(
    preview: LibraryImportPreview,
    busy: Boolean,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(text = stringResource(R.string.settings_import_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.settings_import_preview_found, preview.foundCount))
                Text(text = stringResource(R.string.settings_import_preview_new, preview.newCount))
                Text(
                    text = stringResource(
                        R.string.settings_import_preview_conflicts,
                        preview.conflictCount,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onMerge, enabled = !busy) {
                Text(text = stringResource(R.string.settings_import_merge))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onReplace, enabled = !busy) {
                    Text(text = stringResource(R.string.settings_import_replace))
                }
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Text(text = stringResource(R.string.settings_import_cancel))
                }
            }
        },
    )
}

/** Minimum touch target for a settings row, independent of how long its label wraps. */
private val SettingsActionRowMinHeight = 48.dp
private const val DISABLED_CONTENT_ALPHA = 0.38f
