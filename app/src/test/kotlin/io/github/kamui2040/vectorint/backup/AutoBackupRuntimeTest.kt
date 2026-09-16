package io.github.kamui2040.vectorint.backup

import android.app.Application
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AutoBackupRuntimeTest {
    private lateinit var context: Context
    private lateinit var jobScheduler: JobScheduler
    private lateinit var gateway: AndroidAutoBackupWorkGateway

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        jobScheduler = context.getSystemService(JobScheduler::class.java)
        jobScheduler.cancel(ONE_TIME_JOB_ID)
        jobScheduler.cancel(PERIODIC_JOB_ID)
        gateway = AndroidAutoBackupWorkGateway(context, jobScheduler)
    }

    @After
    fun tearDown() {
        jobScheduler.cancel(ONE_TIME_JOB_ID)
        jobScheduler.cancel(PERIODIC_JOB_ID)
    }

    @Test
    fun `one-time work is persisted and replaces the same bounded job identity`() {
        gateway.enqueueOneTime(5L)
        val first = requireNotNull(jobScheduler.getPendingJob(ONE_TIME_JOB_ID))

        gateway.enqueueOneTime(0L)
        val replaced = requireNotNull(jobScheduler.getPendingJob(ONE_TIME_JOB_ID))

        assertTrue(first.isPersisted)
        assertTrue(replaced.isPersisted)
        assertEquals(ComponentName(context, AutoBackupJobService::class.java), replaced.service)
        assertEquals(1, jobScheduler.allPendingJobs.count { it.id == ONE_TIME_JOB_ID })
    }

    @Test
    fun `periodic work follows the selected interval and can be disabled`() {
        gateway.updatePeriodic(AutoBackupInterval.DAILY)

        val periodic = requireNotNull(jobScheduler.getPendingJob(PERIODIC_JOB_ID))
        assertTrue(periodic.isPeriodic)
        assertEquals(24L * 60L * 60L * 1_000L, periodic.intervalMillis)

        gateway.updatePeriodic(AutoBackupInterval.NEVER)

        assertNull(jobScheduler.getPendingJob(PERIODIC_JOB_ID))
    }

    @Test
    fun `job service is private and protected by Android`() {
        val serviceInfo =
            context.packageManager.getServiceInfo(
                ComponentName(context, AutoBackupJobService::class.java),
                PackageManager.ComponentInfoFlags.of(0L),
            )

        assertFalse(serviceInfo.exported)
        assertEquals("android.permission.BIND_JOB_SERVICE", serviceInfo.permission)
        assertNotNull(serviceInfo.name)
    }
}
