package io.github.kamui2040.vectorint.presentation.activity

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
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
        compose
            .onNodeWithTag("activity_history_list")
            .performScrollToNode(hasText("Groceries"))
        compose.onNodeWithText("Groceries").assertIsDisplayed()
        compose.onAllNodesWithText("Expense").assertCountEquals(1)
        compose.onNodeWithText("Planned for Sep 18, 2026").assertIsDisplayed()
        compose.onAllNodesWithText("Account: Main").assertCountEquals(1)
        compose.onNodeWithText("Groceries").assertIsDisplayed()
        compose.onNodeWithText("Tags: household, shared").performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithTag("activity_history_list")
            .performScrollToNode(hasText("Add activity"))
        compose.onNodeWithText("Add activity").performClick()
        compose
            .onNodeWithTag("activity_history_list")
            .performScrollToNode(hasText("Confirmed on Sep 17, 2026"))
        compose.onNodeWithText("Confirmed on Sep 17, 2026").assertIsDisplayed()
        compose
            .onNodeWithTag("activity_history_list")
            .performScrollToNode(hasText("€25.00"))
        compose.onNodeWithText("€25.00").performClick()
        compose.runOnIdle {
            assertEquals(firstId, selected)
            assertEquals(1, adds)
        }
    }

    @Test
    fun `history searches entry details and combines type and status filters`() {
        compose.setContent {
            VectorintTheme {
                ActivityHistoryScreen(
                    state =
                        ActivityHistoryUiState.Ready(
                            listOf(
                                ActivityHistoryItemUi(
                                    id = ActivityId("rent"),
                                    name = "Rent payment",
                                    direction = Direction.EXPENSE,
                                    amount = "€750.00",
                                    timing = ActivityTimingUi.PlannedDate("Oct 1, 2026"),
                                    accountName = "Main",
                                    categoryId = PredefinedCategory.HOUSING.id,
                                    tags = listOf("household"),
                                ),
                                ActivityHistoryItemUi(
                                    id = ActivityId("salary"),
                                    name = "Monthly pay",
                                    direction = Direction.INCOME,
                                    amount = "€2,000.00",
                                    timing = ActivityTimingUi.ConfirmedDate("Sep 28, 2026"),
                                    accountName = "Savings",
                                    categoryId = PredefinedCategory.SALARY.id,
                                ),
                            ),
                        ),
                    onActivitySelected = {},
                    onAddActivity = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("Search history").performTextInput("household")
        compose.onNodeWithTag("activity_history_list").performScrollToNode(hasText("Rent payment"))
        compose.onNodeWithText("Rent payment").assertIsDisplayed()
        compose.onNodeWithText("Monthly pay").assertDoesNotExist()

        compose.onNodeWithTag("activity_history_list").performScrollToNode(hasContentDescription("Clear search"))
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithTag("activity_history_list").performScrollToNode(hasText("Monthly pay"))
        compose.onNodeWithText("Monthly pay").assertIsDisplayed()

        compose.onNodeWithTag("activity_history_list").performScrollToNode(hasTestTag("open_filters"))
        compose.onNodeWithTag("open_filters").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag("activity_filter_sheet", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("activity_history_direction:EXPENSE", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("active_filter:direction").assertIsSelected()
        compose.onNodeWithTag("activity_history_list").performScrollToNode(hasText("Rent payment"))
        compose.onNodeWithText("Rent payment").assertIsDisplayed()
        compose.onNodeWithText("Monthly pay").assertDoesNotExist()

        compose.onNodeWithTag("activity_history_list").performScrollToNode(hasTestTag("open_filters"))
        compose.onNodeWithTag("open_filters").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag("activity_history_status:CONFIRMED").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("active_filter:status").assertIsSelected()
        compose.onNodeWithTag("activity_history_list").performScrollToNode(hasText("No matching activity"))
        compose
            .onNodeWithText("No matching activity")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))

        compose.onNodeWithTag("activity_history_list").performScrollToNode(hasText("Clear search and filters"))
        compose.onAllNodesWithText("Clear search and filters")[0].performClick()
        compose.onNodeWithTag("activity_history_list").performScrollToNode(hasText("Rent payment"))
        compose.onNodeWithText("Rent payment").assertIsDisplayed()
        compose.onNodeWithTag("activity_history_list").performScrollToNode(hasText("Monthly pay"))
        compose.onNodeWithText("Monthly pay").assertIsDisplayed()
    }

    @Test
    fun `history filters assigned and unassigned entries`() {
        compose.setContent {
            VectorintTheme {
                ActivityHistoryScreen(
                    state =
                        ActivityHistoryUiState.Ready(
                            listOf(
                                ActivityHistoryItemUi(
                                    id = ActivityId("assigned"),
                                    name = "Assigned purchase",
                                    direction = Direction.EXPENSE,
                                    amount = "€10.00",
                                    timing = ActivityTimingUi.PlannedDate("Sep 20, 2026"),
                                    accountName = "Main",
                                ),
                                ActivityHistoryItemUi(
                                    id = ActivityId("unassigned"),
                                    name = "Kept purchase",
                                    direction = Direction.EXPENSE,
                                    amount = "€20.00",
                                    timing = ActivityTimingUi.PlannedDate("Sep 21, 2026"),
                                    accountName = null,
                                ),
                            ),
                        ),
                    onActivitySelected = {},
                    onAddActivity = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithTag("activity_history_list").performScrollToNode(hasTestTag("open_filters"))
        compose.onNodeWithTag("open_filters").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("activity_history_assignment:UNASSIGNED").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("active_filter:assignment").assertIsSelected()
        compose
            .onNodeWithTag("activity_history_list")
            .performScrollToNode(hasText("Kept purchase"))
        compose.onNodeWithText("Kept purchase").assertIsDisplayed()
        compose.onNodeWithText("Assigned purchase").assertDoesNotExist()
        compose.onNodeWithText("Account: Unassigned").assertIsDisplayed()

        compose.onNodeWithTag("activity_history_list").performScrollToNode(hasTestTag("open_filters"))
        compose.onNodeWithTag("open_filters").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("activity_history_assignment:ASSIGNED").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.waitForIdle()
        compose
            .onNodeWithTag("activity_history_list")
            .performScrollToNode(hasText("Assigned purchase"))
        compose.onNodeWithText("Assigned purchase").assertIsDisplayed()
        compose.onNodeWithText("Kept purchase").assertDoesNotExist()
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
