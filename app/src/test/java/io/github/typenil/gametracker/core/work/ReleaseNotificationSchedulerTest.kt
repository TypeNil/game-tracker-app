package io.github.typenil.gametracker.core.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotificationSchedulerTest {

    private val context: Context = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)

    @Test
    fun triggerImmediateCheck_enqueuesUniqueWorkWithKeepPolicy() {
        val requestSlot = slot<OneTimeWorkRequest>()
        every {
            workManager.enqueueUniqueWork(
                ReleaseNotificationScheduler.IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                capture(requestSlot)
            )
        } returns mockk(relaxed = true)

        ReleaseNotificationScheduler.triggerImmediateCheck(context, workManager)

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                ReleaseNotificationScheduler.IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>()
            )
        }
        assertEquals(ReleaseNotificationWorker::class.java.name, requestSlot.captured.workSpec.workerClassName)
    }

    @Test
    fun schedulePeriodicCheck_enqueuesUniquePeriodicWorkWithKeepPolicy() {
        val requestSlot = slot<PeriodicWorkRequest>()
        every {
            workManager.enqueueUniquePeriodicWork(
                ReleaseNotificationScheduler.WORK_NAME,
                any(),
                capture(requestSlot)
            )
        } returns mockk(relaxed = true)

        ReleaseNotificationScheduler.schedulePeriodicCheck(context, workManager)

        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork(
                ReleaseNotificationScheduler.WORK_NAME,
                any(),
                any<PeriodicWorkRequest>()
            )
        }
        assertEquals(ReleaseNotificationWorker::class.java.name, requestSlot.captured.workSpec.workerClassName)
    }
}
