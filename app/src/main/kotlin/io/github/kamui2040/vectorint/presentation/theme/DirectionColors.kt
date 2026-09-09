package io.github.kamui2040.vectorint.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.github.kamui2040.vectorint.core.Direction

@Immutable
internal data class DirectionColors(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
)

@Composable
internal fun directionColors(direction: Direction): DirectionColors {
    val colors = MaterialTheme.flowColors
    return when (direction) {
        Direction.INCOME ->
            DirectionColors(
                accent = colors.income,
                onAccent = colors.onIncome,
                container = colors.incomeContainer,
                onContainer = colors.onIncomeContainer,
            )

        Direction.EXPENSE ->
            DirectionColors(
                accent = colors.expense,
                onAccent = colors.onExpense,
                container = colors.expenseContainer,
                onContainer = colors.onExpenseContainer,
            )
    }
}
