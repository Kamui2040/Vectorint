package io.github.kamui2040.vectorint.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Locale

class MoneyTest {
    @Test
    fun `currency code normalizes casing and surrounding whitespace`() {
        assertEquals("EUR", CurrencyCode.of(" eur ").value)
    }

    @Test
    fun `currency code normalization is independent of process locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))

            assertEquals("INR", CurrencyCode.of("inr").value)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `currency code rejects malformed values`() {
        assertThrows(IllegalArgumentException::class.java) { CurrencyCode.of("EURO") }
        assertThrows(IllegalArgumentException::class.java) { CurrencyCode.of("€") }
    }

    @Test
    fun `money never mixes currencies`() {
        assertThrows(IllegalArgumentException::class.java) {
            Money(100, CurrencyCode.of("EUR")) + Money(100, CurrencyCode.of("USD"))
        }
    }

    @Test
    fun `money arithmetic detects overflow`() {
        val eur = CurrencyCode.of("EUR")

        assertThrows(ArithmeticException::class.java) {
            Money(Long.MAX_VALUE, eur) + Money(1, eur)
        }
    }
}
