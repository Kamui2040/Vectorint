package io.github.kamui2040.vectorint.backup

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import io.github.kamui2040.vectorint.core.Account
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
import io.github.kamui2040.vectorint.core.RecurrenceInterval
import io.github.kamui2040.vectorint.core.RecurrenceUnit
import io.github.kamui2040.vectorint.core.RecurringItem
import io.github.kamui2040.vectorint.core.RecurringItemId
import io.github.kamui2040.vectorint.core.RecurringSchedule
import io.github.kamui2040.vectorint.core.ReminderLead
import io.github.kamui2040.vectorint.core.ReminderSettings
import io.github.kamui2040.vectorint.core.Tag
import io.github.kamui2040.vectorint.core.asLegacyDefaultAccount
import io.github.kamui2040.vectorint.data.BackupData
import io.github.kamui2040.vectorint.data.ColorPalette
import io.github.kamui2040.vectorint.data.ThemeMode
import io.github.kamui2040.vectorint.data.UserSettings
import java.io.StringReader
import java.io.StringWriter
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

internal class VectorintBackupCodec {
    fun encode(backup: VectorintBackup): ByteArray {
        VectorintBackupValidator.validate(backup)
        val output = StringWriter()
        JsonWriter(output).use { writer ->
            writer.beginObject()
            writer.name("format").value(VectorintBackupContract.FORMAT)
            writer.name("version").value(VectorintBackupContract.VERSION.toLong())
            writer.name("createdAt")
            writer.writeInstant(backup.createdAt)
            writer.name("settings")
            writer.writeSettings(backup.settings)
            writer.name("accounts").beginArray()
            backup.data.accounts
                .sortedBy { it.id.value }
                .forEach(writer::writeAccount)
            writer.endArray()
            writer.name("customCategories").beginArray()
            backup.data.customCategories
                .sortedBy { it.id.value }
                .forEach(writer::writeCustomCategory)
            writer.endArray()
            writer.name("activities").beginArray()
            backup.data.activities
                .sortedBy { it.id.value }
                .forEach(writer::writeActivity)
            writer.endArray()
            writer.name("recurringItems").beginArray()
            backup.data.recurringItems
                .sortedBy { it.id.value }
                .forEach(writer::writeRecurringItem)
            writer.endArray()
            writer.endObject()
        }
        return output.toString().toByteArray(StandardCharsets.UTF_8).also { bytes ->
            if (bytes.size > VectorintBackupContract.MAX_BYTES) {
                throw InvalidVectorintBackup("Backup exceeds the size limit")
            }
        }
    }

    fun decode(bytes: ByteArray): VectorintBackup {
        if (bytes.size > VectorintBackupContract.MAX_BYTES) {
            throw InvalidVectorintBackup("Backup exceeds the size limit")
        }
        val text =
            try {
                StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            } catch (_: Exception) {
                throw InvalidVectorintBackup("Backup is not valid UTF-8")
            }

        val backup =
            try {
                val version =
                    JsonReader(StringReader(text)).use { reader ->
                        reader.isLenient = false
                        val parsedVersion = reader.readBackupVersion()
                        if (reader.peek() != JsonToken.END_DOCUMENT) invalid("Unexpected data after backup")
                        parsedVersion
                    }
                JsonReader(StringReader(text)).use { reader ->
                    reader.isLenient = false
                    val parsed = reader.readBackup(version)
                    if (reader.peek() != JsonToken.END_DOCUMENT) invalid("Unexpected data after backup")
                    parsed
                }
            } catch (invalid: InvalidVectorintBackup) {
                throw invalid
            } catch (_: Exception) {
                throw InvalidVectorintBackup("Backup JSON is malformed")
            }
        VectorintBackupValidator.validate(backup)
        return backup
    }
}

private fun JsonWriter.writeSettings(settings: UserSettings) {
    beginObject()
    name("includeExpectedIncome").value(settings.includeExpectedIncome)
    name("themeMode").value(settings.themeMode.storedValue())
    name("colorPalette").value(settings.colorPalette.storedValue())
    endObject()
}

private fun JsonWriter.writeCurrentFunds(currentFunds: CurrentFunds) {
    beginObject()
    name("amount")
    writeMoney(currentFunds.amount)
    name("capturedAt")
    writeInstant(currentFunds.capturedAt)
    endObject()
}

private fun JsonWriter.writeAccount(account: Account) {
    beginObject()
    name("id").value(account.id.value)
    name("name").value(account.name)
    name("currentFunds")
    writeCurrentFunds(account.currentFunds)
    name("includeInAvailableNow").value(account.includeInAvailableNow)
    endObject()
}

private fun JsonWriter.writeMoney(money: Money) {
    beginObject()
    name("minorUnits").value(money.minorUnits)
    name("currency").value(money.currency.value)
    endObject()
}

private fun JsonWriter.writeInstant(instant: Instant) {
    beginObject()
    name("epochSecond").value(instant.epochSecond)
    name("nano").value(instant.nano.toLong())
    endObject()
}

