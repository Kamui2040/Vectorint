package io.github.kamui2040.vectorint.presentation.format

import io.github.kamui2040.vectorint.core.BudgetMonth
import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RegionalFormattingTest {
    private val usd = CurrencyCode.of("USD")
    private val eur = CurrencyCode.of("EUR")

    @Test
    fun `money formatting follows the supplied format locale`() {
        val locale = MutableFormatLocaleProvider(Locale.US)
        val formatter = RegionalFormatter(locale)

        assertEquals("$1,234.56", formatter.formatMoney(Money(123_456, usd)).normalizedSpaces())

        locale.value = Locale.GERMANY
        assertEquals("1.234,56 €", formatter.formatMoney(Money(123_456, eur)).normalizedSpaces())
    }

    @Test
    fun `money formatting preserves negative values`() {
        val formatter = RegionalFormatter { Locale.US }

        assertEquals("-€42.00", formatter.formatMoney(Money(-4_200, eur)).normalizedSpaces())
    }

    @Test
    fun `money input formatting is regional exact and never grouped`() {
        val locale = MutableFormatLocaleProvider(Locale.US)
        val formatter = RegionalFormatter(locale)

        assertEquals("1234.56", formatter.formatMoneyInput(Money(123_456, usd)))
        locale.value = Locale.GERMANY
        assertEquals("1234,56", formatter.formatMoneyInput(Money(123_456, eur)))
        assertEquals("-12,34", formatter.formatMoneyInput(Money(-1_234, eur)))
    }

    @Test
    fun `money formatting rejects unsupported currency metadata`() {
        val formatter = RegionalFormatter { Locale.US }

        assertThrows(IllegalArgumentException::class.java) {
            formatter.formatMoney(Money(100, CurrencyCode.of("ZZZ")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            formatter.formatMoney(Money(100, CurrencyCode.of("XXX")))
        }
    }

    @Test
    fun `date and month formatting follow the supplied format locale`() {
        val locale = MutableFormatLocaleProvider(Locale.US)
        val formatter = RegionalFormatter(locale)
        val date = LocalDate.of(2026, 9, 5)
        val month = BudgetMonth(YearMonth.of(2026, 9))

        assertEquals("Sep 5, 2026", formatter.formatDate(date).normalizedSpaces())
        assertEquals("September 2026", formatter.formatMonth(month).normalizedSpaces())

        locale.value = Locale.JAPAN
        assertEquals("2026年9月5日", formatter.formatDate(date).normalizedSpaces())
        assertEquals("2026年9月", formatter.formatMonth(month).normalizedSpaces())
    }

    @Test
    fun `whole number formatting follows the current format locale without grouping`() {
        val locale = MutableFormatLocaleProvider(Locale.US)
        val formatter = RegionalFormatter(locale)

        assertEquals("1234", formatter.formatWholeNumber(1234))
        locale.value = Locale.forLanguageTag("ar-EG")
        assertEquals("١٢٣٤", formatter.formatWholeNumber(1234))
    }

    @Test
    fun `percentage formatting follows the current format locale`() {
        val locale = MutableFormatLocaleProvider(Locale.US)
        val formatter = RegionalFormatter(locale)

        assertEquals("33.3%", formatter.formatPercentage(1, 3).normalizedSpaces())
        locale.value = Locale.GERMANY
        assertEquals("33,3 %", formatter.formatPercentage(1, 3).normalizedSpaces())
    }

    @Test
    fun `whole number parser accepts localized digits and enforces its maximum`() {
        val parser = RegionalWholeNumberInputParser()

        assertEquals(WholeNumberInputResult.Accepted(123), parser.parse("١٢٣", 3660))
        assertEquals(WholeNumberInputResult.Accepted(3660), parser.parse("3660", 3660))
        assertEquals(WholeNumberInputResult.Rejected, parser.parse("3661", 3660))
        assertEquals(WholeNumberInputResult.Rejected, parser.parse("1", 0))
    }

    @Test
    fun `whole number parser rejects signs separators decimals and blank input`() {
        val parser = RegionalWholeNumberInputParser()

        assertEquals(WholeNumberInputResult.Rejected, parser.parse("", 3660))
        assertEquals(WholeNumberInputResult.Rejected, parser.parse("-1", 3660))
        assertEquals(WholeNumberInputResult.Rejected, parser.parse("1,000", 3660))
        assertEquals(WholeNumberInputResult.Rejected, parser.parse("1.5", 3660))
    }

    @Test
    fun `format locale is resolved again for every operation`() {
        val locale = MutableFormatLocaleProvider(Locale.US)
        val formatter = RegionalFormatter(locale)

        assertEquals("$1.00", formatter.formatMoney(Money(100, usd)).normalizedSpaces())
        locale.value = Locale.GERMANY
        assertEquals("1,00 $", formatter.formatMoney(Money(100, usd)).normalizedSpaces())
        assertEquals(2, locale.calls)
    }

    @Test
    fun `parser accepts locale decimal separators without floating point`() {
        assertAccepted("1234.56", usd, Locale.US, 123_456)
        assertAccepted("1234,56", eur, Locale.GERMANY, 123_456)
    }

    @Test
    fun `parser accepts localized decimal digits`() {
        val arabicEgypt = Locale.forLanguageTag("ar-EG")

        assertAccepted("١٢٣٤٫٥٦", usd, arabicEgypt, 123_456)
    }

    @Test
    fun `parser accepts leading decimal and surrounding regional whitespace`() {
        assertAccepted("\u00A0,50\u202F", eur, Locale.GERMANY, 50)
    }

    @Test
    fun `parser rejects grouping and a decimal separator from another locale`() {
        assertRejected(
            "1,234.56",
            usd,
            Locale.US,
            MoneyInputRejection.GROUPING_SEPARATOR_NOT_ALLOWED,
        )
        assertRejected(
            "12,34",
            usd,
            Locale.US,
            MoneyInputRejection.GROUPING_SEPARATOR_NOT_ALLOWED,
        )
        assertRejected(
            "12.34",
            eur,
            Locale.GERMANY,
            MoneyInputRejection.GROUPING_SEPARATOR_NOT_ALLOWED,
        )
        assertRejected(
            "12.34",
            usd,
            Locale.forLanguageTag("ar-EG"),
            MoneyInputRejection.WRONG_DECIMAL_SEPARATOR,
        )
    }

    @Test
    fun `parser enforces each currency fraction scale`() {
        val jpy = CurrencyCode.of("JPY")
        val tnd = CurrencyCode.of("TND")

        assertAccepted("123", jpy, Locale.JAPAN, 123)
        assertRejected("123.0", jpy, Locale.US, MoneyInputRejection.TOO_MANY_FRACTION_DIGITS)
        assertAccepted("1.234", tnd, Locale.US, 1_234)
        assertRejected("1.2340", tnd, Locale.US, MoneyInputRejection.TOO_MANY_FRACTION_DIGITS)
        assertRejected("1.230", usd, Locale.US, MoneyInputRejection.TOO_MANY_FRACTION_DIGITS)
    }

    @Test
    fun `parser rejects blank incomplete repeated and nonnumeric input`() {
        assertRejected("  ", usd, Locale.US, MoneyInputRejection.EMPTY)
        assertRejected("12.", usd, Locale.US, MoneyInputRejection.INVALID_CHARACTER)
        assertRejected("12.3.4", usd, Locale.US, MoneyInputRejection.INVALID_CHARACTER)
        assertRejected("$12.34", usd, Locale.US, MoneyInputRejection.INVALID_CHARACTER)
        assertRejected("+", usd, Locale.US, MoneyInputRejection.INVALID_CHARACTER)
    }

    @Test
    fun `parser applies the requested sign policy`() {
        assertRejected("-12.34", usd, Locale.US, MoneyInputRejection.NEGATIVE_NOT_ALLOWED)
        assertAccepted("+12.34", usd, Locale.US, 1_234)
        assertAccepted("-12.34", usd, Locale.US, -1_234, AmountSignPolicy.SIGNED)
    }

    @Test
    fun `parser accepts the complete signed long range and rejects overflow`() {
        assertAccepted(
            "92233720368547758.07",
            usd,
            Locale.US,
            Long.MAX_VALUE,
            AmountSignPolicy.SIGNED,
        )
        assertAccepted(
            "-92233720368547758.08",
            usd,
            Locale.US,
            Long.MIN_VALUE,
            AmountSignPolicy.SIGNED,
        )
        assertRejected(
            "92233720368547758.08",
            usd,
            Locale.US,
            MoneyInputRejection.OUT_OF_RANGE,
            AmountSignPolicy.SIGNED,
        )
    }

    @Test
    fun `parser rejects unsupported currency metadata`() {
        assertRejected("1", CurrencyCode.of("ZZZ"), Locale.US, MoneyInputRejection.UNSUPPORTED_CURRENCY)
        assertRejected("1", CurrencyCode.of("XXX"), Locale.US, MoneyInputRejection.UNSUPPORTED_CURRENCY)
    }

    @Test
    fun `parser resolves the format locale again for every operation`() {
        val locale = MutableFormatLocaleProvider(Locale.US)
        val parser = RegionalMoneyInputParser(locale)

        assertEquals(MoneyInputResult.Accepted(Money(150, usd)), parser.parse("1.50", usd))
        locale.value = Locale.GERMANY
        assertEquals(
            MoneyInputResult.Rejected(MoneyInputRejection.GROUPING_SEPARATOR_NOT_ALLOWED),
            parser.parse("1.50", usd),
        )
        assertEquals(2, locale.calls)
    }

    private fun assertAccepted(
        input: String,
        currency: CurrencyCode,
        locale: Locale,
        expectedMinorUnits: Long,
        signPolicy: AmountSignPolicy = AmountSignPolicy.NON_NEGATIVE,
    ) {
        val parser = RegionalMoneyInputParser { locale }

        assertEquals(
            MoneyInputResult.Accepted(Money(expectedMinorUnits, currency)),
            parser.parse(input, currency, signPolicy),
        )
    }

    private fun assertRejected(
        input: String,
        currency: CurrencyCode,
        locale: Locale,
        expectedReason: MoneyInputRejection,
        signPolicy: AmountSignPolicy = AmountSignPolicy.NON_NEGATIVE,
    ) {
        val parser = RegionalMoneyInputParser { locale }

        assertEquals(
            MoneyInputResult.Rejected(expectedReason),
            parser.parse(input, currency, signPolicy),
        )
    }
}

private class MutableFormatLocaleProvider(
    var value: Locale,
) : FormatLocaleProvider {
    var calls: Int = 0
        private set

    override fun currentFormatLocale(): Locale {
        calls++
        return value
    }
}

private fun String.normalizedSpaces(): String = replace('\u00A0', ' ').replace('\u202F', ' ')
