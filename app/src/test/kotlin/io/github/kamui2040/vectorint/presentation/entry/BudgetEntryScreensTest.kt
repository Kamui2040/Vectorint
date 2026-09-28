package io.github.kamui2040.vectorint.presentation.entry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.kamui2040.vectorint.core.ActivityState
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.presentation.theme.VectorintTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BudgetEntryScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `one-off activity choices state their accounting effect`() {
        var direction by mutableStateOf(Direction.EXPENSE)
        var state by mutableStateOf(ActivityState.CONFIRMED)
        var saves = 0
        compose.setContent {
            VectorintTheme {
                OneOffActivityScreen(
                    nameInput = "Groceries",
                    amountInput = "25.00",
                    currencyCode = "USD",
                    direction = direction,
                    state = state,
                    plannedDateLabel = "Sep 15, 2026",
                    saving = false,
                    issue = null,
                    onNameChange = {},
                    onAmountChange = {},
                    onDirectionChange = { direction = it },
                    onStateChange = { state = it },
                    onSelectPlannedDate = {},
                    onSave = { saves++ },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Add activity").assertIsDisplayed()
        compose.onNodeWithText("Groceries").assertIsDisplayed()
        compose.onNodeWithText("Income").performScrollTo().performClick()
        compose.onNodeWithText("Planned for a date").performScrollTo().performClick()
        compose.onNodeWithText("Planned for Sep 15, 2026").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("More about When").performScrollTo().performClick()
        compose
            .onNodeWithText("Included for the selected date’s month only when expected income is on.")
            .assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Save activity").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test
    fun `one-off activity exposes positive amount and storage failures`() {
        compose.setContent {
            VectorintTheme {
                OneOffActivityScreen(
                    nameInput = "Groceries",
                    amountInput = "0.00",
                    currencyCode = "USD",
                    direction = Direction.EXPENSE,
                    state = ActivityState.PLANNED,
                    plannedDateLabel = "Sep 15, 2026",
                    saving = false,
                    issue = EntrySaveResult.AmountMustBePositive,
                    onNameChange = {},
                    onAmountChange = {},
                    onDirectionChange = {},
                    onStateChange = {},
                    onSelectPlannedDate = {},
                    onSave = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Enter an amount greater than zero.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `receipt suggestion tells the user to verify the extracted total`() {
        compose.setContent {
            VectorintTheme {
                OneOffActivityScreen(
                    nameInput = "",
                    amountInput = "25.00",
                    currencyCode = "USD",
                    direction = Direction.EXPENSE,
                    state = ActivityState.CONFIRMED,
                    plannedDateLabel = "Sep 15, 2026",
                    saving = false,
                    issue = null,
                    receiptSuggestion = ReceiptSuggestion.TOTAL_ADDED,
                    onNameChange = {},
                    onAmountChange = {},
                    onDirectionChange = {},
                    onStateChange = {},
                    onSelectPlannedDate = {},
                    onSave = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Receipt total added. Check it before saving.").assertIsDisplayed()
        compose.onNodeWithText("25.00").assertIsDisplayed()
    }
}