private fun JsonWriter.writeActivity(activity: ActivityEntry) {
    beginObject()
    name("id").value(activity.id.value)
    name("name").value(activity.name)
    name("accountId").value(activity.accountId.value)
    name("direction").value(activity.direction.storedValue())
    name("amount")
    writeMoney(activity.amount)
    name("state").value(activity.state.storedValue())
    name("budgetMonth").value(activity.budgetMonth.value.toString())
    name("expectedOn")
    activity.expectedOn?.let { value(it.toString()) } ?: nullValue()
    name("bookedAt")
    activity.bookedAt?.let(::writeInstant) ?: nullValue()
    name("source")
    writeSource(activity.source)
    name("categoryId")
    activity.categoryId?.let { value(it.value) } ?: nullValue()
    name("tags")
    writeTags(activity.tags)
    endObject()
}

private fun JsonWriter.writeSource(source: ActivitySource) {
    beginObject()
    when (source) {
        ActivitySource.OneOff -> name("kind").value("one_off")
        is ActivitySource.Recurring -> {
            name("kind").value("recurring")
            name("itemId").value(source.itemId.value)
            name("occurrenceKey").value(source.occurrenceKey)
        }
    }
    endObject()
}

private fun JsonWriter.writeRecurringItem(item: RecurringItem) {
    beginObject()
    name("id").value(item.id.value)
    name("name").value(item.name)
    name("accountId").value(item.accountId.value)
    name("direction").value(item.direction.storedValue())
    name("amount")
    writeMoney(item.amount)
    name("schedule")
    writeSchedule(item.schedule)
    name("reminders")
    writeReminders(item.reminders)
    name("categoryId")
    item.categoryId?.let { value(it.value) } ?: nullValue()
    name("tags")
    writeTags(item.tags)
    endObject()
}

private fun JsonWriter.writeCustomCategory(category: CustomCategory) {
    beginObject()
    name("id").value(category.id.value)
    name("name").value(category.name)
    name("icon").value(category.icon.storedValue())
    endObject()
}

private fun CategoryIcon.storedValue(): String = name.lowercase()

private fun JsonWriter.writeSchedule(schedule: RecurringSchedule) {
    beginObject()
    name("firstOccurrence").value(schedule.firstOccurrence.toString())
    name("timing")
    writeOccurrenceTiming(schedule.timing)
    name("repeatEvery").value(schedule.interval.every.toLong())
    name("repeatUnit").value(schedule.interval.unit.storedValue())
    name("countsToward").value(schedule.countsToward.storedValue())
    name("requireManualConfirmation").value(schedule.requireManualConfirmation)
    name("endsOn")
    schedule.endsOn?.let { value(it.toString()) } ?: nullValue()
    name("remindOn")
    schedule.remindOn?.let { value(it.toString()) } ?: nullValue()
    endObject()
}

private fun JsonWriter.writeOccurrenceTiming(timing: OccurrenceTiming) {
    beginObject()
    when (timing) {
        OccurrenceTiming.SpecificDate -> name("kind").value("specific_date")
        is OccurrenceTiming.DateRange -> {
            name("kind").value("date_range")
            name("endOffsetDays").value(timing.endOffsetDays.toLong())
        }

        OccurrenceTiming.AnyTimeInMonth -> name("kind").value("any_time_in_month")
    }
    endObject()
}

private fun JsonWriter.writeReminders(reminders: ReminderSettings) {
    beginObject()
    name("occurrenceDaysBefore")
    reminders.occurrence?.let { value(it.daysBefore.toLong()) } ?: nullValue()
    name("remindDaysBefore")
    reminders.remind?.let { value(it.daysBefore.toLong()) } ?: nullValue()
    name("endDaysBefore")
    reminders.end?.let { value(it.daysBefore.toLong()) } ?: nullValue()
    endObject()
}

private fun JsonWriter.writeTags(tags: Set<Tag>) {
    beginArray()
    tags.sortedBy { it.value }.forEach { value(it.value) }
    endArray()
}

private fun JsonReader.readBackupVersion(): Int {
    var format: String? = null
    var version: Int? = null
    val fields =
        readObject { field ->
            when (field) {
                "format" -> format = readStringValue()
                "version" -> version = readIntValue()
                "createdAt",
                "settings",
                "currentFunds",
                "accounts",
                "activities",
                "recurringItems",
                "customCategories",
                -> skipValue()

                else -> invalid("Unknown backup field: $field")
            }
        }
    if (format != VectorintBackupContract.FORMAT) invalid("Unknown backup format")
    val parsedVersion = requireNotNull(version)
    if (parsedVersion !in VectorintBackupContract.MIN_SUPPORTED_VERSION..VectorintBackupContract.VERSION) {
        invalid("Unsupported backup version")
    }
    fields.requireExactly(
        buildSet {
            addAll(setOf("format", "version", "createdAt", "settings", "activities", "recurringItems"))
            if (parsedVersion >= 7) add("accounts") else add("currentFunds")
            if (parsedVersion >= 5) add("customCategories")
        },
    )
    return parsedVersion
}

