package io.github.kamui2040.vectorint.presentation.settings

import io.github.kamui2040.vectorint.backup.AutoBackupConfiguration
import io.github.kamui2040.vectorint.backup.AutoBackupInterval
import io.github.kamui2040.vectorint.backup.AutoBackupLastResult
import io.github.kamui2040.vectorint.backup.AutoBackupScheduler
import io.github.kamui2040.vectorint.backup.AutoBackupSettingsRepository
import io.github.kamui2040.vectorint.backup.AutoBackupState
import io.github.kamui2040.vectorint.backup.AutoBackupWorkGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.time.Instant

class AutoBackupSettingsEditorTest {
    @Test
    fun `selecting a folder saves it and schedules the first backup`() =
        runBlocking {
            val repository = EditorAutoBackupSettingsRepository()
            val work = EditorWorkGateway()
            val editor = AutoBackupSettingsEditor(repository, AutoBackupScheduler(repository, work, this))

            val result =
                editor.selectDestination(
                    current = AutoBackupConfiguration(),
                    treeUri = "content://documents/tree/backups",
                )

            assertEquals(AutoBackupSettingsSaveResult.Saved, result)
            assertEquals("content://documents/tree/backups", repository.current.configuration.destinationTreeUri)
            assertEquals(listOf(0L), work.oneTimeDelays)
        }

    @Test
    fun `a scheduling failure never hides that the configuration was saved`() =
        runBlocking {
            val repository = EditorAutoBackupSettingsRepository()
            val work = EditorWorkGateway(failScheduling = true)
            val editor = AutoBackupSettingsEditor(repository, AutoBackupScheduler(repository, work, this))

            val result =
                editor.selectDestination(
                    current = AutoBackupConfiguration(),
                    treeUri = "content://documents/tree/backups",
                )

            assertEquals(AutoBackupSettingsSaveResult.SavedSchedulingFailed, result)
            assertEquals("content://documents/tree/backups", repository.current.configuration.destinationTreeUri)
        }
}

private class EditorAutoBackupSettingsRepository : AutoBackupSettingsRepository {
    private val mutableState = MutableStateFlow(AutoBackupState())
    val current: AutoBackupState
        get() = mutableState.value

    override val state: Flow<AutoBackupState> = mutableState

    override suspend fun saveConfiguration(configuration: AutoBackupConfiguration) {
        mutableState.value = mutableState.value.copy(configuration = configuration)
    }

    override suspend fun recordResult(
        result: AutoBackupLastResult,
        attemptedAt: Instant,
    ) = Unit
}

private class EditorWorkGateway(
    private val failScheduling: Boolean = false,
) : AutoBackupWorkGateway {
    val oneTimeDelays = mutableListOf<Long>()

    override fun enqueueOneTime(delaySeconds: Long) {
        if (failScheduling) throw IOException("synthetic scheduling failure")
        oneTimeDelays += delaySeconds
    }

    override fun updatePeriodic(interval: AutoBackupInterval?) {
        if (failScheduling) throw IOException("synthetic scheduling failure")
    }

    override fun cancelOneTime() {
        if (failScheduling) throw IOException("synthetic scheduling failure")
    }
}
