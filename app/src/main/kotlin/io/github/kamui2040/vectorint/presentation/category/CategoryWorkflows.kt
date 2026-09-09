package io.github.kamui2040.vectorint.presentation.category

import io.github.kamui2040.vectorint.core.CategoryIcon
import io.github.kamui2040.vectorint.core.CategoryId
import io.github.kamui2040.vectorint.core.CustomCategory
import io.github.kamui2040.vectorint.data.BudgetRepository
import kotlinx.coroutines.CancellationException
import java.util.Locale
import java.util.UUID

internal sealed interface CategoryLibraryLoadResult {
    data class Ready(
        val customCategories: List<CustomCategory>,
    ) : CategoryLibraryLoadResult

    data object Failed : CategoryLibraryLoadResult
}

internal sealed interface CategoryMutationResult {
    data class Created(
        val category: CustomCategory,
    ) : CategoryMutationResult

    data object Deleted : CategoryMutationResult

    data object InvalidName : CategoryMutationResult

    data object DuplicateName : CategoryMutationResult

    data object Missing : CategoryMutationResult

    data object StorageFailed : CategoryMutationResult
}

internal fun interface CustomCategoryIdFactory {
    fun create(): CategoryId
}

internal class CategoryManager(
    private val budgetRepository: BudgetRepository,
    private val idFactory: CustomCategoryIdFactory =
        CustomCategoryIdFactory {
            CategoryId(CustomCategory.CUSTOM_ID_PREFIX + UUID.randomUUID().toString().replace("-", ""))
        },
) {
    suspend fun load(): CategoryLibraryLoadResult =
        try {
            CategoryLibraryLoadResult.Ready(
                budgetRepository.loadCustomCategories().sortedWith(customCategoryOrder),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            CategoryLibraryLoadResult.Failed
        }

    suspend fun create(
        nameInput: String,
        icon: CategoryIcon,
    ): CategoryMutationResult {
        val name = nameInput.trim()
        if (name.isEmpty() || name.length > CustomCategory.MAX_NAME_LENGTH) {
            return CategoryMutationResult.InvalidName
        }
        return try {
            if (budgetRepository.loadCustomCategories().any { it.name.equals(name, ignoreCase = true) }) {
                CategoryMutationResult.DuplicateName
            } else {
                val category = CustomCategory(idFactory.create(), name, icon)
                budgetRepository.createCustomCategory(category)
                CategoryMutationResult.Created(category)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            CategoryMutationResult.StorageFailed
        }
    }

    suspend fun delete(categoryId: CategoryId): CategoryMutationResult =
        try {
            if (budgetRepository.deleteCustomCategory(categoryId)) {
                CategoryMutationResult.Deleted
            } else {
                CategoryMutationResult.Missing
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            CategoryMutationResult.StorageFailed
        }

    private companion object {
        val customCategoryOrder =
            compareBy<CustomCategory>(
                { it.name.lowercase(Locale.ROOT) },
                CustomCategory::name,
                { it.id.value },
            )
    }
}
