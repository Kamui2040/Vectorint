package io.github.kamui2040.vectorint.presentation.format

import android.os.Build
import android.text.format.DateFormat
import io.github.kamui2040.vectorint.core.BudgetMonth
import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

fun interface FormatLocaleProvider {
    fun currentFormatLocale(): Locale
}

object OsFormatLocaleProvider : FormatLocaleProvider {
    override fun currentFormatLocale(): Locale =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Locale.getDefault(Locale.Category.FORMAT)
        } else {
            Locale.getDefault()
        }
}

enum class AmountSignPolicy {
    NON_NEGATIVE,
    SIGNED,
}

enum class MoneyInputRejection {
    EMPTY,
    UNSUPPORTED_CURRENCY,
    INVALID_CHARACTER,
    GROUPING_SEPARATOR_NOT_ALLOWED,
    WRONG_DECIMAL_SEPARATOR,
    TOO_MANY_FRACTION_DIGITS,
    NEGATIVE_NOT_ALLOWED,
    OUT_OF_RANGE,
}

sealed interface MoneyInputResult {
    data class Accepted(
        val money: Money,
    ) : MoneyInputResult

    data class Rejected(
        val reason: MoneyInputRejection,
    ) : MoneyInputResult
}

sealed interface WholeNumberInputResult {
    data class Accepted(
        val value: Int,
    ) : WholeNumberInputResult

    data object Rejected : WholeNumberInputResult
}

