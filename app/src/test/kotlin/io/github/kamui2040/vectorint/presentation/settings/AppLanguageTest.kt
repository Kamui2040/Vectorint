package io.github.kamui2040.vectorint.presentation.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun `supported language tags map to in-app choices`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag("en-US"))
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromLanguageTag("de-DE"))
        assertEquals(AppLanguage.PORTUGUESE, AppLanguage.fromLanguageTag("pt-PT"))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromLanguageTag("es-ES"))
        assertEquals(AppLanguage.ITALIAN, AppLanguage.fromLanguageTag("it-IT"))
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromLanguageTag("fr-FR"))
    }

    @Test
    fun `empty or unsupported language tags follow the device`() {
        assertEquals(AppLanguage.FOLLOW_DEVICE, AppLanguage.fromLanguageTag(null))
        assertEquals(AppLanguage.FOLLOW_DEVICE, AppLanguage.fromLanguageTag(""))
        assertEquals(AppLanguage.FOLLOW_DEVICE, AppLanguage.fromLanguageTag("ja"))
    }
}
