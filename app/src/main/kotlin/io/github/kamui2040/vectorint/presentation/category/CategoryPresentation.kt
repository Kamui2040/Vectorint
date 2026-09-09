package io.github.kamui2040.vectorint.presentation.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kamui2040.vectorint.R
import io.github.kamui2040.vectorint.core.CategoryIcon
import io.github.kamui2040.vectorint.core.CategoryId
import io.github.kamui2040.vectorint.core.CustomCategory
import io.github.kamui2040.vectorint.core.PredefinedCategory

internal data class CategoryInfo(
    val id: CategoryId?,
    val name: String,
    val icon: CategoryIcon,
    val custom: CustomCategory? = null,
)

@Composable
internal fun categoryInfo(
    id: CategoryId?,
    customCategories: List<CustomCategory>,
): CategoryInfo {
    if (id == null) {
        return CategoryInfo(null, stringResource(R.string.category_other), CategoryIcon.OTHER)
    }
    PredefinedCategory.fromId(id)?.let { category ->
        return CategoryInfo(category.id, stringResource(category.labelResource()), category.icon)
    }
    val custom = customCategories.singleOrNull { it.id == id }
    return custom?.let { CategoryInfo(it.id, it.name, it.icon, it) }
        ?: CategoryInfo(null, stringResource(R.string.category_other), CategoryIcon.OTHER)
}

@Composable
internal fun CategorySummary(
    categoryId: CategoryId?,
    customCategories: List<CustomCategory>,
    color: Color = LocalContentColor.current,
) {
    val info = categoryInfo(categoryId, customCategories)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = info.icon.imageVector(),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = color,
        )
        Text(
            text = info.name,
            color = color,
        )
    }
}

internal fun PredefinedCategory.labelResource(): Int =
    when (this) {
        PredefinedCategory.HOUSING -> R.string.category_housing
        PredefinedCategory.GROCERIES -> R.string.category_groceries
        PredefinedCategory.DINING -> R.string.category_dining
        PredefinedCategory.TRANSPORT -> R.string.category_transport
        PredefinedCategory.HEALTH -> R.string.category_health
        PredefinedCategory.INSURANCE -> R.string.category_insurance
        PredefinedCategory.UTILITIES -> R.string.category_utilities
        PredefinedCategory.SUBSCRIPTIONS -> R.string.category_subscriptions
        PredefinedCategory.SHOPPING -> R.string.category_shopping
        PredefinedCategory.LEISURE -> R.string.category_leisure
        PredefinedCategory.EDUCATION -> R.string.category_education
        PredefinedCategory.TRAVEL -> R.string.category_travel
        PredefinedCategory.SAVINGS -> R.string.category_savings
        PredefinedCategory.SALARY -> R.string.category_salary
    }

internal fun CategoryIcon.labelResource(): Int =
    when (this) {
        CategoryIcon.OTHER -> R.string.category_other
        CategoryIcon.HOME -> R.string.category_icon_home
        CategoryIcon.GROCERIES -> R.string.category_icon_groceries
        CategoryIcon.DINING -> R.string.category_icon_dining
        CategoryIcon.TRANSPORT -> R.string.category_icon_transport
        CategoryIcon.HEALTH -> R.string.category_icon_health
        CategoryIcon.INSURANCE -> R.string.category_icon_insurance
        CategoryIcon.UTILITIES -> R.string.category_icon_utilities
        CategoryIcon.SUBSCRIPTIONS -> R.string.category_icon_subscriptions
        CategoryIcon.SHOPPING -> R.string.category_icon_shopping
        CategoryIcon.LEISURE -> R.string.category_icon_leisure
        CategoryIcon.EDUCATION -> R.string.category_icon_education
        CategoryIcon.TRAVEL -> R.string.category_icon_travel
        CategoryIcon.SAVINGS -> R.string.category_icon_savings
        CategoryIcon.SALARY -> R.string.category_icon_salary
        CategoryIcon.WORK -> R.string.category_icon_work
        CategoryIcon.GIFTS -> R.string.category_icon_gifts
        CategoryIcon.PETS -> R.string.category_icon_pets
        CategoryIcon.CHILDCARE -> R.string.category_icon_childcare
    }

internal fun CategoryIcon.imageVector(): ImageVector =
    when (this) {
        CategoryIcon.OTHER -> Icons.Rounded.Category
        CategoryIcon.HOME -> Icons.Rounded.Home
        CategoryIcon.GROCERIES -> Icons.Rounded.ShoppingCart
        CategoryIcon.DINING -> Icons.Rounded.Restaurant
        CategoryIcon.TRANSPORT -> Icons.Rounded.DirectionsCar
        CategoryIcon.HEALTH -> Icons.Rounded.MedicalServices
        CategoryIcon.INSURANCE -> Icons.Rounded.Shield
        CategoryIcon.UTILITIES -> Icons.Rounded.Bolt
        CategoryIcon.SUBSCRIPTIONS -> Icons.Rounded.Subscriptions
        CategoryIcon.SHOPPING -> Icons.Rounded.ShoppingBag
        CategoryIcon.LEISURE -> Icons.Rounded.SportsEsports
        CategoryIcon.EDUCATION -> Icons.Rounded.School
        CategoryIcon.TRAVEL -> Icons.Rounded.Flight
        CategoryIcon.SAVINGS -> Icons.Rounded.Savings
        CategoryIcon.SALARY -> Icons.Rounded.Payments
        CategoryIcon.WORK -> Icons.Rounded.Work
        CategoryIcon.GIFTS -> Icons.Rounded.Redeem
        CategoryIcon.PETS -> Icons.Rounded.Pets
        CategoryIcon.CHILDCARE -> Icons.Rounded.ChildCare
    }
