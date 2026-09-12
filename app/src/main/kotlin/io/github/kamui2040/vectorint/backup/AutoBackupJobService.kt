package io.github.kamui2040.vectorint.backup

import android.app.job.JobParameters
import android.app.job.JobService
import io.github.kamui2040.vectorint.VectorintApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class AutoBackupJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val runningJobs = ConcurrentHashMap<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        val application = application as? VectorintApplication ?: return false
        runningJobs[params.jobId] =
            serviceScope.launch {
                try {
                    application.runAutomaticBackup()
                    jobFinished(params, false)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    jobFinished(params, false)
                } finally {
                    runningJobs.remove(params.jobId)
                }
            }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        runningJobs.remove(params.jobId)?.cancel()
        return true
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

internal const val ONE_TIME_JOB_ID = 0x56424131
internal const val PERIODIC_JOB_ID = 0x56424132
