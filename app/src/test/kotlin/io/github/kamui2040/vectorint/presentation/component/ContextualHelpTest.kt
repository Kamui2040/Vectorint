package io.github.kamui2040.vectorint.presentation.component

import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.kamui2040.vectorint.presentation.theme.VectorintTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ContextualHelpTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `info control has an accessible target and dismissible explanation`() {
        compose.setContent {
            VectorintTheme {
                InfoHeading(title = "Timing", help = "How timing works.")
            }
        }

        val info = compose.onNodeWithContentDescription("More about Timing")
        info.assertWidthIsEqualTo(48.dp)
        info.assertHeightIsEqualTo(48.dp)
        compose.onNodeWithText("How timing works.").assertDoesNotExist()

        info.performClick()
        compose.onNodeWithText("How timing works.").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("How timing works.").assertDoesNotExist()
    }
}
