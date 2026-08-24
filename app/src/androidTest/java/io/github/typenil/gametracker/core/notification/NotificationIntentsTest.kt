package io.github.typenil.gametracker.core.notification

import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationIntentsTest {

    @Test
    fun appNotificationSettingsIntent_targetsThisPackage() {
        val intent = NotificationIntents.appNotificationSettingsIntent("io.github.typenil.gametracker.demo.debug")

        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(
            "io.github.typenil.gametracker.demo.debug",
            intent.getStringExtra(Settings.EXTRA_APP_PACKAGE)
        )
    }
}
