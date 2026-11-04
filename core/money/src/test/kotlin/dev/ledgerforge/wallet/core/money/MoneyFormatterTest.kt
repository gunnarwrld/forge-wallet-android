package dev.ledgerforge.wallet.core.money

import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

class MoneyFormatterTest {
    private val sek = CurrencyCode.of("SEK")
    private val usd = CurrencyCode.of("USD")
    private val eur = CurrencyCode.of("EUR")

    // JDK releases ship different CLDR versions, which disagree on the space character
    // (U+00A0 or U+202F) and the minus sign (U+2212). Normalise both so the tests pin
    // digits, separators and symbols rather than whitespace flavour.
    private fun format(
        money: Money,
        languageTag: String,
    ): String =
        MoneyFormatter
            .format(money, Locale.forLanguageTag(languageTag))
            .replace(' ', ' ')
            .replace(' ', ' ')
            .replace('−', '-')

    @Test
    fun `uses the locale's separators and symbol position`() {
        assertEquals("$1,234.50", format(Money(123_450, usd), "en-US"))
        assertEquals("1 234,50 kr", format(Money(123_450, sek), "sv-SE"))
        assertEquals("1.234,50 €", format(Money(123_450, eur), "de-DE"))
    }

    @Test
    fun `shows exactly the currency's number of decimals`() {
        assertEquals("¥1,234", format(Money(1_234, CurrencyCode.of("JPY")), "en-US"))
        assertEquals("KWD 1.234", format(Money(1_234, CurrencyCode.of("KWD")), "en-US"))
        assertEquals("$0.05", format(Money(5, usd), "en-US"))
    }

    @Test
    fun `formats negative amounts`() {
        assertEquals("-$5.00", format(Money(-500, usd), "en-US"))
    }

    @Test
    fun `formats the largest amount without losing digits`() {
        assertEquals("$92,233,720,368,547,758.07", format(Money(Long.MAX_VALUE, usd), "en-US"))
    }
}
