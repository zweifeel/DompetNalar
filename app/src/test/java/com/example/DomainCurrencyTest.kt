package com.example

import com.example.data.model.DomainCurrency
import com.example.data.model.UnknownCurrencyException
import com.example.data.model.UnsupportedCurrencyScaleException
import org.junit.Assert.assertEquals
import org.junit.Test

class DomainCurrencyTest {

    @Test
    fun `valid ISO currencies are resolved with correct scale`() {
        val idr = DomainCurrency.fromCode("IDR")
        assertEquals("IDR", idr.code)
        assertEquals(2, idr.decimalDigits) // ISO 4217 defines IDR minor unit = 2 (sen)

        val jpy = DomainCurrency.fromCode("JPY")
        assertEquals("JPY", jpy.code)
        assertEquals(0, jpy.decimalDigits) // ISO 4217 defines JPY minor unit = 0

        val usd = DomainCurrency.fromCode("usd ")
        assertEquals("USD", usd.code)
        assertEquals(2, usd.decimalDigits) // ISO 4217 defines USD minor unit = 2 (cents)

        val kwd = DomainCurrency.fromCode("KWD")
        assertEquals("KWD", kwd.code)
        assertEquals(3, kwd.decimalDigits) // ISO 4217 defines KWD minor unit = 3 (fils)
    }

    @Test(expected = UnknownCurrencyException::class)
    fun `invalid currency code throws UnknownCurrencyException`() {
        DomainCurrency.fromCode("INVALID")
    }

    @Test(expected = UnsupportedCurrencyScaleException::class)
    fun `pseudo-currency with negative fraction digits is rejected`() {
        // T-30: XAU (Gold) has defaultFractionDigits = -1 in ISO 4217
        DomainCurrency.fromCode("XAU")
    }
}
