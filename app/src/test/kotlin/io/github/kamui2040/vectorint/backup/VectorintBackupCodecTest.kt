package io.github.kamui2040.vectorint.backup

import io.github.kamui2040.vectorint.core.AccountId
import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.ActivitySource
import io.github.kamui2040.vectorint.core.ActivityState
import io.github.kamui2040.vectorint.core.BudgetMonth
import io.github.kamui2040.vectorint.core.BudgetMonthAssignment
import io.github.kamui2040.vectorint.core.CategoryIcon
import io.github.kamui2040.vectorint.core.CategoryId
import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.CurrentFunds
import io.github.kamui2040.vectorint.core.CustomCategory
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.Money
import io.github.kamui2040.vectorint.core.OccurrenceTiming
import io.github.kamui2040.vectorint.core.PredefinedCategory
import io.github.kamui2040.vectorint.core.RecurrenceInterval
import io.github.kamui2040.vectorint.core.RecurrenceUnit
import io.github.kamui2040.vectorint.core.RecurringItem
import io.github.kamui2040.vectorint.core.RecurringItemId
import io.github.kamui2040.vectorint.core.RecurringSchedule
import io.github.kamui2040.vectorint.core.ReminderLead
import io.github.kamui2040.vectorint.core.ReminderSettings
import io.github.kamui2040.vectorint.core.Tag
import io.github.kamui2040.vectorint.data.BackupData
import io.github.kamui2040.vectorint.data.ColorPalette
import io.github.kamui2040.vectorint.data.ThemeMode
import io.github.kamui2040.vectorint.data.UserSettings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VectorintBackupCodecTest {
    private val codec = VectorintBackupCodec()

    @Test
    fun `round trip preserves complete supported data and exact timestamps`() {
        val backup = completeBackup()

        val decoded = codec.decode(codec.encode(backup))

        assertEquals(backup, decoded)
        assertEquals(
            123_456_789,
            decoded.data.accounts
                .single()
                .currentFunds.capturedAt.nano,
        )
        assertEquals(
            987_654_321,
            decoded.data.activities
                .single { it.id == ActivityId("confirmed-income") }
                .bookedAt
                ?.nano,
        )
    }

    @Test
    fun `round trip preserves multiple accounts flags and record assignments`() {
        val original = completeBackup()
        val main =
            original.data.accounts
                .single()
                .copy(name = "Bank")
        val cash =
            main.copy(
                id = AccountId("cash"),
                name = "Cash",
                currentFunds =
                    CurrentFunds(
                        amount = Money(12_345, main.currentFunds.amount.currency),
                        capturedAt = Instant.parse("2026-09-04T09:00:00.000000003Z"),
                    ),
                includeInAvailableNow = false,
            )
        val backup =
            original.copy(
                data =
                    original.data.copy(
                        accounts = listOf(cash, main),
                        activities =
                            original.data.activities.mapIndexed { index, activity ->
                                activity.copy(accountId = if (index == 0) main.id else cash.id)
                            },
                        recurringItems =
                            original.data.recurringItems.mapIndexed { index, item ->
                                item.copy(accountId = if (index == 0) cash.id else main.id)
                            },
                    ),
            )

        val decoded = codec.decode(codec.encode(backup))

        assertEquals(backup, decoded)
        assertEquals(listOf(false, true), decoded.data.accounts.map { it.includeInAvailableNow })
        assertEquals(listOf(main.id, cash.id), decoded.data.activities.map { it.accountId })
    }

    @Test
    fun `encoding is deterministic across record and tag order`() {
        val backup = completeBackup()
        val reordered =
            backup.copy(
                data =
                    backup.data.copy(
                        activities = backup.data.activities.reversed(),
                        recurringItems = backup.data.recurringItems.reversed(),
                    ),
            )

        assertArrayEquals(codec.encode(backup), codec.encode(reordered))
    }

    @Test
    fun `empty pre-setup budget is supported`() {
        val backup =
            VectorintBackup(
                createdAt = Instant.EPOCH,
                data = BackupData(null, emptyList(), emptyList()),
                settings = UserSettings(),
            )

        assertEquals(backup, codec.decode(codec.encode(backup)))
    }

    @Test
    fun `historical recurring activity does not require its deleted definition`() {
        val backup = completeBackup().let { it.copy(data = it.data.copy(recurringItems = emptyList())) }

        assertEquals(backup, codec.decode(codec.encode(backup)))
    }

    @Test
    fun `version four backups load every entry into Other`() {
        val categoryFree =
            completeBackup().let { backup ->
                backup.copy(
                    data =
                        backup.data.copy(
                            activities = backup.data.activities.map { it.copy(categoryId = null) },
                            recurringItems = backup.data.recurringItems.map { it.copy(categoryId = null) },
                            customCategories = emptyList(),
                        ),
                )
            }
        val versionFour =
            codec
                .encode(categoryFree)
                .toString(StandardCharsets.UTF_8)
                .asLegacyVersion(4)
                .replaceFirst(",\"name\":\"Salary payment\"", "")
                .replaceFirst(",\"name\":\"Rent\"", "")
                .replace(",\"customCategories\":[]", "")
                .replace(",\"categoryId\":null", "")

        val decoded = codec.decode(versionFour.toByteArray(StandardCharsets.UTF_8))

        assertEquals(4, decoded.sourceVersion)
        assertEquals(emptyList<CustomCategory>(), decoded.data.customCategories)
        assertEquals(List(decoded.data.activities.size) { null }, decoded.data.activities.map { it.categoryId })
        assertEquals(List(decoded.data.recurringItems.size) { null }, decoded.data.recurringItems.map { it.categoryId })
    }

    @Test
    fun `version five backups load activities with an empty legacy name`() {
        val versionFive =
            codec
                .encode(completeBackup())
                .toString(StandardCharsets.UTF_8)
                .asLegacyVersion(5)
                .replaceFirst(",\"name\":\"Salary payment\"", "")
                .replaceFirst(",\"name\":\"Rent\"", "")

        val decoded = codec.decode(versionFive.toByteArray(StandardCharsets.UTF_8))

        assertEquals(5, decoded.sourceVersion)
        assertEquals(
            "Main",
            decoded.data.accounts
                .single()
                .name,
        )
        assertEquals(listOf("", ""), decoded.data.activities.map { it.name })
    }

    @Test
    fun `unknown format version fields duplicates and trailing content are rejected`() {
        val text = codec.encode(completeBackup()).toString(StandardCharsets.UTF_8)

        assertInvalid(text.replace(VectorintBackupContract.FORMAT, "other.format"))
        assertInvalid(text.replace("\"version\":${VectorintBackupContract.VERSION}", "\"version\":99"))
        assertInvalid(text.replaceFirst("{", "{\"unknown\":true,"))
        assertInvalid(text.replaceFirst("{", "{\"format\":\"${VectorintBackupContract.FORMAT}\","))
        assertInvalid(
            text.replace(
                "\"settings\":{\"includeExpectedIncome\":true,\"themeMode\":\"dark\",\"colorPalette\":\"nebula\"},",
                "",
            ),
        )
        assertInvalid(text.replaceFirst("\"kind\":\"date_range\"", "\"kind\":\"sometime\""))
        assertInvalid(text + "{}")
    }

    @Test
    fun `strict scalar types and integer syntax are enforced`() {
        val text = codec.encode(completeBackup()).toString(StandardCharsets.UTF_8)

        assertInvalid(text.replaceFirst("\"minorUnits\":100000", "\"minorUnits\":1.0"))
        assertInvalid(text.replace("\"includeExpectedIncome\":true", "\"includeExpectedIncome\":\"true\""))
        assertInvalid(text.replace("\"themeMode\":\"dark\"", "\"themeMode\":\"automatic\""))
        assertInvalid(text.replace("\"colorPalette\":\"nebula\"", "\"colorPalette\":\"unknown\""))
        assertInvalid(text.replaceFirst("\"requireManualConfirmation\":true", "\"requireManualConfirmation\":1"))
        assertInvalid(text.replaceFirst("\"budgetMonth\":\"2026-09\"", "\"budgetMonth\":\"September\""))
        assertInvalid(text.replaceFirst("\"currency\":\"EUR\"", "\"currency\":\"eur\""))
    }

    @Test
    fun `invalid UTF-8 and oversized input are rejected`() {
        assertThrows(InvalidVectorintBackup::class.java) {
            codec.decode(byteArrayOf(0xC3.toByte(), 0x28))
        }
        assertThrows(InvalidVectorintBackup::class.java) {
            codec.decode(ByteArray(VectorintBackupContract.MAX_BYTES + 1))
        }
    }

    @Test
    fun `duplicate identities tags and mixed currency are rejected`() {
        val text = codec.encode(completeBackup()).toString(StandardCharsets.UTF_8)
        val duplicateActivity = text.replaceFirst("\"id\":\"planned-rent\"", "\"id\":\"confirmed-income\"")
        val duplicateOccurrence =
            text.replaceFirst(
                "\"itemId\":\"rent\",\"occurrenceKey\":\"2026-09\"",
                "\"itemId\":\"salary\",\"occurrenceKey\":\"2026-08\"",
            )
        val duplicateTag = text.replaceFirst("[\"home\",\"priority\"]", "[\"home\",\"home\"]")
        val mixedCurrency =
            text.replaceFirst(
                "\"minorUnits\":40000,\"currency\":\"EUR\"",
                "\"minorUnits\":40000,\"currency\":\"USD\"",
            )

        assertInvalid(duplicateActivity)
        assertInvalid(duplicateOccurrence)
        assertInvalid(duplicateTag)
        assertInvalid(mixedCurrency)
    }

    @Test
    fun `unknown assignments duplicate category names and unknown icons are rejected`() {
        val backup = completeBackup()
        val unknownAssignment =
            backup.copy(
                data =
                    backup.data.copy(
                        activities =
                            backup.data.activities.mapIndexed { index, activity ->
                                if (index == 0) activity.copy(categoryId = CategoryId("custom_missing")) else activity
                            },
                    ),
            )
        val duplicateName =
            backup.copy(
                data =
                    backup.data.copy(
                        customCategories =
                            backup.data.customCategories +
                                backup.data.customCategories.single().copy(
                                    id = CategoryId("custom_duplicate"),
                                    name = "work bonus",
                                ),
                    ),
            )

        assertThrows(InvalidVectorintBackup::class.java) { codec.encode(unknownAssignment) }
        assertThrows(InvalidVectorintBackup::class.java) { codec.encode(duplicateName) }
        assertInvalid(
            codec
                .encode(backup)
                .toString(StandardCharsets.UTF_8)
                .replaceFirst("\"icon\":\"work\"", "\"icon\":\"emoji\""),
        )
    }

    @Test
    fun `budget records without accounts are rejected`() {
        val backup = completeBackup().let { it.copy(data = it.data.copy(accounts = emptyList())) }

        assertThrows(InvalidVectorintBackup::class.java) { codec.encode(backup) }
    }

    @Test
    fun `unknown account assignments are rejected before backup replacement`() {
        val original = completeBackup()
        val unknown =
            original.copy(
                data =
                    original.data.copy(
                        activities =
                            original.data.activities.mapIndexed { index, activity ->
                                if (index == 0) activity.copy(accountId = AccountId("missing")) else activity
                            },
                    ),
            )

        assertThrows(InvalidVectorintBackup::class.java) { codec.encode(unknown) }
    }

    @Test
    fun `expected date can be outside its explicitly assigned budget month`() {
        val activity =
            completeBackup().data.activities.first().copy(
                expectedOn = LocalDate.of(2026, 10, 1),
            )
        val backup = completeBackup().let { it.copy(data = it.data.copy(activities = listOf(activity))) }

        assertEquals(backup, codec.decode(codec.encode(backup)))
    }

    @Test
    fun `remind and end notifications require their matching schedule dates`() {
        val item =
            completeBackup().data.recurringItems.first().copy(
                schedule = RecurringSchedule(firstOccurrence = LocalDate.of(2026, 1, 1)),
                reminders = ReminderSettings(remind = ReminderLead(1), end = ReminderLead(1)),
            )
        val backup = completeBackup().let { it.copy(data = it.data.copy(recurringItems = listOf(item))) }

        assertThrows(InvalidVectorintBackup::class.java) { codec.encode(backup) }
    }

    @Test
    fun `version one recurring definitions retain their first effective booking date`() {
        val legacy =
            """{"format":"${VectorintBackupContract.FORMAT}","version":1,"createdAt":{"epochSecond":0,"nano":0},"settings":{"includeExpectedIncome":false},"currentFunds":{"amount":{"minorUnits":100000,"currency":"EUR"},"capturedAt":{"epochSecond":0,"nano":0}},"activities":[],"recurringItems":[{"id":"legacy","name":"Legacy","direction":"expense","amount":{"minorUnits":1000,"currency":"EUR"},"timing":{"kind":"exact_day","day":31},"lifecycle":{"startsOn":"2026-02-01","endsAt":"2027-02-28","reviewOn":"2026-06-01"},"reminders":{"occurrenceDaysBefore":1,"reviewDaysBefore":7,"endDaysBefore":14},"tags":[]}]}"""

        val item =
            codec
                .decode(legacy.toByteArray(StandardCharsets.UTF_8))
                .data.recurringItems
                .single()

        assertEquals(LocalDate.of(2026, 2, 28), item.schedule.firstOccurrence)
        assertEquals(OccurrenceTiming.SpecificDate, item.schedule.timing)
        assertEquals(RecurrenceInterval.Monthly, item.schedule.interval)
        assertEquals(LocalDate.of(2026, 6, 1), item.schedule.remindOn)
        assertEquals(ReminderLead(7), item.reminders.remind)
    }

    @Test
    fun `version two exact schedules retain automatic confirmation defaults`() {
        val versionTwo =
            """{"format":"${VectorintBackupContract.FORMAT}","version":2,"createdAt":{"epochSecond":0,"nano":0},"settings":{"includeExpectedIncome":false},"currentFunds":{"amount":{"minorUnits":100000,"currency":"EUR"},"capturedAt":{"epochSecond":0,"nano":0}},"activities":[],"recurringItems":[{"id":"salary","name":"Salary","direction":"income","amount":{"minorUnits":1000,"currency":"EUR"},"schedule":{"firstOccurrence":"2026-09-05","repeatEvery":1,"repeatUnit":"months","countsToward":"occurrence_month","endsOn":null,"remindOn":null},"reminders":{"occurrenceDaysBefore":null,"remindDaysBefore":null,"endDaysBefore":null},"tags":[]}]}"""

        val schedule =
            codec
                .decode(versionTwo.toByteArray(StandardCharsets.UTF_8))
                .data.recurringItems
                .single()
                .schedule

        assertEquals(OccurrenceTiming.SpecificDate, schedule.timing)
        assertEquals(false, schedule.requireManualConfirmation)
    }

    private fun assertInvalid(text: String) {
        assertThrows(InvalidVectorintBackup::class.java) {
            codec.decode(text.toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun String.asLegacyVersion(version: Int): String {
        val accountsStart = indexOf("\"accounts\":[")
        val categoriesStart = indexOf(",\"customCategories\":", startIndex = accountsStart)
        check(accountsStart >= 0 && categoriesStart > accountsStart)
        val accountsJson = substring(accountsStart, categoriesStart)
        val currentFundsStart = accountsJson.indexOf("\"currentFunds\":") + "\"currentFunds\":".length
        val currentFundsEnd = accountsJson.indexOf(",\"includeInAvailableNow\":", startIndex = currentFundsStart)
        check(currentFundsStart >= 0 && currentFundsEnd > currentFundsStart)
        val currentFundsJson = accountsJson.substring(currentFundsStart, currentFundsEnd)
        return replaceRange(accountsStart, categoriesStart, "\"currentFunds\":$currentFundsJson")
            .replace("\"version\":${VectorintBackupContract.VERSION}", "\"version\":$version")
            .replace(",\"accountId\":\"legacy-main\"", "")
    }

    private fun completeBackup(): VectorintBackup {
        val eur = CurrencyCode.of("EUR")
        val customWork =
            CustomCategory(
                id = CategoryId("custom_work_bonus"),
                name = "Work bonus",
                icon = CategoryIcon.WORK,
            )
        return VectorintBackup(
            createdAt = Instant.parse("2026-09-06T12:30:00.000000007Z"),
            data =
                BackupData(
                    currentFunds =
                        CurrentFunds(
                            amount = Money(100_000, eur),
                            capturedAt = Instant.parse("2026-09-01T10:00:00.123456789Z"),
                        ),
                    activities =
                        listOf(
                            ActivityEntry(
                                id = ActivityId("confirmed-income"),
                                name = "Salary payment",
                                direction = Direction.INCOME,
                                amount = Money(12_345, eur),
                                state = ActivityState.CONFIRMED,
                                budgetMonth = BudgetMonth(YearMonth.of(2026, 8)),
                                bookedAt = Instant.parse("2026-09-02T08:15:00.987654321Z"),
                                source =
                                    ActivitySource.Recurring(
                                        itemId = RecurringItemId("salary"),
                                        occurrenceKey = "2026-08",
                                    ),
                                categoryId = customWork.id,
                                tags = setOf(Tag("work")),
                            ),
                            ActivityEntry(
                                id = ActivityId("planned-rent"),
                                name = "Rent",
                                direction = Direction.EXPENSE,
                                amount = Money(40_000, eur),
                                state = ActivityState.PLANNED,
                                budgetMonth = BudgetMonth(YearMonth.of(2026, 9)),
                                expectedOn = LocalDate.of(2026, 9, 5),
                                source =
                                    ActivitySource.Recurring(
                                        itemId = RecurringItemId("rent"),
                                        occurrenceKey = "2026-09",
                                    ),
                                categoryId = PredefinedCategory.HOUSING.id,
                                tags = setOf(Tag("priority"), Tag("home")),
                            ),
                        ),
                    recurringItems =
                        listOf(
                            RecurringItem(
                                id = RecurringItemId("quarterly"),
                                name = "Quarterly",
                                direction = Direction.EXPENSE,
                                amount = Money(1_000, eur),
                                schedule =
                                    RecurringSchedule(
                                        firstOccurrence = LocalDate.of(2026, 2, 28),
                                        timing = OccurrenceTiming.DateRange(2),
                                        interval = RecurrenceInterval(3, RecurrenceUnit.MONTHS),
                                        requireManualConfirmation = true,
                                    ),
                                categoryId = PredefinedCategory.INSURANCE.id,
                            ),
                            RecurringItem(
                                id = RecurringItemId("rent"),
                                name = "Rent",
                                direction = Direction.EXPENSE,
                                amount = Money(40_000, eur),
                                schedule =
                                    RecurringSchedule(
                                        firstOccurrence = LocalDate.of(2026, 1, 5),
                                        endsOn = LocalDate.of(2027, 12, 31),
                                        remindOn = LocalDate.of(2027, 6, 1),
                                    ),
                                reminders =
                                    ReminderSettings(
                                        occurrence = ReminderLead(2),
                                        remind = ReminderLead(7),
                                        end = ReminderLead(14),
                                    ),
                                categoryId = PredefinedCategory.HOUSING.id,
                                tags = setOf(Tag("home")),
                            ),
                            RecurringItem(
                                id = RecurringItemId("salary"),
                                name = "Salary",
                                direction = Direction.INCOME,
                                amount = Money(250_000, eur),
                                schedule =
                                    RecurringSchedule(
                                        firstOccurrence = LocalDate.of(2026, 1, 31),
                                        countsToward = BudgetMonthAssignment.FOLLOWING_MONTH,
                                    ),
                                categoryId = customWork.id,
                            ),
                        ),
                    customCategories = listOf(customWork),
                ),
            settings =
                UserSettings(
                    includeExpectedIncome = true,
                    themeMode = ThemeMode.DARK,
                    colorPalette = ColorPalette.NEBULA,
                ),
        )
    }
}
