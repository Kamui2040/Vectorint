package io.github.kamui2040.vectorint.data

import kotlinx.coroutines.flow.Flow

internal enum class ThemeMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}

internal enum class ColorPalette {
    ORBIT,
    NOVA,
    NEBULA,
}

internal data class UserSettings(
    val includeExpectedIncome: Boolean = false,
    val showUpcomingEntries: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val colorPalette: ColorPalette = ColorPalette.ORBIT,
)

internal interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setIncludeExpectedIncome(include: Boolean)

    suspend fun save(settings: UserSettings)
}
