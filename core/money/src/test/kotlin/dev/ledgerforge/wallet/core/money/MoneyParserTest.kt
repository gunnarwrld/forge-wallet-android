package dev.ledgerforge.wallet.core.money

import dev.ledgerforge.wallet.core.money.MoneyParseError.Empty
import dev.ledgerforge.wallet.core.money.MoneyParseError.InvalidCharacter
import dev.ledgerforge.wallet.core.money.MoneyParseError.Overflow
import dev.ledgerforge.wallet.core.money.MoneyParseError.TooManyDecimalSeparators
import dev.ledgerforge.wallet.core.money.MoneyParseError.TooManyFractionDigits
import org.junit.Test
import kotlin.test.assertEquals

class MoneyParserTest {
    private val sek = CurrencyCode.of("SEK")
    private val jpy = CurrencyCode.of("JPY")
    private val kwd = CurrencyCode.of("KWD")

    private fun parse(
        input: String,
        currency: CurrencyCode = sek,
    ) = MoneyParser.parse(input, currency)

    private fun success(
        minor: Long,
        currency: CurrencyCode = sek,
    ) = MoneyParseResult.Success(Money(minor, currency))

    private fun failure(error: MoneyParseError) = MoneyParseResult.Failure(error)

    @Test
    fun `parses whole and fractional amounts`() {
        assertEquals(success(1_250), parse("12.50"))
        assertEquals(success(1_250), parse("12,50"))
        assertEquals(success(1_205), parse("12.05"))
        assertEquals(success(1_250), parse("12.5"))
        assertEquals(success(1_200), parse("12"))
        assertEquals(success(1_200), parse("12."))
        assertEquals(success(50), parse(".5"))
        assertEquals(success(0), parse("0"))
        assertEquals(success(700), parse("007"))
        assertEquals(success(1_250), parse("  12.50 "))
    }

    @Test
    fun `stays exact where Double does not`() {
        // 0.29 * 100 == 28.999999999999996 and 1.15 * 100 == 114.99999999999999 as Double.
        assertEquals(success(29), parse("0.29"))
        assertEquals(success(115), parse("1.15"))
        // Seventeen significant digits: more than a Double can hold.
        assertEquals(success(99_999_999_999_999_999), parse("999999999999999.99"))
    }

    @Test
    fun `respects the currency exponent`() {
        assertEquals(success(1_234, jpy), parse("1234", jpy))
        assertEquals(failure(TooManyFractionDigits), parse("12.5", jpy))
        assertEquals(success(1_005, kwd), parse("1.005", kwd))
        assertEquals(failure(TooManyFractionDigits), parse("12.505"))
    }

    @Test
    fun `refuses input it cannot read exactly`() {
        assertEquals(failure(Empty), parse(""))
        assertEquals(failure(Empty), parse("   "))
        assertEquals(failure(Empty), parse("."))
        assertEquals(failure(InvalidCharacter), parse("-12"))
        assertEquals(failure(InvalidCharacter), parse("12a"))
        assertEquals(failure(InvalidCharacter), parse("1 000"))
        assertEquals(failure(InvalidCharacter), parse("1e3"))
        assertEquals(failure(InvalidCharacter), parse("١٢"))
        assertEquals(failure(TooManyDecimalSeparators), parse("1.2.3"))
        // Never guesses which separator groups thousands.
        assertEquals(failure(TooManyDecimalSeparators), parse("1,000.50"))
    }

    @Test
    fun `reports overflow instead of wrapping`() {
        assertEquals(success(Long.MAX_VALUE), parse("92233720368547758.07"))
        assertEquals(failure(Overflow), parse("92233720368547758.08"))
        assertEquals(failure(Overflow), parse("99999999999999999999999"))
    }
}
