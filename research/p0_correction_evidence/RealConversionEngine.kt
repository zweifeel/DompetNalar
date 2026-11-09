package com.example.research

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.ulp

sealed class ConversionResult {
    data class Safe(val minorUnits: Long, val tier: String) : ConversionResult()
    data class PrecisionLoss(val rawAmount: Double, val reason: String) : ConversionResult()
    data class Overflow(val rawAmount: Double, val reason: String) : ConversionResult()
    data class Invalid(val rawAmount: Double, val reason: String) : ConversionResult()
}

object RealConversionEngine {
    private val CURRENCY_EXPONENTS = mapOf(
        "IDR" to 2,
        "USD" to 2,
        "EUR" to 2,
        "JPY" to 0,
        "BHD" to 3
    )

    private const val BUSINESS_SAFETY_CEILING_IDR = 900_000_000_000.0 // Rp 900 Miliar approved personal limit

    fun convert(
        amountReal: Double,
        currencyCode: String,
        fieldName: String,
        allowNegative: Boolean = false
    ): ConversionResult {
        val exponent = CURRENCY_EXPONENTS[currencyCode]
            ?: return ConversionResult.Invalid(amountReal, "UNKNOWN_CURRENCY_$currencyCode")

        if (amountReal.isNaN()) return ConversionResult.Invalid(amountReal, "NAN_VALUE")
        if (amountReal.isInfinite()) return ConversionResult.Invalid(amountReal, "INFINITY_VALUE")

        if (amountReal == 0.0) {
            return ConversionResult.Safe(0L, "SAFE_UNDER_EXPLICIT_NORMALIZATION_POLICY")
        }

        if (amountReal < 0.0 && !allowNegative) {
            return ConversionResult.Invalid(amountReal, "NEGATIVE_NOT_PERMITTED_FOR_$fieldName")
        }

        val absAmount = abs(amountReal)
        if (currencyCode == "IDR" && absAmount > BUSINESS_SAFETY_CEILING_IDR) {
            return ConversionResult.Overflow(amountReal, "EXCEEDS_BUSINESS_CEILING_$BUSINESS_SAFETY_CEILING_IDR")
        }

        val factor = BigDecimal.TEN.pow(exponent)
        val maxMajorUnits = Long.MAX_VALUE / factor.toLong()
        if (absAmount > maxMajorUnits) {
            return ConversionResult.Overflow(amountReal, "EXCEEDS_SIGNED_LONG_MAX")
        }

        // Canonical decimal parsing via BigDecimal.valueOf(Double)
        val decimal = BigDecimal.valueOf(amountReal)
        val normalized = decimal.stripTrailingZeros()
        val effectiveScale = maxOf(0, normalized.scale())

        val ulp = absAmount.ulp
        val minorUnitStep = 1.0 / factor.toDouble()

        return if (effectiveScale <= exponent) {
            val minorUnits = decimal.multiply(factor).setScale(0, RoundingMode.UNNECESSARY).toLong()
            ConversionResult.Safe(minorUnits, "SAFE_UNDER_EXPLICIT_NORMALIZATION_POLICY")
        } else {
            val residual = absAmount % minorUnitStep
            if (residual < ulp) {
                val minorUnits = decimal.multiply(factor).setScale(0, RoundingMode.HALF_EVEN).toLong()
                ConversionResult.Safe(minorUnits, "AMBIGUOUS_FLOATING_POINT_NOISE")
            } else {
                ConversionResult.PrecisionLoss(amountReal, "GENUINE_EXCESS_FRACTIONAL_SEN_SCALE_$effectiveScale")
            }
        }
    }
}
