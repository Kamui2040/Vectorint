package io.github.kamui2040.vectorint.backup

import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.ActivitySource
import io.github.kamui2040.vectorint.core.ActivityState
import io.github.kamui2040.vectorint.core.AvailableFundsCalculator
import io.github.kamui2040.vectorint.core.AvailableFundsResult
import io.github.kamui2040.vectorint.core.BudgetMonth
import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.CurrentFunds
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.Money
import io.github.kamui2040.vectorint.core.RecurringItemId
import io.github.kamui2040.vectorint.data.BackupData
import io.github.kamui2040.vectorint.data.BackupDataRepository
import io.github.kamui2040.vectorint.data.ColorPalette
import io.github.kamui2040.vectorint.data.SettingsRepository
import io.github.kamui2040.vectorint.data.ThemeMode
import io.github.kamui2040.vectorint.data.UserSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupCoordinatorTest {
    private val codec = VectorintBackupCodec()
    private val eur = CurrencyCode.of("EUR")
    private val oldData = BackupData(CurrentFunds(Money(90_000, eur), Instant.EPOCH), emptyList(), emptyList())
    private val newData =
        BackupData(
            currentFunds = CurrentFunds(Money(60_000, eur), Instant.parse("2026-09-03T10:00:00Z")),
            activities =
                listOf(
                    ActivityEntry(
                        id = ActivityId("rent-occurrence"),
                        name = "Rent",
                        direction = Direction.EXPENSE,
                        amount = Money(40_000, eur),
                        state = ActivityState.CONFIRMED,
                        budgetMonth = BudgetMonth(YearMonth.of(2026, 9)),
                        bookedAt = Instant.parse("2026-09-02T08:00:00Z"),
                        source = ActivitySource.Recurring(RecurringItemId("deleted-rent"), "2026-09"),
                    ),
                ),
            recurringItems = emptyList(),
        )

    @Test
    fun `backup creation includes one consistent data and settings snapshot`() =
        runBlocking {
            val data = FakeDataRepository(newData)
            val userSettings =
                UserSettings(
                    includeExpectedIncome = true,
                    themeMode = ThemeMode.DARK,
                    colorPalette = ColorPalette.NEBULA,
                )
            val settings = FakeSettingsRepository(userSettings)
            val coordinator = coordinator(data, settings)

            val result = coordinator.createBackup() as BackupCreationResult.Ready
            val decoded = codec.decode(result.bytes)

            assertEquals(newData, decoded.data)
            assertEquals(userSettings, decoded.settings)
            assertEquals(Instant.parse("2026-09-06T12:00:00Z"), decoded.createdAt)
        }

    @Test
    fun `invalid backup changes neither data nor settings`() =
        runBlocking {
            val data = FakeDataRepository(oldData)
            val settings = FakeSettingsRepository(UserSettings())

            assertEquals(
                BackupRestoreResult.InvalidBackup,
                coordinator(data, settings).restore("not-json".toByteArray()),
            )
            assertEquals(oldData, data.data)
            assertEquals(UserSettings(), settings.value)
            assertEquals(0, data.replaceCalls)
            assertEquals(0, settings.writeCalls)
        }

    @Test
    fun `successful restore replaces the complete dataset and setting`() =
        runBlocking {
            val data = FakeDataRepository(oldData)
            val settings = FakeSettingsRepository(UserSettings())

            assertEquals(BackupRestoreResult.Restored, coordinator(data, settings).restore(newBackupBytes()))
            assertEquals(newData, data.data)
            assertEquals(UserSettings(includeExpectedIncome = true), settings.value)
        }

    @Test
    fun `current backup restores appearance settings`() =
        runBlocking {
            val data = FakeDataRepository(oldData)
            val settings = FakeSettingsRepository(UserSettings())
            val restoredSettings =
                UserSettings(
                    includeExpectedIncome = true,
                    themeMode = ThemeMode.DARK,
                    colorPalette = ColorPalette.NOVA,
                )

            assertEquals(
                BackupRestoreResult.Restored,
                coordinator(data, settings).restore(newBackupBytes(restoredSettings)),
            )
            assertEquals(restoredSettings, settings.value)
        }

    @Test
    fun `older backup changes calculation setting without replacing current appearance`() =
        runBlocking {
            val data = FakeDataRepository(oldData)
            val currentSettings =
                UserSettings(
                    includeExpectedIncome = false,
                    themeMode = ThemeMode.DARK,
                    colorPalette = ColorPalette.NEBULA,
                )
            val settings = FakeSettingsRepository(currentSettings)
            val versionThreeBytes =
                newBackupBytes()
                    .toString(Charsets.UTF_8)
                    .replace("\"version\":${VectorintBackupContract.VERSION}", "\"version\":3")
                    .replace(",\"name\":\"Rent\"", "")
                    .replace(",\"customCategories\":[]", "")
                    .replace(",\"categoryId\":null", "")
                    .replace(",\"themeMode\":\"system\",\"colorPalette\":\"orbit\"", "")
                    .toByteArray(Charsets.UTF_8)

            assertEquals(BackupRestoreResult.Restored, coordinator(data, settings).restore(versionThreeBytes))
            assertEquals(currentSettings.copy(includeExpectedIncome = true), settings.value)
        }

    @Test
    fun `settings failure restores and verifies the previous state`() =
        runBlocking {
            val data = FakeDataRepository(oldData)
            val settings = FakeSettingsRepository(UserSettings(), failWrites = setOf(1))

            assertEquals(BackupRestoreResult.StorageFailed, coordinator(data, settings).restore(newBackupBytes()))
            assertEquals(oldData, data.data)
            assertEquals(UserSettings(), settings.value)
            assertEquals(2, data.replaceCalls)
            assertEquals(2, settings.writeCalls)
        }

    @Test
    fun `database failure restores and verifies the previous state`() =
        runBlocking {
            val data = FakeDataRepository(oldData, failReplaces = setOf(1))
            val settings = FakeSettingsRepository(UserSettings())

            assertEquals(BackupRestoreResult.StorageFailed, coordinator(data, settings).restore(newBackupBytes()))
            assertEquals(oldData, data.data)
            assertEquals(UserSettings(), settings.value)
        }

    @Test
    fun `failed compensation is reported as recovery failure`() =
        runBlocking {
            val data = FakeDataRepository(oldData, failReplaces = setOf(2))
            val settings = FakeSettingsRepository(UserSettings(), failWrites = setOf(1))

            assertEquals(BackupRestoreResult.RecoveryFailed, coordinator(data, settings).restore(newBackupBytes()))
            assertEquals(UserSettings(), settings.value)
            assertEquals(2, settings.writeCalls)
        }

    @Test
    fun `restore verification mismatch is compensated`() =
        runBlocking {
            val data = FakeDataRepository(oldData, ignoredReplaces = setOf(1))
            val settings = FakeSettingsRepository(UserSettings())

            assertEquals(BackupRestoreResult.StorageFailed, coordinator(data, settings).restore(newBackupBytes()))
            assertEquals(oldData, data.data)
            assertEquals(UserSettings(), settings.value)
        }

    @Test
    fun `restored confirmed occurrence is not doubled or replayed across its baseline`() =
        runBlocking {
            val data = FakeDataRepository(oldData)
            val settings = FakeSettingsRepository(UserSettings())
            assertEquals(BackupRestoreResult.Restored, coordinator(data, settings).restore(newBackupBytes()))

            val restored = data.data
            val result =
                AvailableFundsCalculator.calculate(
                    currentFunds = requireNotNull(restored.currentFunds),
                    month = BudgetMonth(YearMonth.of(2026, 9)),
                    activity = restored.activities,
                ) as AvailableFundsResult.Available

            assertEquals(1, restored.activities.size)
            assertEquals(60_000, result.availableNow.minorUnits)
        }

    @Test
    fun `cancellation is never converted into a restore result`() {
        val data = FakeDataRepository(oldData, loadFailure = CancellationException("synthetic cancellation"))
        val settings = FakeSettingsRepository(UserSettings())

        assertThrows(CancellationException::class.java) {
            runBlocking { coordinator(data, settings).restore(newBackupBytes()) }
        }
    }

    @Test
    fun `cancellation during coordinated writes restores prior state before propagating`() {
        val data = FakeDataRepository(oldData)
        val settings = FakeSettingsRepository(UserSettings(), cancelWrites = setOf(1))

        assertThrows(CancellationException::class.java) {
            runBlocking { coordinator(data, settings).restore(newBackupBytes()) }
        }
        assertEquals(oldData, data.data)
        assertEquals(UserSettings(), settings.value)
        assertEquals(2, data.replaceCalls)
        assertEquals(2, settings.writeCalls)
    }

    private fun coordinator(
        data: BackupDataRepository,
        settings: SettingsRepository,
    ) = BackupCoordinator(
        dataRepository = data,
        settingsRepository = settings,
        codec = codec,
        clock = Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"), ZoneOffset.UTC),
    )

    private fun newBackupBytes(settings: UserSettings = UserSettings(includeExpectedIncome = true)): ByteArray =
        codec.encode(
            VectorintBackup(
                createdAt = Instant.parse("2026-09-06T12:00:00Z"),
                data = newData,
                settings = settings,
            ),
        )
}

