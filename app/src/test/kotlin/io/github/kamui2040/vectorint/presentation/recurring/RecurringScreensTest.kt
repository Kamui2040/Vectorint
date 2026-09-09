package io.github.kamui2040.vectorint.presentation.recurring

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.kamui2040.vectorint.core.BudgetMonthAssignment
import io.github.kamui2040.vectorint.core.CategoryIcon
import io.github.kamui2040.vectorint.core.CategoryId
import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.CustomCategory
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.RecurrenceInterval
import io.github.kamui2040.vectorint.core.RecurrenceUnit
import io.github.kamui2040.vectorint.core.RecurringItemId
import io.github.kamui2040.vectorint.core.RecurringSchedule
import io.github.kamui2040.vectorint.core.Tag
import io.github.kamui2040.vectorint.presentation.theme.VectorintTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecurringScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `list shows interval and following-month assignment`() {
        val itemId = RecurringItemId("insurance")
        val category =
            CustomCategory(
                id = CategoryId("custom_annual_bills"),
                name = "Annual bills",
                icon = CategoryIcon.INSURANCE,
            )
        var selected: RecurringItemId? = null
        compose.setContent {
            VectorintTheme {
                RecurringListScreen(
                    state =
                        RecurringListUiState.Ready(
                            listOf(
                                RecurringListItemUi(
                                    id = itemId,
                                    name = "Insurance",
                                    direction = Direction.EXPENSE,
                                    amount = "€400.00",
                                    schedule =
                                        RecurringSchedule(
                                            firstOccurrence = LocalDate.of(2026, 9, 30),
                                            interval = RecurrenceInterval(3, RecurrenceUnit.MONTHS),
                                            countsToward = BudgetMonthAssignment.FOLLOWING_MONTH,
                                        ),
                                    firstOccurrenceLabel = "Sep 30, 2026",
                                    categoryId = category.id,
                                    tags = listOf("fixed"),
                                ),
                            ),
                            customCategories = listOf(category),
                        ),
                    onRetry = {},
                    onItemSelected = { selected = it },
                    onAdd = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Every 3 months · first Sep 30, 2026").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Counts toward the following month").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Annual bills").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Insurance").performClick()
        compose.runOnIdle { assertEquals(itemId, selected) }
    }

    @Test
    fun `list uses natural wording for a monthly item`() {
        compose.setContent {
            VectorintTheme {
                RecurringListScreen(
                    state =
                        RecurringListUiState.Ready(
                            listOf(
                                RecurringListItemUi(
                                    id = RecurringItemId("salary"),
                                    name = "Salary",
                                    direction = Direction.INCOME,
                                    amount = "€3,000.00",
                                    schedule =
                                        RecurringSchedule(
                                            firstOccurrence = LocalDate.of(2026, 9, 30),
                                            interval = RecurrenceInterval(1, RecurrenceUnit.MONTHS),
                                            countsToward = BudgetMonthAssignment.FOLLOWING_MONTH,
                                        ),
                                    firstOccurrenceLabel = "Sep 30, 2026",
                                    tags = emptyList(),
                                ),
                            ),
                        ),
                    onRetry = {},
                    onItemSelected = {},
                    onAdd = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Every month · first Sep 30, 2026").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `empty and failed recurring list states remain distinct`() {
        val state = mutableStateOf<RecurringListUiState>(RecurringListUiState.Empty)
        var retries = 0
        compose.setContent {
            VectorintTheme {
                RecurringListScreen(
                    state = state.value,
                    onRetry = { retries++ },
                    onItemSelected = {},
                    onAdd = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("No recurring items yet").assertIsDisplayed()
        compose.runOnIdle { state.value = RecurringListUiState.LoadFailed }
        compose.onNodeWithText("Couldn’t load recurring items").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun `editor exposes timing confirmation recurrence assignment and reminders`() {
        val direction = mutableStateOf(Direction.EXPENSE)
        val timingChoice = mutableStateOf(RecurringTimingChoice.SPECIFIC_DATE)
        val repeatUnit = mutableStateOf(RecurrenceUnit.MONTHS)
        val countsToward = mutableStateOf(BudgetMonthAssignment.OCCURRENCE_MONTH)
        val requireManualConfirmation = mutableStateOf(false)
        var firstSelections = 0
        var periodEndSelections = 0
        var saves = 0
        val seed =
            RecurringItemFormSeed(
                item = null,
                nameInput = "",
                amountInput = "",
                currencyCode = CurrencyCode.of("EUR"),
                direction = Direction.EXPENSE,
                firstOccurrence = LocalDate.of(2026, 9, 15),
                repeatEveryInput = "1",
                repeatUnit = RecurrenceUnit.MONTHS,
                countsToward = BudgetMonthAssignment.OCCURRENCE_MONTH,
                endsOn = null,
                remindOn = null,
                occurrenceReminder = RecurringReminderInput(false, "1"),
                remindReminder = RecurringReminderInput(false, "7"),
                endReminder = RecurringReminderInput(false, "7"),
                tags = setOf(Tag("housing")),
            )
        compose.setContent {
            VectorintTheme {
                RecurringItemEditorScreen(
                    seed = seed,
                    nameInput = "Salary",
                    amountInput = "2500.00",
                    direction = direction.value,
                    timingChoice = timingChoice.value,
                    firstOccurrenceLabel = "Sep 30, 2026",
                    firstPeriodEndsOnLabel = "Oct 2, 2026",
                    repeatEveryInput = "3",
                    repeatUnit = repeatUnit.value,
                    countsToward = countsToward.value,
                    requireManualConfirmation = requireManualConfirmation.value,
                    endsOnLabel = "Sep 30, 2027",
                    remindOnLabel = "Jun 1, 2027",
                    occurrenceReminder = RecurringReminderInput(false, "1"),
                    remindReminder = RecurringReminderInput(false, "7"),
                    endReminder = RecurringReminderInput(false, "7"),
                    notificationsAvailable = true,
                    saving = false,
                    issue = null,
                    onNameChange = {},
                    onAmountChange = {},
                    onDirectionChange = { direction.value = it },
                    onTimingChoiceChange = { timingChoice.value = it },
                    onSelectFirstOccurrence = { firstSelections++ },
                    onSelectFirstPeriodEndsOn = { periodEndSelections++ },
                    onRepeatEveryChange = {},
                    onRepeatUnitChange = { repeatUnit.value = it },
                    onCountsTowardChange = { countsToward.value = it },
                    onRequireManualConfirmationChange = { requireManualConfirmation.value = it },
                    onSelectEndsOn = {},
                    onClearEndsOn = {},
                    onSelectRemindOn = {},
                    onClearRemindOn = {},
                    onOccurrenceReminderEnabledChange = {},
                    onOccurrenceReminderDaysChange = {},
                    onRemindReminderEnabledChange = {},
                    onRemindReminderDaysChange = {},
                    onEndReminderEnabledChange = {},
                    onEndReminderDaysChange = {},
                    onOpenNotificationSettings = {},
                    onSave = { saves++ },
                    onDelete = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Income").performClick()
        val expectedIncomeHelp =
            "Future income is included before its booking date only when “Include expected income” is on. " +
                "On that date it confirms automatically unless you require manual confirmation."
        compose.onNodeWithText(expectedIncomeHelp).assertDoesNotExist()
        compose.onNodeWithContentDescription("More about Direction").performScrollTo().performClick()
        compose.onNodeWithText(expectedIncomeHelp).assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("First occurrence: Sep 30, 2026").performScrollTo().performClick()
        compose.onNodeWithText("Date range").performScrollTo().performClick()
        compose.onNodeWithText("First period ends: Oct 2, 2026").performScrollTo().performClick()
        compose.onNodeWithText("Years").performScrollTo().performClick()
        compose.onNodeWithText("Following month").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Require manual confirmation").performScrollTo().performClick()
        compose.onNodeWithText("Ends on: Sep 30, 2027").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Remind me on: Jun 1, 2027").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save recurring item").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(Direction.INCOME, direction.value)
            assertEquals(RecurringTimingChoice.DATE_RANGE, timingChoice.value)
            assertEquals(RecurrenceUnit.YEARS, repeatUnit.value)
            assertEquals(BudgetMonthAssignment.FOLLOWING_MONTH, countsToward.value)
            assertEquals(true, requireManualConfirmation.value)
            assertEquals(1, firstSelections)
            assertEquals(1, periodEndSelections)
            assertEquals(1, saves)
        }
    }
}
