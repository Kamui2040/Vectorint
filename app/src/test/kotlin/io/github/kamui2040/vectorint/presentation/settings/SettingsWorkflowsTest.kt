package io.github.kamui2040.vectorint.presentation.settings

import io.github.kamui2040.vectorint.data.ColorPalette
import io.github.kamui2040.vectorint.data.SettingsRepository
import io.github.kamui2040.vectorint.data.ThemeMode
import io.github.kamui2040.vectorint.data.UserSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class SettingsWorkflowsTest {
    @Test
    fun `settings load and save preserve the conservative default`() =
        runBlocking {
            val repository = FakeSettingsRepository(flowOf(UserSettings()))
            val editor = SettingsEditor(repository)

            assertEquals(
                SettingsLoadResult.Ready(UserSettings(includeExpectedIncome = false)),
                editor.load(),
            )
            val changed =
                UserSettings(
                    includeExpectedIncome = true,
                    themeMode = ThemeMode.DARK,
                    colorPalette = ColorPalette.NEBULA,
                )
            assertEquals(SettingsSaveResult.Saved, editor.save(changed))
            assertEquals(listOf(changed), repository.writes)
        }

    @Test
    fun `storage failures stay visible and cancellation is never converted`() {
        val failure = IOException("synthetic settings failure")
        assertEquals(
            SettingsLoadResult.Failed,
            runBlocking {
                SettingsEditor(FakeSettingsRepository(flow { throw failure })).load()
            },
        )
        assertEquals(
            SettingsSaveResult.StorageFailed,
            runBlocking {
                SettingsEditor(
                    FakeSettingsRepository(flowOf(UserSettings()), saveFailure = failure),
                ).save(UserSettings(includeExpectedIncome = true))
            },
        )
        assertThrows(CancellationException::class.java) {
            runBlocking {
                SettingsEditor(
                    FakeSettingsRepository(flow { throw CancellationException("synthetic cancellation") }),
                ).load()
            }
        }
    }
}

private class FakeSettingsRepository(
    override val settings: Flow<UserSettings>,
    private val saveFailure: Exception? = null,
) : SettingsRepository {
    val writes = mutableListOf<UserSettings>()

    override suspend fun setIncludeExpectedIncome(include: Boolean) {
        saveFailure?.let { throw it }
    }

    override suspend fun save(settings: UserSettings) {
        saveFailure?.let { throw it }
        writes += settings
    }
}
