package io.github.kamui2040.vectorint.core

import java.util.Locale

@JvmInline
value class CurrencyCode private constructor(
    val value: String,
) {
    companion object {
        fun of(value: String): CurrencyCode {
            val normalized = value.trim().uppercase(Locale.ROOT)
            require(normalized.matches(Regex("[A-Z]{3}"))) {
                "Currency codes must contain exactly three ASCII letters"
            }
            return CurrencyCode(normalized)
        }
    }
}

data class Money(
    val minorUnits: Long,
    val currency: CurrencyCode,
) {
    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Currencies must match" }
        return copy(minorUnits = Math.addExact(minorUnits, other.minorUnits))
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "Currencies must match" }
        return copy(minorUnits = Math.subtractExact(minorUnits, other.minorUnits))
    }

    companion object {
        fun zero(currency: CurrencyCode): Money = Money(0, currency)
    }
}

enum class Direction {
    INCOME,
    EXPENSE,
    ;

    fun signedMinorUnits(unsignedMinorUnits: Long): Long {
        require(unsignedMinorUnits >= 0) { "Activity amounts must be non-negative" }
        return when (this) {
            INCOME -> unsignedMinorUnits
            EXPENSE -> Math.negateExact(unsignedMinorUnits)
        }
    }
}
