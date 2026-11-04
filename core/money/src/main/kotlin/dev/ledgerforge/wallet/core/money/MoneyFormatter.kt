package dev.ledgerforge.wallet.core.money

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Locale-aware display text for an amount, always with the currency's own number of decimals. */
object MoneyFormatter {
    fun format(
        money: Money,
        locale: Locale,
    ): String {
        // NumberFormat isn't thread-safe, so build one per call instead of sharing it.
        val format = NumberFormat.getCurrencyInstance(locale)
        // Setting the currency resets the fraction digits, so pin them afterwards.
        format.currency = Currency.getInstance(money.currency.code)
        format.minimumFractionDigits = money.currency.exponent
        format.maximumFractionDigits = money.currency.exponent
        // BigDecimal keeps every digit; a Double would lose them above 2^53.
        return format.format(money.toBigDecimal())
    }
}
