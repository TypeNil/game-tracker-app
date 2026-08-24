package io.github.typenil.gametracker.core.notification

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * State holder managing notification permission status and request triggering.
 */
@Stable
class NotificationPermissionState(
    val hasPermission: Boolean,
    val requestPermission: () -> Unit
)

/**
 * Reusable Compose hook for querying and requesting Android 13+ (POST_NOTIFICATIONS) permission,
 * with fallback to app notification settings on pre-Tiramisu platforms.
 */
@Composable
fun rememberNotificationPermissionState(
    onPermissionResult: (Boolean) -> Unit = {}
): NotificationPermissionState {
    val context = LocalContext.current

    fun checkPermission(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    var hasPermission by remember { mutableStateOf(checkPermission()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = checkPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = checkPermission()
        onPermissionResult(isGranted)
    }

    val requestPermission: () -> Unit = remember(launcher, context) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                try {
                    context.startActivity(
                        NotificationIntents.appNotificationSettingsIntent(context.packageName)
                    )
                } catch (_: ActivityNotFoundException) {
                    // Ignored on platforms where intent cannot resolve
                }
            }
        }
    }

    return remember(hasPermission, requestPermission) {
        NotificationPermissionState(
            hasPermission = hasPermission,
            requestPermission = requestPermission
        )
    }
}
