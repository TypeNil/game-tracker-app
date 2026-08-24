package io.github.typenil.gametracker.core.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        val config = Configuration.Builder()
            .setExecutor { it.run() }
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters
                    ): ListenableWorker {
                        return object : Worker(appContext, workerParameters) {
                            override fun doWork(): Result = Result.success()
                        }
                    }
                }
            )
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }

    @After
    fun tearDown() {
        workManager.cancelUniqueWork(ReleaseNotificationScheduler.WORK_NAME)
    }

    @Test
    fun schedulePeriodicCheck_enqueuesUniquePeriodicWorkWithConnectedNetworkConstraint() {
        ReleaseNotificationScheduler.schedulePeriodicCheck(context)

        val workInfo = requireUniqueWork()
        assertEquals(WorkInfo.State.ENQUEUED, workInfo.state)
        assertTrue(workInfo.tags.contains(ReleaseNotificationWorker::class.java.name))
        assertEquals(
            ReleaseNotificationScheduler.REPEAT_INTERVAL_HOURS * 60 * 60 * 1000L,
            workInfo.periodicityInfo?.repeatIntervalMillis
        )
        assertEquals(
            ReleaseNotificationScheduler.FLEX_INTERVAL_HOURS * 60 * 60 * 1000L,
            workInfo.periodicityInfo?.flexIntervalMillis
        )
        assertEquals(NetworkType.CONNECTED, workInfo.constraints.requiredNetworkType)
    }

    @Test
    fun testDriver_meetsConstraintsAndPeriodWithoutFailingPeriodicWork() {
        ReleaseNotificationScheduler.schedulePeriodicCheck(context)

        val workInfo = requireUniqueWork()
        val testDriver = WorkManagerTestInitHelper.getTestDriver(context)
        assertNotNull(testDriver)
        testDriver!!.setAllConstraintsMet(workInfo.id)
        testDriver.setPeriodDelayMet(workInfo.id)

        val after = workManager.getWorkInfoById(workInfo.id).get()
        assertNotNull(after)
        assertNotEquals(WorkInfo.State.FAILED, after!!.state)
        assertTrue(
            after.state == WorkInfo.State.ENQUEUED ||
                after.state == WorkInfo.State.RUNNING ||
                after.state == WorkInfo.State.SUCCEEDED
        )
    }

    private fun requireUniqueWork(): WorkInfo {
        val workInfos = workManager.getWorkInfosForUniqueWork(ReleaseNotificationScheduler.WORK_NAME).get()
        assertNotNull(workInfos)
        assertTrue(workInfos.isNotEmpty())
        return workInfos.first()
    }
}
