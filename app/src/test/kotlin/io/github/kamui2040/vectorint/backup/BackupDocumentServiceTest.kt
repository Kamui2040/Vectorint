package io.github.kamui2040.vectorint.backup

import android.net.Uri
import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.CurrentFunds
import io.github.kamui2040.vectorint.core.Money
import io.github.kamui2040.vectorint.data.BackupData
import io.github.kamui2040.vectorint.data.BackupDataRepository
import io.github.kamui2040.vectorint.data.SettingsRepository
import io.github.kamui2040.vectorint.data.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
class BackupDocumentServiceTest {
    private val uri = Uri.parse("content://example.invalid/vectorint-backup")
    private val codec = VectorintBackupCodec()
    private val emptyData = BackupData(null, emptyList(), emptyList())

    @Test
    fun `export writes a complete supported document`() =
        runBlocking {
            val documents = FakeDocumentGateway()
            val service = service(FakeDocumentDataRepository(emptyData), FakeDocumentSettingsRepository(UserSettings()), documents)

            assertEquals(BackupExportResult.Exported, service.export(uri))
            val written = requireNotNull(documents.written)
            assertEquals(emptyData, codec.decode(written).data)
        }

    @Test
    fun `document write failure remains an export failure`() =
        runBlocking {
            val documents = FakeDocumentGateway(writeFailure = IOException("synthetic write failure"))
            val service = service(FakeDocumentDataRepository(emptyData), FakeDocumentSettingsRepository(UserSettings()), documents)

            assertEquals(BackupExportResult.Failed, service.export(uri))
        }

    @Test
    fun `invalid document is rejected before repository replacement`() =
        runBlocking {
            val data = FakeDocumentDataRepository(emptyData)
            val documents = FakeDocumentGateway(readBytes = "not-json".toByteArray())
            val service = service(data, FakeDocumentSettingsRepository(UserSettings()), documents)

            assertEquals(BackupRestoreResult.InvalidBackup, service.restore(uri))
            assertEquals(0, data.replaceCalls)
        }

    @Test
    fun `valid document replaces data and settings`() =
        runBlocking {
            val eur = CurrencyCode.of("EUR")
            val restored =
                BackupData(
                    currentFunds = CurrentFunds(Money(75_000, eur), Instant.parse("2026-09-01T10:00:00Z")),
                    activities = emptyList(),
                    recurringItems = emptyList(),
                )
            val bytes =
                codec.encode(
                    VectorintBackup(
                        createdAt = Instant.parse("2026-09-06T12:00:00Z"),
                        data = restored,
                        settings = UserSettings(includeExpectedIncome = true),
                    ),
                )
            val data = FakeDocumentDataRepository(emptyData)
            val settings = FakeDocumentSettingsRepository(UserSettings())
            val service = service(data, settings, FakeDocumentGateway(readBytes = bytes))

            assertEquals(BackupRestoreResult.Restored, service.restore(uri))
            assertEquals(restored, data.data)
            assertEquals(UserSettings(includeExpectedIncome = true), settings.value)
        }

    private fun service(
        data: BackupDataRepository,
        settings: SettingsRepository,
        documents: BackupDocumentGateway,
    ): BackupDocumentService =
        BackupDocumentService(
            coordinator =
                BackupCoordinator(
                    dataRepository = data,
                    settingsRepository = settings,
                    codec = codec,
                    clock = Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"), ZoneOffset.UTC),
                ),
            documents = documents,
        )
}

private class FakeDocumentGateway(
    private val readBytes: ByteArray = byteArrayOf(),
    private val writeFailure: Exception? = null,
) : BackupDocumentGateway {
    var written: ByteArray? = null

    override suspend fun write(
        uri: Uri,
        bytes: ByteArray,
    ) {
        writeFailure?.let { throw it }
        written = bytes
    }

    override suspend fun read(uri: Uri): ByteArray = readBytes
}

private class FakeDocumentDataRepository(
    var data: BackupData,
) : BackupDataRepository {
    var replaceCalls = 0

    override suspend fun loadBackupData(): BackupData = data

    override suspend fun replaceBackupData(data: BackupData) {
        replaceCalls++
        this.data = data
    }
}

private class FakeDocumentSettingsRepository(
    initial: UserSettings,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    val value: UserSettings
        get() = state.value
    override val settings: Flow<UserSettings> = state

    override suspend fun setIncludeExpectedIncome(include: Boolean) {
        state.value = state.value.copy(includeExpectedIncome = include)
    }

    override suspend fun save(settings: UserSettings) {
        state.value = settings
    }
}
