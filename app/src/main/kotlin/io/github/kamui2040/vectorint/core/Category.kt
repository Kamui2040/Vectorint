package io.github.kamui2040.vectorint.core

@JvmInline
value class CategoryId(
    val value: String,
) {
    init {
        require(value.matches(VALID_ID)) { "Category ID is invalid" }
    }

    companion object {
        private val VALID_ID = Regex("[a-z0-9][a-z0-9_-]{0,63}")
    }
}

enum class CategoryIcon {
    OTHER,
    HOME,
    GROCERIES,
    DINING,
    TRANSPORT,
    HEALTH,
    INSURANCE,
    UTILITIES,
    SUBSCRIPTIONS,
    SHOPPING,
    LEISURE,
    EDUCATION,
    TRAVEL,
    SAVINGS,
    SALARY,
    WORK,
    GIFTS,
    PETS,
    CHILDCARE,
}

enum class PredefinedCategory(
    val id: CategoryId,
    val icon: CategoryIcon,
) {
    HOUSING(CategoryId("housing"), CategoryIcon.HOME),
    GROCERIES(CategoryId("groceries"), CategoryIcon.GROCERIES),
    DINING(CategoryId("dining"), CategoryIcon.DINING),
    TRANSPORT(CategoryId("transport"), CategoryIcon.TRANSPORT),
    HEALTH(CategoryId("health"), CategoryIcon.HEALTH),
    INSURANCE(CategoryId("insurance"), CategoryIcon.INSURANCE),
    UTILITIES(CategoryId("utilities"), CategoryIcon.UTILITIES),
    SUBSCRIPTIONS(CategoryId("subscriptions"), CategoryIcon.SUBSCRIPTIONS),
    SHOPPING(CategoryId("shopping"), CategoryIcon.SHOPPING),
    LEISURE(CategoryId("leisure"), CategoryIcon.LEISURE),
    EDUCATION(CategoryId("education"), CategoryIcon.EDUCATION),
    TRAVEL(CategoryId("travel"), CategoryIcon.TRAVEL),
    SAVINGS(CategoryId("savings"), CategoryIcon.SAVINGS),
    SALARY(CategoryId("salary"), CategoryIcon.SALARY),
    ;

    companion object {
        private val byId = entries.associateBy(PredefinedCategory::id)

        fun fromId(id: CategoryId): PredefinedCategory? = byId[id]
    }
}

data class CustomCategory(
    val id: CategoryId,
    val name: String,
    val icon: CategoryIcon,
) {
    init {
        require(id.value.startsWith(CUSTOM_ID_PREFIX)) { "Custom category ID must use the custom prefix" }
        require(name.isNotBlank()) { "Custom category name must not be blank" }
        require(name == name.trim()) { "Custom category name must not have surrounding whitespace" }
        require(name.length <= MAX_NAME_LENGTH) { "Custom category name is too long" }
    }

    companion object {
        const val CUSTOM_ID_PREFIX = "custom_"
        const val MAX_NAME_LENGTH = 80
    }
}