private fun JsonReader.readBackup(versionToRead: Int): VectorintBackup {
    var format: String? = null
    var version: Int? = null
    var createdAt: Instant? = null
    var settings: UserSettings? = null
    var currentFunds: CurrentFunds? = null
    var accounts: List<Account>? = null
    var activities: List<ActivityEntry>? = null
    var recurringItems: List<RecurringItem>? = null
    var customCategories: List<CustomCategory>? = null
    val fields =
        readObject { field ->
            when (field) {
                "format" -> format = readStringValue()
                "version" -> version = readIntValue()
                "createdAt" -> createdAt = readInstant()
                "settings" -> settings = readSettings(versionToRead)
                "currentFunds" -> currentFunds = readNullable { readCurrentFunds() }
                "accounts" -> accounts = readAccounts()
                "activities" -> activities = readActivities(versionToRead)
                "recurringItems" -> recurringItems = readRecurringItems(versionToRead)
                "customCategories" -> customCategories = readCustomCategories()
                else -> invalid("Unknown backup field: $field")
            }
        }
    fields.requireExactly(
        buildSet {
            addAll(setOf("format", "version", "createdAt", "settings", "activities", "recurringItems"))
            if (versionToRead >= 7) add("accounts") else add("currentFunds")
            if (versionToRead >= 5) add("customCategories")
        },
    )
    if (format != VectorintBackupContract.FORMAT) invalid("Unknown backup format")
    if (version != versionToRead) invalid("Backup version changed while reading")
    return VectorintBackup(
        createdAt = requireNotNull(createdAt),
        data =
            BackupData(
                accounts =
                    if (versionToRead >= 7) {
                        requireNotNull(accounts)
                    } else {
                        currentFunds?.let { listOf(it.asLegacyDefaultAccount()) }.orEmpty()
                    },
                activities = requireNotNull(activities),
                recurringItems = requireNotNull(recurringItems),
                customCategories = customCategories.orEmpty(),
            ),
        settings = requireNotNull(settings),
        sourceVersion = versionToRead,
    )
}

private fun JsonReader.readSettings(version: Int): UserSettings {
    var includeExpectedIncome: Boolean? = null
    var themeMode: ThemeMode? = null
    var colorPalette: ColorPalette? = null
    val fields =
        readObject { field ->
            when (field) {
                "includeExpectedIncome" -> includeExpectedIncome = readBooleanValue()
                "themeMode" -> themeMode = readThemeMode()
                "colorPalette" -> colorPalette = readColorPalette()
                else -> invalid("Unknown settings field: $field")
            }
        }
    if (version >= 4) {
        fields.requireExactly(setOf("includeExpectedIncome", "themeMode", "colorPalette"))
    } else {
        fields.requireExactly(setOf("includeExpectedIncome"))
    }
    return UserSettings(
        includeExpectedIncome = requireNotNull(includeExpectedIncome),
        themeMode = themeMode ?: ThemeMode.FOLLOW_SYSTEM,
        colorPalette = colorPalette ?: ColorPalette.ORBIT,
    )
}

private fun ThemeMode.storedValue(): String =
    when (this) {
        ThemeMode.FOLLOW_SYSTEM -> "system"
        ThemeMode.LIGHT -> "light"
        ThemeMode.DARK -> "dark"
    }

private fun ColorPalette.storedValue(): String =
    when (this) {
        ColorPalette.ORBIT -> "orbit"
        ColorPalette.NOVA -> "nova"
        ColorPalette.NEBULA -> "nebula"
    }

private fun JsonReader.readThemeMode(): ThemeMode =
    when (val value = readStringValue()) {
        "system" -> ThemeMode.FOLLOW_SYSTEM
        "light" -> ThemeMode.LIGHT
        "dark" -> ThemeMode.DARK
        else -> invalid("Unknown theme mode: $value")
    }

private fun JsonReader.readColorPalette(): ColorPalette =
    when (val value = readStringValue()) {
        "orbit" -> ColorPalette.ORBIT
        "nova" -> ColorPalette.NOVA
        "nebula" -> ColorPalette.NEBULA
        else -> invalid("Unknown color palette: $value")
    }

private fun JsonReader.readCurrentFunds(): CurrentFunds {
    var amount: Money? = null
    var capturedAt: Instant? = null
    val fields =
        readObject { field ->
            when (field) {
                "amount" -> amount = readMoney()
                "capturedAt" -> capturedAt = readInstant()
                else -> invalid("Unknown Current funds field: $field")
            }
        }
    fields.requireExactly(setOf("amount", "capturedAt"))
    return CurrentFunds(requireNotNull(amount), requireNotNull(capturedAt))
}

