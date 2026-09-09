package io.github.kamui2040.vectorint.presentation.about

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import io.github.kamui2040.vectorint.presentation.theme.VectorintTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AboutDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `about presents the brand purpose privacy and navigation`() {
        compose.setContent {
            VectorintTheme {
                AboutDialog(onDismiss = {})
            }
        }

        compose.onNodeWithText("Vectorint").assertIsDisplayed()
        compose
            .onNodeWithText("See how much you can safely spend now.")
            .assertIsDisplayed()
        compose
            .onNodeWithText("Local · offline-first · no account · no ads · no analytics · no tracking")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Changelog").assertIsDisplayed()
        compose.onNodeWithText("License & usage").assertIsDisplayed()
        compose.onNodeWithText("Sources").assertIsDisplayed()
    }

    @Test
    fun `about exposes software and raven licences`() {
        compose.setContent {
            VectorintTheme {
                AboutDialog(onDismiss = {})
            }
        }

        compose.onNodeWithText("License & usage").performClick()
        compose
            .onNodeWithText(
                "Vectorint source is licensed under GPL-3.0-only. The Vectorint Raven is Copyright 2026 K2040 " +
                    "and licensed under CC BY 4.0. The artwork was generated with OpenAI at K2040’s direction " +
                    "and contributed by K2040. The K2040 logo is Copyright 2026 K2040 and is used only in About.",
            ).assertIsDisplayed()
    }

    @Test
    fun `about close action dismisses the card`() {
        var dismisses = 0
        compose.setContent {
            VectorintTheme {
                AboutDialog(onDismiss = { dismisses++ })
            }
        }

        compose.onNodeWithContentDescription("Close About").performClick()
        compose.runOnIdle { assertEquals(1, dismisses) }
    }

    @Test
    fun `about title stays on one line at enlarged text`() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.3f)) {
                VectorintTheme {
                    AboutDialog(onDismiss = {})
                }
            }
        }

        val titleHeight =
            compose
                .onNodeWithText("Vectorint")
                .fetchSemanticsNode()
                .boundsInRoot
                .height
        assertTrue("Enlarged title wrapped onto more than one line", titleHeight <= 48f)
    }
}
