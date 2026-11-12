package dev.ledgerforge.wallet.core.designsystem.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import dev.ledgerforge.wallet.core.designsystem.theme.ForgeTheme
import dev.ledgerforge.wallet.core.money.CurrencyCode
import dev.ledgerforge.wallet.core.money.Money
import dev.ledgerforge.wallet.core.money.MoneyFormatter
import java.util.Locale

/**
 * An amount in the user's locale, set in tabular figures so digits line up in lists and a
 * changing balance doesn't shift width.
 */
@Composable
fun AmountText(
    money: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    locale: Locale = currentLocale(),
) {
    val text = remember(money, locale) { MoneyFormatter.format(money, locale) }
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style.merge(TextStyle(fontFeatureSettings = "tnum")),
        maxLines = 1,
    )
}

/** [money] as display text in the user's locale, for amounts that sit inside a sentence. */
@Composable
@ReadOnlyComposable
fun formatAmount(money: Money): String = MoneyFormatter.format(money, currentLocale())

@Composable
@ReadOnlyComposable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AmountTextPreview() {
    ForgeTheme {
        Surface {
            AmountText(
                money = Money(123_450, CurrencyCode.of("SEK")),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}
