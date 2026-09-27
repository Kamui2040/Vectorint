package io.github.kamui2040.vectorint.receipt

import io.github.kamui2040.vectorint.core.CurrencyCode
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptTextRowAssemblerTest {
    @Test
    fun `joins columns that share one printed receipt row`() {
        val text =
            ReceiptTextRowAssembler.assemble(
                fragments =
                    listOf(
                        fragment("7,46", left = 420, top = 102, right = 485, bottom = 128),
                        fragment("SUMME", left = 20, top = 100, right = 105, bottom = 126),
                        fragment("EUR", left = 300, top = 101, right = 350, bottom = 127),
                        fragment("7,46", left = 420, top = 153, right = 485, bottom = 179),
                        fragment("Card payment", left = 20, top = 151, right = 180, bottom = 177),
                        fragment("EUR", left = 300, top = 152, right = 350, bottom = 178),
                    ),
                fallbackText = "unused",
            )

        assertEquals("SUMME EUR 7,46\nCard payment EUR 7,46", text)
        assertEquals(
            746L,
            ReceiptTotalExtractor.extract(text, CurrencyCode.of("EUR"))?.minorUnits,
        )
    }

    @Test
    fun `keeps nearby printed rows separate`() {
        val text =
            ReceiptTextRowAssembler.assemble(
                fragments =
                    listOf(
                        fragment("TOTAL", left = 10, top = 100, right = 80, bottom = 120),
                        fragment("12,34", left = 200, top = 101, right = 260, bottom = 121),
                        fragment("Tax", left = 10, top = 128, right = 50, bottom = 148),
                        fragment("2,34", left = 200, top = 129, right = 250, bottom = 149),
                    ),
                fallbackText = "unused",
            )

        assertEquals("TOTAL 12,34\nTax 2,34", text)
    }

    @Test
    fun `uses plain OCR text when positions are unavailable`() {
        assertEquals(
            "TOTAL 12,34",
            ReceiptTextRowAssembler.assemble(emptyList(), "TOTAL 12,34"),
        )
    }

    private fun fragment(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) = ReceiptTextFragment(text, left, top, right, bottom)
}
