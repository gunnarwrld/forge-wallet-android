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
 *
 * Either '.' or ',' works as the decimal separator, so both "12.50" and "12,50" read as twelve
 * fifty. Grouping separators are rejected rather than guessed: "1,000.50" is an error.
 */
object MoneyParser {
    fun parse(
        input: String,
        currency: CurrencyCode,
    ): MoneyParseResult {
        val text = input.trim()
        val hasInvalidCharacter = text.any { !it.isAsciiDigit() && !it.isDecimalSeparator() }
        val separatorCount = text.count { it.isDecimalSeparator() }
        return when {
            text.isEmpty() -> failure(MoneyParseError.Empty)
            hasInvalidCharacter -> failure(MoneyParseError.InvalidCharacter)
            separatorCount > 1 -> failure(MoneyParseError.TooManyDecimalSeparators)
            else -> parseNumber(text, currency)
        }
    }

    private fun parseNumber(
        text: String,
        currency: CurrencyCode,
    ): MoneyParseResult {
        val separator = text.indexOfFirst { it.isDecimalSeparator() }
        val whole = if (separator < 0) text else text.substring(0, separator)
        val fraction = if (separator < 0) "" else text.substring(separator + 1)
        return when {
            whole.isEmpty() && fraction.isEmpty() -> failure(MoneyParseError.Empty)
            fraction.length > currency.exponent -> failure(MoneyParseError.TooManyFractionDigits)
            else -> toMinorUnits(whole + fraction.padEnd(currency.exponent, '0'), currency)
        }
    }

    private fun toMinorUnits(
        digits: String,
        currency: CurrencyCode,
    ): MoneyParseResult =
        try {
            val minor = digits.fold(0L) { acc, digit -> Math.addExact(Math.multiplyExact(acc, 10L), (digit - '0').toLong()) }
            MoneyParseResult.Success(Money(minor, currency))
        } catch (_: ArithmeticException) {
            failure(MoneyParseError.Overflow)
        }

    private fun failure(error: MoneyParseError) = MoneyParseResult.Failure(error)

    private fun Char.isAsciiDigit() = this in '0'..'9'

    private fun Char.isDecimalSeparator() = this == '.' || this == ','
}
