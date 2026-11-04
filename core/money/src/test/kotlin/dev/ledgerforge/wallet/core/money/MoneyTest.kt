package dev.ledgerforge.wallet.core.money

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoneyTest {
    private val sek = CurrencyCode.of("SEK")
    private val eur = CurrencyCode.of("EUR")

    @Test
    fun `adds and subtracts in minor units`() {
        assertEquals(Money(1_550, sek), Money(1_000, sek) + Money(550, sek))
        assertEquals(Money(-50, sek), Money(500, sek) - Money(550, sek))
        assertEquals(Money(-500, sek), -Money(500, sek))
    }

    @Test
    fun `refuses to mix currencies`() {
        assertFailsWith<IllegalArgumentException> { Money(1, sek) + Money(1, eur) }
        assertFailsWith<IllegalArgumentException> { Money(1, sek) - Money(1, eur) }
        assertFailsWith<IllegalArgumentException> { Money(1, sek) < Money(1, eur) }
    }

    @Test
    fun `throws instead of silently overflowing`() {
        assertFailsWith<ArithmeticException> { Money(Long.MAX_VALUE, sek) + Money(1, sek) }
        assertFailsWith<ArithmeticException> { Money(Long.MIN_VALUE, sek) - Money(1, sek) }
        assertFailsWith<ArithmeticException> { -Money(Long.MIN_VALUE, sek) }
    }

    @Test
    fun `compares amounts in the same currency`() {
        assertTrue(Money(100, sek) > Money(99, sek))
        assertEquals(0, Money(100, sek).compareTo(Money(100, sek)))
    }

    @Test
    fun `reports its sign`() {
        assertTrue(Money.zero(sek).isZero)
        assertFalse(Money.zero(sek).isPositive)
        assertTrue(Money(1, sek).isPositive)
        assertTrue(Money(-1, sek).isNegative)
    }

    @Test
    fun `renders an exact plain decimal string`() {
        assertEquals("12.50", Money(1_250, sek).toPlainString())
        assertEquals("-0.05", Money(-5, sek).toPlainString())
        assertEquals("0.00", Money.zero(sek).toPlainString())
        assertEquals("1234", Money(1_234, CurrencyCode.of("JPY")).toPlainString())
        assertEquals("1.005", Money(1_005, CurrencyCode.of("KWD")).toPlainString())
        assertEquals("92233720368547758.07", Money(Long.MAX_VALUE, sek).toPlainString())
    }
}
