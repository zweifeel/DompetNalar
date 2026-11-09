package com.example.util

import java.math.BigDecimal
import java.math.BigInteger

sealed class ConversionResult {
    data class Safe(val minorUnits: Long) : ConversionResult()
    data class PrecisionAmbiguous(val raw: Double, val reason: String) : ConversionResult()
    data class MathematicalOverflow(val raw: Double, val reason: String) : ConversionResult()
    data class BusinessCeilingExceeded(val raw: Double, val reason: String) : ConversionResult()
}

/**
 * P0-A Real Conversion Engine for Room 11 Migration and runtime monetary conversions.
 * Evaluates mathematical boundaries using BigDecimal and BigInteger post-shift,
 * preventing silent truncation, floating-point ambiguity, and overflow.
 */
object RealConversionEngine {
    private val BIG_DECIMAL_LONG_MAX = BigDecimal(Long.MAX_VALUE)
    private val BIG_DECIMAL_LONG_MIN = BigDecimal(Long.MIN_VALUE)
    // Plafon Kebijakan Bisnis IDR: Rp 900 Miliar (900_000_000_000 * 10^2)
    private val IDR_BUSINESS_CEILING_MINOR = BigDecimal("90000000000000")

    fun convert(legacyDouble: Double, currencyCode: String, currencyExponent: Int): ConversionResult {
        if (!legacyDouble.isFinite()) {
            return if (legacyDouble.isNaN()) {
                ConversionResult.PrecisionAmbiguous(legacyDouble, "NAN_VALUE")
            } else {
                ConversionResult.MathematicalOverflow(legacyDouble, "INFINITY_VALUE")
            }
        }

        val normalizedDouble = if (legacyDouble == 0.0) 0.0 else legacyDouble
        val decimal = BigDecimal.valueOf(normalizedDouble)
        val normalized = if (decimal.signum() == 0) BigDecimal.ZERO else decimal.stripTrailingZeros()
        val effectiveScale = maxOf(0, normalized.scale())

        // 1. Skala desimal melebihi eksponen mata uang -> Karantina Ambigu
        if (effectiveScale > currencyExponent) {
            return ConversionResult.PrecisionAmbiguous(legacyDouble, "EXCESS_SCALE_$effectiveScale")
        }

        // 2. Shift eksponen secara eksak via BigDecimal
        val minorUnitsDec = decimal.movePointRight(currencyExponent)

        // 3. Evaluasi Batas Long 64-bit Bertanda
        if (minorUnitsDec.compareTo(BIG_DECIMAL_LONG_MAX) > 0 || minorUnitsDec.compareTo(BIG_DECIMAL_LONG_MIN) < 0) {
            return ConversionResult.MathematicalOverflow(legacyDouble, "EXCEEDS_64BIT_SIGNED_RANGE")
        }

        // 4. Evaluasi Plafon Kebijakan Bisnis (HANYA BERLAKU UNTUK IDR)
        if (currencyCode.equals("IDR", ignoreCase = true)) {
            if (minorUnitsDec.abs().compareTo(IDR_BUSINESS_CEILING_MINOR) > 0) {
                return ConversionResult.BusinessCeilingExceeded(legacyDouble, "EXCEEDS_IDR_900B_CEILING")
            }
        }

        // 5. Ekstraksi Nilai Integer Eksak
        val minorUnits = minorUnitsDec.toBigIntegerExact().toLong()
        return ConversionResult.Safe(minorUnits)
    }
}
