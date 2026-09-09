package io.github.kamui2040.vectorint.presentation.tag

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.kamui2040.vectorint.core.Tag
import io.github.kamui2040.vectorint.presentation.theme.VectorintTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TagControlsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `tag input trims outer whitespace and rejects blank input`() {
        assertEquals(Tag("shared home"), tagFromInput("  shared home  "))
        assertNull(tagFromInput("   "))
        assertEquals(
            listOf(Tag("Annual"), Tag("shared"), Tag("work")),
            sortedTags(setOf(Tag("work"), Tag("Annual"), Tag("shared"))),
        )
    }

    @Test
    fun `tag editor adds and removes exact labels`() {
        var tags by mutableStateOf(setOf(Tag("work")))
        compose.setContent {
            VectorintTheme {
                TagEditor(
                    tags = tags,
                    enabled = true,
                    onTagsChange = { tags = it },
                )
            }
        }

        val help =
            "Use tags to organize entries, for example by bank or account. " +
                "They never change your totals. Tap a tag to remove it."
        compose.onNodeWithText(help).assertDoesNotExist()
        compose.onNodeWithContentDescription("More about Tags").performClick()
        compose.onNodeWithText(help).assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("New tag").performTextInput("  shared  ")
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("shared").assertIsDisplayed()
        compose.onNodeWithText("work").performClick()

        compose.runOnIdle {
            assertEquals(setOf(Tag("shared")), tags)
        }
    }
}
