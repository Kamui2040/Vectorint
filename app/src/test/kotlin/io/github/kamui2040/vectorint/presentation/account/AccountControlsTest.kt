package io.github.kamui2040.vectorint.presentation.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.kamui2040.vectorint.core.Account
import io.github.kamui2040.vectorint.core.AccountId
import io.github.kamui2040.vectorint.core.CurrencyCode
import io.github.kamui2040.vectorint.core.CurrentFunds
import io.github.kamui2040.vectorint.core.Money
import io.github.kamui2040.vectorint.presentation.theme.VectorintTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccountControlsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `one account is implicit and does not show a chooser`() {
        val main = account("main", "Main")

        compose.setContent {
            VectorintTheme {
                AccountSelector(
                    accounts = listOf(main),
                    selectedAccountId = main.id,
                    enabled = true,
                    onAccountChange = {},
                )
            }
        }

        compose.onNodeWithText("Main").assertDoesNotExist()
        compose.onNodeWithText("Account").assertDoesNotExist()
    }

    @Test
    fun `multiple accounts are displayed as directly selectable tags`() {
        val bank = account("bank", "Bank")
        val cash = account("cash", "Cash")
        var selected = bank.id

        compose.setContent {
            VectorintTheme {
                AccountSelector(
                    accounts = listOf(bank, cash),
                    selectedAccountId = selected,
                    enabled = true,
                    onAccountChange = { selected = it },
                )
            }
        }

        compose.onNodeWithText("Bank").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("Cash").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(cash.id, selected) }
    }

    private fun account(
        id: String,
        name: String,
    ): Account =
        Account(
            id = AccountId(id),
            name = name,
            currentFunds =
                CurrentFunds(
                    amount = Money(10_000, CurrencyCode.of("EUR")),
                    capturedAt = Instant.EPOCH,
                ),
        )
}
