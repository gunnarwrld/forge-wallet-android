package dev.ledgerforge.wallet.core.money

import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyPropertiesTest {
    private val currencies = Arb.element(CurrencyCode.supported)
    private val sek = CurrencyCode.of("SEK")

    @Test
    fun `parsing the plain string of any amount gives the same amount back`() =
        runTest {
            checkAll(Arb.long(0L..Long.MAX_VALUE), currencies) { minor, currency ->
                val money = Money(minor, currency)
                assertEquals(MoneyParseResult.Success(money), MoneyParser.parse(money.toPlainString(), currency))
            }
        }

    @Test
    fun `addition is exact or throws, never wraps`() =
        runTest {
            checkAll(Arb.long(), Arb.long()) { a, b ->
                val exact = BigInteger.valueOf(a) + BigInteger.valueOf(b)
                if (exact.bitLength() < Long.SIZE_BITS) {
                    assertEquals(exact.toLong(), (Money(a, sek) + Money(b, sek)).minor)
                } else {
                    assertFailsWith<ArithmeticException> { Money(a, sek) + Money(b, sek) }
                }
            }
        }

    @Test
    fun `subtracting what was added restores the original amount`() =
        runTest {
            val amounts = Arb.long(-1_000_000_000_000_000L..1_000_000_000_000_000L)
            checkAll(amounts, amounts) { a, b ->
                assertEquals(Money(a, sek), Money(a, sek) + Money(b, sek) - Money(b, sek))
            }
        }
}
