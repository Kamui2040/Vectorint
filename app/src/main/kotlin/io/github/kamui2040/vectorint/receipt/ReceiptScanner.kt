package io.github.kamui2040.vectorint.receipt

internal interface ReceiptScanner {
    val isInProgress: Boolean

    fun launch()
}

internal sealed interface ReceiptScanOutcome {
    data class RecognizedText(
        val text: String,
    ) : ReceiptScanOutcome

    data object Cancelled : ReceiptScanOutcome

    data object Unavailable : ReceiptScanOutcome

    data object Failed : ReceiptScanOutcome
}
