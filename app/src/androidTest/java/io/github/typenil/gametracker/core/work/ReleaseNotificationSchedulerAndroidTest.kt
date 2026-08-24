package io.github.typenil.gametracker.core.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseNotificationSchedulerAndroidTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun schedulePeriodicCheck_enqueuesUniquePeriodicWorkWithConnectedNetworkConstraint() {
        ReleaseNotificationScheduler.schedulePeriodicCheck(context)

        val workInfos = workManager.getWorkInfosForUniqueWork(ReleaseNotificationScheduler.WORK_NAME).get()
        assertNotNull(workInfos)
        assertTrue(workInfos.isNotEmpty())

        val workInfo = workInfos.first()
        assertEquals(WorkInfo.State.ENQUEUED, workInfo.state)
        assertTrue(workInfo.tags.contains(ReleaseNotificationWorker::class.java.name))
        assertEquals(ReleaseNotificationScheduler.REPEAT_INTERVAL_HOURS * 60 * 60 * 1000L, workInfo.periodicityInfo?.repeatIntervalMillis)
        assertEquals(ReleaseNotificationScheduler.FLEX_INTERVAL_HOURS * 60 * 60 * 1000L, workInfo.periodicityInfo?.flexIntervalMillis)
        assertEquals(NetworkType.CONNECTED, workInfo.constraints.requiredNetworkType)
    }
}
