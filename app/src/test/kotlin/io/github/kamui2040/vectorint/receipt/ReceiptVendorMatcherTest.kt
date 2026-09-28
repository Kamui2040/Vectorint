package io.github.kamui2040.vectorint.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptVendorMatcherTest {
    @Test
    fun `recognizes a decorated German receipt header`() {
        val text =
            """
            *** PENNY - MARKT GmbH ***
            Musterstrasse 12
            12345 Musterstadt
            SUMME EUR 4,32
            """.trimIndent()

        assertEquals("penny", ReceiptVendorMatcher.match(text)?.id)
        assertEquals("PENNY", ReceiptVendorMatcher.match(text)?.displayName)
    }

    @Test
    fun `normalizes accents and common ASCII receipt spelling`() {
        assertEquals("aldi-sued", ReceiptVendorMatcher.match("ALDI SUED GmbH & Co. KG")?.id)
        assertEquals("mueller", ReceiptVendorMatcher.match("MUELLER HANDELS GMBH")?.id)
    }

    @Test
    fun `prefers the more specific vendor on the same header line`() {
        assertEquals("globus-baumarkt", ReceiptVendorMatcher.match("GLOBUS BAUMARKT")?.id)
    }

    @Test
    fun `does not guess from ambiguous discount names`() {
        assertNull(ReceiptVendorMatcher.match("ALDI\nMusterstrasse 1"))
        assertNull(ReceiptVendorMatcher.match("NETTO\nMusterstrasse 1"))
    }

    @Test
    fun `does not match store words found outside the receipt header`() {
        val text =
            (1..12).joinToString(separator = "\n") { "Header line $it" } +
                "\nPENNY product 1,99"

        assertNull(ReceiptVendorMatcher.match(text))
    }
}
