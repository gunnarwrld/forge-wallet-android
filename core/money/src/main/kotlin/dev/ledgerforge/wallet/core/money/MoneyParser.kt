package dev.ledgerforge.wallet.core.money

sealed interface MoneyParseResult {
    data class Success(
        val money: Money,
    ) : MoneyParseResult

    data class Failure(
        val error: MoneyParseError,
    ) : MoneyParseResult
}

enum class MoneyParseError {
    Empty,
    InvalidCharacter,
    TooManyDecimalSeparators,
    TooManyFractionDigits,
    Overflow,
}

/**
 * Reads what a user typed into an amount field, digit by digit, straight into minor units.
 * It never goes through a Double, and it refuses input it can't read exactly instead of guessing.
 */
object MoneyParser {
    fun parse(
        input: String,
        currency: CurrencyCode,
    ): MoneyParseResult = TODO()
}
