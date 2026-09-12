package io.github.kamui2040.vectorint.data

import io.github.kamui2040.vectorint.core.Account
import io.github.kamui2040.vectorint.core.AccountId
import io.github.kamui2040.vectorint.core.ActivityEntry
import io.github.kamui2040.vectorint.core.ActivityId
import io.github.kamui2040.vectorint.core.CategoryId
import io.github.kamui2040.vectorint.core.CurrentFunds
import io.github.kamui2040.vectorint.core.CustomCategory
import io.github.kamui2040.vectorint.core.Direction
import io.github.kamui2040.vectorint.core.Money
import io.github.kamui2040.vectorint.core.RecurringItem
import io.github.kamui2040.vectorint.core.RecurringItemId
import io.github.kamui2040.vectorint.core.Tag
import io.github.kamui2040.vectorint.core.asLegacyDefaultAccount
import java.time.Instant

internal data class BudgetSnapshot(
    val accounts: List<Account>,
    val activities: List<ActivityEntry>,
) {
    constructor(currentFunds: CurrentFunds, activities: List<ActivityEntry>) :
        this(accounts = listOf(currentFunds.asLegacyDefaultAccount()), activities = activities)
}

internal interface BudgetRepository {
    suspend fun loadBudgetSnapshot(): BudgetSnapshot?

    suspend fun loadAccounts(): List<Account> = loadBudgetSnapshot()?.accounts.orEmpty()

    suspend fun createAccount(account: Account): Unit = error("Account creation is not implemented")

    suspend fun updateAccount(account: Account): Boolean = error("Account updates are not implemented")

    suspend fun deleteAccount(accountId: AccountId): Boolean = error("Account deletion is not implemented")

    suspend fun loadActivities(): List<ActivityEntry>

    suspend fun loadActivity(activityId: ActivityId): ActivityEntry?

    suspend fun createActivity(activity: ActivityEntry)

    suspend fun saveActivity(activity: ActivityEntry)

    suspend fun updateActivity(activity: ActivityEntry): Boolean

    suspend fun updateActivityDetails(
        activityId: ActivityId,
        name: String,
        direction: Direction,
        amount: Money,
        tags: Set<Tag>,
        categoryId: CategoryId? = null,
    ): ActivityEntry?

    suspend fun confirmActivity(
        activityId: ActivityId,
        bookedAt: Instant,
    ): ActivityEntry?

    suspend fun updateAndConfirmActivity(
        activityId: ActivityId,
        name: String,
        direction: Direction,
        amount: Money,
        tags: Set<Tag>,
        bookedAt: Instant,
        categoryId: CategoryId? = null,
    ): ActivityEntry?

    suspend fun deleteActivity(activityId: ActivityId)

    suspend fun loadRecurringItems(): List<RecurringItem>

    suspend fun saveRecurringItem(item: RecurringItem)

    suspend fun createRecurringItem(item: RecurringItem) = saveRecurringItem(item)

    suspend fun updateRecurringItem(item: RecurringItem): Boolean {
        saveRecurringItem(item)
        return true
    }

    suspend fun deleteRecurringItem(recurringItemId: RecurringItemId)

    suspend fun loadCustomCategories(): List<CustomCategory>

    suspend fun createCustomCategory(category: CustomCategory)

    suspend fun deleteCustomCategory(categoryId: CategoryId): Boolean
}
