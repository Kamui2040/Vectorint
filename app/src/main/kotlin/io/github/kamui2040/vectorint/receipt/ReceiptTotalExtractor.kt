package io.github.kamui2040.vectorint.receipt

import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.Money
import java.math.BigInteger
import java.text.Normalizer
import java.util.Currency

internal object ReceiptTotalExtractor {
    fun extract(
        recognizedText: String,
        currencyCode: CurrencyCode,
    ): Money? {
        val fractionDigits =
            try {
                Currency.getInstance(currencyCode.value).defaultFractionDigits
            } catch (_: IllegalArgumentException) {
                return null
            }
        if (fractionDigits !in 0..3) return null

        val lines =
            recognizedText
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toList()
        val candidates = mutableListOf<Candidate>()
        lines.forEachIndexed { index, line ->
            val labelScore = labelScore(line)
            if (labelScore == 0) return@forEachIndexed

            val sameLineAmounts = amountCandidates(line, fractionDigits)
            sameLineAmounts.forEach { amount -> candidates += Candidate(amount, labelScore) }
            if (sameLineAmounts.isEmpty() && index + 1 < lines.size) {
                amountCandidates(lines[index + 1], fractionDigits).forEach { amount ->
                    candidates += Candidate(amount, labelScore - 1)
                }
            }
        }
        candidates
            .asSequence()
            .map(Candidate::score)
            .distinct()
            .sortedDescending()
            .forEach { score ->
                val amounts =
                    candidates
                        .asSequence()
                        .filter { it.score == score }
                        .map(Candidate::minorUnits)
                        .distinct()
                        .toList()
                if (amounts.size == 1) return Money(amounts.single(), currencyCode)
            }
        return null
    }

    private fun labelScore(line: String): Int {
        val normalized = line.normalizedForLabels()
        if (EXCLUDED_LABELS.any { it.containsMatchIn(normalized) }) return 0
        return when {
            STRONG_LABELS.any { it.containsMatchIn(normalized) } -> 4
            GENERIC_TOTAL.containsMatchIn(normalized) -> 3
            else -> 0
        }
    }

    private fun amountCandidates(
        line: String,
        fractionDigits: Int,
    ): List<Long> =
        AMOUNT_TOKEN
            .findAll(line)
            .mapNotNull { match -> parseAmount(match.value, fractionDigits) }
            .toList()

    private fun parseAmount(
        token: String,
        fractionDigits: Int,
    ): Long? {
        val compact = token.filterNot { it.isWhitespace() || it == '\'' || it == '’' }
        if (compact.isEmpty()) return null
        val punctuation = compact.withIndex().filter { it.value == '.' || it.value == ',' }
        val decimalIndex =
            when {
                punctuation.isEmpty() -> null
                punctuation.map { it.value }.distinct().size > 1 -> punctuation.last().index
                punctuation.size > 1 -> {
                    val last = punctuation.last().index
                    if (compact.length - last - 1 == fractionDigits) last else null
                }
                else -> {
                    val only = punctuation.single().index
                    val trailingDigits = compact.length - only - 1
                    when {
                        trailingDigits == fractionDigits && fractionDigits > 0 -> only
                        trailingDigits == 3 && fractionDigits != 3 -> null
                        else -> return null
                    }
                }
            }

        val wholePart =
            if (decimalIndex == null) compact else compact.substring(0, decimalIndex)
        val fractionPart =
            if (decimalIndex == null) "" else compact.substring(decimalIndex + 1)
        val wholeDigits = wholePart.filter(Char::isDigit)
        if (wholeDigits.isEmpty()) return null
        if (wholePart.any { !it.isDigit() && it != '.' && it != ',' }) return null
        if (fractionPart.any { !it.isDigit() } || fractionPart.length != if (decimalIndex == null) 0 else fractionDigits) {
            return null
        }

        val minorUnits =
            BigInteger(wholeDigits)
                .multiply(BigInteger.TEN.pow(fractionDigits))
                .add(if (fractionPart.isEmpty()) BigInteger.ZERO else BigInteger(fractionPart))
        if (minorUnits <= BigInteger.ZERO || minorUnits > LONG_MAX) return null
        return minorUnits.toLong()
    }

    private fun String.normalizedForLabels(): String =
        Normalizer
            .normalize(lowercase(), Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")

    private data class Candidate(
        val minorUnits: Long,
        val score: Int,
    )

    private val EXCLUDED_LABELS =
        listOf(
            Regex("\\bsub[ -]?total\\b"),
            Regex("\\bzwischensumme\\b"),
            Regex("\\bsous[ -]?total\\b"),
            Regex("\\bsubtotal\\b"),
        )
    private val STRONG_LABELS =
        listOf(
            Regex("\\bgrand total\\b"),
            Regex("\\b(?:amount|balance|total) due\\b"),
            Regex("\\btotal to pay\\b"),
            Regex("\\bzu zahlen\\b"),
            Regex("\\bgesamtbetrag\\b"),
            Regex("\\bendbetrag\\b"),
            Regex("\\bnet a payer\\b"),
            Regex("\\bmontant total\\b"),
            Regex("\\btotal a pagar\\b"),
            Regex("\\bvalor total\\b"),
            Regex("\\btotal geral\\b"),
            Regex("\\bimporte total\\b"),
            Regex("\\bda pagare\\b"),
        )
    private val GENERIC_TOTAL = Regex("\\b(?:total|totale|summe)\\b")
    private val AMOUNT_TOKEN =
        Regex(
            "(?<![\\p{L}\\p{N}])(?:" +
                "\\d{1,3}(?:[ ' ’]\\d{3})+(?:[.,]\\d{1,3})?|" +
                "\\d{1,3}(?:[.,]\\d{3})+[.,]\\d{1,3}|" +
                "\\d+(?:[.,]\\d+)?" +
                ")(?![\\p{L}\\p{N}%])",
        )
    private val COMBINING_MARKS = Regex("\\p{M}+")
    private val LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE)
}