private fun JsonReader.readAccounts(): List<Account> =
    readArray(VectorintBackupContract.MAX_ACCOUNTS, "accounts") {
        var id: AccountId? = null
        var name: String? = null
        var currentFunds: CurrentFunds? = null
        var includeInAvailableNow: Boolean? = null
        val fields =
            readObject { field ->
                when (field) {
                    "id" -> id = AccountId(readStringValue())
                    "name" -> name = readStringValue()
                    "currentFunds" -> currentFunds = readCurrentFunds()
                    "includeInAvailableNow" -> includeInAvailableNow = readBooleanValue()
                    else -> invalid("Unknown account field: $field")
                }
            }
        fields.requireExactly(setOf("id", "name", "currentFunds", "includeInAvailableNow"))
        Account(
            id = requireNotNull(id),
            name = requireNotNull(name),
            currentFunds = requireNotNull(currentFunds),
            includeInAvailableNow = requireNotNull(includeInAvailableNow),
        )
    }

private fun JsonReader.readMoney(): Money {
    var minorUnits: Long? = null
    var currency: CurrencyCode? = null
    val fields =
        readObject { field ->
            when (field) {
                "minorUnits" -> minorUnits = readLongValue()
                "currency" -> currency = readCurrencyCode()
                else -> invalid("Unknown money field: $field")
            }
        }
    fields.requireExactly(setOf("minorUnits", "currency"))
    return Money(requireNotNull(minorUnits), requireNotNull(currency))
}

private fun JsonReader.readInstant(): Instant {
    var epochSecond: Long? = null
    var nano: Int? = null
    val fields =
        readObject { field ->
            when (field) {
                "epochSecond" -> epochSecond = readLongValue()
                "nano" -> nano = readIntValue()
                else -> invalid("Unknown instant field: $field")
            }
        }
    fields.requireExactly(setOf("epochSecond", "nano"))
    val nanos = requireNotNull(nano)
    if (nanos !in 0..999_999_999) invalid("Nanoseconds are out of range")
    return Instant.ofEpochSecond(requireNotNull(epochSecond), nanos.toLong())
}

private fun JsonReader.readActivities(version: Int): List<ActivityEntry> =
    readArray(VectorintBackupContract.MAX_ACTIVITIES, "activities") { readActivity(version) }

private fun JsonReader.readActivity(version: Int): ActivityEntry {
    var id: ActivityId? = null
    var name = ""
    var accountId: AccountId? = null
    var direction: Direction? = null
    var amount: Money? = null
    var state: ActivityState? = null
    var budgetMonth: BudgetMonth? = null
    var expectedOn: LocalDate? = null
    var bookedAt: Instant? = null
    var source: ActivitySource? = null
    var categoryId: CategoryId? = null
    var tags: Set<Tag>? = null
    val fields =
        readObject { field ->
            when (field) {
                "id" -> id = ActivityId(readStringValue())
                "name" -> name = readStringValue()
                "accountId" -> accountId = AccountId(readStringValue())
                "direction" -> direction = readDirection()
                "amount" -> amount = readMoney()
                "state" -> state = readActivityState()
                "budgetMonth" -> budgetMonth = BudgetMonth(parseYearMonth(readStringValue()))
                "expectedOn" -> expectedOn = readNullable { parseDate(readStringValue()) }
                "bookedAt" -> bookedAt = readNullable { readInstant() }
                "source" -> source = readSource()
                "categoryId" -> categoryId = readNullable { CategoryId(readStringValue()) }
                "tags" -> tags = readTags()
                else -> invalid("Unknown activity field: $field")
            }
        }
    fields.requireExactly(
        buildSet {
            addAll(setOf("id", "direction", "amount", "state", "budgetMonth", "expectedOn", "bookedAt", "source", "tags"))
            if (version >= 5) add("categoryId")
            if (version >= 6) add("name")
            if (version >= 7) add("accountId")
        },
    )
    return ActivityEntry(
        id = requireNotNull(id),
        name = name,
        accountId = accountId ?: io.github.kamui2040.vectorint.core.LEGACY_DEFAULT_ACCOUNT_ID,
        direction = requireNotNull(direction),
        amount = requireNotNull(amount),
        state = requireNotNull(state),
        budgetMonth = requireNotNull(budgetMonth),
        expectedOn = expectedOn,
        bookedAt = bookedAt,
        source = requireNotNull(source),
        categoryId = categoryId,
        tags = requireNotNull(tags),
    )
}

private fun JsonReader.readSource(): ActivitySource {
    var kind: String? = null
    var itemId: RecurringItemId? = null
    var occurrenceKey: String? = null
    val fields =
        readObject { field ->
            when (field) {
                "kind" -> kind = readStringValue()
                "itemId" -> itemId = RecurringItemId(readStringValue())
                "occurrenceKey" -> occurrenceKey = readStringValue()
                else -> invalid("Unknown activity source field: $field")
            }
        }
    return when (kind) {
        "one_off" -> {
            fields.requireExactly(setOf("kind"))
            ActivitySource.OneOff
        }

        "recurring" -> {
            fields.requireExactly(setOf("kind", "itemId", "occurrenceKey"))
            ActivitySource.Recurring(requireNotNull(itemId), requireNotNull(occurrenceKey))
        }

        else -> invalid("Unknown activity source")
    }
}