private class FakeDataRepository(
    var data: BackupData,
    private val failReplaces: Set<Int> = emptySet(),
    private val ignoredReplaces: Set<Int> = emptySet(),
    private val loadFailure: Exception? = null,
) : BackupDataRepository {
    var replaceCalls = 0

    override suspend fun loadBackupData(): BackupData {
        loadFailure?.let { throw it }
        return data
    }

    override suspend fun replaceBackupData(data: BackupData) {
        replaceCalls++
        if (replaceCalls in failReplaces) throw IOException("synthetic database failure")
        if (replaceCalls !in ignoredReplaces) this.data = data
    }
}

private class FakeSettingsRepository(
    initial: UserSettings,
    private val failWrites: Set<Int> = emptySet(),
    private val cancelWrites: Set<Int> = emptySet(),
) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    val value: UserSettings
        get() = state.value
    var writeCalls = 0

    override val settings: Flow<UserSettings> = state

    override suspend fun setIncludeExpectedIncome(include: Boolean) {
        writeCalls++
        if (writeCalls in cancelWrites) throw CancellationException("synthetic settings cancellation")
        if (writeCalls in failWrites) throw IOException("synthetic settings failure")
        state.value = state.value.copy(includeExpectedIncome = include)
    }

    override suspend fun save(settings: UserSettings) {
        writeCalls++
        if (writeCalls in cancelWrites) throw CancellationException("synthetic settings cancellation")
        if (writeCalls in failWrites) throw IOException("synthetic settings failure")
        state.value = settings
    }
}
