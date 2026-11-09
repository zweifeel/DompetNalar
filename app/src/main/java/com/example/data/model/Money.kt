package com.example.data.model

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.math.abs

class CurrencyMismatchException(message: String) : IllegalArgumentException(message)
class PrecisionLossException(message: String) : ArithmeticException(message)

/**
 * Domain Value Object representing a monetary amount bound to an ISO 4217 Currency.
 * All internal arithmetic uses BigDecimal to preserve decimal precision.
 */
data class Money(
    val amount: BigDecimal,
    val currency: DomainCurrency
) : Comparable<Money> {

    operator fun plus(other: Money): Money {
        checkCurrency(other)
        return Money(amount.add(other.amount), currency)
    }

    operator fun minus(other: Money): Money {
        checkCurrency(other)
        return Money(amount.subtract(other.amount), currency)
    }

    operator fun times(factor: BigDecimal): Money {
        return Money(amount.multiply(factor), currency)
    }

    operator fun times(factor: Long): Money {
        return Money(amount.multiply(BigDecimal.valueOf(factor)), currency)
    }

    operator fun times(factor: Double): Money {
        return Money(amount.multiply(BigDecimal.valueOf(factor)), currency)
    }

    operator fun unaryMinus(): Money {
        return Money(amount.negate(), currency)
    }

    /**
     * Quantizes the amount to the currency's standard scale (decimal digits)
     * using the specified RoundingMode (defaults to HALF_EVEN / Banker's Rounding).
     */
    fun quantized(roundingMode: RoundingMode = RoundingMode.HALF_EVEN): Money {
        return Money(amount.setScale(currency.decimalDigits, roundingMode), currency)
    }

    fun toDouble(): Double {
        return amount.toDouble()
    }

    /**
     * Converts to canonical minor units as an exact 64-bit Long.
     * Prevents any fractional loss or silent integer truncation.
     */
    fun toMinorUnits(): Long {
        return quantized(RoundingMode.HALF_EVEN).amount
            .movePointRight(currency.decimalDigits)
            .toBigIntegerExact()
            .longValueExact()
    }

    override fun compareTo(other: Money): Int {
        checkCurrency(other)
        return amount.compareTo(other.amount)
    }

    private fun checkCurrency(other: Money) {
        if (currency != other.currency) {
            throw CurrencyMismatchException(
                "Cannot perform operation between different currencies: ${currency.code} and ${other.currency.code}"
            )
        }
    }

    companion object {
        private const val DOUBLE_PRECISION_LIMIT = 9007199254740992.0 // 2^53

        fun zero(currency: DomainCurrency): Money {
            return Money(BigDecimal.ZERO.setScale(currency.decimalDigits), currency)
        }

        fun of(value: Double, currency: DomainCurrency): Money = fromDouble(value, currency)

        fun of(value: BigDecimal, currency: DomainCurrency): Money = Money(value, currency)

        fun fromMinorUnits(minorUnits: Long, currency: DomainCurrency): Money {
            val bd = BigDecimal.valueOf(minorUnits).movePointLeft(currency.decimalDigits)
            return Money(bd, currency)
        }

        fun fromDouble(value: Double, currency: DomainCurrency): Money {
            require(value.isFinite()) { "Value must be finite: $value" }
            val normalized = if (value == 0.0) 0.0 else value
            if (abs(normalized) >= DOUBLE_PRECISION_LIMIT) {
                throw PrecisionLossException(
                    "Double value $normalized exceeds IEEE-754 exact integer limit (2^53 = $DOUBLE_PRECISION_LIMIT)"
                )
            }
            val bd = BigDecimal.valueOf(normalized)
            val normalizedBd = if (bd.signum() == 0) BigDecimal.ZERO else bd.stripTrailingZeros()
            val effectiveScale = maxOf(0, normalizedBd.scale())
            if (effectiveScale > currency.decimalDigits) {
                throw PrecisionLossException(
                    "Double value $normalized has scale $effectiveScale exceeding currency ${currency.code} fraction digits (${currency.decimalDigits})"
                )
            }
            return Money(bd, currency)
        }

        /**
         * Scale-Safe and Sign-Safe Largest Remainder Method (Hamilton-Hare method).
         * Guarantees exact minor unit conservation: sum(shares) == quantized(total).
         */
        fun divideAndDistribute(total: Money, parts: Int): List<Money> {
            require(parts > 0) { "Parts must be strictly positive" }
            val sign = total.amount.signum()
            if (sign == 0) return List(parts) { zero(total.currency) }

            val quantizedTotal = total.quantized(RoundingMode.HALF_EVEN)
            val unit = BigDecimal.ONE.movePointLeft(quantizedTotal.currency.decimalDigits)

            val totalUnits = quantizedTotal.amount.abs().divide(unit).toBigIntegerExact()
            val baseUnitsPerPart = totalUnits.divide(BigInteger.valueOf(parts.toLong()))
            val remainderUnits = totalUnits.remainder(BigInteger.valueOf(parts.toLong())).toInt()

            return List(parts) { index ->
                val allocatedUnits = if (index < remainderUnits) baseUnitsPerPart + BigInteger.ONE else baseUnitsPerPart
                val signedAmount = BigDecimal(allocatedUnits).multiply(unit).multiply(BigDecimal.valueOf(sign.toLong()))
                Money(signedAmount, total.currency)
            }
        }
    }
}
