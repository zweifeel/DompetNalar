package com.example

import com.example.data.model.DomainCurrency
import com.example.data.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class SplitBillPennyAllocationTest {

    private val usd = DomainCurrency.fromCode("USD")
    private val jpy = DomainCurrency.fromCode("JPY")
    private val kwd = DomainCurrency.fromCode("KWD") // 3 decimal digits

    @Test
    fun `T-22 Largest Remainder Method distributes odd pennies with zero sum loss for USD`() {
        // $100.00 split among 3 people: 33.34, 33.33, 33.33 -> sum = 100.00
        val total = Money(BigDecimal("100.00"), usd)
        val shares = Money.divideAndDistribute(total, 3)

        assertEquals(3, shares.size)
        assertEquals(BigDecimal("33.34"), shares[0].amount)
        assertEquals(BigDecimal("33.33"), shares[1].amount)
        assertEquals(BigDecimal("33.33"), shares[2].amount)

        val sum = shares.reduce { acc, m -> acc + m }
        assertEquals(total.amount, sum.amount)
    }

    @Test
    fun `T-22 Largest Remainder Method preserves exact sum for zero-decimal JPY`() {
        // 100000 JPY split among 3 people: 33334, 33333, 33333 -> sum = 100000
        val total = Money(BigDecimal("100000"), jpy)
        val shares = Money.divideAndDistribute(total, 3)

        assertEquals(3, shares.size)
        assertEquals(BigDecimal("33334"), shares[0].amount)
        assertEquals(BigDecimal("33333"), shares[1].amount)
        assertEquals(BigDecimal("33333"), shares[2].amount)

        val sum = shares.reduce { acc, m -> acc + m }
        assertEquals(total.amount, sum.amount)
    }

    @Test
    fun `T-22 Largest Remainder Method handles negative totals preserving exact sign and sum`() {
        // -$10.00 split among 3: -3.34, -3.33, -3.33 -> sum = -10.00
        val total = Money(BigDecimal("-10.00"), usd)
        val shares = Money.divideAndDistribute(total, 3)

        assertEquals(3, shares.size)
        assertEquals(BigDecimal("-3.34"), shares[0].amount)
        assertEquals(BigDecimal("-3.33"), shares[1].amount)
        assertEquals(BigDecimal("-3.33"), shares[2].amount)

        val sum = shares.reduce { acc, m -> acc + m }
        assertEquals(total.amount, sum.amount)
    }

    @Test
    fun `T-22 Largest Remainder Method handles 3-decimal currencies`() {
        // 10.000 KWD split among 3: 3.334, 3.333, 3.333
        val total = Money(BigDecimal("10.000"), kwd)
        val shares = Money.divideAndDistribute(total, 3)

        assertEquals(3, shares.size)
        assertEquals(BigDecimal("3.334"), shares[0].amount)
        assertEquals(BigDecimal("3.333"), shares[1].amount)
        assertEquals(BigDecimal("3.333"), shares[2].amount)

        val sum = shares.reduce { acc, m -> acc + m }
        assertEquals(total.amount, sum.amount)
    }
}
