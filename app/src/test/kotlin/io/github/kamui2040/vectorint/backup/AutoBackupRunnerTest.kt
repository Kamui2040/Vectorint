package io.github.kamui2040.vectorint.backup

import io.github.kamui2040.vectorint.data.BackupData
import io.github.kamui2040.vectorint.data.BackupDataRepository
import io.github.kamui2040.vectorint.data.SettingsRepository
import io.github.kamui2040.vectorint.data.UserSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutoBackupRunnerTest {
    private val now = Instant.parse("2026-09-12T10:11:12.123Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `configured run writes and verifies a current backup`() =
        runBlocking {
            val automaticSettings =
                RunnerAutoBackupSettingsRepository(
                    AutoBackupConfiguration(destinationTreeUri = "content://documents/tree/backups"),
                )
            val documents = RecordingDirectoryGateway()
            val runner = runner(automaticSettings, documents)

            assertEquals(AutoBackupRunResult.SAVED, runner.run())

            assertEquals("content://documents/tree/backups", documents.treeUri)
            assertEquals("vectorint-auto-backup-20260912T101112.123Z-a1b2c3d4.json", documents.fileName)
            assertEquals(10, documents.keepLatest)
            val expected =
                (backupCoordinator().createBackup() as BackupCreationResult.Ready).bytes
            assertArrayEquals(expected, documents.bytes)
            assertEquals(AutoBackupLastResult.SUCCEEDED, automaticSettings.current.lastResult)
            assertEquals(now, automaticSettings.current.lastAttemptAt)
        }

    @Test
    fun `run without a destination writes nothing and stays neutral`() =
        runBlocking {
            val automaticSettings = RunnerAutoBackupSettingsRepository(AutoBackupConfiguration(afterChanges = true))
            val documents = RecordingDirectoryGateway()

            assertEquals(AutoBackupRunResult.SKIPPED, runner(automaticSettings, documents).run())

            assertEquals(0, documents.writeCalls)
            assertEquals(AutoBackupLastResult.NONE, automaticSettings.current.lastResult)
        }

    @Test
    fun `failed destination is recorded without claiming a backup`() =
        runBlocking {
            val automaticSettings =
                RunnerAutoBackupSettingsRepository(
                    AutoBackupConfiguration(destinationTreeUri = "content://documents/tree/backups"),
                )
            val documents = RecordingDirectoryGateway(failure = IOException("synthetic provider failure"))

            assertEquals(AutoBackupRunResult.FAILED, runner(automaticSettings, documents).run())

            assertEquals(AutoBackupLastResult.FAILED, automaticSettings.current.lastResult)
            assertEquals(now, automaticSettings.current.lastAttemptAt)
        }

    @Test
    fun `cancellation during a document write is never converted into failure`() {
        val automaticSettings =
            RunnerAutoBackupSettingsRepository(
                AutoBackupConfiguration(destinationTreeUri = "content://documents/tree/backups"),
            )
        val documents = RecordingDirectoryGateway(failure = CancellationException("synthetic cancellation"))

        assertThrows(CancellationException::class.java) {
            runBlocking { runner(automaticSettings, documents).run() }
        }
        assertEquals(AutoBackupLastResult.NONE, automaticSettings.current.lastResult)
    }

    @Test
    fun `managed file policy accepts only Vectorint automatic backup names`() {
        assertTrue(AutoBackupFilePolicy.isManagedFileName("vectorint-auto-backup-20260912T101112.123Z-a1b2c3d4.json"))
        assertEquals(false, AutoBackupFilePolicy.isManagedFileName("vectorint-backup-v1.json"))
        assertEquals(false, AutoBackupFilePolicy.isManagedFileName("vectorint-auto-backup-not-a-date-a1b2c3d4.json"))
        assertEquals(false, AutoBackupFilePolicy.isManagedFileName("notes.json"))
    }

    private fun runner(
        automaticSettings: AutoBackupSettingsRepository,
        documents: AutoBackupDirectoryGateway,
    ) = AutoBackupRunner(
        settingsRepository = automaticSettings,
        backupCoordinator = backupCoordinator(),
        directoryGateway = documents,
        clock = clock,
        idSource = { "a1b2c3d4" },
    )

    private fun backupCoordinator(): BackupCoordinator =
        BackupCoordinator(
            dataRepository = EmptyBackupDataRepository(),
            settingsRepository = RunnerUserSettingsRepository(),
            clock = clock,
        )
}

private class EmptyBackupDataRepository : BackupDataRepository {
    override suspend fun loadBackupData(): BackupData = BackupData(null, emptyList(), emptyList())

    override suspend fun replaceBackupData(data: BackupData) = Unit
}

private class RunnerUserSettingsRepository : SettingsRepository {
    override val settings: Flow<UserSettings> = MutableStateFlow(UserSettings())

    override suspend fun setIncludeExpectedIncome(include: Boolean) = Unit

    override suspend fun save(settings: UserSettings) = Unit
}

private class RunnerAutoBackupSettingsRepository(
    initial: AutoBackupConfiguration,
) : AutoBackupSettingsRepository {
    private val mutableState = MutableStateFlow(AutoBackupState(configuration = initial))
    val current: AutoBackupState
        get() = mutableState.value

    override val state: Flow<AutoBackupState> = mutableState

    override suspend fun saveConfiguration(configuration: AutoBackupConfiguration) {
        mutableState.value = mutableState.value.copy(configuration = configuration)
    }

    override suspend fun recordResult(
        result: AutoBackupLastResult,
        attemptedAt: Instant,
    ) {
        mutableState.value = mutableState.value.copy(lastResult = result, lastAttemptAt = attemptedAt)
    }
}

private class RecordingDirectoryGateway(
    private val failure: Exception? = null,
) : AutoBackupDirectoryGateway {
    var writeCalls = 0
    var treeUri: String? = null
    var fileName: String? = null
    var bytes: ByteArray? = null
    var keepLatest: Int? = null

    override suspend fun writeVerified(
        treeUri: String,
        fileName: String,
        bytes: ByteArray,
        keepLatest: Int,
    ) {
        writeCalls++
        failure?.let { throw it }
        this.treeUri = treeUri
        this.fileName = fileName
        this.bytes = bytes
        this.keepLatest = keepLatest
    }
}
