package io.github.kamui2040.vectorint.receipt

import java.text.Normalizer

internal object ReceiptVendorMatcher {
    fun match(recognizedText: String): ReceiptVendor? {
        val headerLines =
            recognizedText
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .take(MAX_HEADER_LINES)
                .map { it.normalizedForVendorMatching() }
                .filter(String::isNotEmpty)
                .toList()

        val candidates =
            GermanReceiptVendorCatalogue.vendors.flatMap { vendor ->
                vendor.receiptAliases.flatMap { alias ->
                    val normalizedAlias = alias.normalizedForVendorMatching()
                    headerLines.mapIndexedNotNull { lineIndex, line ->
                        if (line.containsPhrase(normalizedAlias)) {
                            Candidate(
                                vendor = vendor,
                                lineIndex = lineIndex,
                                aliasWordCount = normalizedAlias.count(Char::isWhitespace) + 1,
                                aliasLength = normalizedAlias.length,
                            )
                        } else {
                            null
                        }
                    }
                }
            }
        val best =
            candidates.minWithOrNull(
                compareBy<Candidate> { it.lineIndex }
                    .thenByDescending(Candidate::aliasWordCount)
                    .thenByDescending(Candidate::aliasLength),
            ) ?: return null
        val equallySpecificVendors =
            candidates
                .asSequence()
                .filter {
                    it.lineIndex == best.lineIndex &&
                        it.aliasWordCount == best.aliasWordCount &&
                        it.aliasLength == best.aliasLength
                }.map { it.vendor.id }
                .distinct()
                .toList()

        return best.vendor.takeIf { equallySpecificVendors.size == 1 }
    }

    private fun String.containsPhrase(phrase: String): Boolean = phrase.isNotEmpty() && " $this ".contains(" $phrase ")

    private fun String.normalizedForVendorMatching(): String =
        Normalizer
            .normalize(lowercase(), Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .replace(NON_ALPHANUMERIC, " ")
            .trim()
            .replace(REPEATED_SPACES, " ")

    private data class Candidate(
        val vendor: ReceiptVendor,
        val lineIndex: Int,
        val aliasWordCount: Int,
        val aliasLength: Int,
    )

    private const val MAX_HEADER_LINES = 12
    private val COMBINING_MARKS = Regex("\\p{M}+")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
    private val REPEATED_SPACES = Regex(" +")
}
