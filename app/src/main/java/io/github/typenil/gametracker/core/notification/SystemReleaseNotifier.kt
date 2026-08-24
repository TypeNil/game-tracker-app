package io.github.typenil.gametracker.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.typenil.gametracker.MainActivity
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.model.ReleaseEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android system implementation of [ReleaseNotifier].
 */
@Singleton
class SystemReleaseNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ReleaseNotifier {

    override fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = context.getString(R.string.notification_channel_releases_name)
            val channelDescription = context.getString(R.string.notification_channel_releases_desc)
            val importance = NotificationManager.IMPORTANCE_DEFAULT

            val channel = NotificationChannel(CHANNEL_ID_RELEASES, channelName, importance).apply {
                description = channelDescription
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun hasNotificationPermission(): Boolean {
        val managerCompat = NotificationManagerCompat.from(context)
        if (!managerCompat.areNotificationsEnabled()) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }

        return true
    }

    override fun postReleaseNotification(event: ReleaseEvent): Boolean {
        if (!hasNotificationPermission()) {
            return false
        }

        createNotificationChannels()

        val notificationId = ReleaseNotificationPayload.computeNotificationId(event.gameId, event.eventType)
        val intent = buildTapIntent(event.gameId)

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = ReleaseNotificationPayload.getTitle(context, event)
        val body = ReleaseNotificationPayload.getBody(context, event)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_RELEASES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            return true
        } catch (_: SecurityException) {
            return false
        }
    }

    fun buildTapIntent(gameId: Long): Intent {
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse(ReleaseNotificationPayload.buildDeepLinkUri(gameId)),
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }


    companion object {
        const val CHANNEL_ID_RELEASES = "game_releases"
    }
}
