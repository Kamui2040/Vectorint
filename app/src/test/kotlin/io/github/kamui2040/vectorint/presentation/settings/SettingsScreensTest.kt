package io.github.kamui2040.vectorint.presentation.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.kamui2040.vectorint.data.ColorPalette
import io.github.kamui2040.vectorint.data.ThemeMode
import io.github.kamui2040.vectorint.presentation.theme.VectorintTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `settings root exposes the requested cards and closes`() {
        var closes = 0
        var aboutOpens = 0
        compose.setContent {
            VectorintTheme {
                SettingsScreen(
                    includeExpectedIncome = false,
                    saving = false,
                    saveFailed = false,
                    onIncludeExpectedIncomeChange = {},
                    onSave = {},
                    onOpenAbout = { aboutOpens++ },
                    onDismiss = { closes++ },
                )
            }
        }

        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Settings"))
            .assertExists()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        compose.onNodeWithText("Language").assertIsDisplayed()
        compose.onNodeWithText("Calculation").assertIsDisplayed()
        compose.onNodeWithText("Data & Backup").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Use device setting · Orbit").assertDoesNotExist()
        compose.onNodeWithText("Follow device").assertDoesNotExist()
        compose.onNodeWithText("Expected income not included").assertDoesNotExist()
        compose.onNodeWithText("Export or restore a local JSON backup").assertDoesNotExist()
        compose
            .onNodeWithText("Version, privacy, licences, changelog, and sources")
            .assertDoesNotExist()
        compose.onNodeWithText("About").performScrollTo().performClick()
        compose.onNodeWithText("Close").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, aboutOpens)
            assertEquals(1, closes)
        }
    }

    @Test
    fun `calculation page explains and saves the expected-income choice`() {
        var page by mutableStateOf(SettingsPage.ROOT)
        var includeExpectedIncome by mutableStateOf(false)
        var saves = 0
        compose.setContent {
            VectorintTheme {
                SettingsScreen(
                    includeExpectedIncome = includeExpectedIncome,
                    page = page,
                    saving = false,
                    saveFailed = false,
                    onPageChange = { page = it },
                    onIncludeExpectedIncomeChange = { includeExpectedIncome = it },
                    onSave = { saves++ },
                )
            }
        }

        compose.onNodeWithText("Calculation").performClick()
        val help =
            "When on, planned income for this month increases Available now. " +
                "When off, only money already in Current funds counts."
        compose.onNodeWithText(help).assertDoesNotExist()
        compose.onNodeWithContentDescription("More about Calculation").performClick()
        compose.onNodeWithText(help).assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Include expected income").assertIsOff()
        compose.onNodeWithText("Include expected income").performClick()
        compose.onNodeWithText("Include expected income").assertIsOn()
        compose.onNodeWithText("Save settings").performClick()
        compose.runOnIdle {
            assertEquals(true, includeExpectedIncome)
            assertEquals(1, saves)
        }
    }

    @Test
    fun `appearance page offers theme and palette choices`() {
        var page by mutableStateOf(SettingsPage.ROOT)
        var themeMode by mutableStateOf(ThemeMode.FOLLOW_SYSTEM)
        var colorPalette by mutableStateOf(ColorPalette.ORBIT)
        compose.setContent {
            VectorintTheme {
                SettingsScreen(
                    includeExpectedIncome = false,
                    themeMode = themeMode,
                    colorPalette = colorPalette,
                    page = page,
                    saving = false,
                    saveFailed = false,
                    onPageChange = { page = it },
                    onIncludeExpectedIncomeChange = {},
                    onThemeModeChange = { themeMode = it },
                    onColorPaletteChange = { colorPalette = it },
                    onSave = {},
                )
            }
        }

        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithText("Use device setting").assertIsSelected()
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        compose.onNodeWithText("Nebula").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(ThemeMode.DARK, themeMode)
            assertEquals(ColorPalette.NEBULA, colorPalette)
        }
        compose.onNodeWithText("Dark").assertIsSelected()
        compose.onNodeWithText("Nebula").assertIsSelected()
    }

    @Test
    fun `language page offers device English and German choices`() {
        var page by mutableStateOf(SettingsPage.ROOT)
        var language by mutableStateOf(AppLanguage.FOLLOW_DEVICE)
        compose.setContent {
            VectorintTheme {
                SettingsScreen(
                    includeExpectedIncome = false,
                    language = language,
                    page = page,
                    saving = false,
                    saveFailed = false,
                    onPageChange = { page = it },
                    onLanguageChange = { language = it },
                    onIncludeExpectedIncomeChange = {},
                    onSave = {},
                )
            }
        }

        compose.onNodeWithText("Language").performClick()
        compose.onNodeWithText("Follow device").assertIsSelected()
        compose.onNodeWithText("German").performClick()
        compose.runOnIdle { assertEquals(AppLanguage.GERMAN, language) }
        compose.onNodeWithText("German").assertIsSelected()
        compose.onNodeWithText("English").assertIsDisplayed()
        val help =
            "Choose Vectorint’s language. Money, numbers, and dates still use your device’s regional format."
        compose.onNodeWithText(help).assertDoesNotExist()
        compose.onNodeWithContentDescription("More about Language").performClick()
        compose.onNodeWithText(help).assertIsDisplayed()
    }

    @Test
    fun `data page explains local readable backups and exposes both actions`() {
        var page by mutableStateOf(SettingsPage.ROOT)
        var exports = 0
        var restores = 0
        compose.setContent {
            VectorintTheme {
                SettingsScreen(
                    includeExpectedIncome = false,
                    page = page,
                    saving = false,
                    saveFailed = false,
                    onPageChange = { page = it },
                    onIncludeExpectedIncomeChange = {},
                    onSave = {},
                    onExportBackup = { exports++ },
                    onRestoreBackup = { restores++ },
                )
            }
        }

        compose.onNodeWithText("Data & Backup").performScrollTo().performClick()
        val help =
            "A backup is a readable file with your budget and settings. " +
                "Store it somewhere you trust. Vectorint never uploads it."
        compose.onNodeWithText(help).assertDoesNotExist()
        compose.onNodeWithContentDescription("More about Data & Backup").performClick()
        compose
            .onNodeWithText(help)
            .assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Export backup").performClick()
        compose.onNodeWithText("Restore backup").performClick()
        compose.runOnIdle {
            assertEquals(1, exports)
            assertEquals(1, restores)
        }
    }

    @Test
    fun `save and restore failures remain visibly distinct`() {
        var page by mutableStateOf(SettingsPage.CALCULATION)
        var state by mutableStateOf(BackupUiState.INVALID_BACKUP)
        compose.setContent {
            VectorintTheme {
                SettingsScreen(
                    includeExpectedIncome = false,
                    page = page,
                    saving = false,
                    saveFailed = true,
                    backupState = state,
                    onPageChange = { page = it },
                    onIncludeExpectedIncomeChange = {},
                    onSave = {},
                )
            }
        }

        compose
            .onNodeWithText("Couldn’t save settings. Your saved settings are unchanged.")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertIsDisplayed()
        compose.runOnIdle { page = SettingsPage.DATA }
        compose
            .onNodeWithText("This is not a supported Vectorint backup. Your data is unchanged.")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertIsDisplayed()
        compose.runOnIdle { state = BackupUiState.RECOVERY_FAILED }
        compose
            .onNodeWithText(
                "Vectorint could not verify that your saved data was recovered. " +
                    "Close the app and do not make changes until the data is checked.",
            ).assertIsDisplayed()
    }
}
