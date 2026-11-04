package dev.ledgerforge.wallet.core.money

import org.junit.Test
import java.util.Currency
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurrencyCodeTest {
    @Test
    fun `exponents follow ISO 4217`() {
        assertEquals(2, CurrencyCode.of("SEK").exponent)
        assertEquals(2, CurrencyCode.of("EUR").exponent)
        assertEquals(0, CurrencyCode.of("JPY").exponent)
        assertEquals(3, CurrencyCode.of("KWD").exponent)
    }

    @Test
    fun `rejects malformed codes`() {
        listOf("", "sek", "SE", "SEKK", "S3K", " SEK").forEach { code ->
            assertFailsWith<IllegalArgumentException>("expected '$code' to be rejected") { CurrencyCode.of(code) }
            assertNull(CurrencyCode.parse(code))
        }
    }

    @Test
    fun `rejects well-formed codes without a known exponent`() {
        assertFailsWith<IllegalArgumentException> { CurrencyCode.of("XAU") }
        assertNull(CurrencyCode.parse("XAU"))
    }

    @Test
    fun `parse and of agree on supported codes`() {
        assertEquals(CurrencyCode.of("GBP"), CurrencyCode.parse("GBP"))
        assertEquals("GBP", CurrencyCode.of("GBP").toString())
    }

    @Test
    fun `the pinned table still agrees with the ISO data shipped in the JDK`() {
        assertTrue(CurrencyCode.supported.isNotEmpty())
        CurrencyCode.supported.forEach { currency ->
            assertEquals(
                Currency.getInstance(currency.code).defaultFractionDigits,
                currency.exponent,
                "exponent for $currency",
            )
        }
    }
}
