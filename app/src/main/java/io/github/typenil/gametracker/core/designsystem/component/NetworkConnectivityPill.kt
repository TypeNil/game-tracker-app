package io.github.typenil.gametracker.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.connectivity.NetworkStatus
import kotlinx.coroutines.delay

const val NETWORK_CONNECTIVITY_PILL_TAG = "network_connectivity_pill"
private const val RESTORED_DISPLAY_DURATION_MILLIS = 2500L

enum class PillMode {
    Hidden,
    Offline,
    Restored
}

/**
 * Non-intrusive top indicator showing device network transitions.
 *
 * - When offline: surfaces a subtle pill indicating cached data is being displayed.
 * - When connection recovers: temporarily flashes a positive "Back online" pill for 2.5s.
 * - [isOfflinePillEnabled] selectively suppresses the Offline state for offline-capable
 *   surfaces (e.g. Library, Settings) while keeping the recovery signal active.
 */
@Composable
fun NetworkConnectivityPill(
    networkStatus: NetworkStatus,
    modifier: Modifier = Modifier,
    isOfflinePillEnabled: Boolean = true,
) {
    var previousStatus by rememberSaveable { mutableStateOf(NetworkStatus.Unknown) }
    var mode by remember { mutableStateOf(PillMode.Hidden) }

    LaunchedEffect(networkStatus, isOfflinePillEnabled) {
        val recovered = previousStatus == NetworkStatus.Unavailable &&
            networkStatus == NetworkStatus.Available
        previousStatus = networkStatus

        if (recovered) {
            mode = PillMode.Restored
            delay(RESTORED_DISPLAY_DURATION_MILLIS)
            mode = PillMode.Hidden
        } else if (networkStatus == NetworkStatus.Unavailable && isOfflinePillEnabled) {
            mode = PillMode.Offline
        } else if (networkStatus == NetworkStatus.Available) {
            if (mode != PillMode.Restored) {
                mode = PillMode.Hidden
            }
        } else if (!isOfflinePillEnabled && mode == PillMode.Offline) {
            mode = PillMode.Hidden
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = mode != PillMode.Hidden,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        ) {
            val isRestored = mode == PillMode.Restored
            val containerColor = if (isRestored) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
            val contentColor = if (isRestored) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            val borderColor = if (isRestored) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }

            Surface(
                shape = CircleShape,
                color = containerColor,
                contentColor = contentColor,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier.testTag(NETWORK_CONNECTIVITY_PILL_TAG)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isRestored) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = contentColor
                    )
                    Text(
                        text = stringResource(
                            if (isRestored) {
                                R.string.connectivity_restored
                            } else {
                                R.string.connectivity_offline
                            }
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor
                    )
                }
            }
        }
    }
}
