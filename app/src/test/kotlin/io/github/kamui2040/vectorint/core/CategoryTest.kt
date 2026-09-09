package io.github.kamui2040.vectorint.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CategoryTest {
    @Test
    fun `predefined categories expose unique stable IDs and app icons`() {
        assertEquals(
            PredefinedCategory.entries.size,
            PredefinedCategory.entries
                .map { it.id }
                .distinct()
                .size,
        )
        PredefinedCategory.entries.forEach { category ->
            assertEquals(category, PredefinedCategory.fromId(category.id))
        }
    }

    @Test
    fun `category IDs reject unstable or oversized values`() {
        assertThrows(IllegalArgumentException::class.java) { CategoryId("") }
        assertThrows(IllegalArgumentException::class.java) { CategoryId("Uppercase") }
        assertThrows(IllegalArgumentException::class.java) { CategoryId("contains spaces") }
        assertThrows(IllegalArgumentException::class.java) { CategoryId("a".repeat(65)) }
    }

    @Test
    fun `custom categories require their namespace and a clean visible name`() {
        assertThrows(IllegalArgumentException::class.java) {
            CustomCategory(CategoryId("not_custom"), "Travel", CategoryIcon.TRAVEL)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustomCategory(CategoryId("custom_blank"), " ", CategoryIcon.OTHER)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustomCategory(CategoryId("custom_space"), " Travel ", CategoryIcon.TRAVEL)
        }
    }
}
