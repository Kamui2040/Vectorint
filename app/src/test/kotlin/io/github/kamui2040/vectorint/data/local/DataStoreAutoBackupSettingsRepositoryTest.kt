package io.github.kamui2040.vectorint.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import io.github.kamui2040.vectorint.backup.AutoBackupConfiguration
import io.github.kamui2040.vectorint.backup.AutoBackupInterval
import io.github.kamui2040.vectorint.backup.AutoBackupLastResult
import io.github.kamui2040.vectorint.backup.AutoBackupState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.Instant

class DataStoreAutoBackupSettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DataStoreAutoBackupSettingsRepository

    @Before
    fun createRepository() {
        val dataStoreFile = File(temporaryFolder.newFolder("auto-backup"), "settings.preferences_pb")
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore =
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = { dataStoreFile },
            )
        repository = DataStoreAutoBackupSettingsRepository(dataStore)
    }

    @After
    fun closeDataStore() {
        dataStoreScope.cancel()
    }

    @Test
    fun `automatic backups are opt in and have no destination by default`() =
        runBlocking {
            val state = repository.state.first()

            assertEquals(AutoBackupState(), state)
            assertFalse(state.configuration.hasDestination)
        }

    @Test
    fun `configuration and last result persist independently`() =
        runBlocking {
            val configuration =
                AutoBackupConfiguration(
                    destinationTreeUri = "content://documents/tree/backups",
                    afterChanges = true,
                    onAppStart = true,
                    onAppBackground = true,
                    interval = AutoBackupInterval.DAILY,
                )
            val attemptedAt = Instant.parse("2026-09-12T10:11:12.123456789Z")

            repository.saveConfiguration(configuration)
            repository.recordResult(AutoBackupLastResult.SUCCEEDED, attemptedAt)

            assertEquals(
                AutoBackupState(configuration, AutoBackupLastResult.SUCCEEDED, attemptedAt),
                repository.state.first(),
            )
        }

    @Test
    fun `changing configuration preserves result and unknown preferences`() =
        runBlocking {
            val futurePreference = intPreferencesKey("future_auto_backup_preference")
            val attemptedAt = Instant.parse("2026-09-12T10:11:12Z")
            dataStore.edit { preferences -> preferences[futurePreference] = 7 }
            repository.recordResult(AutoBackupLastResult.FAILED, attemptedAt)

            repository.saveConfiguration(AutoBackupConfiguration(afterChanges = true))

            val state = repository.state.first()
            assertEquals(AutoBackupLastResult.FAILED, state.lastResult)
            assertEquals(attemptedAt, state.lastAttemptAt)
            assertEquals(7, dataStore.data.first()[futurePreference])
        }

    @Test
    fun `storage failures are not replaced with an enabled or permissive state`() {
        val failure = IOException("synthetic storage failure")
        val failingRepository = DataStoreAutoBackupSettingsRepository(FailingAutoBackupDataStore(failure))

        val readFailure =
            assertThrows(IOException::class.java) {
                runBlocking { failingRepository.state.first() }
            }
        val writeFailure =
            assertThrows(IOException::class.java) {
                runBlocking {
                    failingRepository.saveConfiguration(
                        AutoBackupConfiguration(
                            destinationTreeUri = "content://documents/tree/backups",
                            afterChanges = true,
                        ),
                    )
                }
            }

        assertSame(failure, readFailure)
        assertSame(failure, writeFailure)
    }

    private class FailingAutoBackupDataStore(
        private val failure: IOException,
    ) : DataStore<Preferences> {
        override val data = flow<Preferences> { throw failure }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw failure
    }
}
