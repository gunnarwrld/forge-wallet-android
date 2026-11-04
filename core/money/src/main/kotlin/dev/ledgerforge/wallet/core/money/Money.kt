package dev.ledgerforge.wallet.core.money

import java.math.BigDecimal

/**
 * An amount in integer minor units, the same representation Ledger Forge uses.
 * There is deliberately no Float or Double anywhere on this type.
 */
data class Money(
    val minor: Long,
    val currency: CurrencyCode,
) : Comparable<Money> {
    val isZero: Boolean get() = minor == 0L
    val isPositive: Boolean get() = minor > 0L
    val isNegative: Boolean get() = minor < 0L

    /** @throws IllegalArgumentException on a currency mismatch, ArithmeticException on overflow. */
    operator fun plus(other: Money): Money = Money(Math.addExact(minor, sameCurrency(other).minor), currency)

    /** @throws IllegalArgumentException on a currency mismatch, ArithmeticException on overflow. */
    operator fun minus(other: Money): Money = Money(Math.subtractExact(minor, sameCurrency(other).minor), currency)

    /** @throws ArithmeticException for Long.MIN_VALUE, which has no positive counterpart. */
    operator fun unaryMinus(): Money = Money(Math.negateExact(minor), currency)

    /** @throws IllegalArgumentException on a currency mismatch. */
    override fun compareTo(other: Money): Int = minor.compareTo(sameCurrency(other).minor)

    /** Exact decimal form with the currency's number of decimals: "12.50", "-0.05", "1234". */
    fun toPlainString(): String = toBigDecimal().toPlainString()

    internal fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(minor, currency.exponent)

    private fun sameCurrency(other: Money): Money {
        require(other.currency == currency) { "Currency mismatch: $currency and ${other.currency}" }
        return other
    }

    companion object {
        fun zero(currency: CurrencyCode): Money = Money(0, currency)
    }
}
