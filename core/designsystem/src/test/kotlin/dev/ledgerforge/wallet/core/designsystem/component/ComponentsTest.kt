package dev.ledgerforge.wallet.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.ledgerforge.wallet.core.designsystem.theme.ForgeTheme
import dev.ledgerforge.wallet.core.money.CurrencyCode
import dev.ledgerforge.wallet.core.money.Money
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class ComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val sek = CurrencyCode.of("SEK")

    @Test
    fun `a balance card shows the wallet, its balance and what's in flight`() {
        composeRule.setContent {
            ForgeTheme { BalanceCard(name = "Alice", balance = Money(97_250, sek), supportingText = "SEK 3.00 pending") }
        }

        composeRule.onNodeWithText("Alice").assertIsDisplayed()
        composeRule.onNodeWithText("972.50", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("SEK 3.00 pending").assertIsDisplayed()
    }

    @Test
    fun `a transaction row hides its monogram from screen readers and offers its actions`() {
        var retried = 0
        composeRule.setContent {
            ForgeTheme {
                TransactionRow(
                    title = "Alice",
                    amount = Money(-400, sek),
                    supportingText = "Failed",
                    actions = { TextButton(onClick = { retried++ }) { Text("Retry") } },
                )
            }
        }

        composeRule.onNodeWithText("Alice").assertIsDisplayed()
        composeRule.onNodeWithText("A").assertDoesNotExist()
        composeRule.onNodeWithText("Retry").assertHasClickAction().performClick()
        assertEquals(1, retried)
    }

    @Test
    fun `a primary button can't be pressed while it's loading`() {
        var presses = 0
        composeRule.setContent {
            ForgeTheme { PrimaryButton(text = "Send", onClick = { presses++ }, loading = true) }
        }

        composeRule
            .onNodeWithText("Send")
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .performClick()

        assertEquals(0, presses)
    }
}
