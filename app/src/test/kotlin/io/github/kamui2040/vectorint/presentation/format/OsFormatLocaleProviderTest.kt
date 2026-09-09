package io.github.kamui2040.vectorint.presentation.format

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class OsFormatLocaleProviderTest {
    @Test
    @Config(sdk = [36])
    fun `format category is independent from display language`() {
        val originalDefault = Locale.getDefault()
        val originalDisplay = Locale.getDefault(Locale.Category.DISPLAY)
        val originalFormat = Locale.getDefault(Locale.Category.FORMAT)
        try {
            Locale.setDefault(Locale.US)
            Locale.setDefault(Locale.Category.DISPLAY, Locale.JAPAN)
            Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY)

            assertEquals(Locale.GERMANY, OsFormatLocaleProvider.currentFormatLocale())
        } finally {
            Locale.setDefault(originalDefault)
            Locale.setDefault(Locale.Category.DISPLAY, originalDisplay)
            Locale.setDefault(Locale.Category.FORMAT, originalFormat)
        }
    }

    @Test
    @Config(sdk = [23])
    fun `pre Android 7 falls back to the process locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)

            assertEquals(Locale.FRANCE, OsFormatLocaleProvider.currentFormatLocale())
        } finally {
            Locale.setDefault(original)
        }
    }
}