private fun JsonReader.readRecurringItems(version: Int): List<RecurringItem> =
    readArray(VectorintBackupContract.MAX_RECURRING_ITEMS, "recurring items") { readRecurringItem(version) }

private fun JsonReader.readRecurringItem(version: Int): RecurringItem =
    when (version) {
        1 -> readLegacyRecurringItem()
        2,
        3,
        4,
        5,
        6,
        VectorintBackupContract.VERSION,
        -> readCurrentRecurringItem(version)

        else -> invalid("Unsupported backup version")
    }

private fun JsonReader.readCurrentRecurringItem(version: Int): RecurringItem {
    var id: RecurringItemId? = null
    var name: String? = null
    var accountId: AccountId? = null
    var direction: Direction? = null
    var amount: Money? = null
    var schedule: RecurringSchedule? = null
    var reminders: ReminderSettings? = null
    var categoryId: CategoryId? = null
    var tags: Set<Tag>? = null
    val fields =
        readObject { field ->
            when (field) {
                "id" -> id = RecurringItemId(readStringValue())
                "name" -> name = readStringValue()
                "accountId" -> accountId = AccountId(readStringValue())
                "direction" -> direction = readDirection()
                "amount" -> amount = readMoney()
                "schedule" -> schedule = readSchedule(version)
                "reminders" -> reminders = readCurrentReminders()
                "categoryId" -> categoryId = readNullable { CategoryId(readStringValue()) }
                "tags" -> tags = readTags()
                else -> invalid("Unknown recurring item field: $field")
            }
        }
    fields.requireExactly(
        buildSet {
            addAll(setOf("id", "name", "direction", "amount", "schedule", "reminders", "tags"))
            if (version >= 5) add("categoryId")
            if (version >= 7) add("accountId")
        },
    )
    return RecurringItem(
        id = requireNotNull(id),
        name = requireNotNull(name),
        accountId = accountId ?: io.github.kamui2040.vectorint.core.LEGACY_DEFAULT_ACCOUNT_ID,
        direction = requireNotNull(direction),
        amount = requireNotNull(amount),
        schedule = requireNotNull(schedule),
        reminders = requireNotNull(reminders),
        categoryId = categoryId,
        tags = requireNotNull(tags),
    )
}

private fun JsonReader.readSchedule(version: Int): RecurringSchedule = if (version == 2) readVersionTwoSchedule() else readCurrentSchedule()

private fun JsonReader.readVersionTwoSchedule(): RecurringSchedule {
    var firstOccurrence: LocalDate? = null
    var repeatEvery: Int? = null
    var repeatUnit: RecurrenceUnit? = null
    var countsToward: BudgetMonthAssignment? = null
    var endsOn: LocalDate? = null
    var remindOn: LocalDate? = null
    val fields =
        readObject { field ->
            when (field) {
                "firstOccurrence" -> firstOccurrence = parseDate(readStringValue())
                "repeatEvery" -> repeatEvery = readIntValue()
                "repeatUnit" -> repeatUnit = readRecurrenceUnit()
                "countsToward" -> countsToward = readBudgetMonthAssignment()
                "endsOn" -> endsOn = readNullable { parseDate(readStringValue()) }
                "remindOn" -> remindOn = readNullable { parseDate(readStringValue()) }
                else -> invalid("Unknown schedule field: $field")
            }
        }
    fields.requireExactly(setOf("firstOccurrence", "repeatEvery", "repeatUnit", "countsToward", "endsOn", "remindOn"))
    return RecurringSchedule(
        firstOccurrence = requireNotNull(firstOccurrence),
        interval = RecurrenceInterval(requireNotNull(repeatEvery), requireNotNull(repeatUnit)),
        countsToward = requireNotNull(countsToward),
        endsOn = endsOn,
        remindOn = remindOn,
    )
}

private fun JsonReader.readCurrentSchedule(): RecurringSchedule {
    var firstOccurrence: LocalDate? = null
    var timing: OccurrenceTiming? = null
    var repeatEvery: Int? = null
    var repeatUnit: RecurrenceUnit? = null
    var countsToward: BudgetMonthAssignment? = null
    var requireManualConfirmation: Boolean? = null
    var endsOn: LocalDate? = null
    var remindOn: LocalDate? = null
    val fields =
        readObject { field ->
            when (field) {
                "firstOccurrence" -> firstOccurrence = parseDate(readStringValue())
                "timing" -> timing = readOccurrenceTiming()
                "repeatEvery" -> repeatEvery = readIntValue()
                "repeatUnit" -> repeatUnit = readRecurrenceUnit()
                "countsToward" -> countsToward = readBudgetMonthAssignment()
                "requireManualConfirmation" -> requireManualConfirmation = readBooleanValue()
                "endsOn" -> endsOn = readNullable { parseDate(readStringValue()) }
                "remindOn" -> remindOn = readNullable { parseDate(readStringValue()) }
                else -> invalid("Unknown schedule field: $field")
            }
        }
    fields.requireExactly(
        setOf(
            "firstOccurrence",
            "timing",
            "repeatEvery",
            "repeatUnit",
            "countsToward",
            "requireManualConfirmation",
            "endsOn",
            "remindOn",
        ),
    )
    return RecurringSchedule(
        firstOccurrence = requireNotNull(firstOccurrence),
        timing = requireNotNull(timing),
        interval = RecurrenceInterval(requireNotNull(repeatEvery), requireNotNull(repeatUnit)),
        countsToward = requireNotNull(countsToward),
        requireManualConfirmation = requireNotNull(requireManualConfirmation),
        endsOn = endsOn,
        remindOn = remindOn,
    )
}

