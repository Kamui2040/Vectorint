package io.github.kamui2040.vectorint.presentation.recurring

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
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

        compose
            .onNodeWithTag("recurring_list")
            .performScrollToNode(hasText("Every 3 months · first Sep 30, 2026"))
        compose.onNodeWithText("Every 3 months · first Sep 30, 2026").assertIsDisplayed()
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

        compose
            .onNodeWithTag("recurring_list")
            .performScrollToNode(hasText("Every month · first Sep 30, 2026"))
        compose.onNodeWithText("Every month · first Sep 30, 2026").assertIsDisplayed()
    }

    @Test
    fun `recurring list searches visible details and filters by direction`() {
        compose.setContent {
            VectorintTheme {
                RecurringListScreen(
                    state =
                        RecurringListUiState.Ready(
                            listOf(
                                RecurringListItemUi(
                                    id = RecurringItemId("rent"),
                                    name = "Rent",
                                    direction = Direction.EXPENSE,
                                    amount = "€500.00",
                                    schedule = RecurringSchedule(LocalDate.of(2026, 9, 20)),
                                    firstOccurrenceLabel = "Sep 20, 2026",
                                    tags = listOf("housing"),
                                ),
                                RecurringListItemUi(
                                    id = RecurringItemId("salary"),
                                    name = "Salary",
                                    direction = Direction.INCOME,
                                    amount = "€2,000.00",
                                    schedule = RecurringSchedule(LocalDate.of(2026, 9, 19)),
                                    firstOccurrenceLabel = "Sep 19, 2026",
                                    tags = listOf("work"),
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

        compose.onNodeWithText("Search recurring items").performTextInput("salary")
        compose.waitForIdle()
        compose.onNodeWithTag("recurring_list").performScrollToNode(hasText("Salary"))
        compose.onNodeWithText("Salary").assertIsDisplayed()
        compose.onNodeWithText("Rent").assertDoesNotExist()
        compose.onNodeWithContentDescription("Clear search").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("open_filters").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("recurring_direction:EXPENSE").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("recurring_list").performScrollToNode(hasText("Rent"))
        compose.onNodeWithText("Rent").assertIsDisplayed()
        compose.onNodeWithText("Salary").assertDoesNotExist()
        compose.onNodeWithTag("open_filters").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("recurring_direction:INCOME").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("recurring_list").performScrollToNode(hasText("Salary"))
        compose.onNodeWithText("Salary").assertIsDisplayed()
        compose.onNodeWithText("Rent").assertDoesNotExist()
    }

    @Test
    fun `recurring list filters assigned and unassigned items`() {
        compose.setContent {
            VectorintTheme {
                RecurringListScreen(
                    state =
                        RecurringListUiState.Ready(
                            listOf(
                                RecurringListItemUi(
                                    id = RecurringItemId("assigned"),
                                    name = "Assigned rent",
                                    direction = Direction.EXPENSE,
                                    amount = "€500.00",
                                    schedule = RecurringSchedule(LocalDate.of(2026, 9, 20)),
                                    firstOccurrenceLabel = "Sep 20, 2026",
                                    accountName = "Main",
                                ),
                                RecurringListItemUi(
                                    id = RecurringItemId("unassigned"),
                                    name = "Kept rent",
                                    direction = Direction.EXPENSE,
                                    amount = "€600.00",
                                    schedule = RecurringSchedule(LocalDate.of(2026, 9, 21)),
                                    firstOccurrenceLabel = "Sep 21, 2026",
                                    accountName = null,
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

        compose.onNodeWithTag("open_filters").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("recurring_assignment:UNASSIGNED").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("active_filter:assignment").assertIsSelected()
        compose.onNodeWithTag("recurring_list").performScrollToNode(hasText("Kept rent"))
        compose.onNodeWithText("Kept rent").assertIsDisplayed()
        compose.onNodeWithText("Assigned rent").assertDoesNotExist()
        compose.onNodeWithText("Account: Unassigned").assertIsDisplayed()

        compose.onNodeWithTag("open_filters").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("recurring_assignment:ASSIGNED").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("recurring_list").performScrollToNode(hasText("Assigned rent"))
        compose.onNodeWithText("Assigned rent").assertIsDisplayed()
        compose.onNodeWithText("Kept rent").assertDoesNotExist()
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
            "Income counts from its occurrence day by default. Enable “Include before occurrence” below " +
                "to count it earlier in its assigned budget month."
        compose.onNodeWithText(expectedIncomeHelp).assertDoesNotExist()
        compose.onNodeWithContentDescription("More about Direction").performScrollTo().performClick()
        compose.onNodeWithText(expectedIncomeHelp).assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("First occurrence: Sep 30, 2026").performScrollTo().performClick()
        compose.onNodeWithText("Range").performScrollTo().performClick()
        compose.onNodeWithText("First period ends: Oct 2, 2026").performScrollTo().performClick()
        compose.onNodeWithText("Years").performScrollTo().performClick()
        compose.onNodeWithText("Following month").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Require manual confirmation").performScrollTo().performClick()
        compose.onNodeWithTag("recurring_end_date_section").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Set an end date").assertDoesNotExist()
        compose.onNodeWithTag("recurring_end_date_section").performClick()
        compose.onAllNodesWithText("Ends on: Sep 30, 2027").assertCountEquals(2)
        compose.onNodeWithTag("recurring_reminders_section").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Remind me on: Jun 1, 2027").assertDoesNotExist()
        compose.onNodeWithTag("recurring_reminders_section").performClick()
        compose.onNodeWithText("Remind me on: Jun 1, 2027").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("recurring_organization_section").performScrollTo().assertIsDisplayed()
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
