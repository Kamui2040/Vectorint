package io.github.kamui2040.vectorint.presentation.category

import androidx.room.Room
import io.github.kamui2040.vectorint.core.CategoryIcon
import io.github.kamui2040.vectorint.core.CategoryId
import io.github.kamui2040.vectorint.data.local.RoomBudgetRepository
import io.github.kamui2040.vectorint.data.local.VectorintDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CategoryWorkflowsTest {
    private lateinit var database: VectorintDatabase
    private lateinit var repository: RoomBudgetRepository

    @Before
    fun createRepository() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    RuntimeEnvironment.getApplication(),
                    VectorintDatabase::class.java,
                ).build()
        repository = RoomBudgetRepository(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `custom category creation trims names rejects duplicates and sorts the library`() =
        runBlocking {
            val ids = ArrayDeque(listOf(CategoryId("custom_zeta"), CategoryId("custom_alpha")))
            val manager = CategoryManager(repository, CustomCategoryIdFactory { ids.removeFirst() })

            assertTrue(manager.create("  zeta  ", CategoryIcon.WORK) is CategoryMutationResult.Created)
            assertTrue(manager.create("Alpha", CategoryIcon.GIFTS) is CategoryMutationResult.Created)
            assertEquals(
                CategoryMutationResult.DuplicateName,
                manager.create("ZETA", CategoryIcon.HOME),
            )

            val ready = manager.load() as CategoryLibraryLoadResult.Ready
            assertEquals(listOf("Alpha", "zeta"), ready.customCategories.map { it.name })
            assertEquals(CategoryIcon.GIFTS, ready.customCategories.first().icon)
        }

    @Test
    fun `only existing custom categories can be deleted`() =
        runBlocking {
            val categoryId = CategoryId("custom_gifts")
            val manager = CategoryManager(repository, CustomCategoryIdFactory { categoryId })
            assertTrue(manager.create("Gifts", CategoryIcon.GIFTS) is CategoryMutationResult.Created)

            assertEquals(CategoryMutationResult.Deleted, manager.delete(categoryId))
            assertEquals(CategoryMutationResult.Missing, manager.delete(categoryId))
            assertEquals(CategoryMutationResult.Missing, manager.delete(CategoryId("housing")))
        }
}