private fun JsonReader.readOccurrenceTiming(): OccurrenceTiming {
    var kind: String? = null
    var endOffsetDays: Int? = null
    val fields =
        readObject { field ->
            when (field) {
                "kind" -> kind = readStringValue()
                "endOffsetDays" -> endOffsetDays = readIntValue()
                else -> invalid("Unknown timing field: $field")
            }
        }
    return when (kind) {
        "specific_date" -> {
            fields.requireExactly(setOf("kind"))
            OccurrenceTiming.SpecificDate
        }

        "date_range" -> {
            fields.requireExactly(setOf("kind", "endOffsetDays"))
            OccurrenceTiming.DateRange(requireNotNull(endOffsetDays))
        }

        "any_time_in_month" -> {
            fields.requireExactly(setOf("kind"))
            OccurrenceTiming.AnyTimeInMonth
        }

        else -> invalid("Unknown recurring timing")
    }
}

private fun JsonReader.readCurrentReminders(): ReminderSettings {
    var occurrence: ReminderLead? = null
    var remind: ReminderLead? = null
    var end: ReminderLead? = null
    val fields =
        readObject { field ->
            when (field) {
                "occurrenceDaysBefore" -> occurrence = readNullable { ReminderLead(readIntValue()) }
                "remindDaysBefore" -> remind = readNullable { ReminderLead(readIntValue()) }
                "endDaysBefore" -> end = readNullable { ReminderLead(readIntValue()) }
                else -> invalid("Unknown reminders field: $field")
            }
        }
    fields.requireExactly(setOf("occurrenceDaysBefore", "remindDaysBefore", "endDaysBefore"))
    return ReminderSettings(occurrence, remind, end)
}

private sealed interface LegacyTiming {
    data class ExactDay(
        val day: Int,
    ) : LegacyTiming {
        init {
            require(day in 1..31) { "Legacy exact day is out of range" }
        }
    }

    data class DayRange(
        val firstDay: Int,
        val lastDay: Int,
    ) : LegacyTiming {
        init {
            require(firstDay in 1..31 && lastDay in firstDay..31) { "Legacy date range is invalid" }
        }
    }

    data object AnyTimeInMonth : LegacyTiming
}

private data class LegacyLifecycle(
    val startsOn: LocalDate,
    val endsAt: LocalDate?,
    val reviewOn: LocalDate?,
)

private fun JsonReader.readLegacyRecurringItem(): RecurringItem {
    var id: RecurringItemId? = null
    var name: String? = null
    var direction: Direction? = null
    var amount: Money? = null
    var timing: LegacyTiming? = null
    var lifecycle: LegacyLifecycle? = null
    var reminders: ReminderSettings? = null
    var tags: Set<Tag>? = null
    val fields =
        readObject { field ->
            when (field) {
                "id" -> id = RecurringItemId(readStringValue())
                "name" -> name = readStringValue()
                "direction" -> direction = readDirection()
                "amount" -> amount = readMoney()
                "timing" -> timing = readLegacyTiming()
                "lifecycle" -> lifecycle = readLegacyLifecycle()
                "reminders" -> reminders = readLegacyReminders()
                "tags" -> tags = readTags()
                else -> invalid("Unknown recurring item field: $field")
            }
        }
    fields.requireExactly(setOf("id", "name", "direction", "amount", "timing", "lifecycle", "reminders", "tags"))
    val legacyLifecycle = requireNotNull(lifecycle)
    val normalizedTiming = requireNotNull(timing).normalize(legacyLifecycle.startsOn)
    return RecurringItem(
        id = requireNotNull(id),
        name = requireNotNull(name),
        direction = requireNotNull(direction),
        amount = requireNotNull(amount),
        schedule =
            RecurringSchedule(
                firstOccurrence = normalizedTiming.firstOccurrence,
                timing = normalizedTiming.timing,
                endsOn = legacyLifecycle.endsAt,
                remindOn = legacyLifecycle.reviewOn,
            ),
        reminders = requireNotNull(reminders),
        tags = requireNotNull(tags),
    )
}

