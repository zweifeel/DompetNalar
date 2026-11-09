package com.example.util

import com.example.data.model.DomainCurrency
import java.util.Currency
import java.util.Locale

/**
 * P0-3: Centralized ISO-4217 Currency Registry.
 *
 * Provides:
 * 1. Exact exponent (decimal fraction digits) lookup.
 * 2. Dynamic scale divisor calculation (replaces hardcoded 100.0 division).
 * 3. Canonical minor units <-> display major units transformation preserving Room 11 integer invariants.
 * 4. Strict segregation: never fabricates FX exchange rates or merges distinct currencies.
 */
object CurrencyRegistry {

    /**
     * Returns the ISO-4217 decimal fraction digits (exponent) for [currencyCode].
     * E.g., JPY -> 0, KRW -> 0, USD -> 2, EUR -> 2, IDR -> 2, BHD -> 3.
     */
    fun getExponent(currencyCode: String?): Int {
        if (currencyCode.isNullOrBlank()) return 2
        val normalized = currencyCode.trim().uppercase(Locale.ROOT)
        return try {
            val domainCurr = DomainCurrency.fromCode(normalized)
            domainCurr.decimalDigits
        } catch (e: Exception) {
            try {
                val javaCurr = Currency.getInstance(normalized)
                javaCurr.defaultFractionDigits.coerceAtLeast(0)
            } catch (ex: Exception) {
                2
            }
        }
    }

    /**
     * Scale divisor corresponding to 10^[getExponent].
     * Replaces hardcoded 100.0 division.
     * E.g.:
     * - Exponent 0 (JPY) -> 1.0 (10^0)
     * - Exponent 2 (USD, EUR, IDR) -> 100.0 (10^2)
     * - Exponent 3 (BHD) -> 1000.0 (10^3)
     */
    fun getScaleDivisor(currencyCode: String?): Double {
        val exponent = getExponent(currencyCode)
        return Math.pow(10.0, exponent.toDouble()).coerceAtLeast(1.0)
    }

    /**
     * Converts integer canonical minor units to double for display formatting according to registry scale.
     */
    fun canonicalMinorUnitsToDisplay(minorUnits: Long, currencyCode: String?): Double {
        val divisor = getScaleDivisor(currencyCode)
        return minorUnits.toDouble() / divisor
    }

    /**
     * Converts display double to canonical minor units according to registry scale.
     */
    fun displayToCanonicalMinorUnits(displayAmount: Double, currencyCode: String?): Long {
        val divisor = getScaleDivisor(currencyCode)
        return Math.round(displayAmount * divisor)
    }

    /**
     * Formats canonical minor units into a localized currency string.
     * Returns a clear unassigned message if [currencyCode] is null or blank.
     */
    fun formatCanonicalMinorUnits(minorUnits: Long?, currencyCode: String?): String {
        if (currencyCode.isNullOrBlank() || minorUnits == null) {
            return if (LocaleHelper.isEnglish()) "No currency assigned" else "Belum ada mata uang"
        }
        val displayAmount = canonicalMinorUnitsToDisplay(minorUnits, currencyCode)
        return CurrencyFormatter.formatCurrency(displayAmount, currencyCode)
    }
}
