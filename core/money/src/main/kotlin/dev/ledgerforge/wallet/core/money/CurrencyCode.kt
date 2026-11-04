package dev.ledgerforge.wallet.core.money

/** An ISO 4217 code the app knows the minor-unit exponent for. */
@JvmInline
value class CurrencyCode private constructor(
    val code: String,
) {
    /** Number of minor-unit digits: 2 for SEK (öre), 0 for JPY, 3 for KWD (fils). */
    val exponent: Int get() = TODO()

    override fun toString(): String = code

    companion object {
        val supported: List<CurrencyCode> get() = TODO()

        /** @throws IllegalArgumentException for malformed or unsupported codes. */
        fun of(code: String): CurrencyCode = TODO()

        /** Null for malformed or unsupported codes. */
        fun parse(code: String): CurrencyCode? = TODO()
    }
}