private fun JsonReader.readLegacyTiming(): LegacyTiming {
    var kind: String? = null
    var day: Int? = null
    var firstDay: Int? = null
    var lastDay: Int? = null
    val fields =
        readObject { field ->
            when (field) {
                "kind" -> kind = readStringValue()
                "day" -> day = readIntValue()
                "firstDay" -> firstDay = readIntValue()
                "lastDay" -> lastDay = readIntValue()
                else -> invalid("Unknown timing field: $field")
            }
        }
    return when (kind) {
        "exact_day" -> {
            fields.requireExactly(setOf("kind", "day"))
            LegacyTiming.ExactDay(requireNotNull(day))
        }

        "day_range" -> {
            fields.requireExactly(setOf("kind", "firstDay", "lastDay"))
            LegacyTiming.DayRange(requireNotNull(firstDay), requireNotNull(lastDay))
        }

        "any_time_in_month" -> {
            fields.requireExactly(setOf("kind"))
            LegacyTiming.AnyTimeInMonth
        }

        else -> invalid("Unknown monthly timing")
    }
}

private data class NormalizedLegacyTiming(
    val firstOccurrence: LocalDate,
    val timing: OccurrenceTiming,
)

private fun LegacyTiming.normalize(startsOn: LocalDate): NormalizedLegacyTiming =
    when (this) {
        is LegacyTiming.ExactDay -> {
            val currentMonthDate = startsOn.monthDate(day)
            val firstDate =
                if (currentMonthDate.isBefore(startsOn)) {
                    startsOn.plusMonths(1).monthDate(day)
                } else {
                    currentMonthDate
                }
            NormalizedLegacyTiming(firstDate, OccurrenceTiming.SpecificDate)
        }

        is LegacyTiming.DayRange -> {
            var month = YearMonth.from(startsOn)
            var rangeStart = month.atClampedDay(firstDay)
            var rangeEnd = month.atClampedDay(lastDay)
            if (rangeEnd.isBefore(startsOn)) {
                month = month.plusMonths(1)
                rangeStart = month.atClampedDay(firstDay)
                rangeEnd = month.atClampedDay(lastDay)
            }
            val firstDate = maxOf(startsOn, rangeStart)
            NormalizedLegacyTiming(
                firstOccurrence = firstDate,
                timing = OccurrenceTiming.DateRange(ChronoUnit.DAYS.between(firstDate, rangeEnd).toInt()),
            )
        }

        LegacyTiming.AnyTimeInMonth -> NormalizedLegacyTiming(startsOn, OccurrenceTiming.AnyTimeInMonth)
    }

private fun LocalDate.monthDate(day: Int): LocalDate = YearMonth.from(this).atClampedDay(day)

private fun YearMonth.atClampedDay(day: Int): LocalDate = atDay(day.coerceIn(1, lengthOfMonth()))

private fun JsonReader.readLegacyLifecycle(): LegacyLifecycle {
    var startsOn: LocalDate? = null
    var endsAt: LocalDate? = null
    var reviewOn: LocalDate? = null
    val fields =
        readObject { field ->
            when (field) {
                "startsOn" -> startsOn = parseDate(readStringValue())
                "endsAt" -> endsAt = readNullable { parseDate(readStringValue()) }
                "reviewOn" -> reviewOn = readNullable { parseDate(readStringValue()) }
                else -> invalid("Unknown lifecycle field: $field")
            }
        }
    fields.requireExactly(setOf("startsOn", "endsAt", "reviewOn"))
    return LegacyLifecycle(requireNotNull(startsOn), endsAt, reviewOn)
}

private fun JsonReader.readLegacyReminders(): ReminderSettings {
    var occurrence: ReminderLead? = null
    var review: ReminderLead? = null
    var end: ReminderLead? = null
    val fields =
        readObject { field ->
            when (field) {
                "occurrenceDaysBefore" -> occurrence = readNullable { ReminderLead(readIntValue()) }
                "reviewDaysBefore" -> review = readNullable { ReminderLead(readIntValue()) }
                "endDaysBefore" -> end = readNullable { ReminderLead(readIntValue()) }
                else -> invalid("Unknown reminders field: $field")
            }
        }
    fields.requireExactly(setOf("occurrenceDaysBefore", "reviewDaysBefore", "endDaysBefore"))
    return ReminderSettings(occurrence, review, end)
}

private fun JsonReader.readTags(): Set<Tag> {
    val tags =
        readArray(VectorintBackupContract.MAX_TAGS_PER_RECORD, "tags") {
            Tag(readStringValue())
        }
    if (tags.distinct().size != tags.size) invalid("Tags must be unique within a record")
    return tags.toSet()
}

private fun JsonReader.readCustomCategories(): List<CustomCategory> =
    readArray(VectorintBackupContract.MAX_CUSTOM_CATEGORIES, "custom categories") {
        var id: CategoryId? = null
        var name: String? = null
        var icon: CategoryIcon? = null
        val fields =
            readObject { field ->
                when (field) {
                    "id" -> id = CategoryId(readStringValue())
                    "name" -> name = readStringValue()
                    "icon" -> icon = readCategoryIcon()
                    else -> invalid("Unknown custom category field: $field")
                }
            }
        fields.requireExactly(setOf("id", "name", "icon"))
        CustomCategory(requireNotNull(id), requireNotNull(name), requireNotNull(icon))
    }

