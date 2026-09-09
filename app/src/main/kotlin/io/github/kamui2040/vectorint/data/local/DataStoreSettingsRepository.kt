package io.github.kamui2040.vectorint.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.kamui2040.vectorint.data.ColorPalette
import io.github.kamui2040.vectorint.data.SettingsRepository
import io.github.kamui2040.vectorint.data.ThemeMode
import io.github.kamui2040.vectorint.data.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val SETTINGS_DATA_STORE_NAME = "vectorint_settings"
private val INCLUDE_EXPECTED_INCOME = booleanPreferencesKey("include_expected_income")
private val THEME_MODE = stringPreferencesKey("theme_mode")
private val COLOR_PALETTE = stringPreferencesKey("color_palette")

internal val Context.vectorintSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_DATA_STORE_NAME,
)

internal class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<UserSettings> =
        dataStore.data
            .map { preferences ->
                UserSettings(
                    includeExpectedIncome = preferences[INCLUDE_EXPECTED_INCOME] ?: false,
                    themeMode = preferences[THEME_MODE].toThemeMode(),
                    colorPalette = preferences[COLOR_PALETTE].toColorPalette(),
                )
            }.distinctUntilChanged()

    override suspend fun setIncludeExpectedIncome(include: Boolean) {
        dataStore.edit { preferences ->
            preferences[INCLUDE_EXPECTED_INCOME] = include
        }
    }

    override suspend fun save(settings: UserSettings) {
        dataStore.edit { preferences ->
            preferences[INCLUDE_EXPECTED_INCOME] = settings.includeExpectedIncome
            preferences[THEME_MODE] = settings.themeMode.name
            preferences[COLOR_PALETTE] = settings.colorPalette.name
        }
    }
}

private fun String?.toThemeMode(): ThemeMode = ThemeMode.entries.firstOrNull { it.name == this } ?: ThemeMode.FOLLOW_SYSTEM

private fun String?.toColorPalette(): ColorPalette = ColorPalette.entries.firstOrNull { it.name == this } ?: ColorPalette.ORBIT
