package dev.ledgerforge.wallet.core.money

/** An ISO 4217 code the app knows the minor-unit exponent for. */
@JvmInline
value class CurrencyCode private constructor(
    val code: String,
) {
    /** Number of minor-unit digits: 2 for SEK (öre), 0 for JPY, 3 for KWD (fils). */
    val exponent: Int get() = EXPONENTS.getValue(code)

    override fun toString(): String = code

    companion object {
        // Pinned instead of read from java.util.Currency at runtime, so parsing and formatting
        // can't drift with whichever ISO data a device or JDK ships. CurrencyCodeTest checks
        // that the table still agrees with the JDK.
        private val EXPONENTS: Map<String, Int> =
            mapOf(
                "CHF" to 2,
                "CZK" to 2,
                "DKK" to 2,
                "EUR" to 2,
                "GBP" to 2,
                "HUF" to 2,
                "NOK" to 2,
                "PLN" to 2,
                "RON" to 2,
                "SEK" to 2,
                "USD" to 2,
                "ISK" to 0,
                "JPY" to 0,
                "KRW" to 0,
                "BHD" to 3,
                "JOD" to 3,
                "KWD" to 3,
                "OMR" to 3,
                "TND" to 3,
            )

        val supported: List<CurrencyCode> = EXPONENTS.keys.sorted().map(::CurrencyCode)

        /** @throws IllegalArgumentException for malformed or unsupported codes. */
        fun of(code: String): CurrencyCode = requireNotNull(parse(code)) { "Unsupported currency code '$code'" }

        /** Null for malformed or unsupported codes. */
        fun parse(code: String): CurrencyCode? = if (code in EXPONENTS) CurrencyCode(code) else null
    }
}
