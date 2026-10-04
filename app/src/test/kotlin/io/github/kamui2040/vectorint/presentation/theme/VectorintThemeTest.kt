package io.github.kamui2040.vectorint.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VectorintThemeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `multiline typography keeps line height above font size`() {
        lateinit var bodyLarge: TextStyle
        lateinit var bodyMedium: TextStyle
        lateinit var bodySmall: TextStyle
        lateinit var titleLarge: TextStyle

        compose.setContent {
            VectorintTheme {
                bodyLarge = MaterialTheme.typography.bodyLarge
                bodyMedium = MaterialTheme.typography.bodyMedium
                bodySmall = MaterialTheme.typography.bodySmall
                titleLarge = MaterialTheme.typography.titleLarge
            }
        }

        compose.runOnIdle {
            listOf(bodyLarge, bodyMedium, bodySmall, titleLarge).forEach { style ->
                assertTrue(style.lineHeight.value > style.fontSize.value)
            }
        }
    }
}
