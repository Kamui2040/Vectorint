package io.github.kamui2040.vectorint.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import io.github.kamui2040.vectorint.core.CalculationPolicy
import io.github.kamui2040.vectorint.data.ColorPalette
import io.github.kamui2040.vectorint.data.ThemeMode
import io.github.kamui2040.vectorint.data.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class DataStoreSettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var dataStoreFile: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DataStoreSettingsRepository

    @Before
    fun createRepository() {
        dataStoreFile = File(temporaryFolder.newFolder("settings"), "vectorint_settings.preferences_pb")
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore =
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = { dataStoreFile },
            )
        repository = DataStoreSettingsRepository(dataStore)
    }

    @After
    fun closeDataStore() {
        dataStoreScope.cancel()
    }

    @Test
    fun `missing preference defaults to excluding expected income`() =
        runBlocking {
            val settings = repository.settings.first()

            assertEquals(UserSettings(), settings)
            assertFalse(settings.includeExpectedIncome)
            assertEquals(CalculationPolicy(includeExpectedIncome = false), settings.calculationPolicy)
        }

    @Test
    fun `expected income opt-in is persisted and exposed as calculation policy`() =
        runBlocking {
            repository.setIncludeExpectedIncome(true)
            val settings = repository.settings.first()

            assertTrue(settings.includeExpectedIncome)
            assertEquals(CalculationPolicy(includeExpectedIncome = true), settings.calculationPolicy)
            assertTrue(dataStoreFile.isFile)
            assertTrue(dataStoreFile.length() > 0)
        }

    @Test
    fun `expected income can be disabled after it was enabled`() =
        runBlocking {
            repository.setIncludeExpectedIncome(true)
            repository.setIncludeExpectedIncome(false)

            assertEquals(UserSettings(includeExpectedIncome = false), repository.settings.first())
        }

    @Test
    fun `theme and palette are persisted with the calculation choice`() =
        runBlocking {
            val changed =
                UserSettings(
                    includeExpectedIncome = true,
                    themeMode = ThemeMode.DARK,
                    colorPalette = ColorPalette.NEBULA,
                )

            repository.save(changed)

            assertEquals(changed, repository.settings.first())
        }

    @Test
    fun `updating expected income preserves unrelated preferences`() =
        runBlocking {
            val futurePreference = intPreferencesKey("future_test_preference")
            dataStore.edit { preferences -> preferences[futurePreference] = 7 }

            repository.setIncludeExpectedIncome(true)

            assertEquals(7, dataStore.data.first()[futurePreference])
        }

    @Test
    fun `storage failures are not replaced with permissive settings`() {
        val failure = IOException("synthetic storage failure")
        val failingRepository = DataStoreSettingsRepository(FailingDataStore(failure))

        val readFailure =
            assertThrows(IOException::class.java) {
                runBlocking { failingRepository.settings.first() }
            }
        val writeFailure =
            assertThrows(IOException::class.java) {
                runBlocking { failingRepository.setIncludeExpectedIncome(true) }
            }
        val saveFailure =
            assertThrows(IOException::class.java) {
                runBlocking { failingRepository.save(UserSettings(themeMode = ThemeMode.LIGHT)) }
            }

        assertSame(failure, readFailure)
        assertSame(failure, writeFailure)
        assertSame(failure, saveFailure)
    }

    private class FailingDataStore(
        private val failure: IOException,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw failure }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw failure
    }
}