class RegionalFormatter(
    private val localeProvider: FormatLocaleProvider = OsFormatLocaleProvider,
) {
    fun formatMoney(money: Money): String {
        val locale = localeProvider.currentFormatLocale()
        val currencyInfo = requireCurrencyInfo(money.currency)
        val formatter = NumberFormat.getCurrencyInstance(locale)
        formatter.currency = currencyInfo.currency
        formatter.minimumFractionDigits = currencyInfo.fractionDigits
        formatter.maximumFractionDigits = currencyInfo.fractionDigits
        formatter.roundingMode = RoundingMode.UNNECESSARY
        val amount = BigDecimal.valueOf(money.minorUnits, currencyInfo.fractionDigits)
        return formatter.format(amount)
    }

    fun formatMoneyInput(money: Money): String {
        val locale = localeProvider.currentFormatLocale()
        val currencyInfo = requireCurrencyInfo(money.currency)
        val formatter = NumberFormat.getNumberInstance(locale)
        formatter.isGroupingUsed = false
        formatter.minimumFractionDigits = currencyInfo.fractionDigits
        formatter.maximumFractionDigits = currencyInfo.fractionDigits
        formatter.roundingMode = RoundingMode.UNNECESSARY
        val amount = BigDecimal.valueOf(money.minorUnits, currencyInfo.fractionDigits)
        return formatter.format(amount)
    }

    fun formatDate(date: LocalDate): String = format(date, DATE_SKELETON)

    fun formatMonth(month: BudgetMonth): String = format(month.value.atDay(1), MONTH_SKELETON)

    fun formatWholeNumber(value: Int): String {
        require(value >= 0) { "Whole number must be non-negative" }
        return NumberFormat
            .getIntegerInstance(localeProvider.currentFormatLocale())
            .apply { isGroupingUsed = false }
            .format(value)
    }

    fun formatPercentage(
        part: Long,
        total: Long,
    ): String {
        require(part >= 0L) { "Percentage part must be non-negative" }
        require(total > 0L) { "Percentage total must be positive" }
        require(part <= total) { "Percentage part must not exceed total" }
        val ratio =
            BigDecimal.valueOf(part).divide(
                BigDecimal.valueOf(total),
                PERCENTAGE_SCALE,
                RoundingMode.HALF_UP,
            )
        return NumberFormat
            .getPercentInstance(localeProvider.currentFormatLocale())
            .apply {
                minimumFractionDigits = 0
                maximumFractionDigits = 1
                roundingMode = RoundingMode.HALF_UP
            }.format(ratio)
    }

    private fun format(
        date: LocalDate,
        skeleton: String,
    ): String {
        val locale = localeProvider.currentFormatLocale()
        val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
        return date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    private companion object {
        const val DATE_SKELETON = "yMMMd"
        const val MONTH_SKELETON = "yMMMM"
        const val PERCENTAGE_SCALE = 6
    }
}

class RegionalWholeNumberInputParser {
    fun parse(
        input: String,
        maximum: Int,
    ): WholeNumberInputResult {
        require(maximum >= 0) { "Maximum must be non-negative" }
        val trimmed = input.trimRegionalWhitespace()
        if (trimmed.isEmpty()) return WholeNumberInputResult.Rejected

        var value = 0
        for (character in trimmed) {
            val digit = Character.digit(character, 10)
            if (
                digit < 0 ||
                value > maximum / 10 ||
                (value == maximum / 10 && digit > maximum % 10)
            ) {
                return WholeNumberInputResult.Rejected
            }
            value = value * 10 + digit
        }
        return WholeNumberInputResult.Accepted(value)
    }
}

class RegionalMoneyInputParser(
    private val localeProvider: FormatLocaleProvider = OsFormatLocaleProvider,
) {
    fun parse(
        input: String,
        currencyCode: CurrencyCode,
        signPolicy: AmountSignPolicy = AmountSignPolicy.NON_NEGATIVE,
    ): MoneyInputResult {
        val locale = localeProvider.currentFormatLocale()
        val currencyInfo =
            currencyInfoOrNull(currencyCode)
                ?: return MoneyInputResult.Rejected(MoneyInputRejection.UNSUPPORTED_CURRENCY)
        val trimmed = input.trimRegionalWhitespace()
        if (trimmed.isEmpty()) {
            return MoneyInputResult.Rejected(MoneyInputRejection.EMPTY)
        }

        val symbols = DecimalFormatSymbols.getInstance(locale)
        var index = 0
        var isNegative = false
        when (trimmed.first()) {
            '+' -> index++
            '-', symbols.minusSign -> {
                isNegative = true
                index++
            }
        }

        val normalized = StringBuilder(trimmed.length)
        var decimalSeen = false
        var totalDigits = 0
        var fractionDigits = 0

        while (index < trimmed.length) {
            val character = trimmed[index]
            val digit = Character.digit(character, 10)
            when {
                digit >= 0 -> {
                    normalized.append(digit)
                    totalDigits++
                    if (decimalSeen) {
                        fractionDigits++
                    }
                }

                character == symbols.decimalSeparator && !decimalSeen -> {
                    normalized.append('.')
                    decimalSeen = true
                }

                character == symbols.decimalSeparator -> {
                    return MoneyInputResult.Rejected(MoneyInputRejection.INVALID_CHARACTER)
                }

                character == symbols.groupingSeparator -> {
                    return MoneyInputResult.Rejected(MoneyInputRejection.GROUPING_SEPARATOR_NOT_ALLOWED)
                }

                character == '.' || character == ',' -> {
                    return MoneyInputResult.Rejected(MoneyInputRejection.WRONG_DECIMAL_SEPARATOR)
                }

                else -> {
                    return MoneyInputResult.Rejected(MoneyInputRejection.INVALID_CHARACTER)
                }
            }
            index++
        }

        if (totalDigits == 0 || (decimalSeen && fractionDigits == 0)) {
            return MoneyInputResult.Rejected(MoneyInputRejection.INVALID_CHARACTER)
        }
        if (fractionDigits > currencyInfo.fractionDigits) {
            return MoneyInputResult.Rejected(MoneyInputRejection.TOO_MANY_FRACTION_DIGITS)
        }
        if (isNegative && signPolicy == AmountSignPolicy.NON_NEGATIVE) {
            return MoneyInputResult.Rejected(MoneyInputRejection.NEGATIVE_NOT_ALLOWED)
        }

        val signedValue = if (isNegative) "-$normalized" else normalized.toString()
        val minorUnits =
            try {
                BigDecimal(signedValue)
                    .movePointRight(currencyInfo.fractionDigits)
                    .longValueExact()
            } catch (_: ArithmeticException) {
                return MoneyInputResult.Rejected(MoneyInputRejection.OUT_OF_RANGE)
            } catch (_: NumberFormatException) {
                return MoneyInputResult.Rejected(MoneyInputRejection.INVALID_CHARACTER)
            }

        return MoneyInputResult.Accepted(Money(minorUnits, currencyCode))
    }
}

private data class CurrencyInfo(
    val currency: Currency,
    val fractionDigits: Int,
)

private fun requireCurrencyInfo(currencyCode: CurrencyCode): CurrencyInfo =
    requireNotNull(currencyInfoOrNull(currencyCode)) {
        "Unsupported currency code: ${currencyCode.value}"
    }

private fun currencyInfoOrNull(currencyCode: CurrencyCode): CurrencyInfo? {
    val currency =
        try {
            Currency.getInstance(currencyCode.value)
        } catch (_: IllegalArgumentException) {
            return null
        }
    return currency.defaultFractionDigits
        .takeIf { it >= 0 }
        ?.let { fractionDigits -> CurrencyInfo(currency, fractionDigits) }
}

private fun String.trimRegionalWhitespace(): String = trim { it.isWhitespace() || Character.isSpaceChar(it) }
