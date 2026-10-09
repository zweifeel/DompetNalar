package com.example.util

import com.example.data.model.ReceiptItem
import com.example.data.model.TaxTreatment

/**
 * Deterministic business logic and math calculations for Receipt OCR,
 * discount extraction, and tax treatment (inclusive vs exclusive).
 */
object ReceiptCalculator {

    /**
     * Determines whether tax is INCLUSIVE, EXCLUSIVE, or UNKNOWN.
     * Prioritizes mathematical evidence from the receipt, backed by contextual keywords.
     */
    fun determineTaxTreatment(
        merchantName: String = "",
        rawText: String? = null,
        subtotal: Double,
        tax: Double,
        service: Double = 0.0,
        discount: Double = 0.0,
        totalAmount: Double = 0.0
    ): TaxTreatment {
        if (tax <= 0.0) {
            return TaxTreatment.EXCLUSIVE
        }

        // Arithmetic check with small epsilon tolerance (1.5 IDR for rounding)
        val isInclusiveArithmetically = totalAmount > 0.0 &&
                Math.abs((subtotal + service - discount) - totalAmount) <= 1.5

        val isExclusiveArithmetically = totalAmount > 0.0 &&
                Math.abs(((subtotal + tax + service) - discount) - totalAmount) <= 1.5

        if (isInclusiveArithmetically && !isExclusiveArithmetically) {
            return TaxTreatment.INCLUSIVE
        }
        if (isExclusiveArithmetically && !isInclusiveArithmetically) {
            return TaxTreatment.EXCLUSIVE
        }

        // Contextual keywords check
        val combinedText = (merchantName + " " + (rawText ?: "")).lowercase()
        val isRetailKeyword = combinedText.contains("indomaret") ||
                combinedText.contains("alfamart") ||
                combinedText.contains("superindo") ||
                combinedText.contains("hypermart") ||
                combinedText.contains("transmart") ||
                combinedText.contains("circle k") ||
                combinedText.contains("lawson") ||
                combinedText.contains("dpp=") ||
                combinedText.contains("non ppn")

        if (isRetailKeyword) {
            return TaxTreatment.INCLUSIVE
        }

        val isRestaurantKeyword = combinedText.contains("pb1") ||
                combinedText.contains("resto") ||
                combinedText.contains("restaurant") ||
                combinedText.contains("cafe") ||
                combinedText.contains("service charge")

        if (isRestaurantKeyword) {
            return TaxTreatment.EXCLUSIVE
        }

        return TaxTreatment.UNKNOWN
    }

    /**
     * Calculates the total amount based on items subtotal, tax, service, discount, and tax treatment.
     */
    fun calculateTotal(
        subtotal: Double,
        tax: Double,
        service: Double = 0.0,
        discount: Double = 0.0,
        taxTreatment: TaxTreatment,
        fallbackTotal: Double = 0.0
    ): Double {
        return when (taxTreatment) {
            TaxTreatment.INCLUSIVE -> {
                (subtotal + service - discount).coerceAtLeast(0.0)
            }
            TaxTreatment.EXCLUSIVE -> {
                (subtotal + tax + service - discount).coerceAtLeast(0.0)
            }
            TaxTreatment.UNKNOWN -> {
                if (fallbackTotal > 0.0) {
                    fallbackTotal
                } else {
                    // Safe fallback: do not add unverified tax
                    (subtotal + service - discount).coerceAtLeast(0.0)
                }
            }
        }
    }

    /**
     * Recalculates subtotal and total when items, tax, service, discount, or tax treatment change.
     */
    fun recalculate(
        items: List<ReceiptItem>,
        tax: Double,
        service: Double = 0.0,
        discount: Double = 0.0,
        taxTreatment: TaxTreatment,
        fallbackTotal: Double = 0.0
    ): Pair<Double, Double> {
        val newSubtotal = items.sumOf { it.total }
        val newTotal = calculateTotal(
            subtotal = newSubtotal,
            tax = tax,
            service = service,
            discount = discount,
            taxTreatment = taxTreatment,
            fallbackTotal = fallbackTotal
        )
        return Pair(newSubtotal, newTotal)
    }

    /**
     * Parses an Indonesian / receipt number string safely.
     * Handles:
     * - Parentheses denoting discount: "(10,600)" or "(10.600)" -> 10600.0
     * - Negative signs: "-10,600" -> 10600.0 (returns positive magnitude for discount)
     * - Thousand separators: "103,600" or "103.600" -> 103600.0
     * - Decimal separators: "10.50" or "10,50" -> 10.5
     * - Prefix "Rp" or "IDR"
     */
    fun parseAmountSafe(raw: Any?, defaultVal: Double = 0.0): Double {
        if (raw == null) return defaultVal
        if (raw is Number) return Math.abs(raw.toDouble())

        val str = raw.toString().trim()
        if (str.isBlank()) return defaultVal

        val cleaned = str.replace("Rp", "", ignoreCase = true)
            .replace("IDR", "", ignoreCase = true)
            .replace("(", "")
            .replace(")", "")
            .replace("-", "")
            .trim()

        if (cleaned.isBlank()) return defaultVal

        // If string contains both '.' and ','
        if (cleaned.contains('.') && cleaned.contains(',')) {
            val lastDot = cleaned.lastIndexOf('.')
            val lastComma = cleaned.lastIndexOf(',')
            val normalized = if (lastComma > lastDot) {
                // e.g. 10.600,50 -> dot is thousand, comma is decimal
                cleaned.replace(".", "").replace(',', '.')
            } else {
                // e.g. 10,600.50 -> comma is thousand, dot is decimal
                cleaned.replace(",", "")
            }
            return normalized.toDoubleOrNull()?.let { Math.abs(it) } ?: defaultVal
        }

        // If string contains only ','
        if (cleaned.contains(',')) {
            val parts = cleaned.split(',')
            val allAfterFirstAre3Digits = parts.drop(1).all { it.length == 3 }
            if (parts.size > 1 && allAfterFirstAre3Digits) {
                val noComma = cleaned.replace(",", "")
                return noComma.toDoubleOrNull()?.let { Math.abs(it) } ?: defaultVal
            } else {
                val withDot = cleaned.replace(',', '.')
                return withDot.toDoubleOrNull()?.let { Math.abs(it) } ?: defaultVal
            }
        }

        // If string contains only '.'
        if (cleaned.contains('.')) {
            val parts = cleaned.split('.')
            val allAfterFirstAre3Digits = parts.drop(1).all { it.length == 3 }
            if (parts.size > 1 && allAfterFirstAre3Digits) {
                val noDot = cleaned.replace(".", "")
                return noDot.toDoubleOrNull()?.let { Math.abs(it) } ?: defaultVal
            } else {
                return cleaned.toDoubleOrNull()?.let { Math.abs(it) } ?: defaultVal
            }
        }

        val digitsOnly = cleaned.filter { it.isDigit() || it == '.' }
        return digitsOnly.toDoubleOrNull()?.let { Math.abs(it) } ?: defaultVal
    }
}
