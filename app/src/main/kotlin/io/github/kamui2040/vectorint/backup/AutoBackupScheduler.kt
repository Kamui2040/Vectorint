package io.github.kamui2040.vectorint.backup

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal enum class AutoBackupTrigger {
    CHANGE,
    APP_START,
    APP_BACKGROUND,
}

internal interface AutoBackupWorkGateway {
    fun enqueueOneTime(delaySeconds: Long)

    fun updatePeriodic(interval: AutoBackupInterval?)

    fun cancelOneTime()
}

internal class AndroidAutoBackupWorkGateway(
    context: Context,
    private val jobScheduler: JobScheduler = context.getSystemService(JobScheduler::class.java),
) : AutoBackupWorkGateway {
    private val applicationContext = context.applicationContext
    private val service = ComponentName(applicationContext, AutoBackupJobService::class.java)

    override fun enqueueOneTime(delaySeconds: Long) {
        schedule(
            newJobBuilder(ONE_TIME_JOB_ID)
                .setMinimumLatency(delaySeconds * 1_000L)
                .build(),
        )
    }

    override fun updatePeriodic(interval: AutoBackupInterval?) {
        if (interval == null || interval == AutoBackupInterval.NEVER) {
            jobScheduler.cancel(PERIODIC_JOB_ID)
            return
        }
        val repeatMillis =
            when (interval) {
                AutoBackupInterval.DAILY -> 24L * 60L * 60L * 1_000L
                AutoBackupInterval.WEEKLY -> 7L * 24L * 60L * 60L * 1_000L
                AutoBackupInterval.NEVER -> error("A disabled interval cannot be scheduled")
            }
        schedule(newJobBuilder(PERIODIC_JOB_ID).setPeriodic(repeatMillis).build())
    }

    override fun cancelOneTime() {
        jobScheduler.cancel(ONE_TIME_JOB_ID)
    }

    private fun newJobBuilder(jobId: Int): JobInfo.Builder =
        JobInfo
            .Builder(jobId, service)
            .setPersisted(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setRequiresStorageNotLow(true)
                }
            }

    private fun schedule(job: JobInfo) {
        check(jobScheduler.schedule(job) == JobScheduler.RESULT_SUCCESS) {
            "Android could not schedule automatic backup work"
        }
    }
}

internal class AutoBackupScheduler(
    private val settingsRepository: AutoBackupSettingsRepository,
    private val workGateway: AutoBackupWorkGateway,
    private val scope: CoroutineScope,
) {
    suspend fun request(trigger: AutoBackupTrigger) {
        try {
            val configuration = settingsRepository.state.first().configuration
            if (configuration.hasDestination && configuration.isEnabledFor(trigger)) {
                val delaySeconds = if (trigger == AutoBackupTrigger.CHANGE) CHANGE_DEBOUNCE_SECONDS else 0L
                workGateway.enqueueOneTime(delaySeconds)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // A later app start or settings change can retry this trigger.
        }
    }

    fun requestNow() {
        workGateway.enqueueOneTime(0L)
    }

    fun applyConfiguration(configuration: AutoBackupConfiguration) {
        if (!configuration.hasDestination) {
            workGateway.cancelOneTime()
            workGateway.updatePeriodic(null)
            return
        }
        workGateway.updatePeriodic(configuration.interval)
    }

    fun synchronize() {
        scope.launch {
            try {
                applyConfiguration(settingsRepository.state.first().configuration)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // The next app start or settings change retries synchronization.
            }
        }
    }

    fun requestAsync(trigger: AutoBackupTrigger) {
        scope.launch { request(trigger) }
    }

    private fun AutoBackupConfiguration.isEnabledFor(trigger: AutoBackupTrigger): Boolean =
        when (trigger) {
            AutoBackupTrigger.CHANGE -> afterChanges
            AutoBackupTrigger.APP_START -> onAppStart
            AutoBackupTrigger.APP_BACKGROUND -> onAppBackground
        }

    private companion object {
        const val CHANGE_DEBOUNCE_SECONDS = 5L
    }
}
