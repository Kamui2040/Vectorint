package io.github.kamui2040.vectorint.presentation.activity

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.ActivityState
import io.github.kamui2040.vectorint.core.BudgetMonth
import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.Money
import io.github.kamui2040.vectorint.core.PredefinedCategory
import io.github.kamui2040.vectorint.presentation.theme.VectorintTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActivityScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `history shows chronological entries and opens the selected activity`() {
        var selected: ActivityId? = null
        var adds = 0
        val firstId = ActivityId("first")
        compose.setContent {
            VectorintTheme {
                ActivityHistoryScreen(
                    state =
                        ActivityHistoryUiState.Ready(
                            listOf(
                                ActivityHistoryItemUi(
                                    id = firstId,
                                    name = "Groceries",
                                    direction = Direction.EXPENSE,
                                    amount = "€25.00",
                                    timing = ActivityTimingUi.PlannedDate("Sep 18, 2026"),
                                    categoryId = PredefinedCategory.GROCERIES.id,
                                    tags = listOf("household", "shared"),
                                ),
                                ActivityHistoryItemUi(
                                    id = ActivityId("second"),
                                    name = "Salary",
                                    direction = Direction.INCOME,
                                    amount = "€10.00",
                                    timing = ActivityTimingUi.ConfirmedDate("Sep 17, 2026"),
                                ),
                            ),
                        ),
                    onActivitySelected = { selected = it },
                    onAddActivity = { adds++ },
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("History").assertIsDisplayed()
        compose
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "History"))
            .assertExists()
        compose.onNodeWithText("Back").assertDoesNotExist()
        compose.onNodeWithText("Groceries").assertIsDisplayed()
        compose.onNodeWithText("Expense").assertIsDisplayed()
        compose.onNodeWithText("Planned for Sep 18, 2026").assertIsDisplayed()
        compose.onAllNodesWithText("Account: Main").assertCountEquals(2)
        compose.onNodeWithText("Groceries").assertIsDisplayed()
        compose.onNodeWithText("Tags: household, shared").assertIsDisplayed()
        compose.onNodeWithText("Add activity").performClick()
        compose.onNodeWithText("Confirmed on Sep 17, 2026").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("€25.00").performClick()
        compose.runOnIdle {
            assertEquals(firstId, selected)
            assertEquals(1, adds)
        }
    }

    @Test
    fun `empty and failed history states remain distinct`() {
        var state by mutableStateOf<ActivityHistoryUiState>(ActivityHistoryUiState.Empty)
        var retries = 0
        compose.setContent {
            VectorintTheme {
                ActivityHistoryScreen(
                    state = state,
                    onActivitySelected = {},
                    onAddActivity = {},
                    onRetry = { retries++ },
                )
            }
        }

        compose.onNodeWithText("No activity yet").assertIsDisplayed()
        compose.onNodeWithText("Couldn’t load activity").assertDoesNotExist()

        compose.runOnIdle { state = ActivityHistoryUiState.LoadFailed }
        compose.onNodeWithText("Couldn’t load activity").assertIsDisplayed()
        compose
            .onNodeWithText("Couldn’t load activity")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithText("No activity yet").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun `planned activity supports editing confirmation and guarded deletion`() {
        var direction by mutableStateOf(Direction.EXPENSE)
        var showDelete by mutableStateOf(false)
        var saves = 0
        var confirmations = 0
        var deletions = 0
        val seed = plannedSeed()
        compose.setContent {
            VectorintTheme {
                ActivityEditScreen(
                    seed = seed,
                    nameInput = seed.activity.name,
                    amountInput = "25.00",
                    direction = direction,
                    busy = false,
                    issue = null,
                    showDeleteConfirmation = showDelete,
                    onNameChange = {},
                    onAmountChange = {},
                    onDirectionChange = { direction = it },
                    onSave = { saves++ },
                    onConfirm = { confirmations++ },
                    onDeleteRequest = { showDelete = true },
                    onDeleteDismiss = { showDelete = false },
                    onDeleteConfirm = {
                        showDelete = false
                        deletions++
                    },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Planned for Sep 18, 2026").assertIsDisplayed()
        compose.onNodeWithText("Income").performScrollTo().performClick()
        compose.onNodeWithText("Save changes").performScrollTo().performClick()
        val confirmationHelp = "Confirming records this entry once using today’s date."
        compose.onNodeWithText(confirmationHelp).assertDoesNotExist()
        compose.onNodeWithContentDescription("More about Confirmation").performScrollTo().performClick()
        compose.onNodeWithText(confirmationHelp).assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Confirm now").performScrollTo().performClick()
        compose.onNodeWithText("Delete activity").performScrollTo().performClick()
        compose.onNodeWithText("Delete activity?").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()

        compose.runOnIdle {
            assertEquals(Direction.INCOME, direction)
            assertEquals(1, saves)
            assertEquals(1, confirmations)
            assertEquals(1, deletions)
        }
    }

    @Test
    fun `confirmed activity cannot be confirmed twice and failures remain visible`() {
        val seed =
            plannedSeed().copy(
                activity =
                    plannedSeed().activity.copy(
                        state = ActivityState.CONFIRMED,
                        bookedAt = java.time.Instant.parse("2026-09-18T12:00:00Z"),
                    ),
                timing = ActivityTimingUi.ConfirmedDate("Sep 18, 2026"),
            )
        compose.setContent {
            VectorintTheme {
                ActivityEditScreen(
                    seed = seed,
                    nameInput = seed.activity.name,
                    amountInput = "25.00",
                    direction = Direction.EXPENSE,
                    busy = false,
                    issue = ActivityEditIssue.StorageFailed,
                    showDeleteConfirmation = false,
                    onNameChange = {},
                    onAmountChange = {},
                    onDirectionChange = {},
                    onSave = {},
                    onConfirm = {},
                    onDeleteRequest = {},
                    onDeleteDismiss = {},
                    onDeleteConfirm = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Confirmed on Sep 18, 2026").assertIsDisplayed()
        compose.onNodeWithText("Confirm now").assertDoesNotExist()
        compose
            .onNodeWithText("Couldn’t save this change. The entry is unchanged.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun plannedSeed(): ActivityEditSeed =
        ActivityEditSeed(
            activity =
                ActivityEntry(
                    id = ActivityId("activity"),
                    name = "Groceries",
                    direction = Direction.EXPENSE,
                    amount = Money(2_500, CurrencyCode.of("EUR")),
                    state = ActivityState.PLANNED,
                    budgetMonth = BudgetMonth(YearMonth.of(2026, 9)),
                    expectedOn = LocalDate.of(2026, 9, 18),
                ),
            amountInput = "25.00",
            timing = ActivityTimingUi.PlannedDate("Sep 18, 2026"),
        )
}
