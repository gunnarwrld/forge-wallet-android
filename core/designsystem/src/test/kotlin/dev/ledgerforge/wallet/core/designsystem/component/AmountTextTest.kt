package dev.ledgerforge.wallet.core.designsystem.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.ledgerforge.wallet.core.designsystem.theme.ForgeTheme
import dev.ledgerforge.wallet.core.money.CurrencyCode
import dev.ledgerforge.wallet.core.money.Money
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class AmountTextTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows the amount formatted for the given locale`() {
        composeRule.setContent {
            ForgeTheme {
                AmountText(money = Money(123_450, CurrencyCode.of("USD")), locale = Locale.US)
            }
        }

        composeRule.onNodeWithText("$1,234.50").assertIsDisplayed()
    }

    @Test
    fun `keeps every decimal of a three-decimal currency`() {
        composeRule.setContent {
            ForgeTheme {
                AmountText(money = Money(1_005, CurrencyCode.of("KWD")), locale = Locale.US)
            }
        }

        composeRule.onNodeWithText("1.005", substring = true).assertIsDisplayed()
    }
}
