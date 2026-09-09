package io.github.kamui2040.vectorint.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class DataStoreReminderDeliveryStateRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DataStoreReminderDeliveryStateRepository

    @Before
    fun createRepository() {
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore =
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = {
                    File(temporaryFolder.newFolder("reminders"), "vectorint_settings.preferences_pb")
                },
            )
        repository = DataStoreReminderDeliveryStateRepository(dataStore)
    }

    @After
    fun closeDataStore() {
        dataStoreScope.cancel()
    }

    @Test
    fun `missing acknowledgement set is empty`() =
        runBlocking {
            assertEquals(emptySet<String>(), repository.loadAcknowledgedKeys())
        }

    @Test
    fun `replacement is exact and can clear all keys`() =
        runBlocking {
            repository.replaceAcknowledgedKeys(setOf("key-a", "key-b"))
            assertEquals(setOf("key-a", "key-b"), repository.loadAcknowledgedKeys())

            repository.replaceAcknowledgedKeys(setOf("key-c"))
            assertEquals(setOf("key-c"), repository.loadAcknowledgedKeys())

            repository.replaceAcknowledgedKeys(emptySet())
            assertEquals(emptySet<String>(), repository.loadAcknowledgedKeys())
        }

    @Test
    fun `acknowledgement replacement preserves unrelated preferences`() =
        runBlocking {
            val unrelated = intPreferencesKey("future_test_preference")
            dataStore.edit { preferences -> preferences[unrelated] = 7 }

            repository.replaceAcknowledgedKeys(setOf("key-a"))

            assertEquals(7, dataStore.data.first()[unrelated])
        }

    @Test
    fun `storage failures remain visible`() {
        val failure = IOException("synthetic storage failure")
        val repository = DataStoreReminderDeliveryStateRepository(FailingReminderDataStore(failure))

        assertSame(
            failure,
            assertThrows(IOException::class.java) {
                runBlocking { repository.loadAcknowledgedKeys() }
            },
        )
        assertSame(
            failure,
            assertThrows(IOException::class.java) {
                runBlocking { repository.replaceAcknowledgedKeys(setOf("key")) }
            },
        )
    }
}

private class FailingReminderDataStore(
    private val failure: IOException,
) : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw failure }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw failure
}