private fun JsonReader.readCategoryIcon(): CategoryIcon {
    val stored = readStringValue()
    return CategoryIcon.entries.singleOrNull { it.name.equals(stored, ignoreCase = true) }
        ?: invalid("Unknown category icon: $stored")
}

private fun JsonReader.readDirection(): Direction =
    when (readStringValue()) {
        "income" -> Direction.INCOME
        "expense" -> Direction.EXPENSE
        else -> invalid("Unknown direction")
    }

private fun JsonReader.readActivityState(): ActivityState =
    when (readStringValue()) {
        "planned" -> ActivityState.PLANNED
        "confirmed" -> ActivityState.CONFIRMED
        else -> invalid("Unknown activity state")
    }

private fun JsonReader.readRecurrenceUnit(): RecurrenceUnit =
    when (readStringValue()) {
        "days" -> RecurrenceUnit.DAYS
        "weeks" -> RecurrenceUnit.WEEKS
        "months" -> RecurrenceUnit.MONTHS
        "years" -> RecurrenceUnit.YEARS
        else -> invalid("Unknown recurrence unit")
    }

private fun RecurrenceUnit.storedValue(): String =
    when (this) {
        RecurrenceUnit.DAYS -> "days"
        RecurrenceUnit.WEEKS -> "weeks"
        RecurrenceUnit.MONTHS -> "months"
        RecurrenceUnit.YEARS -> "years"
    }

private fun JsonReader.readBudgetMonthAssignment(): BudgetMonthAssignment =
    when (readStringValue()) {
        "occurrence_month" -> BudgetMonthAssignment.OCCURRENCE_MONTH
        "following_month" -> BudgetMonthAssignment.FOLLOWING_MONTH
        else -> invalid("Unknown budget-month assignment")
    }

private fun BudgetMonthAssignment.storedValue(): String =
    when (this) {
        BudgetMonthAssignment.OCCURRENCE_MONTH -> "occurrence_month"
        BudgetMonthAssignment.FOLLOWING_MONTH -> "following_month"
    }

private fun JsonReader.readCurrencyCode(): CurrencyCode {
    val raw = readStringValue()
    val currency = CurrencyCode.of(raw)
    if (currency.value != raw) invalid("Currency code must use three uppercase ASCII letters")
    return currency
}

private fun Direction.storedValue(): String =
    when (this) {
        Direction.INCOME -> "income"
        Direction.EXPENSE -> "expense"
    }

private fun ActivityState.storedValue(): String =
    when (this) {
        ActivityState.PLANNED -> "planned"
        ActivityState.CONFIRMED -> "confirmed"
    }

private fun JsonReader.readStringValue(): String {
    if (peek() != JsonToken.STRING) invalid("Expected a string")
    return nextString()
}

private fun JsonReader.readBooleanValue(): Boolean {
    if (peek() != JsonToken.BOOLEAN) invalid("Expected a boolean")
    return nextBoolean()
}

private fun JsonReader.readLongValue(): Long {
    if (peek() != JsonToken.NUMBER) invalid("Expected an integer")
    val raw = nextString()
    if (!raw.matches(Regex("-?(0|[1-9][0-9]*)"))) invalid("Expected an integer")
    return raw.toLongOrNull() ?: invalid("Integer is out of range")
}

private fun JsonReader.readIntValue(): Int {
    val value = readLongValue()
    if (value !in Int.MIN_VALUE..Int.MAX_VALUE) invalid("Integer is out of range")
    return value.toInt()
}

private inline fun <T> JsonReader.readNullable(readValue: JsonReader.() -> T): T? =
    if (peek() == JsonToken.NULL) {
        nextNull()
        null
    } else {
        readValue()
    }

private inline fun <T> JsonReader.readArray(
    maximumSize: Int,
    label: String,
    readValue: JsonReader.() -> T,
): List<T> {
    if (peek() != JsonToken.BEGIN_ARRAY) invalid("Expected $label array")
    beginArray()
    val values = mutableListOf<T>()
    while (hasNext()) {
        if (values.size >= maximumSize) invalid("Too many $label")
        values += readValue()
    }
    endArray()
    return values
}

private inline fun JsonReader.readObject(readField: JsonReader.(String) -> Unit): Set<String> {
    if (peek() != JsonToken.BEGIN_OBJECT) invalid("Expected an object")
    beginObject()
    val fields = mutableSetOf<String>()
    while (hasNext()) {
        val field = nextName()
        if (!fields.add(field)) invalid("Duplicate field: $field")
        readField(field)
    }
    endObject()
    return fields
}

private fun Set<String>.requireExactly(expected: Set<String>) {
    if (this != expected) invalid("Required fields are missing")
}

private fun parseDate(value: String): LocalDate =
    try {
        LocalDate.parse(value)
    } catch (_: DateTimeException) {
        invalid("Invalid date")
    }

private fun parseYearMonth(value: String): YearMonth =
    try {
        YearMonth.parse(value)
    } catch (_: DateTimeException) {
        invalid("Invalid budget month")
    }

private fun invalid(message: String): Nothing = throw InvalidVectorintBackup(message)
