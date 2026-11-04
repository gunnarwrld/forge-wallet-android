package dev.ledgerforge.wallet.core.money

/**
 * An amount in integer minor units, the same representation Ledger Forge uses.
 * There is deliberately no Float or Double anywhere on this type.
 */
data class Money(
    val minor: Long,
    val currency: CurrencyCode,
) : Comparable<Money> {
    val isZero: Boolean get() = TODO()
    val isPositive: Boolean get() = TODO()
    val isNegative: Boolean get() = TODO()

    /** @throws IllegalArgumentException on a currency mismatch, ArithmeticException on overflow. */
    operator fun plus(other: Money): Money = TODO()

    /** @throws IllegalArgumentException on a currency mismatch, ArithmeticException on overflow. */
    operator fun minus(other: Money): Money = TODO()

    /** @throws ArithmeticException for Long.MIN_VALUE, which has no positive counterpart. */
    operator fun unaryMinus(): Money = TODO()

    /** @throws IllegalArgumentException on a currency mismatch. */
    override fun compareTo(other: Money): Int = TODO()

    /** Exact decimal form with the currency's number of decimals: "12.50", "-0.05", "1234". */
    fun toPlainString(): String = TODO()

    companion object {
        fun zero(currency: CurrencyCode): Money = TODO()
    }
}
