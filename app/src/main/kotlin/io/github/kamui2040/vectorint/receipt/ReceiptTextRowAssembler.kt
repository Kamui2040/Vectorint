package io.github.kamui2040.vectorint.receipt

internal data class ReceiptTextFragment(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

internal object ReceiptTextRowAssembler {
    fun assemble(
        fragments: List<ReceiptTextFragment>,
        fallbackText: String,
    ): String {
        val positioned =
            fragments
                .filter { it.text.isNotBlank() && it.right > it.left && it.bottom > it.top }
                .sortedWith(compareBy<ReceiptTextFragment> { it.centerY }.thenBy { it.left })
        if (positioned.isEmpty()) return fallbackText

        val rows = mutableListOf<Row>()
        positioned.forEach { fragment ->
            val row =
                rows
                    .asSequence()
                    .filter { it.accepts(fragment) }
                    .minByOrNull { it.verticalDistanceFrom(fragment) }
            if (row == null) {
                rows += Row(fragment)
            } else {
                row.add(fragment)
            }
        }

        return rows
            .sortedBy(Row::centerY)
            .joinToString("\n") { row ->
                row.fragments
                    .sortedBy(ReceiptTextFragment::left)
                    .joinToString(" ") { it.text.trim() }
            }
    }

    private val ReceiptTextFragment.centerY: Int
        get() = top + (bottom - top) / 2

    private val ReceiptTextFragment.height: Int
        get() = bottom - top

    private class Row(
        first: ReceiptTextFragment,
    ) {
        val fragments = mutableListOf(first)
        private var centerTotal = first.centerY
        private var maximumHeight = first.height

        val centerY: Int
            get() = centerTotal / fragments.size

        fun accepts(fragment: ReceiptTextFragment): Boolean =
            verticalDistanceFrom(fragment) * 5 <= maxOf(maximumHeight, fragment.height) * 3

        fun verticalDistanceFrom(fragment: ReceiptTextFragment): Int = kotlin.math.abs(centerY - fragment.centerY)

        fun add(fragment: ReceiptTextFragment) {
            fragments += fragment
            centerTotal += fragment.centerY
            maximumHeight = maxOf(maximumHeight, fragment.height)
        }
    }
}
