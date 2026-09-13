package io.github.kamui2040.vectorint.core

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

@JvmInline
value class ActivityId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Activity ID must not be blank" }
    }
}

@JvmInline
value class RecurringItemId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Recurring item ID must not be blank" }
    }
}

@JvmInline
value class Tag(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Tag must not be blank" }
    }
}

@JvmInline
value class BudgetMonth(
    val value: YearMonth,
) {
    fun contains(date: LocalDate): Boolean = YearMonth.from(date) == value
}

enum class ActivityState {
    PLANNED,
    CONFIRMED,
}

sealed interface ActivitySource {
    data object OneOff : ActivitySource

    data class Recurring(
        val itemId: RecurringItemId,
        val occurrenceKey: String,
    ) : ActivitySource {
        init {
            require(occurrenceKey.isNotBlank()) { "Occurrence key must not be blank" }
        }
    }
}

data class ActivityEntry(
    val id: ActivityId,
    val name: String = "",
    val accountId: AccountId = LEGACY_DEFAULT_ACCOUNT_ID,
    val direction: Direction,
    val amount: Money,
    val state: ActivityState,
    val budgetMonth: BudgetMonth,
    val expectedOn: LocalDate? = null,
    val bookedAt: Instant? = null,
    val source: ActivitySource = ActivitySource.OneOff,
    val categoryId: CategoryId? = null,
    val tags: Set<Tag> = emptySet(),
) {
    init {
        require(amount.minorUnits >= 0) { "Activity amounts must be non-negative" }
        require((state == ActivityState.CONFIRMED) == (bookedAt != null)) {
            "Only confirmed activity has a booking instant"
        }
    }

    fun confirm(at: Instant): ActivityEntry =
        if (state == ActivityState.CONFIRMED) {
            this
        } else {
            copy(state = ActivityState.CONFIRMED, bookedAt = at)
        }
}
