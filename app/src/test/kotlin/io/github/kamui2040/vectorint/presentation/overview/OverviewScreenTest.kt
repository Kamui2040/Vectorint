package io.github.kamui2040.vectorint.presentation.overview

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
import io.github.kamui2040.vectorint.core.CategoryIcon
import io.github.kamui2040.vectorint.core.CategoryId
import io.github.kamui2040.vectorint.core.CustomCategory
import io.github.kamui2040.vectorint.core.PredefinedCategory
import io.github.kamui2040.vectorint.core.UnsafeReason
import io.github.kamui2040.vectorint.presentation.theme.VectorintTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OverviewScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun readyOverviewShowsExactTotalAndAccessibleCategoryLegend() {
        val customCategory =
            CustomCategory(
                id = CategoryId("custom_pets"),
                name = "Pets",
                icon = CategoryIcon.PETS,
            )
        compose.setContent {
            VectorintTheme {
                OverviewScreen(
                    state =
                        OverviewUiState.Ready(
                            monthLabel = "September 2026",
                            totalExpenses = "€900.00",
                            slices =
                                listOf(
                                    OverviewSliceUi(
                                        categoryId = PredefinedCategory.HOUSING.id,
                                        amount = "€600.00",
                                        share = "66.7%",
                                        fraction = 2f / 3f,
                                    ),
                                    OverviewSliceUi(
                                        categoryId = customCategory.id,
                                        amount = "€250.00",
                                        share = "27.8%",
                                        fraction = 0.2777778f,
                                    ),
                                    OverviewSliceUi(
                                        categoryId = null,
                                        amount = "€50.00",
                                        share = "5.6%",
                                        fraction = 0.0555556f,
                                    ),
                                ),
                            customCategories = listOf(customCategory),
                        ),
                    onRetry = {},
                )
            }
        }

        compose
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Overview"))
            .assertExists()
        compose.onNodeWithText("Total expenses").assertIsDisplayed()
        compose.onNodeWithText("€900.00").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pie chart. Category amounts and shares are listed below.").assertIsDisplayed()
        compose.onNodeWithText("Housing").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pets").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Other").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("€600.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("66.7%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("€250.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("27.8%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("€50.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("5.6%").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun monthWithoutExpensesHasAnExplicitEmptyState() {
        compose.setContent {
            VectorintTheme {
                OverviewScreen(
                    state = OverviewUiState.Empty("September 2026"),
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("No expenses this month").assertIsDisplayed()
        compose.onNodeWithText("Expenses assigned to this month will appear here.").assertIsDisplayed()
        compose.onNodeWithText("Expenses by category").assertDoesNotExist()
    }

    @Test
    fun monthLabelOpensTheSameBrowsingControlsAsHome() {
        var previous = 0
        var next = 0
        var current = 0
        compose.setContent {
            VectorintTheme {
                OverviewScreen(
                    state = OverviewUiState.Empty("October 2026"),
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
    fun missingCurrentFundsOffersTheProgressiveSetupAction() {
        var setupCount = 0
        compose.setContent {
            VectorintTheme {
                OverviewScreen(
                    state = OverviewUiState.NeedsCurrentFunds("September 2026"),
                    onRetry = {},
                    onSetCurrentFunds = { setupCount++ },
                )
            }
        }

        compose.onNodeWithText("Set up an account first").assertIsDisplayed()
        compose
            .onNodeWithText("Set up an account first")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithText("Set up an account").performClick()
        compose.runOnIdle { assertEquals(1, setupCount) }
    }

    @Test
    fun unsafeDataHidesTheChartAndOffersRetry() {
        var retryCount = 0
        compose.setContent {
            VectorintTheme {
                OverviewScreen(
                    state =
                        OverviewUiState.Unsafe(
                            monthLabel = "September 2026",
                            reasons = setOf(UnsafeReason.CURRENCY_MISMATCH),
                        ),
                    onRetry = { retryCount++ },
                )
            }
        }

        compose.onNodeWithText("Overview unavailable").assertIsDisplayed()
        compose.onNodeWithText("Expenses by category").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test
    fun chartColorsRemainDistinctForLargeCategoryLibrariesAndBothThemes() {
        val light = overviewSliceColors(count = 32, darkTheme = false)
        val dark = overviewSliceColors(count = 32, darkTheme = true)

        assertEquals(32, light.distinct().size)
        assertEquals(32, dark.distinct().size)
        light.zip(dark).forEach { (lightColor, darkColor) ->
            assertNotEquals(lightColor, darkColor)
        }
    }
}
