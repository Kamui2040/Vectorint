package io.github.kamui2040.vectorint.presentation.about

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import io.github.kamui2040.vectorint.BuildConfig
import io.github.kamui2040.vectorint.presentation.theme.VectorintTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AboutDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `about presents compact navigation and privacy summary`() {
        compose.setContent {
            VectorintTheme {
                AboutDialog(onDismiss = {})
            }
        }

        compose.onNodeWithText("Vectorint").assertIsDisplayed()
        compose
            .onNodeWithText("See how much you can safely spend now.")
            .assertIsDisplayed()
        compose.onNodeWithText("Changelog").assertIsDisplayed()
        compose.onNodeWithText("License & usage").assertIsDisplayed()
        compose.onNodeWithText("Privacy").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sources").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Support on Ko-fi").performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithText("Local · offline-first · no sign-in · no ads · no analytics · no tracking")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `about changelog follows the current app version`() {
        compose.setContent {
            VectorintTheme {
                AboutDialog(onDismiss = {})
            }
        }

        compose.onNodeWithText("Changelog").performClick()
        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        compose
            .onNodeWithText("Tighter About card", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `about exposes application artwork and runtime licences`() {
        compose.setContent {
            VectorintTheme {
                AboutDialog(onDismiss = {})
            }
        }

        compose.onNodeWithText("License & usage").performClick()
        compose.onNodeWithText("Vectorint source").assertIsDisplayed()
        compose.onNodeWithText("GPL-3.0-only").assertIsDisplayed()
        compose.onNodeWithText("Vectorint Raven").assertIsDisplayed()
        compose.onNodeWithText("CC BY 4.0").assertIsDisplayed()
        compose.onNodeWithText("Material Icons").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Apache License 2.0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("desugar_jdk_libs").performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithText("GPL-2.0 with Classpath Exception")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `about privacy page reflects the repository privacy policy`() {
        val opened = mutableListOf<String>()
        val uriHandler =
            object : UriHandler {
                override fun openUri(uri: String) {
                    opened += uri
                }
            }

        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                VectorintTheme {
                    AboutDialog(onDismiss = {})
                }
            }
        }

        compose.onNodeWithText("Privacy").performScrollTo().performClick()
        compose
            .onNodeWithText("Vectorint keeps your budget data on your device.")
            .assertIsDisplayed()
        compose.onNodeWithText("Data use").assertIsDisplayed()
        compose
            .onNodeWithText("K2040 does not receive, upload, sell, or share", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Stored locally").performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithText("does not access your Android system calendar", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Backups").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Notifications").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Removing your data").performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithText("Policy last updated: 16 September 2026.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Privacy questions & reports").performScrollTo().performClick()

        compose.runOnIdle {
            assertEquals(
                listOf("https://github.com/Kamui2040/Vectorint/issues"),
                opened,
            )
        }
    }

    @Test
    fun `about opens support repository and websites from labelled rows`() {
        val opened = mutableListOf<String>()
        val uriHandler =
            object : UriHandler {
                override fun openUri(uri: String) {
                    opened += uri
                }
            }

        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                VectorintTheme {
                    AboutDialog(onDismiss = {})
                }
            }
        }

        compose.onNodeWithText("Support on Ko-fi").performScrollTo().performClick()
        compose.onNodeWithText("Sources").performScrollTo().performClick()
        compose.onNodeWithText("Repository").performClick()
        compose.onNodeWithText("App website").performClick()
        compose.onNodeWithText("Main website").performClick()

        compose.runOnIdle {
            assertEquals(
                listOf(
                    "https://ko-fi.com/k2040",
                    "https://github.com/Kamui2040/Vectorint",
                    "https://kamui2040.github.io/K2040-Android-Releases/apps/vectorint/",
                    "https://kamui2040.github.io/",
                ),
                opened,
            )
        }
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
