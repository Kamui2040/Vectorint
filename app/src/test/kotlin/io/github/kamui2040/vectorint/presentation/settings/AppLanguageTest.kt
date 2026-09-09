package io.github.kamui2040.vectorint.presentation.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun `supported language tags map to in-app choices`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag("en-US"))
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromLanguageTag("de-DE"))
    }

    @Test
    fun `empty or unsupported language tags follow the device`() {
        assertEquals(AppLanguage.FOLLOW_DEVICE, AppLanguage.fromLanguageTag(null))
        assertEquals(AppLanguage.FOLLOW_DEVICE, AppLanguage.fromLanguageTag(""))
        assertEquals(AppLanguage.FOLLOW_DEVICE, AppLanguage.fromLanguageTag("fr"))
    }
}
