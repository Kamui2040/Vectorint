package io.github.kamui2040.vectorint.presentation.home

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.kamui2040.vectorint.core.UnsafeReason
import io.github.kamui2040.vectorint.presentation.theme.VectorintTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `loading state does not display a budget amount`() {
        compose.setContent {
            VectorintTheme {
                HomeScreen(
                    state = HomeUiState.Loading("September 2026"),
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("Loading budget").assertIsDisplayed()
        compose.onNodeWithText("Available now").assertDoesNotExist()
        compose.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun `ready state gives Available now and Add activity priority before the collapsed breakdown`() {
        compose.setContent {
            VectorintTheme {
                HomeScreen(
                    state =
                        HomeUiState.Ready(
                            monthLabel = "September 2026",
                            availableNow = "€650.00",
                            currentFunds = "€900.00",
                            reservedExpenses = "€250.00",
                            expectedIncome = ExpectedIncomeUi.Excluded,
                        ),
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("Available now").assertIsDisplayed()
        compose.onNodeWithText("€650.00").assertIsDisplayed()
        compose.onNodeWithText("September 2026").assertIsDisplayed()
        compose.onNodeWithText("Add activity").assertIsDisplayed()
        compose.onNodeWithText("Current funds").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show This month details").performClick()
        compose.onNodeWithText("Current funds").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("€900.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Reserved expenses").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("€250.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Expected income").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Not included").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Hide This month details").performScrollTo().performClick()
        compose.onNodeWithText("Current funds").assertDoesNotExist()
        compose.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun `included expected income is labelled explicitly`() {
        compose.setContent {
            VectorintTheme {
                HomeScreen(
                    state =
                        HomeUiState.Ready(
                            monthLabel = "September 2026",
                            availableNow = "€750.00",
                            currentFunds = "€900.00",
                            reservedExpenses = "€250.00",
                            expectedIncome = ExpectedIncomeUi.Included("€100.00"),
                        ),
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Show This month details").performClick()
        compose.onNodeWithText("Expected income").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("€100.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Included").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Not included").assertDoesNotExist()
    }

    @Test
    fun `month label opens browsing controls`() {
        var previous = 0
        var next = 0
        var current = 0
        compose.setContent {
            VectorintTheme {
                HomeScreen(
                    state =
                        HomeUiState.MonthOverview(
                            monthLabel = "October 2026",
                            relation = HomeMonthRelation.FUTURE,
                            income = "€2,000.00",
                            expenses = "€1,250.00",
                            net = "€750.00",
                            netIsNegative = false,
                        ),
                    selectedMonthIsCurrent = false,
                    onRetry = {},
                    onPreviousMonth = { previous++ },
                    onNextMonth = { next++ },
                    onCurrentMonth = { current++ },
                )
            }
        }

        compose.onNodeWithText("October 2026").performClick()
        compose.onNodeWithText("Previous").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Current month").performClick()
        compose.runOnIdle {
            assertEquals(1, previous)
            assertEquals(1, next)
            assertEquals(1, current)
        }
    }

    @Test
    fun `future month is a forecast rather than Available now`() {
        compose.setContent {
            VectorintTheme {
                HomeScreen(
                    state =
                        HomeUiState.MonthOverview(
                            monthLabel = "October 2026",
                            relation = HomeMonthRelation.FUTURE,
                            income = "€2,000.00",
                            expenses = "€1,250.00",
                            net = "€750.00",
                            netIsNegative = false,
                        ),
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("Upcoming month forecast").assertIsDisplayed()
        compose.onNodeWithText("Income").assertIsDisplayed()
        compose.onNodeWithText("Expenses").assertIsDisplayed()
        compose.onNodeWithText("Net").assertIsDisplayed()
        compose.onNodeWithText("Available now").assertDoesNotExist()
    }

    @Test
    fun `unsafe state hides all amounts and offers retry`() {
        var retryCount = 0
        compose.setContent {
            VectorintTheme {
                HomeScreen(
                    state =
                        HomeUiState.Unsafe(
                            monthLabel = "September 2026",
                            reasons = setOf(UnsafeReason.CURRENCY_MISMATCH),
                        ),
                    onRetry = { retryCount++ },
                )
            }
        }

        compose.onNodeWithText("Available now unavailable").assertIsDisplayed()
        compose.onNodeWithText("€650.00").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test
    fun `missing Current funds is distinct from a load failure`() {
        var setupCount = 0
        compose.setContent {
            VectorintTheme {
                HomeScreen(
                    state = HomeUiState.NeedsCurrentFunds("September 2026"),
                    onRetry = {},
                    onSetCurrentFunds = { setupCount++ },
                )
            }
        }

        compose.onNodeWithText("Start here").assertIsDisplayed()
        compose.onNodeWithText("See what you can safely spend").assertIsDisplayed()
        compose
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Home"))
            .assertExists()
        compose
            .onNodeWithText("See what you can safely spend")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithText("Couldn’t load your budget").assertDoesNotExist()
        compose.onNodeWithText("Try again").assertDoesNotExist()
        compose.onNodeWithText("Set Current funds").performClick()
        compose.runOnIdle { assertEquals(1, setupCount) }
    }

    @Test
    fun `ready state offers activity and Current funds actions`() {
        var activityCount = 0
        var recurringCount = 0
        var editCount = 0
        compose.setContent {
            VectorintTheme {
                HomeScreen(
                    state =
                        HomeUiState.Ready(
                            monthLabel = "September 2026",
                            availableNow = "€650.00",
                            currentFunds = "€900.00",
                            reservedExpenses = "€250.00",
                            expectedIncome = ExpectedIncomeUi.Excluded,
                        ),
                    onRetry = {},
                    onEditCurrentFunds = { editCount++ },
                    onAddActivity = { activityCount++ },
                    onViewRecurringItems = { recurringCount++ },
                )
            }
        }

        compose.onNodeWithText("Add activity").assertIsDisplayed().performClick()
        compose.onNodeWithText("Recurring items").performScrollTo().performClick()
        compose.onNodeWithText("Edit Current funds").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, activityCount)
            assertEquals(1, recurringCount)
            assertEquals(1, editCount)
        }
        compose.onNodeWithText("History").assertDoesNotExist()
        compose.onNodeWithText("Settings").assertDoesNotExist()
    }

    @Test
    fun `load failure offers a retry without showing a budget amount`() {
        var retryCount = 0
        compose.setContent {
            VectorintTheme {
                HomeScreen(
                    state = HomeUiState.LoadFailed("September 2026"),
                    onRetry = { retryCount++ },
                )
            }
        }

        compose.onNodeWithText("Couldn’t load your budget").assertIsDisplayed()
        compose.onNodeWithText("Available now").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle { assertEquals(1, retryCount) }
    }
}
