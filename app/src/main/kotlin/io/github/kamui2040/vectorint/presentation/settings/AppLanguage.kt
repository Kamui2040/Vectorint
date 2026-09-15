package io.github.kamui2040.vectorint.presentation.settings

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.LocaleListCompat
import io.github.kamui2040.vectorint.R

internal enum class AppLanguage(
    @StringRes val labelResource: Int,
    val languageTag: String?,
) {
    FOLLOW_DEVICE(R.string.settings_language_device, null),
    ENGLISH(R.string.settings_language_english, "en"),
    GERMAN(R.string.settings_language_german, "de"),
    ;

    companion object {
        fun fromLanguageTag(languageTag: String?): AppLanguage =
            when (languageTag?.substringBefore('-')?.lowercase()) {
                "en" -> ENGLISH
                "de" -> GERMAN
                else -> FOLLOW_DEVICE
            }
    }
}

@Composable
internal fun currentAppLanguage(): AppLanguage {
    LocalConfiguration.current
    return AppLanguage.fromLanguageTag(
        AppCompatDelegate
            .getApplicationLocales()
            .get(0)
            ?.toLanguageTag(),
    )
}

internal fun applyAppLanguage(language: AppLanguage) {
    val locales =
        language.languageTag?.let(LocaleListCompat::forLanguageTags)
            ?: LocaleListCompat.getEmptyLocaleList()
    AppCompatDelegate.setApplicationLocales(locales)
}
