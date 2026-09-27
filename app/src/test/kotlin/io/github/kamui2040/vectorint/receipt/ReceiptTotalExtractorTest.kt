package io.github.kamui2040.vectorint.receipt

import io.github.kamui2040.vectorint.core.CurrencyCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptTotalExtractorTest {
    private val eur = CurrencyCode.of("EUR")

    @Test
    fun `extracts labelled totals with comma or dot decimals`() {
        assertEquals(1_234L, ReceiptTotalExtractor.extract("TOTAL 12,34 EUR", eur)?.minorUnits)
        assertEquals(1_234L, ReceiptTotalExtractor.extract("Amount due $12.34", eur)?.minorUnits)
    }

    @Test
    fun `handles grouped receipt amounts`() {
        assertEquals(123_456L, ReceiptTotalExtractor.extract("Gesamtbetrag 1.234,56 EUR", eur)?.minorUnits)
        assertEquals(123_456L, ReceiptTotalExtractor.extract("Grand total 1,234.56", eur)?.minorUnits)
        assertEquals(123_400L, ReceiptTotalExtractor.extract("TOTAL 1.234 EUR", eur)?.minorUnits)
    }

    @Test
    fun `supports a total on the line after its label`() {
        assertEquals(4_250L, ReceiptTotalExtractor.extract("Montant total\n42,50 €", eur)?.minorUnits)
    }

    @Test
    fun `ignores subtotals and prefers a stronger payable label`() {
        val text = "Subtotal 18.00\nTax 3.42\nTOTAL 21.42\nAmount due 20.00"

        assertEquals(2_000L, ReceiptTotalExtractor.extract(text, eur)?.minorUnits)
    }

    @Test
    fun `uses clear summe when a later tax breakdown has multiple totals`() {
        val text =
            """
            SUMME EUR 29,49
            Geg. BAR EUR 100,00
            Rückgeld BAR EUR 70,51
            Gesamtbetrag 25,48 4,01 29,49
            """.trimIndent()

        assertEquals(2_949L, ReceiptTotalExtractor.extract(text, eur)?.minorUnits)
    }

    @Test
    fun `does not join multiple amounts on one labelled line`() {
        assertNull(ReceiptTotalExtractor.extract("Gesamtbetrag 25,48 4,01 29,49", eur))
    }

    @Test
    fun `does not guess when equally strong totals disagree`() {
        assertNull(ReceiptTotalExtractor.extract("TOTAL 12.00\nTOTAL 13.00", eur))
    }

    @Test
    fun `does not guess from unlabelled prices`() {
        assertNull(ReceiptTotalExtractor.extract("Coffee 3.50\nCake 4.20", eur))
    }

    @Test
    fun `supports currencies without fractional units`() {
        val jpy = CurrencyCode.of("JPY")

        assertEquals(1_234L, ReceiptTotalExtractor.extract("TOTAL 1,234 JPY", jpy)?.minorUnits)
    }
}
