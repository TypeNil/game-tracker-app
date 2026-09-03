package io.github.typenil.gametracker.feature.settings

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import io.github.typenil.gametracker.BuildConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import io.github.typenil.gametracker.core.notification.NotificationIntents
import io.github.typenil.gametracker.core.notification.rememberNotificationPermissionState

@Composable
fun SettingsRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val notificationPermissionState = rememberNotificationPermissionState()
    SettingsScreen(
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
        modifier = modifier
    )

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    hasNotificationPermission: Boolean,
    onRequestPermission: () -> Unit,
    onManageNotifications: () -> Unit,
    onBackClick: () -> Unit,
    onOpenIgdb: () -> Unit,
    onOpenGitHub: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
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
                .padding(GtDimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(GtDimens.Gutter)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(GtDimens.Gutter),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_notifications_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.settings_notifications_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (hasNotificationPermission) {
                                stringResource(R.string.settings_notifications_enabled)
                            } else {
                                stringResource(R.string.settings_notifications_disabled)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasNotificationPermission) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        if (!hasNotificationPermission) {
                            Button(onClick = onRequestPermission) {
                                Text(text = stringResource(R.string.settings_notifications_enable))
                            }
                        } else {
                            OutlinedButton(onClick = onManageNotifications) {
                                Text(text = stringResource(R.string.settings_notifications_manage))
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(GtDimens.Gutter),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_app_info_title),
                        style = MaterialTheme.typography.titleMedium
                    )
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
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = onOpenGitHub) {
                        Text(text = stringResource(R.string.settings_github_link))
                    }
                }
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_igdb_attribution),
                    style = MaterialTheme.typography.bodyLarge
                )
                TextButton(onClick = onOpenIgdb) {
                    Text(text = stringResource(R.string.settings_igdb_link))
                }
            }
        }
    }
}
