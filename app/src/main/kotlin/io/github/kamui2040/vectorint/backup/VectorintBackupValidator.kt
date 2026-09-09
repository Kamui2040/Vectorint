package io.github.kamui2040.vectorint.backup

import io.github.kamui2040.vectorint.core.ActivitySource
import io.github.kamui2040.vectorint.core.PredefinedCategory
import java.util.Locale

internal object VectorintBackupValidator {
    fun validate(backup: VectorintBackup) {
        val data = backup.data
        requireBackup(data.activities.size <= VectorintBackupContract.MAX_ACTIVITIES, "Too many activities")
        requireBackup(
            data.recurringItems.size <= VectorintBackupContract.MAX_RECURRING_ITEMS,
            "Too many recurring items",
        )
        requireBackup(
            data.customCategories.size <= VectorintBackupContract.MAX_CUSTOM_CATEGORIES,
            "Too many custom categories",
        )
        requireBackup(
            data.currentFunds != null || (data.activities.isEmpty() && data.recurringItems.isEmpty()),
            "Budget records require Current funds",
        )

        val activityIds = data.activities.map { it.id.value }
        requireBackup(activityIds.distinct().size == activityIds.size, "Activity IDs must be unique")
        val recurringIds = data.recurringItems.map { it.id.value }
        requireBackup(recurringIds.distinct().size == recurringIds.size, "Recurring item IDs must be unique")
        val customCategoryIds = data.customCategories.map { it.id }
        requireBackup(
            customCategoryIds.distinct().size == customCategoryIds.size,
            "Custom category IDs must be unique",
        )
        val customCategoryNames = data.customCategories.map { it.name.lowercase(Locale.ROOT) }
        requireBackup(
            customCategoryNames.distinct().size == customCategoryNames.size,
            "Custom category names must be unique",
        )
        data.customCategories.forEach { category ->
            requireText(category.id.value, "Custom category ID")
            requireText(category.name, "Custom category name")
            requireBackup(
                PredefinedCategory.fromId(category.id) == null,
                "A custom category cannot replace a predefined category",
            )
        }
        val occurrenceKeys =
            data.activities.mapNotNull { activity ->
                (activity.source as? ActivitySource.Recurring)?.let { source ->
                    source.itemId.value to source.occurrenceKey
                }
            }
        requireBackup(
            occurrenceKeys.distinct().size == occurrenceKeys.size,
            "Recurring occurrence identities must be unique",
        )

        val currency = data.currentFunds?.amount?.currency
        data.activities.forEach { activity ->
            requireText(activity.id.value, "Activity ID")
            requireBackup(activity.amount.currency == currency, "Activity currency does not match Current funds")
            requireKnownCategory(activity.categoryId, customCategoryIds)
            requireTags(activity.tags.map { it.value })
            (activity.source as? ActivitySource.Recurring)?.let { source ->
                requireText(source.itemId.value, "Recurring item ID")
                requireText(source.occurrenceKey, "Occurrence key")
            }
        }
        data.recurringItems.forEach { item ->
            requireText(item.id.value, "Recurring item ID")
            requireText(item.name, "Recurring item name")
            requireBackup(item.amount.currency == currency, "Recurring currency does not match Current funds")
            requireKnownCategory(item.categoryId, customCategoryIds)
            requireBackup(
                item.reminders.remind == null || item.schedule.remindOn != null,
                "A reminder-date notification requires Remind me on",
            )
            requireBackup(
                item.reminders.end == null || item.schedule.endsOn != null,
                "An end reminder requires Ends on",
            )
            requireTags(item.tags.map { it.value })
        }
    }

    private fun requireKnownCategory(
        categoryId: io.github.kamui2040.vectorint.core.CategoryId?,
        customCategoryIds: List<io.github.kamui2040.vectorint.core.CategoryId>,
    ) {
        if (categoryId == null) return
        requireBackup(
            PredefinedCategory.fromId(categoryId) != null || categoryId in customCategoryIds,
            "Category assignment is unknown",
        )
    }

    private fun requireTags(tags: List<String>) {
        requireBackup(tags.size <= VectorintBackupContract.MAX_TAGS_PER_RECORD, "Too many tags on one record")
        tags.forEach { requireText(it, "Tag") }
    }

    private fun requireText(
        value: String,
        label: String,
    ) {
        requireBackup(value.isNotBlank(), "$label must not be blank")
        requireBackup(value.length <= VectorintBackupContract.MAX_TEXT_CHARS, "$label is too long")
    }

    private fun requireBackup(
        condition: Boolean,
        message: String,
    ) {
        if (!condition) throw InvalidVectorintBackup(message)
    }
}
