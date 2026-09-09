package io.github.kamui2040.vectorint.presentation.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.kamui2040.vectorint.presentation.theme.VectorintTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `main navigation exposes Home History and Overview while Settings stays in the header`() {
        assertEquals(3, MainView.entries.size)
        var selectedView by mutableStateOf(MainView.HOME)
        compose.setContent {
            VectorintTheme {
                MainNavigationBar(
                    selectedView = selectedView,
                    onViewSelected = { selectedView = it },
                )
            }
        }

        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.onNodeWithText("History").assertIsDisplayed()
        compose.onNodeWithText("Overview").assertIsDisplayed()
        compose.onNodeWithText("Settings").assertDoesNotExist()
        compose.onNodeWithText("Home").assertIsSelected()
        compose.onNodeWithText("History").assertIsNotSelected()
        compose.onNodeWithText("Overview").assertIsNotSelected()

        compose.onNodeWithText("Overview").performClick()
        compose.runOnIdle { assertEquals(MainView.OVERVIEW, selectedView) }
        compose.onNodeWithText("Overview").assertIsSelected()
        compose.onNodeWithText("History").performClick()
        compose.runOnIdle { assertEquals(MainView.HISTORY, selectedView) }
        compose.onNodeWithText("History").assertIsSelected()
        compose.onNodeWithText("Home").performClick()
        compose.runOnIdle { assertEquals(MainView.HOME, selectedView) }
        compose.onNodeWithText("Home").assertIsSelected()
    }

    @Test
    fun `top app bar keeps the centered brand and both actions available`() {
        var aboutOpens = 0
        var settingsOpens = 0
        compose.setContent {
            VectorintTheme {
                VectorintTopAppBar(
                    onOpenAbout = { aboutOpens++ },
                    onOpenSettings = { settingsOpens++ },
                )
            }
        }

        compose.onNodeWithText("Vectorint").assertIsDisplayed()
        compose.onNodeWithContentDescription("About Vectorint").performClick()
        compose.onNodeWithContentDescription("Open Settings").performClick()
        compose.runOnIdle {
            assertEquals(1, aboutOpens)
            assertEquals(1, settingsOpens)
        }
    }

    @Test
    fun `selected main view remains actionable from a child screen`() {
        val selections = mutableListOf<MainView>()
        compose.setContent {
            VectorintTheme {
                MainNavigationBar(
                    selectedView = MainView.HOME,
                    onViewSelected = selections::add,
                )
            }
        }

        compose.onNodeWithText("Home").assertIsSelected().performClick()
        compose.runOnIdle { assertEquals(listOf(MainView.HOME), selections) }
    }
}
