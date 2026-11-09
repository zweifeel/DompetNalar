package com.example

import com.example.data.model.CurrencyMismatchException
import com.example.data.model.DomainCurrency
import com.example.data.model.Money
import com.example.data.model.PrecisionLossException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class MoneyTest {

    private val idr = DomainCurrency.fromCode("IDR")
    private val usd = DomainCurrency.fromCode("USD")
    private val jpy = DomainCurrency.fromCode("JPY")

    @Test
    fun `same currency arithmetic operates correctly`() {
        val m1 = Money(BigDecimal("100.50"), usd)
        val m2 = Money(BigDecimal("50.25"), usd)

        val sum = m1 + m2
        assertEquals(BigDecimal("150.75"), sum.amount)

        val diff = m1 - m2
        assertEquals(BigDecimal("50.25"), diff.amount)

        val prod = m1 * 2L
        assertEquals(BigDecimal("201.00"), prod.amount)
    }

    @Test(expected = CurrencyMismatchException::class)
    fun `T-23 cross-currency addition throws CurrencyMismatchException`() {
        val m1 = Money(BigDecimal("10000"), idr)
        val m2 = Money(BigDecimal("10.00"), usd)
        val unused = m1 + m2
    }

    @Test(expected = CurrencyMismatchException::class)
    fun `T-23 cross-currency comparison throws CurrencyMismatchException`() {
        val m1 = Money(BigDecimal("10000"), idr)
        val m2 = Money(BigDecimal("10.00"), usd)
        val unused = m1.compareTo(m2)
    }

    @Test
    fun `quantization applies bankers rounding to currency scale`() {
        val unrounded = Money(BigDecimal("10.555"), usd)
        val quantized = unrounded.quantized(RoundingMode.HALF_EVEN)
        assertEquals(BigDecimal("10.56"), quantized.amount)

        val jpyUnrounded = Money(BigDecimal("1000.6"), jpy)
        val jpyQuantized = jpyUnrounded.quantized(RoundingMode.HALF_EVEN)
        assertEquals(BigDecimal("1001"), jpyQuantized.amount)
    }

    @Test
    fun `fromDouble within safe range converts cleanly`() {
        val m = Money.fromDouble(500000.0, idr)
        assertEquals(0, BigDecimal.valueOf(500000.0).compareTo(m.amount))
    }

    @Test(expected = PrecisionLossException::class)
    fun `T-24 double at or above 2 to the 53rd throws PrecisionLossException`() {
        val hugeValue = 9007199254740992.0 // 2^53
        Money.fromDouble(hugeValue, idr)
    }

    @Test
    fun `V-MAT-01 safe IDR conversion and minor units`() {
        val m = Money.fromDouble(900000000000.0, idr)
        assertEquals(90000000000000L, m.toMinorUnits())
        val roundTrip = Money.fromMinorUnits(90000000000000L, idr)
        assertEquals(0, m.amount.compareTo(roundTrip.amount))
        assertEquals(m.toMinorUnits(), roundTrip.toMinorUnits())
    }

    @Test(expected = PrecisionLossException::class)
    fun `V-MAT-13 JPY rejects fractional digits exceeding scale 0`() {
        Money.fromDouble(5000.5, jpy)
    }

    @Test(expected = PrecisionLossException::class)
    fun `V-MAT-14 IDR rejects fractional digits exceeding scale 2`() {
        Money.fromDouble(15000.123, idr)
    }

    @Test
    fun `V-MAT-08 and V-MAT-09 zero and negative zero normalized to zero minor units`() {
        val zeroPos = Money.fromDouble(0.0, idr)
        val zeroNeg = Money.fromDouble(-0.0, idr)
        assertEquals(0L, zeroPos.toMinorUnits())
        assertEquals(0L, zeroNeg.toMinorUnits())
        assertEquals(BigDecimal.ZERO.compareTo(zeroNeg.amount), 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `V-MAT-10 NaN is explicitly rejected`() {
        Money.fromDouble(Double.NaN, idr)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `V-MAT-11a positive infinity is explicitly rejected`() {
        Money.fromDouble(Double.POSITIVE_INFINITY, idr)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `V-MAT-11b negative infinity is explicitly rejected`() {
        Money.fromDouble(Double.NEGATIVE_INFINITY, idr)
    }

    @Test
    fun `V-MAT-12 JPY integer double converts cleanly to minor units`() {
        val m = Money.fromDouble(5000.0, jpy)
        assertEquals(5000L, m.toMinorUnits())
    }
}
