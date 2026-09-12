package io.github.kamui2040.vectorint.backup

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class AutoBackupSchedulerTest {
    @Test
    fun `after-change trigger is coalesced with a short delay`() =
        runBlocking {
            val repository =
                SchedulerSettingsRepository(
                    AutoBackupConfiguration(
                        destinationTreeUri = "content://documents/tree/backups",
                        afterChanges = true,
                    ),
                )
            val gateway = RecordingWorkGateway()
            val scheduler = AutoBackupScheduler(repository, gateway, this)

            scheduler.request(AutoBackupTrigger.CHANGE)

            assertEquals(listOf(5L), gateway.oneTimeDelays)
        }

    @Test
    fun `disabled or unconfigured triggers schedule nothing`() =
        runBlocking {
            val repository = SchedulerSettingsRepository(AutoBackupConfiguration(afterChanges = true))
            val gateway = RecordingWorkGateway()
            val scheduler = AutoBackupScheduler(repository, gateway, this)

            scheduler.request(AutoBackupTrigger.CHANGE)
            repository.configuration =
                AutoBackupConfiguration(
                    destinationTreeUri = "content://documents/tree/backups",
                    onAppStart = false,
                )
            scheduler.request(AutoBackupTrigger.APP_START)

            assertEquals(emptyList<Long>(), gateway.oneTimeDelays)
        }

    @Test
    fun `configuration keeps exactly one requested periodic schedule`() =
        runBlocking {
            val repository = SchedulerSettingsRepository(AutoBackupConfiguration())
            val gateway = RecordingWorkGateway()
            val scheduler = AutoBackupScheduler(repository, gateway, this)

            scheduler.applyConfiguration(
                AutoBackupConfiguration(
                    destinationTreeUri = "content://documents/tree/backups",
                    interval = AutoBackupInterval.WEEKLY,
                ),
            )
            scheduler.applyConfiguration(AutoBackupConfiguration())

            assertEquals(listOf(AutoBackupInterval.WEEKLY, null), gateway.periodicIntervals)
            assertEquals(1, gateway.cancelOneTimeCalls)
        }
}

private class SchedulerSettingsRepository(
    initial: AutoBackupConfiguration,
) : AutoBackupSettingsRepository {
    private val mutableState = MutableStateFlow(AutoBackupState(configuration = initial))
    var configuration: AutoBackupConfiguration
        get() = mutableState.value.configuration
        set(value) {
            mutableState.value = mutableState.value.copy(configuration = value)
        }

    override val state: Flow<AutoBackupState> = mutableState

    override suspend fun saveConfiguration(configuration: AutoBackupConfiguration) {
        this.configuration = configuration
    }

    override suspend fun recordResult(
        result: AutoBackupLastResult,
        attemptedAt: Instant,
    ) = Unit
}

private class RecordingWorkGateway : AutoBackupWorkGateway {
    val oneTimeDelays = mutableListOf<Long>()
    val periodicIntervals = mutableListOf<AutoBackupInterval?>()
    var cancelOneTimeCalls = 0

    override fun enqueueOneTime(delaySeconds: Long) {
        oneTimeDelays += delaySeconds
    }

    override fun updatePeriodic(interval: AutoBackupInterval?) {
        periodicIntervals += interval
    }

    override fun cancelOneTime() {
        cancelOneTimeCalls++
    }
}
